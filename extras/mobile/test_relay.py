#!/usr/bin/env python3
import asyncio
from contextlib import suppress
import json
from pathlib import Path
import socket
import sys
import unittest
from aiohttp.test_utils import TestClient,TestServer
from websockets.legacy.server import serve
from websockets.exceptions import ConnectionClosed
sys.path.insert(0,str(Path(__file__).resolve().parent))
from relay import Relay,make_app

def ts(n,ch):return b''.join(bytes((71,1,ch,16+i%16))+bytes([ch+20])*184 for i in range(n))

class RelayTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.commands=[[],[]];self.servers=[];self.channels=[]
        async def handler(ws,path,ch):
            with suppress(ConnectionClosed):
                if ws.subprotocol=='monitor':
                    while True:
                        await ws.send(json.dumps({'type':'status','packet':{'rx':{'frequency':741551+ch*2000,
                            'symbolrate':1500000,'demod_state':4,'mer':98-ch*10,'modcod':8},
                            'ts':{'service_name':('A71A','OTHER')[ch]}}}));await asyncio.sleep(.03)
                else:
                    async for data in ws:self.commands[ch].append(data)
        for ch in range(2):
            server=await serve(lambda ws,path,ch=ch:handler(ws,path,ch),'127.0.0.1',0,subprotocols=['monitor','control'])
            self.servers.append(server)
            with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as s:s.bind(('127.0.0.1',0));port=s.getsockname()[1]
            self.channels.append(dict(web_port=server.sockets[0].getsockname()[1],ts_ip=f'230.0.0.{12+ch}',ts_port=port,symbolrate_ksps=1500))
        self.relay=Relay({'channels':self.channels},'127.0.0.1')
        self.client=TestClient(TestServer(make_app(self.relay)));await self.client.start_server()
        async with asyncio.timeout(2):
            while not all(self.relay.updated):await asyncio.sleep(.01)

    async def asyncTearDown(self):
        await self.client.close()
        for server in self.servers:server.close()
        await asyncio.gather(*(s.wait_closed() for s in self.servers))

    async def test_status_and_independent_control_never_changes_udp_target(self):
        response=await self.client.get('/api/status');data=await response.json()
        self.assertEqual([c['service'] for c in data['channels']],['A71A','OTHER'])
        self.assertAlmostEqual(data['channels'][0]['margin_db'],5.1)
        response=await self.client.post('/api/tune/1',json={'frequency_khz':10493750,'symbolrate_ksps':500})
        self.assertEqual(response.status,200)
        await asyncio.sleep(.05)
        self.assertEqual(self.commands,[[],['C743750,500']])
        for ch in self.commands:self.assertFalse(any(c.startswith('U') for c in ch))

    async def test_invalid_and_browser_origin_commands_rejected(self):
        for value in (1,'10491500',True):
            r=await self.client.post('/api/tune/0',json={'frequency_khz':value,'symbolrate_ksps':1500})
            self.assertEqual(r.status,400)
        r=await self.client.post('/api/tune/0',json={'frequency_khz':10491500,'symbolrate_ksps':1500},headers={'Origin':'http://other.example'})
        self.assertEqual(r.status,403);self.assertEqual(self.commands,[[],[]])

    async def test_spectrum_api_uses_shared_cache(self):
        import struct
        self.relay.spectrum.publish(struct.pack('<128H',*[11000]*128))
        r=await self.client.get('/api/spectrum');data=await r.json()
        self.assertTrue(data['online']);self.assertEqual(data['bins'],[11000]*128)
        self.assertEqual(data['start_khz'],10490500);self.assertEqual(data['span_khz'],9000)

    async def test_two_viewers_same_channel_and_second_channel_get_identical_ts(self):
        responses=[await self.client.get('/live/0.ts'),await self.client.get('/live/0.ts'),await self.client.get('/live/1.ts')]
        try:
            self.relay.feed(0,ts(14,0));self.relay.feed(1,ts(14,1))
            for i,r in enumerate(responses):
                data=await asyncio.wait_for(r.content.readexactly(14*188),2)
                self.assertEqual(data,ts(14,0 if i<2 else 1))
        finally:
            for r in responses:r.close()

    async def test_slow_viewer_is_closed_instead_of_silent_drops(self):
        q=asyncio.Queue(maxsize=1);self.relay.clients[0].add(q)
        self.relay.broadcast(0,ts(7,0));self.relay.broadcast(0,ts(7,0))
        self.assertIsNone(q.get_nowait());self.assertNotIn(q,self.relay.clients[0])

    async def test_parallel_beacon_rate_via_udp_input_to_two_http_viewers(self):
        responses=[await self.client.get('/live/0.ts'),await self.client.get('/live/1.ts')]
        streams=[ts(1020,ch) for ch in range(2)]
        async def read(ch):
            return await asyncio.wait_for(responses[ch].content.readexactly(len(streams[ch])),3)
        readers=[asyncio.create_task(read(ch)) for ch in range(2)]
        sender=socket.socket(socket.AF_INET,socket.SOCK_DGRAM)
        try:
            for first in range(0,376,3):
                for n in range(first,min(first+3,376)):
                    for ch in range(2):
                        sender.sendto(streams[ch][n*510:(n+1)*510],('127.0.0.1',self.channels[ch]['ts_port']))
                await asyncio.sleep(.005)
            self.assertEqual(await asyncio.gather(*readers),streams)
        finally:
            sender.close()
            for r in responses:r.close()

if __name__=='__main__':unittest.main()
