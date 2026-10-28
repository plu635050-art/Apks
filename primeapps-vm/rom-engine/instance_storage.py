#!/usr/bin/env python3
"""Create isolated copy-on-write instance disks from a verified ext4 guest base.

Produces four or more independent storage instances, NOT running virtual machines.
Requires qemu-utils and e2fsprogs; never modifies the source system image.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess


def command(*args):
    r = subprocess.run([str(a) for a in args], capture_output=True, text=True, check=False, timeout=120)
    if r.returncode:
        raise RuntimeError('Command failed: '+str(args[0])+' '+(r.stderr+r.stdout)[-1600:])
    return r.stdout


def digest(path):
    h = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for chunk in iter(lambda: stream.read(2 * 1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()


def check_ext4(path):
    path = Path(path)
    if not path.is_file() or path.stat().st_size < 32 * 1024 * 1024:
        raise ValueError('Guest base must be a real ext4 image >= 32 MiB')
    with path.open('rb') as stream:
        stream.seek(1024+56)
        if stream.read(2) != b'\x53\xef':
            raise ValueError('Expected ext4 superblock magic, not a guest ext4 image')


def plan_group(base, workspace, count=4, data_mib=256, prefix='android10', port_base=6300):
    if not 1 <= count <= 16:
        raise ValueError('Group must contain between 1 and 16 instances; the overall 4-VM target is validated separately')
    if not 1024 <= port_base + 1 <= port_base + count <= 65535:
        raise ValueError('Guest ADB port range invalid')
    if not 64 <= data_mib <= 8192:
        raise ValueError('data_mib out of bounds: 64..8192 MiB')
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_-]{0,24}', prefix):
        raise ValueError('Unsafe VM group name')
    for program in ('qemu-img', 'mke2fs', 'e2fsck'):
        if not shutil.which(program):
            raise RuntimeError('Missing executable '+program)
    base = Path(base).resolve()
    check_ext4(base)
    workspace = Path(workspace).resolve()
    if workspace == base.parent or workspace == base or base.is_relative_to(workspace):
        # The base should not be inside the output that might be deleted together with guests.
        raise ValueError('Shared source image must remain outside the instance workspace')
    manifest_file = workspace / 'instances.json'
    if manifest_file.exists() or workspace.exists() and any(workspace.iterdir()):
        raise FileExistsError('Refusing to overwrite existing VM workspace')
    workspace.mkdir(parents=True, exist_ok=True)
    before = digest(base)
    instances = []
    try:
        for number in range(1, count+1):
            name = f'{prefix}-{number:02d}'
            folder = workspace/name
            folder.mkdir()
            overlay = folder/'system.qcow2'
            # Relative backing file, so moving the entire workspace with the base intact is possible.
            relative_base = str(Path(__import__('os').path.relpath(base, folder)))
            command('qemu-img','create','-f','qcow2','-F','raw','-b',relative_base,overlay)
            spec = json.loads(command('qemu-img','info','--output=json',overlay))
            if spec.get('format')!='qcow2' or spec.get('backing-filename-format')!='raw':
                raise RuntimeError('Unexpected QCOW2 backing metadata')
            userdata = folder/'userdata.ext4'
            with userdata.open('wb') as f:
                f.truncate(data_mib*1024*1024)
            command('mke2fs','-q','-F','-t','ext4','-m','0','-L',f'vmdata{number:02d}',userdata)
            fsck = subprocess.run(['e2fsck','-fn',str(userdata)],capture_output=True,text=True)
            if fsck.returncode:
                raise RuntimeError('Bad guest ext4 data disk: '+fsck.stdout+fsck.stderr)
            instances.append({
                'id':name,'system_overlay':str(overlay.relative_to(workspace)),
                'userdata':str(userdata.relative_to(workspace)),
                'adb_port_proposed':port_base+number,
                'status':'DISKS_CREATED_NOT_BOOTED',
                'guest_root_verified':False,'guest_boot_verified':False,
                'guest_adb_verified':False,
            })
        if digest(base) != before:
            raise RuntimeError('Shared guest base changed during instance creation')
        result={'schema':1,'base_sha256':before,'base_path_external':str(base),
                'instance_count':count,'guest_boot_verified':False,
                'four_simultaneous_boots_verified':False,
                'host_android16_verified':False,'instances':instances}
        manifest_file.write_text(json.dumps(result,indent=2,ensure_ascii=False),encoding='utf-8')
        return result
    except BaseException:
        # The workspace was required to be empty. Clean only the newly-created files.
        shutil.rmtree(workspace,ignore_errors=True)
        raise

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('ext4_base',type=Path)
    p.add_argument('workspace',type=Path)
    p.add_argument('--count',type=int,default=4)
    p.add_argument('--data-mib',type=int,default=256)
    p.add_argument('--name',default='android10')
    p.add_argument('--adb-port-base',type=int,default=6300)
    a=p.parse_args()
    result=plan_group(a.ext4_base,a.workspace,a.count,a.data_mib,a.name,a.adb_port_base)
    print(json.dumps({'instance_count':result['instance_count'],
                      'status':'DISKS_CREATED_NOT_BOOTED',
                      'manifest':str(a.workspace/'instances.json')},indent=2))