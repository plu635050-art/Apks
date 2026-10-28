#!/usr/bin/env python3
"""Host/guest file & APK transfer via authorized ADB connection to a *running* guest.

Requires a booted Android guest with an authorized adb endpoint. No guest boot
is provided here. The Android app will use SAF to obtain host files later.
"""
import argparse
from pathlib import Path
import re
import subprocess

PACKAGE = re.compile(r'^[a-zA-Z][a-zA-Z0-9_]*(?:\.[a-zA-Z0-9_]+)+$')
SERIAL = re.compile(r'^127\.0\.0\.1:(\d{4,5})$')


class GuestBridge:
    def __init__(self,serial, runner=None):
        m=SERIAL.fullmatch(serial)
        if not m or not 1024 <= int(m.group(1)) <= 65535:
            raise ValueError('Must use an explicitly selected local guest ADB port')
        self.serial=serial
        self.runner=runner or subprocess.run

    def execute(self, *cmd):
        p=self.runner(['adb','-s',self.serial,*map(str,cmd)],
                      capture_output=True,text=True,timeout=90,check=False)
        if p.returncode:
            raise RuntimeError(f'Guest ADB command failed ({cmd[0]}): '+p.stderr[-500:])
        return p.stdout

    def check_guest(self):
        if self.execute('get-state').strip()!='device':
            raise RuntimeError('Guest ADB endpoint not authorized/running')

    def host_to_guest(self,host_file, guest_name=None):
        source=Path(host_file).resolve()
        if not source.is_file():raise FileNotFoundError(str(source))
        name=guest_name or source.name
        if name!=Path(name).name or name in ('.','..') or '/' in name or '\\' in name:
            raise ValueError('Unsafe guest destination filename')
        self.check_guest()
        dest='/sdcard/Download/'+name
        self.execute('push',source,dest)
        return dest

    def guest_to_host(self,guest_name, host_dir):
        if guest_name!=Path(guest_name).name or guest_name in ('.','..') or '\\' in guest_name:
            raise ValueError('Only a single guest Downloads filename can be exported')
        self.check_guest()
        dest=Path(host_dir).resolve()
        dest.mkdir(parents=True,exist_ok=True)
        target=dest/guest_name
        if target.exists():raise FileExistsError(str(target))
        self.execute('pull','/sdcard/Download/'+guest_name,target)
        if not target.is_file():raise RuntimeError('Guest pull did not produce a file')
        return target

    def install_host_apk(self,paths):
        paths=[Path(x).resolve() for x in paths]
        if not paths or any(not p.is_file() or p.suffix.lower()!='.apk' for p in paths):
            raise ValueError('APK installation requires valid local .apk files')
        self.check_guest()
        if len(paths)==1:self.execute('install','-r',paths[0])
        else:self.execute('install-multiple','-r',*paths)

    def export_guest_apk(self,package,output):
        if not PACKAGE.fullmatch(package):raise ValueError('Invalid Android package name')
        self.check_guest()
        paths=[]
        for line in self.execute('shell','pm','path',package).splitlines():
            if line.startswith('package:'):
                p=line[8:].strip()
                if not p.startswith('/') or '..' in Path(p).parts or not p.endswith('.apk'):
                    raise ValueError('Unexpected guest APK path')
                paths.append(p)
        if not paths:raise FileNotFoundError('Guest package not installed: '+package)
        out=Path(output).resolve()
        if out.exists():raise FileExistsError(str(out))
        out.mkdir(parents=True)
        try:
            for n,p in enumerate(paths):
                basename=Path(p).name
                safe=f'{n:02d}_{basename}'
                self.execute('pull',p,out/safe)
                if not (out/safe).is_file():raise RuntimeError('Guest APK export incomplete')
            return [out/f'{n:02d}_{Path(p).name}' for n,p in enumerate(paths)]
        except Exception:
            import shutil
            shutil.rmtree(out)
            raise


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--serial',required=True,help='explicit guest serial e.g. 127.0.0.1:6301')
    sub=p.add_subparsers(dest='cmd',required=True)
    q=sub.add_parser('push');q.add_argument('file',type=Path)
    q=sub.add_parser('pull');q.add_argument('file_name');q.add_argument('output_dir',type=Path)
    q=sub.add_parser('install');q.add_argument('apks',type=Path,nargs='+')
    q=sub.add_parser('export-app');q.add_argument('package');q.add_argument('output_dir',type=Path)
    a=p.parse_args();bridge=GuestBridge(a.serial)
    if a.cmd=='push':print(bridge.host_to_guest(a.file))
    elif a.cmd=='pull':print(bridge.guest_to_host(a.file_name,a.output_dir))
    elif a.cmd=='install':bridge.install_host_apk(a.apks);print('Installed')
    elif a.cmd=='export-app':print(*bridge.export_guest_apk(a.package,a.output_dir),sep='\n')