#!/usr/bin/env python3
"""Installer dry-run test: no system mutations and no running receiver needed."""
from pathlib import Path
import hashlib
import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('installer',ROOT/'install_bridge.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)

class InstallTests(unittest.TestCase):
    def test_dry_run_does_not_call_privileged_operations(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td);config=root/'config.json'
            config.write_text(json.dumps({'user':'pi','channels':[
                {'ts_ip':'230.0.0.12','ts_port':4003,'status_port':4002},
                {'ts_ip':'230.0.0.13','ts_port':4005,'status_port':4004}]}))
            (root/'bridge.py').write_text('example')
            (root/'SHA256SUMS').write_text(hashlib.sha256(b'example').hexdigest()+'  bridge.py\n')
            with patch.object(m,'ROOT',root),patch.object(m,'CONFIG',config),patch.object(m.pwd,'getpwnam'),\
                 patch.object(sys,'argv',['install_bridge.py','--dry-run']),patch.object(m,'run') as runner:
                self.assertEqual(m.main(),0)
                runner.assert_not_called()

    def test_corrupted_source_is_rejected_before_system_changes(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td);config=root/'config.json'
            config.write_text(json.dumps({'user':'pi','channels':[
                {'ts_ip':'230.0.0.12','ts_port':4003}, {'ts_ip':'230.0.0.13','ts_port':4005}]}))
            (root/'bridge.py').write_text('damaged')
            (root/'SHA256SUMS').write_text('0'*64+'  bridge.py\n')
            with patch.object(m,'ROOT',root),patch.object(m,'CONFIG',config),patch.object(m.pwd,'getpwnam'),\
                 patch.object(sys,'argv',['install_bridge.py']),patch.object(m,'run') as runner:
                self.assertEqual(m.main(),1);runner.assert_not_called()

if __name__=='__main__':unittest.main()
