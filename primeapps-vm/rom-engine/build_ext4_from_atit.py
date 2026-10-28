#!/usr/bin/env python3
"""Construct a real ext4 filesystem image from a recovered ATIT partition.

Requires Linux e2fsprogs: mke2fs, debugfs, e2fsck. This is not a VM engine.
The guest filesystem is read-only input; no host root or loop mount required.
"""
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

from atitreconstruction import Atit

MIB = 1024 * 1024


def run(argv, **kwargs):
    result = subprocess.run(argv, text=True, capture_output=True, check=False, **kwargs)
    if result.returncode:
        raise RuntimeError(f"{' '.join(map(str, argv[:3]))}: exit {result.returncode}: "
                           f"{(result.stdout + result.stderr)[-1800:]}")
    return result


def safe_inode_path(name):
    if '\n' in name or '\r' in name or '\\' in name or '"' in name or '\x00' in name:
        raise ValueError('Unsupported path for debugfs metadata restoration: ' + repr(name))
    if name.startswith('/') or any(part in ('.', '..') for part in Path(name).parts):
        raise ValueError('Unsafe guest path ' + repr(name))
    return '/' + name if name else '/'


def sufficient_size_mib(paths):
    regular = sum(x['size'] for x in paths if x['type'] == 'file')
    # Ext4 metadata, journals and directory entries need headroom. Cap output to avoid surprises.
    estimate = int(regular * 1.25 + max(64 * MIB, len(paths) * 4096))
    return max(96, math.ceil(estimate / (32*MIB)) * 32)


def make_image(readonly, output, label='system', size_mib=None):
    for name in ('mke2fs', 'debugfs', 'e2fsck'):
        if not shutil.which(name):
            raise RuntimeError(f'Missing e2fsprogs command {name}; run on Linux or GitHub Actions')
    if not label or len(label.encode('ascii')) > 16 or not label.isascii():
        raise ValueError('Ext4 label must be 1-16 ASCII characters')
    readonly, output = Path(readonly).resolve(), Path(output).resolve()
    if readonly == output:
        raise ValueError('Input and output must differ')
    if output.exists():
        raise FileExistsError('Refusing to overwrite existing image: ' + str(output))
    output.parent.mkdir(parents=True, exist_ok=True)
    reader = Atit(readonly)
    try:
        manifest = reader.manifest()
        records = [(p,reader.inodes[p['inode']]) for p in manifest['entries']]
        needed_mib = sufficient_size_mib(manifest['entries'])
        if size_mib is None:
            size_mib = needed_mib
        if size_mib < needed_mib:
            raise ValueError(f'Image too small ({size_mib} MiB); minimum safe estimate is {needed_mib} MiB')
        if size_mib > 8192:
            raise ValueError('Refusing image larger than 8 GiB')
        with tempfile.TemporaryDirectory(prefix='primevm-build-') as scratch:
            stage = Path(scratch)/'tree'
            reader.extract(stage)  # validates each guest file CRC32 and links
            (stage/'_ATIT_INDEX.json').unlink()  # index is NOT part of the guest filesystem
            # The extracted filenames are from a validated inode graph. No host sudo/mount.
            with output.open('wb') as f:
                f.truncate(size_mib * MIB)
            try:
                run(['mke2fs','-q','-F','-t','ext4','-b','4096','-I','256','-m','0',
                     '-O','^metadata_csum,^64bit,^orphan_file',
                     '-L',label,'-d',str(stage),str(output)])
                cmds = Path(scratch)/'metadata.debugfs'
                with cmds.open('w', encoding='utf-8') as f:
                    for item,record in records:
                        p=safe_inode_path(item['path'])
                        # Quote pathname only; debugfs parser treats spaces in quotes.
                        for field in ('uid','gid'):
                            value=record[field]
                            if not 0<=value<=65535:
                                raise ValueError('Cannot represent inode UID/GID in this ext4 build')
                            f.write(f'set_inode_field "{p}" {field} {value}\n')
                d=subprocess.run(['debugfs','-w','-f',str(cmds),str(output)],
                                 capture_output=True,text=True,check=False)
                diag=d.stdout+d.stderr
                if d.returncode or any(x in diag.lower() for x in
                                       ('command not found','file not found','not found by ext2_lookup',
                                        'invalid field specifier','invalid inode specifier')):
                    raise RuntimeError('debugfs failed to restore UID/GID: '+diag[-2500:])
                fsck=subprocess.run(['e2fsck','-fn',str(output)],
                                    capture_output=True,text=True,check=False)
                # -n never fixes anything. bit 2: reboot required; bit 4: uncorrected errors.
                if fsck.returncode != 0:
                    raise RuntimeError('Ext4 image has filesystem errors: '+(fsck.stdout+fsck.stderr)[-2500:])
            except Exception:
                output.unlink(missing_ok=True)
                raise
    finally:
        reader.close()
    info={
        'format':'ext4', 'bootable':False,'guest_rootfs_only':True,
        'source':str(readonly),'image':str(output),'label':label,'size_mib':size_mib,
        'paths':manifest['path_count'],'inode_count':manifest['inode_count'],
        'file_count':manifest['types'].get('file',0),
        'sha256':hashlib.sha256(output.read_bytes()).hexdigest() if size_mib <= 256 else None,
        'metadata_restored':['uid','gid','mode-from-extracted-source'],
        'metadata_not_restored':['SELinux xattrs','overlay','sparse runtime state','kernel','bootloader'],
    }
    # Do not embed host filesystem paths in a shipped Android VM configuration.
    report=output.with_name(output.name+'.json')
    report.write_text(json.dumps(info,indent=2,ensure_ascii=False),encoding='utf-8')
    return info


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('readonly_bin',type=Path)
    p.add_argument('output_ext4',type=Path)
    p.add_argument('--label',default='system')
    p.add_argument('--size-mib',type=int)
    args=p.parse_args()
    print(json.dumps(make_image(args.readonly_bin,args.output_ext4,args.label,args.size_mib),indent=2))