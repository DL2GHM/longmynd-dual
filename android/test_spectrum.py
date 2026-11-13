import asyncio
from pathlib import Path
import struct
import time
import unittest
from websockets.legacy.server import serve
from spectrum import Spectrum,decode_fft,detect_signals,symbolrate

class SpectrumTests(unittest.TestCase):
    def test_little_endian_and_malformed_frames(self):
        values=[11000,22000]*64
        self.assertEqual(decode_fft(struct.pack('<128H',*values)),values)
        for bad in (b'',b'a'*257,'not binary',b'a'*16386):
            with self.assertRaises(ValueError):decode_fft(bad)

    def test_separate_signals_have_correct_centres_and_nominal_rates(self):
        bins=[11000]*900
        bins[50:200]=[30000]*150;bins[650:683]=[25000]*33
        found=detect_signals(bins)
        self.assertEqual([s['symbolrate_ksps'] for s in found],[1500,333])
        self.assertLess(abs(found[0]['frequency_khz']-10491750),12)
        self.assertEqual(detect_signals([11000]*900),[])
        self.assertEqual(detect_signals([0]*900),[])

    def test_captured_batc_frame_recognises_beacon(self):
        data=decode_fft((Path(__file__).parent/'fixtures/batc-fft.bin').read_bytes())
        self.assertEqual(len(data),922)
        beacon=next(s for s in detect_signals(data) if s['frequency_khz']<10492000)
        self.assertEqual(beacon['symbolrate_ksps'],1500)
        self.assertLess(abs(beacon['frequency_khz']-10491538),25)

    def test_no_stale_spectrum_is_exposed_as_live(self):
        s=Spectrum();self.assertFalse(s.snapshot()['online'])
        s.publish(struct.pack('<128H',*[11000]*128))
        self.assertTrue(s.snapshot()['online'])
        s.updated=time.monotonic()-4
        stale=s.snapshot();self.assertFalse(stale['online']);self.assertEqual(stale['bins'],[]);self.assertEqual(stale['signals'],[])

class WebsocketTests(unittest.IsolatedAsyncioTestCase):
    async def test_real_websocket_feed_uses_expected_protocol_and_bytes(self):
        raw=struct.pack('<128H',*[12000]*128)
        async def handler(ws,path):
            self.assertEqual(ws.subprotocol,'fft_m0dtslivetune')
            await ws.send(raw);await ws.wait_closed()
        server=await serve(handler,'127.0.0.1',0,subprotocols=['fft_m0dtslivetune'])
        s=Spectrum('ws://127.0.0.1:'+str(server.sockets[0].getsockname()[1]));s.snapshot();task=asyncio.create_task(s.run())
        try:
            async with asyncio.timeout(2):
                while not s.updated:await asyncio.sleep(.01)
            self.assertEqual(s.snapshot()['bins'],[12000]*128)
        finally:
            task.cancel();await asyncio.gather(task,return_exceptions=True);server.close();await server.wait_closed()

if __name__=='__main__':unittest.main()
