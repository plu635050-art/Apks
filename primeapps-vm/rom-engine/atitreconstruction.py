#!/usr/bin/env python3
"""Read-only ATIT filesystem reader from VPhoneGaGa Gold ROMs.
This reconstructs guest files; it does NOT boot a VM.
Use with readonly.bin extracted from the user's own ROM archive.
"""
import argparse
from collections import Counter,deque
import json
from pathlib import Path
import stat
import struct
import zlib

class FormatError(Exception):
    pass

class Atit:
    def __init__(self,path):
        self.path=Path(path)
        self.fd=self.path.open('rb')
        self.available=self.path.stat().st_size
        h=self.read(0,64)
        if h[:4]!=b'ATIT' or struct.unpack_from('<I',h,4)[0]!=64:
            raise FormatError('Invalid ATIT header')
        self.count=struct.unpack_from('<I',h,8)[0]
        if not 0<self.count<5000000:raise FormatError('Invalid inode count')
        records=self.read(64,self.count*52)
        self.inodes=[]
        for i in range(self.count):
            d=struct.unpack_from('<13I',records,i*52)
            mode,uid,gid,links,atime,mtime,ctime,btime,unused,size,offset,crc,extra=d
            kind='directory' if stat.S_ISDIR(mode) else 'symlink' if stat.S_ISLNK(mode) else 'file' if stat.S_ISREG(mode) else 'special'
            self.inodes.append(dict(index=i,mode=mode,uid=uid,gid=gid,kind=kind,size=size,offset=offset,crc=crc))
    def read(self,offset,size):
        if offset<0 or size<0 or offset+size>self.available:
            raise FormatError('Truncated ATIT; supply the complete readonly.bin for extraction')
        self.fd.seek(offset);data=self.fd.read(size)
        if len(data)!=size:raise FormatError('Truncated content')
        return data
    def data(self,inode,limit=8*1024*1024):
        item=self.inodes[inode]
        if item['size']>limit:raise FormatError('Data object too large for in-memory read')
        b=self.read(item['offset'],item['size'])
        if zlib.crc32(b)&0xffffffff!=item['crc']:raise FormatError('CRC error in inode '+str(inode))
        return b
    def children(self,inode):
        if self.inodes[inode]['kind']!='directory':raise FormatError('Not a directory')
        b=self.data(inode)
        i=0
        while i<len(b):
            if i+4>len(b):raise FormatError('Broken directory')
            length=struct.unpack_from('<I',b,i)[0];i+=4
            if length<2 or length>256 or i+length+8>len(b):raise FormatError('Invalid directory record')
            name=b[i:i+length];i+=length
            if name[-1]!=0:raise FormatError('Invalid name terminator')
            inode_id=struct.unpack_from('<Q',b,i)[0];i+=8
            name=name[:-1].decode('utf-8','surrogateescape')
            if name in ('.','..') or '/' in name or '\\' in name or '\x00' in name or inode_id>=self.count:
                raise FormatError('Unsafe guest directory record')
            yield name,inode_id
    def paths(self):
        result=[('',0)]
        q=deque([('',0,(0,))]);seen={''}
        while q:
            path,inode,parents=q.popleft()
            if self.inodes[inode]['kind']!='directory':continue
            if len(parents)>128:raise FormatError('Directory tree too deep')
            for name,target in self.children(inode):
                p=(path+'/' if path else '')+name
                if p in seen:raise FormatError('Duplicate path')
                seen.add(p);result.append((p,target))
                if self.inodes[target]['kind']=='directory':
                    if target in parents:raise FormatError('Directory cycle')
                    q.append((p,target,parents+(target,)))
        return result
    def manifest(self):
        entries=[]
        for path,inode in self.paths():
            r=self.inodes[inode]
            entries.append(dict(path=path,inode=inode,type=r['kind'],size=r['size'],crc32=f"{r['crc']:08x}",mode=f"{r['mode']:o}"))
        return dict(inode_count=self.count,path_count=len(entries),types=dict(Counter(x['type'] for x in entries)),entries=entries)
    def extract(self,target):
        target=Path(target);target.mkdir(parents=True,exist_ok=True)
        manifest=self.manifest()
        names=manifest['entries']
        for x in sorted(names,key=lambda x:x['path'].count('/')):
            if x['type']=='directory':(target/x['path']).mkdir(parents=True,exist_ok=True)
        total=0
        for x in names:
            if x['type']!='file':continue
            r=self.inodes[x['inode']]
            total+=r['size']
            if total>4*1024**3:raise FormatError('Extraction safety cap exceeded')
            dest=target/x['path']
            self.fd.seek(r['offset']);remaining=r['size'];crc=0
            with dest.open('wb') as f:
                while remaining:
                    chunk=self.fd.read(min(1024*1024,remaining))
                    if not chunk:raise FormatError('Truncated data')
                    f.write(chunk);crc=zlib.crc32(chunk,crc);remaining-=len(chunk)
            if crc&0xffffffff!=r['crc']:
                dest.unlink(missing_ok=True);raise FormatError('Bad CRC '+x['path'])
            dest.chmod(stat.S_IMODE(r['mode']))
        # Symlinks last so nothing can redirect file writes outside the output folder.
        for x in names:
            if x['type']=='symlink':
                dest=target/x['path'];link=self.data(x['inode'],16384).decode('utf-8','surrogateescape')
                if '\x00' in link:raise FormatError('NUL symlink')
                dest.symlink_to(link)
        (target/'_ATIT_INDEX.json').write_text(json.dumps(manifest,indent=2,ensure_ascii=False),encoding='utf-8')
        return total
    def close(self):self.fd.close()

if __name__=='__main__':
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument('readonly_bin',type=Path)
    g=ap.add_mutually_exclusive_group(required=True)
    g.add_argument('--index',type=Path);g.add_argument('--extract',type=Path)
    args=ap.parse_args()
    reader=Atit(args.readonly_bin)
    try:
        if args.index:
            manifest=reader.manifest()
            args.index.parent.mkdir(parents=True,exist_ok=True)
            args.index.write_text(json.dumps(manifest,indent=2,ensure_ascii=False),encoding='utf-8')
            print(manifest['inode_count'],'inodes;',manifest['path_count'],'paths;',manifest['types'])
        else:
            print('CRC-checked extracted bytes:',reader.extract(args.extract))
    finally:reader.close()
