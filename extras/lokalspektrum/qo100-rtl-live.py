#!/usr/bin/env python3
"""Live benchmark for the QO-100 wideband downlink using one RTL-SDR."""
import argparse
import asyncio
import ctypes as C
import logging
import threading
import time

import numpy as np
from aiohttp import web


SAMPLE_RATE = 2_560_000
FFT_SIZE = 512
DISCARD_SAMPLES = 16_384
CAPTURE_SAMPLES = 65_536
LNB_LO_HZ = 9_750_000_000
IF_HZ = np.linspace(740_500_000, 749_500_000, 922)
# The first displayed frequency is still covered by two tiles without the lower outer tile.
CENTERS_HZ = [739_000_000 + round(index * 2_000_000 / 3) for index in range(2, 18)]
FFT_OFFSETS = np.fft.fftshift(np.fft.fftfreq(FFT_SIZE, 1 / SAMPLE_RATE))
WINDOW = np.hanning(FFT_SIZE).astype(np.float32)
WINDOW_POWER = np.sum(WINDOW**2)


class RtlStick:
    def __init__(self, index, gain_db):
        self.lib = C.CDLL("librtlsdr.so.0")
        signatures = {
            "open": [C.POINTER(C.c_void_p), C.c_uint32],
            "close": [C.c_void_p],
            "set_sample_rate": [C.c_void_p, C.c_uint32],
            "set_center_freq": [C.c_void_p, C.c_uint32],
            "set_tuner_gain_mode": [C.c_void_p, C.c_int],
            "get_tuner_gains": [C.c_void_p, C.POINTER(C.c_int)],
            "set_tuner_gain": [C.c_void_p, C.c_int],
            "reset_buffer": [C.c_void_p],
            "read_sync": [C.c_void_p, C.c_void_p, C.c_int, C.POINTER(C.c_int)],
        }
        for name, parameters in signatures.items():
            function = getattr(self.lib, "rtlsdr_" + name)
            function.argtypes = parameters
            function.restype = C.c_int

        self.device = C.c_void_p()
        self.discard = (C.c_uint8 * (2 * DISCARD_SAMPLES))()
        self.samples = (C.c_uint8 * (2 * CAPTURE_SAMPLES))()
        self.check("open", C.byref(self.device), index)
        try:
            self.check("set_sample_rate", self.device, SAMPLE_RATE)
            self.check("set_tuner_gain_mode", self.device, 1)
            count = self.lib.rtlsdr_get_tuner_gains(self.device, None)
            if count <= 0:
                raise OSError("Der Tuner meldet keine verfügbaren Verstärkungsstufen.")
            gains = (C.c_int * count)()
            if self.lib.rtlsdr_get_tuner_gains(self.device, gains) != count:
                raise OSError("Verstärkungsstufen konnten nicht gelesen werden.")
            gain = min(gains, key=lambda value: abs(value / 10 - gain_db))
            self.check("set_tuner_gain", self.device, gain)
            self.gain_db = gain / 10
        except BaseException:
            self.close()
            raise

    def check(self, name, *args):
        code = getattr(self.lib, "rtlsdr_" + name)(*args)
        if code:
            raise OSError(f"rtlsdr_{name}: Fehler {code}")

    def read(self, buffer):
        actual = C.c_int()
        self.check("read_sync", self.device, buffer, len(buffer), C.byref(actual))
        if actual.value != len(buffer):
            raise OSError(f"Unvollständige USB-Messung ({actual.value}/{len(buffer)} Bytes).")

    def spectrum(self, center):
        self.check("set_center_freq", self.device, center)
        self.check("reset_buffer", self.device)
        self.read(self.discard)
        self.read(self.samples)
        iq = np.ctypeslib.as_array(self.samples).astype(np.float32).reshape(-1, 2)
        iq = (iq - 127.5) / 128
        iq = (iq[:, 0] + 1j * iq[:, 1]).reshape(-1, FFT_SIZE)
        fft = np.fft.fft(iq * WINDOW, axis=1)
        power = np.mean(fft.real**2 + fft.imag**2, axis=0) / WINDOW_POWER
        return np.fft.fftshift(power)

    def close(self):
        self.lib.rtlsdr_close(self.device)


