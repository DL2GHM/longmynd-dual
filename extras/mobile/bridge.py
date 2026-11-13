#!/usr/bin/env python3
"""Expose two Longmynd channels as a PicoTuner/WinterHill UDP receiver to OpenTuner.
GPL-3.0-or-later. Protocol reference: DH1RK/open_tuner commit
1b3cd075e74de616c5caae79a477688f2a1e8822, WinterhillUDP.cs / WinterhillSource.cs.
The existing receiver and its multicast destinations are left unchanged.
"""
import argparse
import asyncio
from dataclasses import dataclass
import ipaddress
import json
import logging
import math
from pathlib import Path
import signal
import socket
import time
from websockets.legacy.client import connect

LOG = logging.getLogger('longmynd-ot-dual')
VERSION = '0.1.0-beta.4'

# Compatibility reference: OpenTuner D00.12 LongmyndSource.cs threshold tables
# (DH1RK/open_tuner, 2cc4a5430ac13b0dd5f690f92abe4c1ebd42dd60).
# Like OT's native Longmynd mode, reserve = measured MER - nominal threshold.
# These are reference estimates, not a measurement of the actual lock boundary.
DVBS_THRESHOLDS = {4:1.7,5:4.8,6:3.3,7:4.2,9:5.1,10:5.5,11:5.8}
DVBS2_THRESHOLDS = {
    1:-2.3,2:-1.2,3:-.3,4:1.0,5:2.3,6:3.1,7:4.1,8:4.7,9:5.2,
    10:6.2,11:6.5,12:5.5,13:6.6,14:7.9,15:9.4,16:10.7,17:11.0,
    18:9.0,19:10.2,20:11.0,21:11.6,22:12.9,23:13.2,24:12.8,
    25:13.7,26:14.3,27:15.7,28:16.1,
}

def link_margin(rx, fresh):
    """Return OT-compatible reserve in dB, or None if unavailable/unlocked."""
    if not fresh:return None
    try:
        table={3:DVBS_THRESHOLDS,4:DVBS2_THRESHOLDS}.get(int(rx.get('demod_state',0)),{})
        threshold=table.get(int(rx.get('modcod',0)))
        if threshold is None:return None
        mer=float(rx['mer'])/10
        if not math.isfinite(mer):return None
        return mer-threshold
    except (ValueError,TypeError,KeyError,OverflowError):return None

@dataclass(frozen=True)
class Tune:
    frequency: int  # receiver IF, kHz
    symbolrate: int  # kS/s
    offset: int  # kHz
    bottom: bool

def parse_command(data, channel, base):
    """Accept only the tuning command for the receiver on this command port."""
    if len(data) > 1024: raise ValueError('command too long')
    text = data.decode('ascii').strip()
    if not text.startswith('[to@wh] '): raise ValueError('not a WinterHill command')
    body = text[len('[to@wh] '):]
    if body in ('vgx=OFF','vgx=LO','vgx=HI','vgy=OFF','vgy=LO','vgy=HI'):
        return None  # shared LNB power remains external, as in Longmynd Dual
    fields = {}
    for item in body.split(','):
        k,v = item.split('=',1)
        if k in fields: raise ValueError('duplicate field')
        fields[k] = v
    if set(fields) != {'rcv','freq','offset','srate','fplug'}:
        raise ValueError('incomplete tuning command')
    if int(fields['rcv']) != base % 100 + channel + 1: raise ValueError('wrong receiver')
    offset, sky, sr = int(fields['offset']), int(fields['freq']), int(fields['srate'])
    frequency = sky-offset
    if not 0 <= offset <= 20000000: raise ValueError('invalid LNB offset')
    if not 144000 <= frequency <= 2450000: raise ValueError('IF outside Longmynd range')
    if not 33 <= sr <= 27500: raise ValueError('symbol rate outside Longmynd range')
    if fields['fplug'] not in ('A','B'): raise ValueError('invalid RF input')
    return Tune(frequency,sr,offset,fields['fplug']=='B')

def clean(value):
    return str(value if value is not None else '').replace('\r',' ').replace('\n',' ').replace(',',';')[:240]

