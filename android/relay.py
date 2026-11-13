#!/usr/bin/env python3
"""Local/VPN HTTP fan-out for Longmynd Dual. GPL-3.0-or-later."""
import argparse
import asyncio
import ipaddress
import json
import logging
from pathlib import Path
import socket
import time

from aiohttp import web
from websockets.legacy.client import connect
from bridge import TSFramer, link_margin
from spectrum import Spectrum

LOG=logging.getLogger('longmynd-mobile')
VERSION='0.1.0-beta.2'

class Receiver(asyncio.DatagramProtocol):
    def __init__(self,relay,ch):self.relay,self.ch=relay,ch
    def datagram_received(self,data,addr):self.relay.feed(self.ch,data)

class Relay:
    def __init__(self,config,interface='0.0.0.0'):
        self.channels=config['channels']
        if len(self.channels)!=2:raise ValueError('Two channels required')
        self.interface=str(ipaddress.IPv4Address(interface))
        for ch in self.channels:
            if not ipaddress.IPv4Address(ch['ts_ip']).is_multicast:raise ValueError('Multicast required')
        self.framers=[TSFramer(),TSFramer()]
        self.clients=[set(),set()];self.packets=[{},{}];self.updated=[0.,0.]
        self.tasks=[];self.transports=[];self.control_locks=[asyncio.Lock(),asyncio.Lock()]
        self.nominal_sr=[ch['symbolrate_ksps'] for ch in self.channels]
        self.spectrum=Spectrum()
        self.native_seen = float('-inf')
        self.native_streams = set()

    def native_active(self):
        return bool(self.native_streams) or time.monotonic() - self.native_seen < 8

    @staticmethod
    def external_client(request):
        return request.remote not in ('127.0.0.1', '::1', None)

    def feed(self,ch,data):
        for payload in self.framers[ch].feed(data):self.broadcast(ch,payload)

    def broadcast(self,ch,data):
        for queue in tuple(self.clients[ch]):
            if queue.full():
                # Close a slow connection instead of silently corrupting its TS.
                while not queue.empty():queue.get_nowait()
                queue.put_nowait(None);self.clients[ch].discard(queue)
            else:queue.put_nowait(data)

    async def flush(self):
        while True:
            await asyncio.sleep(.02)
            for ch,framer in enumerate(self.framers):
                payload=framer.flush()
                if payload:self.broadcast(ch,payload)

    async def monitor(self,ch):
        while True:
            try:
                async with connect(f'ws://127.0.0.1:{self.channels[ch]["web_port"]}/',
                                   subprotocols=['monitor'],open_timeout=5,close_timeout=1,max_size=65536) as ws:
                    async for data in ws:
                        try:
                            m=json.loads(data)
                            if m.get('type')!='status':continue
                            p=m['packet'];float(p['rx']['frequency']);int(p['rx']['demod_state'])
                            self.packets[ch]=p;self.updated[ch]=time.monotonic()
                        except (ValueError,KeyError,TypeError):continue
            except asyncio.CancelledError:raise
            except Exception as e:LOG.warning('Channel %d monitor: %s',ch+1,e)
            finally:self.updated[ch]=0
            await asyncio.sleep(2)

    async def start(self,app):
        loop=asyncio.get_running_loop()
        try:
            for ch,cfg in enumerate(self.channels):
                sock=socket.socket(socket.AF_INET,socket.SOCK_DGRAM,socket.IPPROTO_UDP)
                try:
                    sock.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
                    sock.setsockopt(socket.SOL_SOCKET,socket.SO_RCVBUF,2*1024*1024)
                    sock.setsockopt(socket.IPPROTO_IP,49,0)
                    sock.bind(('',cfg['ts_port']))
                    sock.setsockopt(socket.IPPROTO_IP,socket.IP_ADD_MEMBERSHIP,
                        socket.inet_aton(cfg['ts_ip'])+socket.inet_aton(self.interface))
                    sock.setblocking(False)
                    transport,_=await loop.create_datagram_endpoint(lambda ch=ch:Receiver(self,ch),sock=sock)
                except BaseException:sock.close();raise
                self.transports.append(transport)
                self.tasks.append(asyncio.create_task(self.monitor(ch)))
            self.tasks.append(asyncio.create_task(self.flush()))
            self.tasks.append(asyncio.create_task(self.spectrum.run()))
        except BaseException:await self.close(app);raise

    async def close(self,app):
        for task in self.tasks:task.cancel()
        await asyncio.gather(*self.tasks,return_exceptions=True);self.tasks.clear()
        for transport in self.transports:transport.close()
        self.transports.clear()
        for clients in self.clients:
            for queue in tuple(clients):
                while not queue.empty():queue.get_nowait()
                queue.put_nowait(None)

    async def status(self,request):
        if self.external_client(request):
            self.native_seen = time.monotonic()
        channels=[];now=time.monotonic()
        for ch,packet in enumerate(self.packets):
            rx,ts=packet.get('rx',{}),packet.get('ts',{})
            fresh=self.updated[ch]>0 and now-self.updated[ch]<2
            locked=fresh and rx.get('demod_state') in (3,4)
            channels.append(dict(index=ch,online=fresh,locked=locked,
                frequency_khz=round(float(rx.get('frequency',0)))+9750000,
                symbolrate_ksps=round(float(rx.get('symbolrate',0))/1000),
                mer_db=float(rx.get('mer',0))/10 if fresh else None,
                margin_db=link_margin(rx,fresh),modcod=rx.get('modcod'),
                service=ts.get('service_name','') if locked else '',
                provider=ts.get('service_provider_name','') if locked else ''))
        return web.json_response(dict(version=VERSION,channels=channels,native_active=self.native_active()),headers={'Cache-Control':'no-store'})

    async def spectrum_status(self,request):
        return web.json_response(self.spectrum.snapshot(),headers={'Cache-Control':'no-store'})

    async def tune(self,request):
        automatic = request.headers.get('X-Longmynd-Auto-Scan') == '1'
        if automatic and self.external_client(request):
            raise web.HTTPForbidden(text='Local scanner only')
        if self.external_client(request):
            self.native_seen = time.monotonic()
        if request.content_type!='application/json':raise web.HTTPUnsupportedMediaType()
        # Native app has no Origin. Reject browser-origin cross-site control.
        if request.headers.get('Origin'):raise web.HTTPForbidden(text='Native app control only')
        try:
            ch=int(request.match_info['channel'])
            if ch not in (0,1):raise ValueError()
            m=await request.json()
            sky,sr=m['frequency_khz'],m['symbolrate_ksps']
            if type(sky) is not int or type(sr) is not int:raise ValueError()
            freq=sky-9750000
            if not 144000<=freq<=2450000 or not 33<=sr<=27500:raise ValueError()
        except (ValueError,KeyError,TypeError):raise web.HTTPBadRequest(text='Invalid frequency or symbol rate')
        try:
            async with self.control_locks[ch]:
                if automatic and self.native_active():
                    raise web.HTTPConflict(text='Native client active')
                async with connect(f'ws://127.0.0.1:{self.channels[ch]["web_port"]}/',
                        subprotocols=['control'],open_timeout=5,close_timeout=1) as ws:
                    await ws.send(f'C{freq},{sr}')
            self.nominal_sr[ch]=sr
            LOG.info('Channel %d tuned: %d kHz, %d kS/s',ch+1,sky,sr)
        except (OSError,TimeoutError):raise web.HTTPBadGateway(text='Receiver unreachable')
        return web.json_response({'accepted':True})

    async def stream(self,request):
        ch=int(request.match_info['channel'])
        if ch not in (0,1):raise web.HTTPNotFound()
        if len(self.clients[ch])>=8:raise web.HTTPServiceUnavailable(text='Too many viewers')
        queue=asyncio.Queue(maxsize=128);self.clients[ch].add(queue)
        if self.external_client(request):
            self.native_streams.add(queue)
        response=web.StreamResponse(headers={'Content-Type':'video/mp2t','Cache-Control':'no-store',
                                            'X-Content-Type-Options':'nosniff'})
        try:
            await response.prepare(request)
            while True:
                try:data=await asyncio.wait_for(queue.get(),10)
                except asyncio.TimeoutError:break
                if data is None:break
                await asyncio.wait_for(response.write(data),3)
        except (ConnectionError,asyncio.TimeoutError):pass
        finally:
            self.clients[ch].discard(queue)
            self.native_streams.discard(queue)
        return response

def make_app(relay):
    app=web.Application(client_max_size=2048)
    app.router.add_get('/api/status',relay.status)
    app.router.add_get('/api/spectrum',relay.spectrum_status)
    app.router.add_post('/api/tune/{channel:[01]}',relay.tune)
    app.router.add_get('/live/{channel:[01]}.ts',relay.stream)
    app.on_startup.append(relay.start);app.on_cleanup.append(relay.close)
    return app

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--config',default='/etc/longmynd-dual.json')
    p.add_argument('--port',type=int,default=8090);p.add_argument('--bind',default='0.0.0.0')
    p.add_argument('--multicast-interface',default='0.0.0.0');a=p.parse_args()
    logging.basicConfig(level=logging.INFO)
    web.run_app(make_app(Relay(json.loads(Path(a.config).read_text()),a.multicast_interface)),
                host=a.bind,port=a.port,shutdown_timeout=5)
