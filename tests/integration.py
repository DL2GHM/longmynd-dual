#!/usr/bin/env python3
"""Integration tests against real TS/parser/UDP/WS threads and simulated NIM/USB.
Requires Python package websockets. Run with LONGMYND_TEST_BINARY=/path/to/mock.
"""
import asyncio
import json
import os
from pathlib import Path
import signal
import socket
import subprocess
import tempfile
import time
import unittest
import websockets

ROOT = Path(__file__).resolve().parent.parent
BINARY = os.environ.get('LONGMYND_TEST_BINARY', str(ROOT / 'tests/longmynd-mock'))

def bind_udp():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.bind(('127.0.0.1', 0)); s.settimeout(3)
    return s

def tcp_port():
    with socket.socket() as s:
        s.bind(('127.0.0.1', 0)); return s.getsockname()[1]

class Receiver:
    def __init__(self, stall=False, slow_report=False):
        self.sockets = [bind_udp() for _ in range(4)]
        self.web = [tcp_port(), tcp_port()]
        while self.web[0] == self.web[1]: self.web[1] = tcp_port()
        self.args = ['--dual', 'master', 'aux']
        for ch in range(2):
            if ch: self.args += ['--next']
            self.args += ['-W', str(self.web[ch]), '-i', '127.0.0.1', str(self.sockets[ch*2].getsockname()[1]),
                          '-I', '127.0.0.1', str(self.sockets[ch*2+1].getsockname()[1]),
                          '-r', '1000' if stall else '5000', str(741538+ch*1000), '1500']
        env = dict(os.environ)
        if stall: env['MOCK_STALL_SECOND'] = '1'
        if slow_report: env['MOCK_SLOW_REPORT'] = '1'
        self.log = tempfile.TemporaryFile()
        self.process = subprocess.Popen([BINARY, *self.args], cwd=ROOT, stdout=self.log, stderr=subprocess.STDOUT, env=env)
        deadline = time.monotonic() + 5
        for port in self.web:
            while True:
                if self.process.poll() is not None: raise AssertionError(self.output())
                try:
                    with socket.create_connection(('127.0.0.1', port), timeout=.1): break
                except OSError:
                    if time.monotonic() > deadline: raise
                    time.sleep(.02)
    def output(self):
        self.log.seek(0); return self.log.read().decode(errors='replace')
    def close(self):
        self.process.send_signal(signal.SIGTERM)
        try: code = self.process.wait(timeout=4)
        except subprocess.TimeoutExpired:
            self.process.kill(); raise AssertionError('Shutdown hung: '+self.output())
        for s in self.sockets: s.close()
        out = self.output(); self.log.close()
        if code != 0: raise AssertionError(f'exit={code}\n{out}')
        if 'ERROR: AddressSanitizer' in out or 'runtime error:' in out: raise AssertionError(out)
        return out

async def monitor(port):
    return await websockets.connect(f'ws://127.0.0.1:{port}/', subprotocols=['monitor'], proxy=None)

async def control(port):
    return await websockets.connect(f'ws://127.0.0.1:{port}/', subprotocols=['control'], proxy=None)

async def until(ws, predicate):
    async with asyncio.timeout(5):
        while True:
            data = json.loads(await ws.recv())
            if predicate(data): return data['packet']

