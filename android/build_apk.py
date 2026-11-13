#!/usr/bin/env python3
"""SDK-only reproducible build entry point. Own sources GPL-3.0-or-later."""
from pathlib import Path
import zipfile,subprocess,os,hashlib
import argparse,tempfile,shutil
parser=argparse.ArgumentParser(description='Build Longmynd Mobil with Android SDK tools and Java 17')
parser.add_argument('--sdk',type=Path,required=True);parser.add_argument('--jdk',type=Path,required=True)
parser.add_argument('--vlc-aar',type=Path,required=True);parser.add_argument('--keystore',type=Path,required=True)
parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
ROOT=Path(__file__).resolve().parent
work=tempfile.TemporaryDirectory(prefix='longmynd-apk-');W=Path(work.name)
J=args.jdk/'bin';B=args.sdk/'build-tools/35.0.0';A=args.sdk/'platforms/android-35/android.jar';P=ROOT/'app/mobile/src/main'
AAR=args.vlc_aar
assert hashlib.sha256(AAR.read_bytes()).hexdigest()=='6a15a1f7acd0738a31acce0c7e5eb6f5f340b62e6b64d8f83df547da56a44b47', 'Unexpected libVLC AAR'
VLC_CLASSES=W/'libvlc-classes.jar'
with zipfile.ZipFile(AAR) as z:VLC_CLASSES.write_bytes(z.read('classes.jar'))
def run(*args):subprocess.run(list(map(str,args)),check=True)
with zipfile.ZipFile(AAR) as z:
 for name in z.namelist():
  if name.startswith('res/') and not name.endswith('/'):
   p=W/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(z.read(name))
# Include the application's launcher resources alongside libVLC resources.
if (P/'res').exists(): shutil.copytree(P/'res', W/'res', dirs_exist_ok=True)
(W/'gen').mkdir(exist_ok=True);(W/'classes').mkdir(exist_ok=True);(W/'dex').mkdir(exist_ok=True)
run(B/'aapt2','compile','--dir',W/'res','-o',W/'resources.zip')
manifest=W/'AndroidManifest.xml';manifest.write_text((P/'AndroidManifest.xml').read_text().replace('<manifest ', '<manifest package="de.dl2ghm.longmyndmobil" ',1))
run(B/'aapt2','link','-I',A,'--manifest',manifest,'--java',W/'gen','--custom-package','org.videolan',
 '--min-sdk-version','26','--target-sdk-version','35','--version-code','39','--version-name','0.1.0-beta.39','-o',W/'base.apk',W/'resources.zip')
sources=[*P.rglob('*.java'),*(W/'gen').rglob('*.java')]
compiler=[J/'javac'] if (J/'javac').exists() else [J/'java','com.sun.tools.javac.Main']
# Some runtime JDKs omit ct.sym; preserve Java 17 bytecode in that case.
platform_flags=['--release','17'] if (args.jdk/'lib/ct.sym').exists() else ['-source','17','-target','17']
run(*compiler,'-encoding','UTF-8',*platform_flags,'-classpath',str(A)+':'+str(VLC_CLASSES),'-d',W/'classes',*sources)
with zipfile.ZipFile(W/'app-classes.jar','w',zipfile.ZIP_DEFLATED) as z:
 for p in (W/'classes').rglob('*.class'):z.write(p,p.relative_to(W/'classes').as_posix())
run(J/'java','-cp',B/'lib/d8.jar','com.android.tools.r8.D8','--release','--min-api','26','--lib',A,'--output',W/'dex',W/'app-classes.jar',VLC_CLASSES)
with zipfile.ZipFile(W/'base.apk','a',zipfile.ZIP_DEFLATED) as z,zipfile.ZipFile(AAR) as aar:
 for p in (W/'dex').glob('*.dex'):z.write(p,p.name)
 for name in aar.namelist():
  if name.endswith('/'):continue
  if name.startswith(('jni/armeabi-v7a/','jni/arm64-v8a/')):z.writestr('lib/'+name[4:],aar.read(name))
  elif name.startswith('assets/'):z.writestr(name,aar.read(name))
 z.writestr('assets/Longmynd-LICENSE.txt',(ROOT/'LICENSE').read_bytes())
 z.writestr('assets/README-Longmynd.txt',(ROOT/'README.md').read_bytes())
run(B/'zipalign','-f','-p','4',W/'base.apk',W/'aligned.apk')
key=args.keystore
if not key.exists():run(J/'keytool','-genkeypair','-keystore',key,'-storepass','android','-keypass','android','-alias','longmynd-mobile','-dname','CN=Longmynd Mobile Development, O=DL2GHM','-keyalg','RSA','-keysize','2048','-validity','10000')
out=args.output;out.parent.mkdir(parents=True,exist_ok=True)
run(J/'java','-jar',B/'lib/apksigner.jar','sign','--ks',key,'--ks-key-alias','longmynd-mobile','--ks-pass','pass:android','--key-pass','pass:android','--out',out,W/'aligned.apk')
run(J/'java','-jar',B/'lib/apksigner.jar','verify','--verbose',out)
run(B/'aapt2','dump','badging',out)
print('APK:',out,out.stat().st_size,'sha256',hashlib.sha256(out.read_bytes()).hexdigest())
