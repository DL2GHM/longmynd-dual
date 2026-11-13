"""Validated Longmynd Dual configuration. GPL-3.0-or-later."""
import ipaddress
import re

VERSION = '0.1.0-beta.1'

def default_config(user, master, aux):
    return {'schema': 1, 'user': user, 'master_serial': master, 'aux_serial': aux,
            'channels': [dict(web_port=8080+i, ts_ip=f'230.0.0.{12+i}',
                ts_port=4003+2*i, status_ip=f'230.0.0.{12+i}', status_port=4002+2*i,
                frequency_khz=741538, symbolrate_ksps=1500, rf_bottom=False,
                timeout_ms=-1) for i in range(2)]}

def validate(c):
    if c.get('schema') != 1: raise ValueError('Unbekannte Konfigurationsversion.')
    if not re.fullmatch(r'[a-z_][a-z0-9_-]*', c.get('user', '')) or c['user'] == 'root':
        raise ValueError('Ein vorhandener Benutzer ohne Root-Rechte ist erforderlich.')
    for name in ('master_serial', 'aux_serial'):
        if not re.fullmatch(r'[A-Za-z0-9_-]{1,64}', c.get(name, '')):
            raise ValueError('Ungueltige oder fehlende USB-Seriennummer: '+name)
    if c['master_serial'] == c['aux_serial']: raise ValueError('MASTER und AUX muessen verschieden sein.')
    if len(c.get('channels', [])) != 2: raise ValueError('Genau zwei Kanaele erforderlich.')
    endpoints = set()
    ports = set()
    for ch in c['channels']:
        for key, lo, hi in [('web_port', 1, 65535), ('ts_port', 1, 65535),
                ('status_port', 1, 65535), ('frequency_khz', 144000, 2450000),
                ('symbolrate_ksps', 33, 27500)]:
            if type(ch.get(key)) is not int or not lo <= ch[key] <= hi:
                raise ValueError('Ungueltiger Wert: '+key)
        if ch['web_port'] in ports: raise ValueError('Web-Ports muessen verschieden sein.')
        ports.add(ch['web_port'])
        for kind in ('ts', 'status'):
            ip = str(ipaddress.IPv4Address(ch[kind+'_ip']))
            ep = (ip, ch[kind+'_port'])
            if ep in endpoints: raise ValueError('UDP-Ziele muessen verschieden sein.')
            endpoints.add(ep)
        if type(ch.get('rf_bottom')) is not bool: raise ValueError('rf_bottom muss true/false sein.')
        t = ch.get('timeout_ms')
        if type(t) is not int or (t != -1 and not 1000 <= t <= 2147483647):
            raise ValueError('timeout_ms: -1 oder mindestens 1000.')
    return c

def receiver_args(c):
    validate(c)
    result = ['--dual', c['master_serial'], c['aux_serial']]
    for i, ch in enumerate(c['channels']):
        if i: result.append('--next')
        if ch['rf_bottom']: result.append('-w')
        result += ['-r', str(ch['timeout_ms']), '-W', str(ch['web_port']),
                   '-i', ch['ts_ip'], str(ch['ts_port']),
                   '-I', ch['status_ip'], str(ch['status_port']),
                   str(ch['frequency_khz']), str(ch['symbolrate_ksps'])]
    return result