def status_datagram(packet, channel, base, client, offset, fresh, nominal_sr=None):
    rx, ts = packet.get('rx', {}), packet.get('ts', {})
    state = int(rx.get('demod_state',0)) if fresh else 0
    mode = {3:'DVB-S1',4:'DVB-S2',2:'header'}.get(state,'search' if fresh else 'lost')
    # PicoWH status is sky frequency in MHz, unlike Longmynd's IF in kHz.
    frequency = (float(rx.get('frequency',0))+offset)/1000.0
    # OT writes this rate into its spectrum signal cache, whose matching uses
    # exact rate equality. Measured 331.6/998.5 kS/s would repeatedly replace
    # spectrum entries detected as nominal 333/1000 and erase their callsigns.
    sr = nominal_sr if nominal_sr is not None else int(float(rx.get('symbolrate',0))/1000+0.5)
    margin=link_margin(rx,fresh)
    values = [(0,base%100+channel+1),(1,mode),(6,f'{frequency:.3f}'),(9,sr),
              (12,f'{float(rx.get("mer",0))/10:.1f}' if fresh else '0.0'),
              (13,ts.get('service_name','') if fresh else ''),
              (14,ts.get('service_provider_name','') if fresh else ''),
              (15,ts.get('null_ratio',0)),(18,rx.get('modcod',0)),
              (30,f'{margin:.1f}' if margin is not None else ''),
              (33,'BOTTOM' if rx.get('rfport',0) else 'TOP'),
              (92,f'{client}:{base+41+channel}')]
    return ''.join(f'${key},{clean(value)}\n' for key,value in values).encode('ascii','replace')

class CommandProtocol(asyncio.DatagramProtocol):
    def __init__(self, bridge, channel): self.bridge,self.channel=bridge,channel
    def datagram_received(self, data, addr): self.bridge.command(self.channel,data,addr[0])

class TSProtocol(asyncio.DatagramProtocol):
    def __init__(self, bridge, channel): self.bridge,self.channel=bridge,channel
    def datagram_received(self, data, addr): self.bridge.forward(self.channel,data)

class TSFramer:
    """Recover 188-byte TS boundaries across arbitrary input datagram cuts.

    Seven complete packets fit a 1316-byte UDP payload without IP fragmentation
    on ordinary Ethernet. A periodic flush sends shorter complete groups for
    low-rate streams. Only unsynchronized bytes are discarded, never TS payload.
    """
    def __init__(self):
        self.input=bytearray();self.pending=bytearray()
        self.synced=False;self.discarded=0

    def feed(self,data):
        self.input.extend(data)
        while True:
            if not self.synced:
                # Require three sync bytes at TS spacing to avoid payload 0x47.
                found=next((i for i in range(max(0,len(self.input)-376))
                            if self.input[i]==self.input[i+188]==self.input[i+376]==0x47),None)
                if found is None:
                    skip=max(0,len(self.input)-376)
                    self.discarded+=skip;del self.input[:skip]
                    break
                self.discarded+=found;del self.input[:found];self.synced=True
            if len(self.input)<188:break
            if self.input[0]!=0x47:
                self.synced=False
                continue
            self.pending.extend(self.input[:188]);del self.input[:188]
            if len(self.pending)==1316:
                yield bytes(self.pending);self.pending.clear()

    def flush(self):
        data=bytes(self.pending);self.pending.clear()
        return data