class Tests(unittest.TestCase):
    def test_slow_hardware_reporting_does_not_pause_usb(self):
        r = Receiver(slow_report=True)
        try:
            for sock in r.sockets[::2]: sock.settimeout(.5)
            # Observe actual packet arrival gaps after startup queues drain.
            for sock in r.sockets[::2]:
                warmup = time.monotonic() + .8
                while time.monotonic() < warmup:
                    sock.recv(65536)
                last = time.monotonic()
                deadline = last + 2
                largest = 0
                while time.monotonic() < deadline:
                    sock.recv(65536)
                    now = time.monotonic()
                    largest = max(largest, now-last)
                    last = now
                self.assertLess(largest, .15, f'USB reader stalled {largest:.3f}s during a 200ms status read')
        finally:
            r.close()

    def test_invalid_cli_rejected_before_hardware(self):
        cases = [[], ['master','master'], ['master','aux','-W'],
                 ['master','aux','-W','8080','--next','-i','127.0.0.1'],
                 ['master','aux','-p','h','741538','1500','--next','741538','1500']]
        for args in cases:
            p = subprocess.run([BINARY,'--dual',*args], capture_output=True, timeout=3)
            self.assertEqual(p.returncode, 2, p.stdout)
            self.assertNotIn(b'MOCK_OPEN', p.stdout)

    def test_udp_and_web_collisions_rejected(self):
        def options(web, ts):
            return ['-W',str(web),'-i','127.0.0.1',str(ts),'-I','127.0.0.1',str(ts+1),'741538','1500']
        for second in (options(18080,24010), options(18081,24000)):
            p = subprocess.run([BINARY,'--dual','master','aux',*options(18080,24000),'--next',*second],
                               capture_output=True, timeout=3)
            self.assertEqual(p.returncode, 2)
            self.assertNotIn(b'MOCK_OPEN',p.stdout)

    def test_stream_status_and_control_isolation(self):
        r = Receiver()
        async def exercise():
            m0, m1 = await monitor(r.web[0]), await monitor(r.web[1])
            c0, c1 = await control(r.web[0]), await control(r.web[1])
            try:
                p0 = await until(m0, lambda d: d['packet']['ts']['service_name']=='MAIN' and d['packet']['ts']['PIDs'])
                p1 = await until(m1, lambda d: d['packet']['ts']['service_name']=='AUX1' and d['packet']['ts']['PIDs'])
                self.assertEqual(p0['ts']['PIDs'], [[257,27]])
                self.assertEqual(p1['ts']['PIDs'], [[513,27]])
                for p in (p0,p1):
                    for key in ('demod_state','frequency','symbolrate','mer','modcod','ber','ts_ip_port'):
                        self.assertIs(type(p['rx'][key]), int)
                for ch in range(2):
                    packets = [r.sockets[ch*2].recv(2048) for _ in range(3)]
                    self.assertTrue(all(len(p)==188 and p[0]==0x47 for p in packets))
                for freq in (743000,743125,743250): await c1.send(f'C{freq},500')
                p1 = await until(m1, lambda d: d['packet']['rx']['frequency']==743250 and d['packet']['rx']['symbolrate']==500000)
                p0 = await until(m0, lambda d: d['packet']['rx']['frequency']==741538)
                self.assertEqual(p0['rx']['symbolrate'], 1500000)
                await c1.send('T1')
                await until(m1, lambda d: d['packet']['rx']['rfport']==1)
                await until(m0, lambda d: d['packet']['rx']['rfport']==0)
                with bind_udp() as new_ts:
                    await c1.send(f'U127.0.0.1:{new_ts.getsockname()[1]}')
                    await until(m1, lambda d: d['packet']['rx']['ts_ip_port']==new_ts.getsockname()[1])
                    self.assertEqual((await asyncio.to_thread(new_ts.recv,2048))[0],0x47)
                for _ in range(5):
                    extra = await monitor(r.web[0]); await extra.recv(); await extra.close()
            finally:
                for ws in (m0,m1,c0,c1): await ws.close()
        try: asyncio.run(exercise())
        finally: out = r.close()
        self.assertIn('MOCK_RETUNE 1 743250 500',out)
        self.assertNotIn('MOCK_RETUNE 0',out)

    def test_timeout_only_restarts_starved_channel(self):
        r = Receiver(stall=True)
        try: time.sleep(2.3)
        finally: out = r.close()
        self.assertIn('MOCK_RETUNE 1',out)
        self.assertNotIn('MOCK_RETUNE 0',out)

if __name__ == '__main__': unittest.main(verbosity=2)
