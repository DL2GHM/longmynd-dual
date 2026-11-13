#!/usr/bin/env python3
"""RTL-SDR capture and display processing for the QO-100 wideband downlink."""
import ctypes as C
import logging
import threading
import time

import numpy as np


SAMPLE_RATE = 2_560_000
FFT_SIZE = 512
DISCARD_SAMPLES = 16_384
CAPTURE_SAMPLES = 65_536
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



class RtlDisplay:
    """Apply the receiver's measured floor profile and suppress its known spur."""

    def __init__(self, downlink_hz):
        mhz = downlink_hz / 1e6
        self.floor = np.interp(
            mhz, [10496.0, 10496.8, 10497.35, 10498.1, 10499.0, 10499.5],
            [0, 0.7, 1.2, 2.0, 2.7, 1.8],
        )
        step = mhz[1] - mhz[0]
        spur_bin = round((10498.802 - mhz[0]) / step)
        masked_bins = int(0.015 / step)
        self.left = spur_bin - masked_bins - 1
        self.right = spur_bin + masked_bins + 1
        self.previous = None
        self.updated_at = None

    def reset(self):
        self.previous = None
        self.updated_at = None

    def update(self, samples, now):
        values = np.array(samples, dtype=float)
        values[self.left:self.right + 1] = np.linspace(
            values[self.left], values[self.right], self.right - self.left + 1
        )
        values -= self.floor
        padded = np.pad(values, 1, mode="edge")
        filtered = 0.8 * values + 0.1 * (padded[:-2] + padded[2:])
        if self.previous is not None:
            weight = -np.expm1(-(now - self.updated_at) / 0.65)
            filtered = self.previous + weight * (filtered - self.previous)
        self.previous = filtered
        self.updated_at = now
        return filtered
