#!/usr/bin/env python3
"""Restore SELinux security.selinux xattrs to a *copy* of an EXT4 guest image.

The labels are matched against official Android file_contexts using libselinux.
The source ATIT readonly.bin is read only, the original EXT4 never modified.
This is guest filesystem preparation, NOT proof of Android guest boot.
"""
import argparse
import ctypes
import json
import os
import re
import shutil
import stat
import subprocess
import tempfile
from pathlib import Path
from atitreconstruction import Atit


class Labeler:
    def __init__(self, contexts):
        library=ctypes.CDLL('libselinux.so.1', use_errno=True)
        class Option(ctypes.Structure):
            _fields_=[('type',ctypes.c_int),('value',ctypes.c_char_p)]
        library.selabel_open.argtypes=[ctypes.c_uint,ctypes.POINTER(Option),ctypes.c_uint]
        library.selabel_open.restype=ctypes.c_void_p
        library.selabel_lookup_raw.argtypes=[ctypes.c_void_p,ctypes.POINTER(ctypes.c_char_p),ctypes.c_char_p,ctypes.c_int]
        library.selabel_lookup_raw.restype=ctypes.c_int
        library.selabel_close.argtypes=[ctypes.c_void_p]
        library.freecon.argtypes=[ctypes.c_char_p]
        self.lib=library
        options=Option(3, os.fsencode(contexts))  # SELABEL_OPT_PATH = 3; SELABEL_CTX_FILE = 0
        self.handle=library.selabel_open(0,ctypes.byref(options),1)
        if not self.handle:
            raise RuntimeError('Could not open Android file_contexts (old compiled formats may be incompatible)')

    def lookup(self,guest_path,mode):
        result=ctypes.c_char_p()
        ret=self.lib.selabel_lookup_raw(self.handle,ctypes.byref(result),os.fsencode(guest_path),mode)
        if ret!=0:return None
        try:return result.value.decode('ascii')
        finally:self.lib.freecon(result)

    def close(self):
        if self.handle:self.lib.selabel_close(self.handle);self.handle=None


def debugfs_escape(path):
    # debugfs parses quoted filenames, so refuse control chars and quotes.
    if re.search(r'[\x00-\x1f\\"]',path):raise ValueError('Unsafe guest pathname')
    return '"'+path+'"'


def normalize_prefix(prefix,name):
    if not prefix.startswith('/') or '..' in prefix.split('/'):
        raise ValueError('Invalid guest prefix')
    return prefix.rstrip('/')+'/'+name if name else prefix.rstrip('/') or '/'


def plan_labels(readonly,contexts,prefix='/', strict=False):
    reader=Atit(readonly)
    labeler=Labeler(contexts)
    try:
        plan=[];unmatched=[]
        walk = reader.paths() if hasattr(reader,'paths') else reader.walk()
        inodes = reader.inodes if hasattr(reader,'inodes') else reader.records
        for path,index in walk:
            if index>=len(inodes):raise ValueError('Bad ATIT inode')
            record=inodes[index]
            guest=normalize_prefix(prefix,path)
            label=labeler.lookup(guest,record['mode'])
            if label is None:
                unmatched.append(guest)
            else:
                if not re.fullmatch(r'[A-Za-z0-9_:.@-]+',label):
                    raise ValueError('Unsafe SELinux label: '+repr(label))
                plan.append({'image_path':'/'+path if path else '/','guest_path':guest,
                             'label':label, 'mode':record['mode']})
        if strict and unmatched:raise RuntimeError('Unmatched SELinux file contexts: '+repr(unmatched[:8]))
        return plan,unmatched
    finally:
        labeler.close();reader.close()


def relabel(source,output,readonly,contexts,prefix='/',strict=False,debugfs='debugfs',e2fsck='e2fsck'):
    source=Path(source).resolve();output=Path(output).resolve()
    if source==output:raise ValueError('Input and output EXT4 must differ')
    if output.exists():raise FileExistsError('Do not overwrite existing output '+str(output))
    if source.stat().st_size<1024*1024:raise ValueError('Image too small')
    plan,unmatched=plan_labels(readonly,contexts,prefix,strict)
    if not plan:raise RuntimeError('No SELinux labels matched; do not write unlabelled image')
    output.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='primevm-label-') as tmp:
        script=Path(tmp)/'setlabels.debugfs'
        with script.open('w') as f:
            for entry in plan:
                f.write('ea_set '+debugfs_escape(entry['image_path'])+' security.selinux '+entry['label']+'\n')
        try:
            subprocess.run(['cp','--sparse=always','--reflink=auto','--',str(source),str(output)],check=True)
            res=subprocess.run([debugfs,'-w','-f',str(script),str(output)],text=True,capture_output=True)
            text=res.stdout+'\n'+res.stderr
            if res.returncode!=0 or re.search(r'(?:\berror\b|invalid argument|not found by ext2_lookup|file not found|command not found)',text,re.I):
                raise RuntimeError('debugfs relabel failed: '+text[-1500:])
            # Verify a handful of important labels by actual readback, not just debugfs exit code.
            selected=[]
            for sought in ('/init','/system/bin/servicemanager','/system/bin/app_process64',
                           '/system/bin/surfaceflinger','/vendor/bin/sh','/build.prop'):
                selected.extend([x for x in plan if x['guest_path']==sought])
            if not selected:selected=plan[:min(5,len(plan))]
            for item in selected:
                x=subprocess.run([debugfs,'-R','ea_get '+debugfs_escape(item['image_path'])+' security.selinux',str(output)],text=True,capture_output=True)
                if x.returncode!=0 or item['label'] not in x.stdout+x.stderr:
                    raise RuntimeError('Xattr readback mismatch: '+item['guest_path']+' '+str(x.stdout))
            check=subprocess.run([e2fsck,'-fn',str(output)],text=True,capture_output=True)
            if check.returncode!=0:raise RuntimeError('EXT4 integrity check failed: '+(check.stdout+check.stderr)[-1800:])
        except Exception:
            output.unlink(missing_ok=True)
            raise
    summary={'format':'ext4','bootable':False,'source':str(source),'output':str(output),
             'guest_prefix':prefix,'labeled':len(plan),'unmatched_count':len(unmatched),
             'unmatched_examples':unmatched[:20],'labels_verified_by_debugfs':len(selected),
             'filesystems_checked_by_e2fsck':1, 'not_validated':['Android boot','SELinux policy loading','guest services','Android16 host']}
    output.with_suffix(output.suffix+'.labels.json').write_text(json.dumps(summary,indent=2))
    return summary


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('source_ext4',type=Path);p.add_argument('output_ext4',type=Path)
    p.add_argument('readonly_bin',type=Path)
    p.add_argument('--contexts',required=True,type=Path,action='append',help='Android text file_contexts (repeatable)')
    p.add_argument('--guest-prefix',default='/',help='e.g. /system or /vendor')
    p.add_argument('--strict',action='store_true')
    p.add_argument('--debugfs',default='debugfs');p.add_argument('--e2fsck',default='e2fsck')
    args=p.parse_args()
    # Combine platform+vendor patterns into one file for proper specificity matching.
    with tempfile.TemporaryDirectory() as tmp:
        combined=Path(tmp)/'file_contexts'
        combined.write_bytes(b'\n'.join(p.read_bytes() for p in args.contexts)+b'\n')
        print(json.dumps(relabel(args.source_ext4,args.output_ext4,args.readonly_bin,combined,
                                  args.guest_prefix,args.strict,args.debugfs,args.e2fsck),indent=2))