class Monitor:
    def __init__(self, index, gain_db):
        self.index = index
        self.gain_db = gain_db
        self.stop = threading.Event()
        self.snapshot = {"message": "Starte RTL-SDR …"}
        self.tiles = []
        self.weight_sum = np.zeros_like(IF_HZ)
        for center in CENTERS_HZ:
            offset = IF_HZ - center
            from_center = abs(offset)
            from_edge = 1_000_000 - from_center
            weight = np.clip((from_edge - 80_000) / 210_000, 0, 1)
            weight *= np.clip((from_center - 25_000) / 55_000, 0, 1)
            mask = weight > 0
            self.tiles.append((mask, offset[mask], weight[mask]))
            self.weight_sum += weight
        if not np.all(self.weight_sum > 0):
            raise ValueError("Die Teilbänder decken das Spektrum nicht vollständig ab.")

    def run(self):
        while not self.stop.is_set():
            stick = None
            try:
                stick = RtlStick(self.index, self.gain_db)
                self.scan(stick)
            except OSError as error:
                message = f"RTL-SDR: {error}"
                self.snapshot = {"message": message}
                logging.warning(message)
            finally:
                if stick is not None:
                    stick.close()
            self.stop.wait(2)

    def scan(self, stick):
        levels = [np.zeros(len(tile[1])) for tile in self.tiles]
        frame = 0
        round_ms = None
        while not self.stop.is_set():
            started = time.monotonic()
            for index, center in enumerate(CENTERS_HZ):
                if self.stop.is_set():
                    return
                mask, offset, weight = self.tiles[index]
                power = stick.spectrum(center)
                levels[index] = np.interp(offset, FFT_OFFSETS, power) * weight

                if index == len(CENTERS_HZ) - 1:
                    round_ms = round((time.monotonic() - started) * 1000)
                if round_ms is None:
                    self.snapshot = {"message": f"Erster Scan · {index + 1}/{len(self.tiles)} Teilbänder"}
                    continue

                # Combine the most recent measurement from each tile in linear power.
                merged = np.zeros_like(IF_HZ)
                for (tile_mask, _, _), level in zip(self.tiles, levels):
                    merged[tile_mask] += level
                db = 10 * np.log10(np.maximum(merged / self.weight_sum, 1e-15))
                floor = float(np.percentile(db, 25))
                frame += 1
                self.snapshot = {
                    "frame": frame,
                    "round_ms": round_ms,
                    "updated_at": time.monotonic(),
                    "gain_db": stick.gain_db,
                    "levels": np.round(db - floor, 2).tolist(),
                }