class Bridge:
    def __init__(self, config, base=9900, bind='0.0.0.0', client=None, interval=.25, stale=2.0, interface='0.0.0.0'):
        if base < 1024 or base+42 > 65535: raise ValueError('invalid UDP base port')
        self.channels = config['channels']
        if len(self.channels)!=2: raise ValueError('exactly two channels required')
        for ch in self.channels:
            ip = ipaddress.IPv4Address(ch['ts_ip'])
            if not ip.is_multicast: raise ValueError('bridge requires existing multicast TS destinations')
            for key in ('web_port','ts_port'):
                if type(ch[key]) is not int or not 1<=ch[key]<=65535: raise ValueError('invalid '+key)
        reserved = {base+1,base+21,base+22,base+41,base+42}
        if any(ch['ts_port'] in reserved or ch.get('status_port') in reserved for ch in self.channels):
            raise ValueError('bridge ports collide with receiver UDP ports')
        self.base,self.bind,self.allowed_client=base,bind,client
        self.interface=str(ipaddress.IPv4Address(interface))
        if client: ipaddress.IPv4Address(client)
        self.interval,self.stale=interval,stale
        self.clients=[None,None]
        self.offsets=[9750000,9750000]
        self.nominal_sr=[ch.get('symbolrate_ksps') for ch in self.channels]
        self.framers=[TSFramer(),TSFramer()]
        self.queues=[asyncio.Queue(maxsize=1),asyncio.Queue(maxsize=1)]
        self.packets=[{},{}];self.updated=[0.0,0.0];self.hold_until=[0.0,0.0]
        self.transports=[];self.tasks=[];self.output=None
        self.forwarded=[0,0];self.dropped=[0,0];self.last_warning=0.0
        self.control_ready=[asyncio.Event(),asyncio.Event()]
        self.monitor_ready=[asyncio.Event(),asyncio.Event()]

    def command(self, channel, data, client):
        if self.allowed_client and client != self.allowed_client: return
        try:
            command = parse_command(data,channel,self.base)
            if command is None: return
        except (ValueError,UnicodeError): return
        if self.clients[channel] != client:
            LOG.info('Channel %d: OpenTuner client %s',channel+1,client)
        # A retune starts a new stream; do not combine its first packets with
        # buffered tail packets from the previous station.
        self.framers[channel]=TSFramer()
        self.clients[channel]=client; self.offsets[channel]=command.offset
        self.nominal_sr[channel]=command.symbolrate
        self.hold_until[channel]=time.monotonic()+.35
        queue=self.queues[channel]
        if queue.full(): queue.get_nowait()
        queue.put_nowait(command)

    def forward(self, channel, data):
        if not self.clients[channel] or not data:return
        for payload in self.framers[channel].feed(data):
            self.send_ts(channel,payload)

    def send_ts(self, channel, data):
        client=self.clients[channel]
        if not client or not data: return
        try:
            self.output.sendto(data,(client,self.base+41+channel))
            self.forwarded[channel]+=len(data)
        except (BlockingIOError,OSError) as e:
            self.dropped[channel]+=1
            if time.monotonic()-self.last_warning>5:
                LOG.warning('UDP forwarding: %s',e);self.last_warning=time.monotonic()

    async def flush_ts(self):
        while True:
            await asyncio.sleep(.02)
            for ch,framer in enumerate(self.framers):
                payload=framer.flush()
                if payload:self.send_ts(ch,payload)

    async def monitor(self, channel):
        url=f'ws://127.0.0.1:{self.channels[channel]["web_port"]}/'
        while True:
            try:
                async with connect(url,subprotocols=['monitor'],open_timeout=5,close_timeout=1,max_size=65536) as ws:
                    self.monitor_ready[channel].set()
                    LOG.info('Channel %d: monitor connected',channel+1)
                    async for data in ws:
                        try:
                            message=json.loads(data)
                            if message.get('type')!='status': continue
                            packet=message['packet']
                            # Validate units used by the status formatter before publishing.
                            int(packet['rx']['demod_state']);float(packet['rx']['frequency'])
                            float(packet['rx']['symbolrate']);float(packet['rx']['mer'])
                            self.packets[channel]=packet;self.updated[channel]=time.monotonic()
                        except (ValueError,KeyError,TypeError): continue
            except asyncio.CancelledError: raise
            except Exception as e: LOG.warning('Channel %d monitor: %s',channel+1,e)
            finally:
                self.updated[channel]=0;self.monitor_ready[channel].clear()
            await asyncio.sleep(2)

    async def control(self, channel):
        url=f'ws://127.0.0.1:{self.channels[channel]["web_port"]}/'
        pending=None
        while True:
            try:
                async with connect(url,subprotocols=['control'],open_timeout=5,close_timeout=1) as ws:
                    self.control_ready[channel].set()
                    LOG.info('Channel %d: control connected',channel+1)
                    while True:
                        if pending is None: pending=await self.queues[channel].get()
                        # Coalesce commands accumulated during a disconnect to the newest tune.
                        if not self.queues[channel].empty(): pending=self.queues[channel].get_nowait()
                        await ws.send('T'+str(int(pending.bottom)))
                        await ws.send(f'C{pending.frequency},{pending.symbolrate}')
                        LOG.info('Channel %d: IF %d kHz, SR %d kS/s, input %s',
                                 channel+1,pending.frequency,pending.symbolrate,'B' if pending.bottom else 'A')
                        pending=None
            except asyncio.CancelledError: raise
            except Exception as e: LOG.warning('Channel %d control: %s',channel+1,e)
            finally: self.control_ready[channel].clear()
            await asyncio.sleep(2)

    async def status_loop(self):
        while True:
            now=time.monotonic()
            for ch,client in enumerate(self.clients):
                if not client: continue
                fresh=self.updated[ch]>0 and now-self.updated[ch]<self.stale and now>=self.hold_until[ch]
                try:
                    data=status_datagram(self.packets[ch],ch,self.base,client,self.offsets[ch],fresh,self.nominal_sr[ch])
                    self.output.sendto(data,(client,self.base+1))
                except (ValueError,TypeError,OSError) as e:
                    if now-self.last_warning>5: LOG.warning('Status: %s',e);self.last_warning=now
            await asyncio.sleep(self.interval)

    async def start(self):
        loop=asyncio.get_running_loop()
        self.output=socket.socket(socket.AF_INET,socket.SOCK_DGRAM)
        self.output.setsockopt(socket.SOL_SOCKET,socket.SO_SNDBUF,1024*1024)
        self.output.setblocking(False)
        try:
            for ch,cfg in enumerate(self.channels):
                transport,_=await loop.create_datagram_endpoint(lambda ch=ch:CommandProtocol(self,ch),
                    local_addr=(self.bind,self.base+21+ch),family=socket.AF_INET)
                self.transports.append(transport)
                sock=socket.socket(socket.AF_INET,socket.SOCK_DGRAM,socket.IPPROTO_UDP)
                try:
                    sock.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
                    sock.setsockopt(socket.SOL_SOCKET,socket.SO_RCVBUF,2*1024*1024)
                    sock.setsockopt(socket.IPPROTO_IP,49,0)  # Linux IP_MULTICAST_ALL
                    sock.bind(('',cfg['ts_port']))
                    sock.setsockopt(socket.IPPROTO_IP,socket.IP_ADD_MEMBERSHIP,
                        socket.inet_aton(cfg['ts_ip'])+socket.inet_aton(self.interface))
                    sock.setblocking(False)
                    transport,_=await loop.create_datagram_endpoint(lambda ch=ch:TSProtocol(self,ch),sock=sock)
                except BaseException: sock.close();raise
                self.transports.append(transport)
            for ch in range(2):
                self.tasks += [asyncio.create_task(self.monitor(ch)),asyncio.create_task(self.control(ch))]
            self.tasks.append(asyncio.create_task(self.status_loop()))
            self.tasks.append(asyncio.create_task(self.flush_ts()))
            LOG.info('OT dual bridge %s: command UDP %d/%d; status %d; TS %d/%d',
                VERSION,self.base+21,self.base+22,self.base+1,self.base+41,self.base+42)
        except BaseException:
            await self.close();raise

    async def close(self):
        for task in self.tasks: task.cancel()
        await asyncio.gather(*self.tasks,return_exceptions=True)
        self.tasks.clear()
        for transport in self.transports: transport.close()
        self.transports.clear()
        if self.output: self.output.close();self.output=None

async def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--config',default='/etc/longmynd-dual.json')
    p.add_argument('--base-port',type=int,default=9900)
    p.add_argument('--bind',default='0.0.0.0')
    p.add_argument('--multicast-interface',default='0.0.0.0',help='IPv4 address of the multicast interface; default: route-selected')
    p.add_argument('--client',help='optional: accept tuning commands only from this PC IPv4 address')
    args=p.parse_args()
    logging.basicConfig(level=logging.INFO,format='%(asctime)s %(levelname)s %(message)s')
    bridge=Bridge(json.loads(Path(args.config).read_text()),args.base_port,args.bind,args.client,interface=args.multicast_interface)
    stop=asyncio.Event();loop=asyncio.get_running_loop()
    for sig in (signal.SIGTERM,signal.SIGINT): loop.add_signal_handler(sig,stop.set)
    await bridge.start()
    try: await stop.wait()
    finally: await bridge.close()

if __name__=='__main__':
    asyncio.run(main())
