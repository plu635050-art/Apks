#!/usr/bin/env python3
"""Extract partition readonly.bin from a user-supplied androidfs_*.7z archive.
Requires Linux libarchive.so.13; never uploads user ROM content.
"""
import argparse
import ctypes as C
import os
from pathlib import Path
import hashlib

L=C.CDLL('libarchive.so.13')
L.archive_read_new.restype=C.c_void_p
for func in ('archive_read_support_filter_all','archive_read_support_format_all','archive_read_free'):
    getattr(L,func).argtypes=[C.c_void_p]
L.archive_read_open_filename.argtypes=[C.c_void_p,C.c_char_p,C.c_size_t]
L.archive_read_next_header.argtypes=[C.c_void_p,C.POINTER(C.c_void_p)]
L.archive_entry_pathname.argtypes=[C.c_void_p];L.archive_entry_pathname.restype=C.c_char_p
L.archive_entry_size.argtypes=[C.c_void_p];L.archive_entry_size.restype=C.c_int64
L.archive_read_data.argtypes=[C.c_void_p,C.c_void_p,C.c_size_t];L.archive_read_data.restype=C.c_ssize_t
L.archive_read_data_skip.argtypes=[C.c_void_p]
L.archive_error_string.argtypes=[C.c_void_p];L.archive_error_string.restype=C.c_char_p

def extract(rom,partition,out):
    archive=L.archive_read_new()
    L.archive_read_support_filter_all(archive);L.archive_read_support_format_all(archive)
    if L.archive_read_open_filename(archive,os.fsencode(rom),1048576)!=0:
        raise RuntimeError('Cannot open ROM archive')
    found=False
    try:
        while True:
            entry=C.c_void_p()
            result=L.archive_read_next_header(archive,C.byref(entry))
            if result==1:break
            if result!=0:raise RuntimeError(str(L.archive_error_string(archive)))
            name=L.archive_entry_pathname(entry).decode('utf-8','replace')
            if name.split('/')[-2:]==[partition,'readonly.bin']:
                if found:raise RuntimeError('Duplicate partition')
                found=True
                expected=L.archive_entry_size(entry);actual=0;sha=hashlib.sha256()
                out.parent.mkdir(parents=True,exist_ok=True)
                try:
                    with out.open('wb') as f:
                        buf=C.create_string_buffer(1048576)
                        while True:
                            n=L.archive_read_data(archive,buf,len(buf))
                            if n==0:break
                            if n<0:raise RuntimeError(str(L.archive_error_string(archive)))
                            chunk=buf.raw[:n]
                            f.write(chunk);sha.update(chunk);actual+=n
                    if actual!=expected:raise RuntimeError('Truncated readonly.bin')
                except Exception:
                    out.unlink(missing_ok=True)
                    raise
                print('Extracted',partition,actual,'SHA256',sha.hexdigest())
            else:L.archive_read_data_skip(archive)
        if not found:raise RuntimeError('Partition not found: '+partition)
    finally:L.archive_read_free(archive)

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('rom',type=Path)
    p.add_argument('partition',choices=('root','system','vendor'))
    p.add_argument('destination',type=Path)
    a=p.parse_args()
    extract(a.rom,a.partition,a.destination)