PAGE = """<!doctype html>
<html lang="de">
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>QO-100 RTL-Live-Test</title>
<style>
  :root { color-scheme: dark; font: 16px system-ui, sans-serif; background: #101713; color: #e3efe7; }
  body { margin: 0; }
  main { max-width: 1080px; margin: 0 auto; padding: clamp(16px, 4vw, 42px); }
  h1 { margin: 0 0 8px; font-size: clamp(22px, 4vw, 32px); }
  p { margin: 0 0 18px; color: #b2c9ba; }
  canvas { width: 100%; height: clamp(230px, 52vh, 480px); display: block;
    background: #0d1610; border: 1px solid #345044; border-radius: 12px; }
</style>
<main>
  <h1>QO-100 RTL-Live-Test</h1>
  <p id="status" role="status">Verbinde …</p>
  <canvas id="spectrum" role="img" aria-label="QO-100-DATV-Spektrum von 10.490,5 bis 10.499,5 MHz"></canvas>
</main>
<script>
"use strict";
const canvas = document.querySelector("#spectrum");
const statusLine = document.querySelector("#status");
const ctx = canvas.getContext("2d");
const number = new Intl.NumberFormat("de-DE", {maximumFractionDigits: 1});
const frequency = new Intl.NumberFormat("de-DE", {maximumFractionDigits: 1, useGrouping: true});
const smoothingMs = 650;
const negativeRangeDb = 6;
const spaceBelowZero = 0.08;
const startMHz = 10490.5;
const stopMHz = 10499.5;
const spurMHz = 10498.802;
// Approximate the measured rise in the right-hand noise floor, in dB.
const floorProfile = [
  [10496.0, 0], [10496.8, 0.7], [10497.35, 1.2],
  [10498.1, 2.0], [10499.0, 2.7], [10499.5, 1.8]
];
const minimumUpperDb = 15;
let levels = [];
let lastFrame = -1;
let lastUpdate = 0;

function floorCorrection(mhz) {
  const upper = floorProfile.findIndex(([frequency]) => frequency >= mhz);
  if (upper === 0) return 0;
  if (upper === -1) return floorProfile.at(-1)[1];
  const [leftMHz, leftDb] = floorProfile[upper - 1];
  const [rightMHz, rightDb] = floorProfile[upper];
  const fraction = (mhz - leftMHz) / (rightMHz - leftMHz);
  return leftDb + fraction * (rightDb - leftDb);
}

function correctTrace(values) {
  const stepMHz = (stopMHz - startMHz) / (values.length - 1);
  const spurBin = Math.round((spurMHz - startMHz) / stepMHz);
  const maskedBins = Math.floor(0.015 / stepMHz);
  const left = spurBin - maskedBins - 1;
  const right = spurBin + maskedBins + 1;
  return values.map((value, index) => {
    const mhz = startMHz + stepMHz * index;
    const correction = floorCorrection(mhz);
    if (left < 0 || right >= values.length || index <= left || index >= right) {
      return value - correction;
    }
    const fraction = (index - left) / (right - left);
    return values[left] + fraction * (values[right] - values[left]) - correction;
  });
}

function updateCurve(data, now) {
  if (!data?.levels?.length) {
    levels = [];
    lastFrame = -1;
    return;
  }
  if (data.frame === lastFrame) return;

  const values = correctTrace(data.levels);
  const filtered = values.map((value, index) =>
    0.8 * value + 0.1 * (values[index - 1] ?? value) + 0.1 * (values[index + 1] ?? value)
  );
  if (levels.length !== filtered.length || data.frame < lastFrame) {
    levels = filtered;
  } else {
    const weight = 1 - Math.exp(-(now - lastUpdate) / smoothingMs);
    levels = levels.map((previous, index) => previous + weight * (filtered[index] - previous));
  }
  lastFrame = data.frame;
  lastUpdate = now;
}

function draw() {
  const w = canvas.clientWidth;
  const h = canvas.clientHeight;
  const ratio = window.devicePixelRatio || 1;
  canvas.width = Math.round(w * ratio);
  canvas.height = Math.round(h * ratio);
  ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
  const left = 38, right = w - 16, top = 18, bottom = h - 42;
  const limit = Math.max(minimumUpperDb, 3 * Math.ceil((Math.max(...levels) + 1) / 3));
  const px = fraction => left + fraction * (right - left);
  const py = level => {
    const position = level < 0
      ? spaceBelowZero * (1 + Math.max(-negativeRangeDb, level) / negativeRangeDb)
      : spaceBelowZero + (1 - spaceBelowZero) * level / limit;
    return bottom - position * (bottom - top);
  };
  ctx.font = "12px system-ui";
  ctx.strokeStyle = "#293e32";
  ctx.fillStyle = "#b2c9ba";
  ctx.textAlign = "right";
  for (let db = 0; db <= limit; db += 3) {
    const y = py(db);
    ctx.beginPath(); ctx.moveTo(left, y); ctx.lineTo(right, y); ctx.stroke();
    ctx.fillText(db, left - 7, y + 4);
  }
  const ticks = w < 540 ? [0, 4.5, 9] : [0, 1.5, 3, 4.5, 6, 7.5, 9];
  for (const mhz of ticks) {
    const x = px(mhz / 9);
    ctx.beginPath(); ctx.moveTo(x, top); ctx.lineTo(x, bottom); ctx.stroke();
    ctx.textAlign = mhz === 0 ? "left" : mhz === 9 ? "right" : "center";
    ctx.fillText(frequency.format(startMHz + mhz), x, bottom + 17);
  }
  ctx.textAlign = "center";
  ctx.fillText("Downlink (MHz)", px(.5), bottom + 34);
  ctx.textAlign = "left";
  ctx.fillText("dB über Rauschboden", left + 5, top + 14);
  if (!levels.length) return;
  const trace = new Path2D();
  levels.forEach((value, i) => {
    const x = px(i / (levels.length - 1)), y = py(value);
    if (i === 0) trace.moveTo(x, y); else trace.lineTo(x, y);
  });
  const area = new Path2D(trace);
  area.lineTo(right, bottom); area.lineTo(left, bottom); area.closePath();
  ctx.fillStyle = "#287e5060";
  ctx.fill(area);
  ctx.strokeStyle = "#87e1ab";
  ctx.lineWidth = 1.4;
  ctx.stroke(trace);
}

async function refresh() {
  try {
    const response = await fetch("/api/status", {cache: "no-store"});
    if (!response.ok) throw new Error("HTTP " + response.status);
    const data = await response.json();
    updateCurve(data, performance.now());
    if (data.levels) {
      statusLine.textContent = "Live · Vollscan " + number.format(data.round_ms / 1000)
        + " s · Gain " + number.format(data.gain_db) + " dB";
    } else {
      statusLine.textContent = data.message;
    }
  } catch (error) {
    updateCurve(null);
    statusLine.textContent = "Verbindung zum Pi unterbrochen.";
  }
  draw();
  setTimeout(refresh, 200);
}

new ResizeObserver(draw).observe(canvas);
refresh();
</script>
"""


def create_app(monitor):
    app = web.Application()

    async def run_monitor(app):
        task = asyncio.create_task(asyncio.to_thread(monitor.run))
        yield
        monitor.stop.set()
        await task

    async def index(request):
        return web.Response(text=PAGE, content_type="text/html")

    async def status(request):
        snapshot = monitor.snapshot
        if "updated_at" in snapshot:
            age = time.monotonic() - snapshot["updated_at"]
            if age > 3:
                snapshot = {"message": "Warte auf neue RTL-Messwerte …"}
            else:
                snapshot = {**snapshot, "age_ms": round(age * 1000)}
                del snapshot["updated_at"]
        return web.json_response(snapshot, headers={"Cache-Control": "no-store"})

    app.cleanup_ctx.append(run_monitor)
    app.router.add_get("/", index)
    app.router.add_get("/api/status", status)
    return app


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=8097)
    parser.add_argument("--device", type=int, default=0)
    parser.add_argument("--gain", type=float, default=20)
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO)
    web.run_app(create_app(Monitor(args.device, args.gain)), host="0.0.0.0", port=args.port,
                access_log=None)


if __name__ == "__main__":
    main()
