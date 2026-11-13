#!/usr/bin/env python3
"""Protocol / real UDP and WebSocket tests; no receiver hardware required."""
import asyncio
from contextlib import suppress
import importlib.util
import json
from pathlib import Path
import socket
import struct
import sys
import unittest
from websockets.legacy.server import serve
from websockets.exceptions import ConnectionClosed

sys.path.insert(0,str(Path(__file__).resolve().parent))
from bridge import Bridge,TSFramer,parse_command,status_datagram,link_margin

def ts_stream(count,ch=0):
    return b''.join(bytes((0x47,1,ch,0x10|(n%16)))+struct.pack('!I',n)+bytes([ch+20])*180
                    for n in range(count))

class FramingTests(unittest.TestCase):
    def test_arbitrary_cuts_preserve_every_complete_packet(self):
        raw=ts_stream(1020)
        for cut in (1,187,188,510,1316,65507):
            f=TSFramer();output=[]
            for n in range(0,len(raw),cut):
                output.extend(f.feed(raw[n:n+cut]))
                if n%5==0:
                    tail=f.flush()
                    if tail:output.append(tail)
            tail=f.flush()
            if tail:output.append(tail)
            self.assertEqual(b''.join(output),raw)
            self.assertEqual(f.discarded,0)
            for payload in output:
                self.assertLessEqual(len(payload),1316)
                self.assertEqual(len(payload)%188,0)
                self.assertTrue(all(payload[i]==0x47 for i in range(0,len(payload),188)))

    def test_join_mid_packet_then_recover_after_missing_bytes(self):
        raw=ts_stream(100)
        f=TSFramer()
        out=list(f.feed(b'junk'+raw[53:50*188]+b'bad'+raw[51*188:]))
        out.append(f.flush())
        self.assertEqual(b''.join(out),raw[188:50*188]+raw[51*188:])
        self.assertGreater(f.discarded,0)
        self.assertLess(len(f.input),377)

def udp_socket(port=0):
    s=socket.socket(socket.AF_INET,socket.SOCK_DGRAM)
    try:s.bind(('127.0.0.1',port));s.setblocking(False);return s
    except BaseException:s.close();raise

def base_port():
    for base in range(24000,60000,100):
        sockets=[]
        try:
            for d in (1,21,22,41,42):sockets.append(udp_socket(base+d))
            return base
        except OSError:pass
        finally:
            for s in sockets:s.close()
    raise RuntimeError('no free test ports')

class ProtocolTests(unittest.TestCase):
    def test_reserve_uses_mode_specific_modcod_and_preserves_negative_values(self):
        self.assertAlmostEqual(link_margin({'demod_state':4,'modcod':8,'mer':98},True),5.1)
        self.assertAlmostEqual(link_margin({'demod_state':3,'modcod':7,'mer':70},True),2.8)
        self.assertAlmostEqual(link_margin({'demod_state':4,'modcod':14,'mer':75},True),-.4)
        self.assertAlmostEqual(link_margin({'demod_state':4,'modcod':1,'mer':-10},True),1.3)

    def test_reserve_unavailable_for_stale_unlocked_unknown_or_invalid_status(self):
        packet={'demod_state':4,'modcod':8,'mer':98}
        self.assertIsNone(link_margin(packet,False))
        for changes in ({'demod_state':1},{'modcod':0},{'modcod':99},{'mer':float('nan')},
                        {'mer':float('inf')},{'mer':None},{'demod_state':None}):
            self.assertIsNone(link_margin(packet|changes,True))
        self.assertIsNone(link_margin({},True))

    def test_nominal_rate_matches_ot_spectrum_despite_clock_correction(self):
        for nominal,measured in ((333,331600),(1000,998500),(500,498500),(1500,1499633)):
            packet={'rx':{'demod_state':4,'frequency':747790,'symbolrate':measured,'mer':61},
                    'ts':{'service_name':'F6FKP'}}
            data=status_datagram(packet,0,9900,'192.0.2.10',9750000,True,nominal).decode()
            fields=dict(line[1:].split(',',1) for line in data.splitlines())
            self.assertEqual(int(fields['9']),nominal)
            self.assertEqual(fields['13'],'F6FKP')
            self.assertEqual(packet['rx']['symbolrate'],measured)

    def test_tune_units_receiver_and_input(self):
        t=parse_command(b'[to@wh] rcv=2,freq=10493750,offset=9750000,srate=500,fplug=B\n',1,9900)
        self.assertEqual((t.frequency,t.symbolrate,t.offset,t.bottom),(743750,500,9750000,True))
        with self.assertRaises(ValueError):parse_command(b'[to@wh] rcv=1,freq=10493750,offset=9750000,srate=500,fplug=B',1,9900)
        with self.assertRaises(ValueError):parse_command(b'[to@wh] rcv=1,freq=1,offset=9750000,srate=500,fplug=A',0,9900)
        self.assertIsNone(parse_command(b'[to@wh] vgx=OFF',0,9900))

    def test_status_units_and_stale_lock(self):
        packet={'rx':{'demod_state':4,'frequency':741551,'symbolrate':1499633,'mer':95,'modcod':8,'rfport':1},
                'ts':{'service_name':'A71A','service_provider_name':'QARS','null_ratio':12}}
        s=status_datagram(packet,1,9900,'192.0.2.10',9750000,True).decode()
        for value in ('$0,2\n','$1,DVB-S2\n','$6,10491.551\n','$9,1500\n','$12,9.5\n','$13,A71A\n','$30,4.8\n','$33,BOTTOM\n','$92,192.0.2.10:9942\n'):
            self.assertIn(value,s)
        s=status_datagram(packet,1,9900,'192.0.2.10',9750000,False).decode()
        self.assertIn('$1,lost\n',s);self.assertIn('$13,\n',s);self.assertIn('$30,\n',s)

class IntegrationTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.base=base_port();self.commands=[[],[]];self.servers=[];self.sockets=[]
        self.packets=[{'rx':{'demod_state':4,'frequency':741550+i*2000,'symbolrate':1500000-i*1000000,
                            'mer':95-i*10,'modcod':8,'rfport':i},
                       'ts':{'service_name':('A71A','OTHER')[i],'service_provider_name':'test','null_ratio':1}} for i in range(2)]
        async def handler(ws,path,ch):
            with suppress(ConnectionClosed):
                if ws.subprotocol=='monitor':
                    while True:
                        await ws.send(json.dumps({'type':'status','packet':self.packets[ch]}));await asyncio.sleep(.03)
                else:
                    async for msg in ws:self.commands[ch].append(msg)
        channels=[]
        for ch in range(2):
            server=await serve(lambda ws,path,ch=ch:handler(ws,path,ch),'127.0.0.1',0,subprotocols=['monitor','control'])
            self.servers.append(server)
            temp=udp_socket();port=temp.getsockname()[1];temp.close()
            channels.append({'web_port':server.sockets[0].getsockname()[1],'ts_ip':f'230.0.0.{12+ch}','ts_port':port})
        self.config={'channels':channels}
        self.status=udp_socket(self.base+1);self.ts=[udp_socket(self.base+41+i) for i in range(2)]
        self.client=udp_socket();self.sockets=[self.status,*self.ts,self.client]
        self.bridge=Bridge(self.config,self.base,'127.0.0.1',interval=.025,stale=.15,interface='127.0.0.1')
        await self.bridge.start()
        await asyncio.wait_for(asyncio.gather(*(e.wait() for e in self.bridge.control_ready+self.bridge.monitor_ready)),3)

    async def asyncTearDown(self):
        await self.bridge.close()
        for server in self.servers:server.close()
        await asyncio.gather(*(s.wait_closed() for s in self.servers))
        for s in self.sockets:s.close()

    def tune(self,ch,frequency=10491500,sr=1500):
        receiver=self.base%100+ch+1
        data=f'[to@wh] rcv={receiver},freq={frequency},offset=9750000,srate={sr},fplug={"AB"[ch]}\n'.encode()
        self.client.sendto(data,('127.0.0.1',self.base+21+ch))

    async def until(self,predicate,timeout=2):
        async with asyncio.timeout(timeout):
            while not predicate():await asyncio.sleep(.01)

    async def test_two_channel_commands_status_and_payloads(self):
        self.tune(0);self.tune(1,10493750,500)
        await self.until(lambda:len(self.commands[0])>=2 and len(self.commands[1])>=2)
        self.assertEqual(self.commands[0],['T0','C741500,1500'])
        self.assertEqual(self.commands[1],['T1','C743750,500'])
        received=set();loop=asyncio.get_running_loop()
        async with asyncio.timeout(2):
            while len(received)<2:
                data=await loop.sock_recv(self.status,4096)
                if b'$1,DVB-S2\n' in data:
                    if b'$13,A71A\n' in data:
                        self.assertIn(b'$30,4.8\n',data);received.add(0)
                    if b'$13,OTHER\n' in data:
                        self.assertIn(b'$30,3.8\n',data);received.add(1)
        for ch in range(2):
            payload=ts_stream(7,ch)
            self.client.sendto(payload,('127.0.0.1',self.config['channels'][ch]['ts_port']))
            data=await asyncio.wait_for(loop.sock_recv(self.ts[ch],4096),1)
            self.assertEqual(data,payload)
        old=list(self.commands[0]);self.tune(1,10497000,333)
        await self.until(lambda:len(self.commands[1])>=4)
        self.assertEqual(self.commands[0],old)
        self.assertEqual(self.commands[1][-2:],['T1','C747000,333'])

    async def test_disconnected_backend_reports_lost(self):
        self.tune(0)
        await self.until(lambda:len(self.commands[0])>=2)
        await asyncio.sleep(.4)
        self.servers[0].close();await self.servers[0].wait_closed()
        await self.until(lambda:not self.bridge.monitor_ready[0].is_set())
        loop=asyncio.get_running_loop()
        async with asyncio.timeout(2):
            while True:
                data=await loop.sock_recv(self.status,4096)
                if b'$1,lost\n' in data:break
        self.assertIn(b'$13,\n',data)

    async def test_each_channel_reports_its_commanded_rate_not_measured_rate(self):
        self.packets[0]['rx']['symbolrate']=331600
        self.packets[1]['rx']['symbolrate']=998500
        self.tune(0,10497790,333);self.tune(1,10493303,1000)
        loop=asyncio.get_running_loop();seen=set()
        async with asyncio.timeout(2):
            while len(seen)<2:
                data=await loop.sock_recv(self.status,4096)
                if b'$1,DVB-S2\n' not in data:continue
                fields=dict(line[1:].split(',',1) for line in data.decode().splitlines())
                ch=int(fields['0'])-1
                self.assertEqual(int(fields['9']),(333,1000)[ch])
                seen.add(ch)
        self.tune(0,10497790,500)
        await self.until(lambda:self.bridge.nominal_sr==[500,1000])

    async def test_invalid_receiver_does_not_register_client(self):
        self.client.sendto(b'[to@wh] rcv=99,freq=10491500,offset=9750000,srate=1500,fplug=A',
                           ('127.0.0.1',self.base+21))
        await asyncio.sleep(.1)
        self.assertEqual(self.bridge.clients,[None,None])
        self.assertEqual(self.commands,[[],[]])

    async def test_parallel_beacon_rate_forwarding_keeps_every_byte(self):
        self.tune(0);self.tune(1)
        await self.until(lambda:all(self.bridge.clients))
        loop=asyncio.get_running_loop()
        count=376
        streams=[ts_stream(1020,ch) for ch in range(2)]
        async def receive(ch):
            received=bytearray()
            while len(received)<len(streams[ch]):
                data=await asyncio.wait_for(loop.sock_recv(self.ts[ch],4096),2)
                self.assertEqual(len(data)%188,0)
                self.assertLessEqual(len(data),1316)
                received.extend(data)
            self.assertEqual(bytes(received),streams[ch])
        readers=[asyncio.create_task(receive(ch)) for ch in range(2)]
        # Three 510-byte datagrams per 5ms per channel: nominal 2.45 Mbit/s.
        for first in range(0,count,3):
            for n in range(first,min(first+3,count)):
                for ch in range(2):
                    data=streams[ch][n*510:(n+1)*510]
                    self.client.sendto(data,('127.0.0.1',self.config['channels'][ch]['ts_port']))
            await asyncio.sleep(.005)
        await asyncio.gather(*readers)
        self.assertEqual(self.bridge.forwarded,[count*510,count*510])
        self.assertEqual(self.bridge.dropped,[0,0])

    async def test_small_stream_flushes_and_retune_does_not_mix_tail(self):
        self.tune(0)
        await self.until(lambda:bool(self.bridge.clients[0]))
        loop=asyncio.get_running_loop()
        payload=ts_stream(3)
        self.client.sendto(payload,('127.0.0.1',self.config['channels'][0]['ts_port']))
        self.assertEqual(await asyncio.wait_for(loop.sock_recv(self.ts[0],4096),.5),payload)
        list(self.bridge.framers[0].feed(ts_stream(3)+ts_stream(1)[:100]))
        self.tune(0,10493750,500)
        await self.until(lambda:self.bridge.nominal_sr[0]==500)
        self.assertEqual(self.bridge.framers[0].input,bytearray())
        self.assertEqual(self.bridge.framers[0].pending,bytearray())

if __name__=='__main__':unittest.main()
