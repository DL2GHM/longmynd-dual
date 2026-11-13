#!/usr/bin/env python3
"""Read-only Longmynd dual diagnostics; Python 3 standard library only.
Reads monitor WebSockets and subscribes to the configured local multicast TS.
Does not send control commands or touch USB/I2C/services.
"""
import argparse
import base64
from collections import Counter
import hashlib
import ipaddress
import json
import os
import select
import socket
import struct
import time

class Transport:
    def __init__(self):
        self.buffer = bytearray()
        self.bytes = self.datagrams = self.packets = self.tei = self.cc_errors = self.discarded = 0
        self.pids = Counter()
        self.previous = {}
    def feed(self, data):
        self.bytes += len(data)
        self.datagrams += 1
        self.buffer.extend(data)
        while len(self.buffer) >= 376:
            if self.buffer[0] != 0x47 or self.buffer[188] != 0x47:
                del self.buffer[0]; self.discarded += 1; continue
            p = bytes(self.buffer[:188]); del self.buffer[:188]
            pid = ((p[1] & 31) << 8) | p[2]
            self.packets += 1; self.pids[pid] += 1
            self.tei += bool(p[1] & 128)
            afc = (p[3] >> 4) & 3
            discontinuity = afc in (2,3) and p[4] > 0 and bool(p[5] & 128)
            if discontinuity: self.previous.pop(pid, None)
            if pid != 8191 and afc in (1,3):
                cc = p[3] & 15
                old = self.previous.get(pid)
                if old is not None and cc != (old[0]+1) % 16 and not (cc == old[0] and p == old[1]):
                    self.cc_errors += 1
                self.previous[pid] = (cc, p)


def monitor(host, port):
    with socket.create_connection((host, port), timeout=4) as connection:
        connection.settimeout(4)
        key = base64.b64encode(os.urandom(16)).decode()
        connection.sendall((f'GET / HTTP/1.1\r\nHost: {host}:{port}\r\n'
            'Upgrade: websocket\r\nConnection: Upgrade\r\n'
            f'Sec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n'
            'Sec-WebSocket-Protocol: monitor\r\n\r\n').encode())
        with connection.makefile('rb') as stream:
            status = stream.readline(4096)
            if b' 101 ' not in status: raise ValueError(f'WebSocket: {status!r}')
            headers = {}
            for _ in range(40):
                line = stream.readline(4096)
                if line == b'\r\n': break
                if not line: raise ValueError('EOF in HTTP headers')
                k,v = line.decode().split(':',1); headers[k.lower()] = v.strip()
            expected = base64.b64encode(hashlib.sha1((key+'258EAFA5-E914-47DA-95CA-C5AB0DC85B11').encode()).digest()).decode()
            if headers.get('sec-websocket-accept') != expected: raise ValueError('Invalid WS handshake')
            for _ in range(10):
                h = stream.read(2)
                if len(h) != 2: raise ValueError('WebSocket closed')
                length = h[1] & 127
                if length == 126: length = struct.unpack('!H', stream.read(2))[0]
                elif length == 127: length = struct.unpack('!Q', stream.read(8))[0]
                if length > 65536: raise ValueError('Oversize monitor frame')
                mask = stream.read(4) if h[1] & 128 else None
                payload = stream.read(length)
                if len(payload) != length: raise ValueError('Incomplete frame')
                if mask: payload = bytes(v ^ mask[i % 4] for i,v in enumerate(payload))
                if h[0] & 15 == 1:
                    data = json.loads(payload)
                    if data.get('type') == 'status': return data['packet']
            raise ValueError('No monitor status')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--seconds', type=int, default=8)
    parser.add_argument('--interface', default='0.0.0.0')
    args = parser.parse_args()
    if not 1 <= args.seconds <= 30: parser.error('--seconds must be 1..30')
    receivers = []
    for channel, web_port in enumerate((8080,8081),1):
        try:
            packet = monitor('127.0.0.1', web_port)
            rx, ts = packet['rx'], packet['ts']
            states = {0:'Init',1:'Hunting',2:'Header',3:'DVB-S Lock',4:'DVB-S2 Lock'}
            print(f'Kanal {channel}, WS {web_port}: {states.get(rx["demod_state"],rx["demod_state"])}; '
                  f'IF={rx["frequency"]} kHz; SR={rx["symbolrate"]/1000:.1f} kS/s; MER={rx["mer"]/10:.1f} dB')
            print(f'  Dienst={ts.get("service_name","")!r}; PIDs={ts.get("PIDs",[])}; '
                  f'TS-Ziel={rx["ts_ip_addr"]}:{rx["ts_ip_port"]}')
            group, port = rx['ts_ip_addr'], int(rx['ts_ip_port'])
            if not ipaddress.ip_address(group).is_multicast:
                print('  Kein Multicast: lokale Empfangsmessung fuer dieses Ziel uebersprungen.'); continue
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
            s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            s.setsockopt(socket.SOL_SOCKET, socket.SO_RCVBUF, 1024*1024)
            # Linux: exclude other sockets' joined groups on the same UDP port.
            s.setsockopt(socket.IPPROTO_IP, 49, 0)  # IP_MULTICAST_ALL
            s.bind(('', port))
            s.setsockopt(socket.IPPROTO_IP, socket.IP_ADD_MEMBERSHIP,
                         socket.inet_aton(group)+socket.inet_aton(args.interface))
            receivers.append((channel, s, Transport()))
        except (OSError, ValueError, KeyError, struct.error) as e:
            print(f'Kanal {channel}: Diagnosefehler: {e}')
    print(f'Messe beide Datenstroeme fuer {args.seconds} Sekunden ...', flush=True)
    start = time.monotonic(); deadline = start + args.seconds
    lookup = {s: stats for _,s,stats in receivers}
    while lookup and time.monotonic() < deadline:
        ready,_,_ = select.select(list(lookup), [], [], max(0,deadline-time.monotonic()))
        for s in ready: lookup[s].feed(s.recv(65536))
    duration = time.monotonic()-start
    for channel,s,stats in receivers:
        s.close()
        print(f'Kanal {channel}: {stats.bytes*8/duration/1000:.1f} kbit/s; '
              f'{stats.datagrams} UDP; {stats.packets} TS-Pakete; TEI={stats.tei}; '
              f'CC-Spruenge={stats.cc_errors}; Sync-Verlustbytes={stats.discarded}')
        print('  Haeufigste PIDs:', ', '.join(f'{pid}:{count}' for pid,count in stats.pids.most_common(8)))
    print('TEI = vom Demodulator als fehlerhaft markierte TS-Pakete. CC-Spruenge koennen auch Empfangs-/Pufferverluste sein.')

if __name__ == '__main__': main()
