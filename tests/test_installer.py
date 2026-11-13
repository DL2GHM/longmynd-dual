#!/usr/bin/env python3
"""Installer validation and rollback tests; never modifies the host system."""
import argparse
import hashlib
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location('dual_installer', ROOT/'install.py')
m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)

class InstallerTests(unittest.TestCase):
    def test_unmanaged_receiver_is_not_taken_over(self):
        def result(text): return subprocess.CompletedProcess([],0,stdout=text,stderr='')
        with patch.object(m,'state',return_value='42'), patch.object(m,'run',return_value=result('42\n99\n')):
            with self.assertRaisesRegex(ValueError,'99'): m.check_manual_receiver()
        with patch.object(m,'state',return_value='42'), patch.object(m,'run',return_value=result('42\n')):
            m.check_manual_receiver()

    def test_distinct_streams_and_explicit_serials(self):
        c = m.default_config('pi', 'MASTER123', 'AUX456')
        m.validate(c)
        args = __import__('settings').receiver_args(c)
        self.assertEqual(args[:3], ['--dual','MASTER123','AUX456'])
        second = args[args.index('--next')+1:]
        self.assertIn('8081', second)
        self.assertEqual(second[second.index('-i')+1:second.index('-i')+3], ['230.0.0.13','4005'])
        for bad in ('same', 'port', 'serial', 'root'):
            c = m.default_config('pi', 'MASTER123', 'AUX456')
            if bad == 'same': c['aux_serial'] = c['master_serial']
            if bad == 'port': c['channels'][1]['web_port'] = 8080
            if bad == 'serial': c['master_serial'] = 'serial;touch /tmp/x'
            if bad == 'root': c['user'] = 'root'
            with self.assertRaises(ValueError): m.validate(c)

    def test_manifest_detects_damage_and_path_escape(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d); (root/'a.c').write_bytes(b'original')
            digest = hashlib.sha256(b'original').hexdigest()
            (root/'SHA256SUMS').write_text(digest+'  a.c\n')
            self.assertEqual(m.manifest_files(root), ['a.c'])
            (root/'a.c').write_bytes(b'modified')
            with self.assertRaises(ValueError): m.manifest_files(root)
            (root/'SHA256SUMS').write_text(digest+'  ../escape.c\n')
            with self.assertRaises(ValueError): m.manifest_files(root)

    def test_failed_activation_restores_original_service(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d); src = root/'src'; src.mkdir()
            (src/'longmynd').write_bytes(b'fake binary')
            (src/'source.c').write_text('source')
            (src/'SHA256SUMS').write_text('manifest')
            (src/'web/htdocs').mkdir(parents=True); (src/'web/htdocs/index.html').write_text('web')
            (src/'packaging').mkdir()
            for name in ('launcher.py', 'settings.py'): (src/'packaging'/name).write_text('module')
            calls = []
            def run(args, **kwargs):
                calls.append((list(map(str,args)),kwargs))
                return subprocess.CompletedProcess(args,0,stdout='',stderr='')
            def state(name, prop):
                if name == 'longmynd.service' and prop == 'LoadState': return 'loaded'
                return 'active' if name == 'longmynd.service' and prop == 'ActiveState' else ('enabled' if name == 'longmynd.service' and prop == 'UnitFileState' else '')
            args = argparse.Namespace(start=True, skip_deps=True, jobs=2)
            config = m.default_config('pi','MASTER123','AUX456')
            with patch.object(m,'ROOT',src), patch.object(m,'PREFIX',root/'opt'), \
                 patch.object(m,'CONFIG',root/'config.json'), patch.object(m,'UNIT',str(root/'dual.service')), \
                 patch.object(m,'run',run), patch.object(m,'state',state), \
                 patch.object(m,'devices',return_value=[{'serial':'MASTER123'},{'serial':'AUX456'}]), \
                 patch.object(m,'check_manual_receiver'), patch.object(m.pwd,'getpwnam'), \
                 patch.object(m.grp,'getgrnam'), patch.object(m.shutil,'which',return_value='/fake/tool'), \
                 patch.object(m.time,'monotonic',side_effect=[0,31]):
                with self.assertRaisesRegex(ValueError,'30 Sekunden'):
                    m.install(args,config,['source.c'])
            commands = [c for c,k in calls]
            self.assertIn(['systemctl','disable','--now','longmynd-dual.service'],commands)
            self.assertIn(['systemctl','disable','--now','longmynd.service'],commands)
            self.assertIn(['systemctl','enable','longmynd.service'],commands)
            self.assertIn(['systemctl','start','longmynd.service'],commands)
            # Failure must not erase the existing receiver installation.
            self.assertFalse(any(c[0]=='rm' for c in commands))

if __name__ == '__main__': unittest.main()
