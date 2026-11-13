import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import install_mobile

class InstallTests(unittest.TestCase):
    def test_dry_run_checks_manifest_without_system_changes(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td);(root/'relay.py').write_text('# source\n')
            digest=hashlib.sha256((root/'relay.py').read_bytes()).hexdigest()
            (root/'SHA256SUMS').write_text(digest+'  relay.py\n')
            config={'user':'pi','channels':[{'web_port':8080},{'web_port':8081}]}
            original=Path.read_text
            def read(path,*a,**kw):
                if str(path)=='/etc/longmynd-dual.json':return json.dumps(config)
                return original(path,*a,**kw)
            with patch.object(install_mobile,'ROOT',root),patch.object(Path,'read_text',read),patch('pwd.getpwnam'),patch.object(install_mobile,'run') as run,patch('sys.argv',['installer','--dry-run']):
                self.assertEqual(install_mobile.main(),0);run.assert_not_called()
                (root/'relay.py').write_text('# corrupt\n')
                self.assertEqual(install_mobile.main(),1);run.assert_not_called()

if __name__=='__main__':unittest.main()
