#!/usr/bin/env python3
"""Public BATC FFT cache, GPL-3.0-or-later.

Protocol and wide-rate reference: M0DTS Live Tune / OpenTuner D00.12.
The feed is remote; these are estimates rather than local RF measurements.
"""
import asyncio
import logging
import struct
import time
from websockets.legacy.client import connect

URL='wss://eshail.batc.org.uk/wb/fft'
PROTOCOL='fft_m0dtslivetune'
START_KHZ=10490500
SPAN_KHZ=9000
LOG=logging.getLogger('longmynd-spectrum')

def decode_fft(raw):
    if not isinstance(raw,bytes) or len(raw)%2 or not 256<=len(raw)<=16384:
        raise ValueError('Unexpected FFT frame')
    return list(struct.unpack('<'+'H'*(len(raw)//2),raw))

def symbolrate(width_khz):
    for bound,rate in ((22,0),(65,33),(86,66),(195,125),(277,250),
                       (388,333),(700,500),(1200,1000),(1600,1500),(2200,2000)):
        if width_khz<bound:return rate
    return max(33,round(width_khz/200)*200)

def detect_signals(bins):
    if len(bins)<3:return []
    n=len(bins);noise=sorted(bins)[n//5];threshold=max(16000,noise+3500)
    smooth=[]
    for i in range(n):
        window=bins[max(0,i-1):min(n,i+2)];smooth.append(sum(window)/len(window))
    signals=[];begin=None
    for i in range(n+1):
        above=i<n and smooth[i]>threshold
        if above and begin is None:begin=i
        if not above and begin is not None:
            end=i-1
            if end-begin>=2:
                middle=smooth[begin+(end-begin)//3:begin+max(1,4*(end-begin)//5)]
                strength=sum(middle)/len(middle) if middle else max(smooth[begin:end+1])
                top=noise+.75*(strength-noise);left,right=begin,end
                while left<right and smooth[left]<top:left+=1
                while right>left and smooth[right]<top:right-=1
                rate=symbolrate((right-left)*SPAN_KHZ/n)
                if rate:
                    center=START_KHZ+((left+right)/2+1)*SPAN_KHZ/n
                    signals.append(dict(frequency_khz=round(center),symbolrate_ksps=rate,
                        start_khz=round(START_KHZ+left*SPAN_KHZ/n),
                        end_khz=round(START_KHZ+(right+1)*SPAN_KHZ/n)))
            begin=None
    return signals

class Spectrum:
    def __init__(self,url=URL):
        self.url=url;self.bins=[];self.signals=[];self.updated=0.;self.requested=0.
    def publish(self,raw):
        bins=decode_fft(raw)
        self.bins=bins;self.signals=detect_signals(bins);self.updated=time.monotonic()
    def snapshot(self):
        now=time.monotonic();self.requested=now;fresh=self.updated>0 and now-self.updated<3
        return dict(source='BATC / AMSAT-UK',online=fresh,start_khz=START_KHZ,span_khz=SPAN_KHZ,
                    bins=self.bins if fresh else [],signals=self.signals if fresh else [])
    async def run(self):
        while True:
            if time.monotonic()-self.requested>15:
                await asyncio.sleep(1);continue
            try:
                async with connect(self.url,subprotocols=[PROTOCOL],open_timeout=10,close_timeout=1,max_size=16384) as ws:
                    while time.monotonic()-self.requested<15:
                        raw=await asyncio.wait_for(ws.recv(),10)
                        if time.monotonic()-self.updated<.15:continue
                        try:self.publish(raw)
                        except ValueError:continue
            except asyncio.CancelledError:raise
            except Exception as e:LOG.warning('BATC spectrum: %s',e)
            self.updated=0;await asyncio.sleep(5)
