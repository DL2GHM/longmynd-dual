#!/usr/bin/env python3
"""Serve the local QO-100 wideband spectrum from one RTL-SDR."""

import argparse
import asyncio
import logging
import math
import struct
import time
from contextlib import suppress
from pathlib import Path

import numpy as np
from aiohttp import WSMsgType, web

from rtl_spectrum import IF_HZ, Monitor, RtlDisplay

BINS = 922
DISPLAY_GAIN = 1.4
DISPLAY_KNEE_DB = 13.2
DISPLAY_TOP_DB = 13.9
BAND_START = 10_490_500_000
BAND_WIDTH = 9_000_000
PROTOCOLS = ("fft", "fft_m0dtslivetune", "fft_fast",
             "fft_ea7kirsatcontroller")


def encode_levels(levels):
    return np.clip(np.rint(9000 + 1250 * levels), 0, 65535).astype(int).tolist()


def display_levels(levels):
    scaled = levels * DISPLAY_GAIN
    headroom = DISPLAY_TOP_DB - DISPLAY_KNEE_DB
    above_knee = np.maximum(scaled - DISPLAY_KNEE_DB, 0)
    return np.minimum(scaled, DISPLAY_KNEE_DB) - headroom * np.expm1(-above_knee / headroom)


def estimate_signals(bins):
    """Estimate DATV widths using the Longmynd Mobil spectrum detector."""
    count = len(bins)
    noise = sorted(value for value in bins if value > 0)
    if not noise:
        return []
    floor = noise[len(noise) // 5]
    threshold = floor + 2500
    span_khz = BAND_WIDTH // 1000
    start_khz = BAND_START // 1000
    rates = ((22, 0), (65, 33), (86, 66), (195, 125), (277, 250),
             (388, 333), (700, 500), (1200, 1000), (1600, 1500), (2200, 2000))
    smooth = [(bins[max(0, i - 1)] + value + bins[min(count - 1, i + 1)]) / 3
              for i, value in enumerate(bins)]
    signals = []
    start = None
    for i, level in enumerate((*smooth, 0)):
        if level > threshold:
            if start is None:
                start = i
            continue
        if start is None:
            continue
        left, right = start, i - 1
        start = None
        if right - left < 2:
            continue
        middle = smooth[left + (right - left) // 3:left + max(1, 4 * (right - left) // 5)]
        peak = sum(middle) / len(middle)
        shoulder = floor + .75 * (peak - floor)
        while left < right and smooth[left] < shoulder:
            left += 1
        while left < right and smooth[right] < shoulder:
            right -= 1
        width = (right - left) * span_khz / count
        rate = next((rate for bound, rate in rates if width < bound), round(width / 200) * 200)
        if rate:
            signals.append({
                "frequency_khz": round(start_khz + ((left + right) / 2 + 1) * span_khz / count),
                "symbolrate_ksps": rate,
                "start_khz": round(start_khz + left * span_khz / count),
                "end_khz": round(start_khz + (right + 1) * span_khz / count),
            })
    return signals


class Spectrum:
    def __init__(self, args):
        self.monitor = Monitor(args.rtl_device, args.rtl_gain)
        self.published = None
        self.latest_bins = []
        self.latest_signals = []
        self.display_status = {}
        self.error = "Warte auf RTL-SDR …"
        self.clients = set()
        self.websockets = set()

    def publish(self, bins, now):
        frame = struct.pack(f"<{BINS}H", *bins)
        self.latest_bins = bins
        self.published = now
        for queue in self.clients:
            if queue.full():
                queue.get_nowait()
            queue.put_nowait(frame)

    def status(self):
        live = self.published is not None and time.monotonic() - self.published < 3
        return {
            "source": "RTL-SDR",
            "display_gain": DISPLAY_GAIN,
            "display_knee_db": DISPLAY_KNEE_DB,
            "display_top_db": DISPLAY_TOP_DB,
            "input_live": live,
            "live": live,
            "center_mhz": 745,
            "span_mhz": BAND_WIDTH / 1e6,
            "message": None if live else self.error or "Warte auf neue RTL-Messwerte …",
            "start_hz": BAND_START,
            "stop_hz": BAND_START + BAND_WIDTH,
            **(self.display_status if live else {}),
        }

    async def read_rtl(self):
        # Keep the browser on the validated scan grid. Resample the wire data
        # to the bin positions used by Longmynd Mobil and the BATC clients.
        downlink = IF_HZ + 9_750_000_000
        wire_freq = BAND_START + np.arange(1, BINS + 1) * BAND_WIDTH / BINS
        display = RtlDisplay(downlink)
        worker = asyncio.create_task(asyncio.to_thread(self.monitor.run))
        previous = None
        try:
            while not worker.done():
                sample = self.monitor.snapshot
                now = time.monotonic()
                if "levels" not in sample or now - sample["updated_at"] >= 3:
                    self.published = None
                    self.display_status = {}
                    self.error = sample.get("message", "Warte auf neue RTL-Messwerte …")
                    display.reset()
                elif sample is not previous:
                    levels = display.update(sample["levels"], sample["updated_at"])
                    self.display_status = {
                        "levels": np.round(levels, 2).tolist(),
                        "round_ms": sample["round_ms"],
                        "gain_db": sample["gain_db"],
                    }
                    wire_levels = np.interp(wire_freq, downlink, levels)
                    self.latest_signals = estimate_signals(encode_levels(wire_levels))
                    self.error = None
                    self.publish(encode_levels(display_levels(wire_levels)), sample["updated_at"])
                previous = sample
                await asyncio.sleep(0.2)
            await worker
        finally:
            self.monitor.stop.set()
            await worker

    async def websocket(self, request):
        ws = web.WebSocketResponse(protocols=PROTOCOLS, heartbeat=20, max_msg_size=4096)
        if not ws.can_prepare(request).ok:
            raise web.HTTPBadRequest(text="WebSocket erwartet")
        await ws.prepare(request)
        queue = asyncio.Queue(maxsize=1)
        self.clients.add(queue)
        self.websockets.add(ws)

        async def send_frames():
            while not ws.closed:
                frame = await queue.get()
                await asyncio.wait_for(ws.send_bytes(frame), timeout=5)

        sender = asyncio.create_task(send_frames())
        try:
            # aiohttp requires receives to stay in the request handler.
            while not ws.closed and not sender.done():
                try:
                    message = await ws.receive(timeout=1)
                except asyncio.TimeoutError:
                    continue
                if message.type in {WSMsgType.CLOSE, WSMsgType.CLOSED, WSMsgType.ERROR}:
                    break
        finally:
            self.clients.discard(queue)
            self.websockets.discard(ws)
            sender.cancel()
            with suppress(asyncio.CancelledError, asyncio.TimeoutError, ConnectionError):
                await sender
            await ws.close()
        return ws


def make_app(args):
    spectrum = Spectrum(args)
    app = web.Application()

    async def start_reader(app):
        reader = asyncio.create_task(spectrum.read_rtl())
        try:
            yield
        finally:
            reader.cancel()
            with suppress(asyncio.CancelledError):
                await reader

    async def close_websockets(app):
        await asyncio.gather(*(ws.close() for ws in tuple(spectrum.websockets)))

    async def index(request):
        return web.FileResponse(Path(__file__).with_name("index.html"),
                                headers={"Cache-Control": "no-cache"})

    async def status(request):
        return web.json_response(spectrum.status(), headers={"Cache-Control": "no-store"})

    async def android_spectrum(request):
        status = spectrum.status()
        bins = spectrum.latest_bins if status["live"] else []
        return web.json_response({
            "source": status["source"],
            "online": status["live"],
            "detail": status["message"] or "",
            "start_khz": BAND_START // 1000,
            "span_khz": BAND_WIDTH // 1000,
            "bins": bins,
            "signals": spectrum.latest_signals if status["live"] else [],
        }, headers={"Cache-Control": "no-store"})

    app.cleanup_ctx.append(start_reader)
    app.on_shutdown.append(close_websockets)
    app.router.add_get("/", index)
    app.router.add_get("/api/status", status)
    app.router.add_get("/api/spectrum", android_spectrum)
    app.router.add_get("/wb/fft", spectrum.websocket)
    return app


def arguments():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rtl-device", type=int, default=0)
    parser.add_argument("--rtl-gain", type=float, default=20)
    parser.add_argument("--listen", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8096)
    args = parser.parse_args()
    if args.rtl_device < 0 or not math.isfinite(args.rtl_gain):
        parser.error("Ungültiger RTL-Geräteindex oder Gain")
    return args


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")
    config = arguments()
    web.run_app(make_app(config), host=config.listen, port=config.port)
