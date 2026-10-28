#!/usr/bin/env python3
"""Offline experiment: replace proprietary ARM64 syscall-thunk in a COPY of init.

Restores Linux SVC calls for exact known hook prologue in VPhoneGaGa Android guest.
Does not modify original ROMs and does not prove the guest can boot.
"""
from pathlib import Path
import argparse,hashlib,struct
SIGNATURE=bytes.fromhex('0800b6d2080d40f900011fd6')
PATCH=bytes.fromhex('e8835ff8010000d4f7ffff17')
# stur x8,[sp,#-8] ; (any adr x8) ; stur x8,[sp,#-16]
PRE_0=bytes.fromhex('e8831ff8')
PRE_2=bytes.fromhex('e8031ff8')
def convert(src:Path,dest:Path,minimum=1):
    if src.resolve()==dest.resolve():raise ValueError('Refusing in-place mutation')
    data=src.read_bytes()
    if data[:4]!=b'\x7fELF' or data[4]!=2 or data[18:20]!=b'\xb7\x00':
        raise ValueError('Requires 64-bit AArch64 ELF')
    offsets=[];start=0
    while True:
        i=data.find(SIGNATURE,start)
        if i<0:break
        start=i+len(SIGNATURE)
        if i<12 or data[i-12:i-8]!=PRE_0 or data[i-4:i]!=PRE_2:
            raise ValueError(f'Unexpected hook prologue @ file offset {i:#x}')
        # instruction at i-8 must be an ADR X8 (varying immediate)
        adr=struct.unpack_from('<I',data,i-8)[0]
        if (adr&0x9F00001F)!=0x10000008:
            raise ValueError(f'Unexpected ADR X8 @ file offset {i-8:#x}')
        offsets.append(i)
    if len(offsets)<minimum:raise ValueError(f'Not enough known thunk sites: {len(offsets)}')
    blob=bytearray(data)
    for i in offsets:blob[i:i+len(PATCH)]=PATCH
    dest.parent.mkdir(parents=True,exist_ok=True)
    dest.write_bytes(blob)
    return {'original_sha256':hashlib.sha256(data).hexdigest(),'patched_sha256':hashlib.sha256(blob).hexdigest(),'replaced_thunks':len(offsets),'first_offsets':[hex(x) for x in offsets[:6]]}
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('input',type=Path);p.add_argument('output',type=Path);a=p.parse_args()
    print(convert(a.input,a.output))