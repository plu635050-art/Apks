import tempfile
from pathlib import Path
import struct
import sys
import unittest
import zlib
sys.path.insert(0,str(Path(__file__).parent))
from atitreconstruction import Atit,FormatError

def make_atit(where):
    # Three synthetic nodes: a directory, a file, and a relative symlink.
    directory=struct.pack('<I',10)+b'hello.txt\0'+struct.pack('<Q',1)+struct.pack('<I',6)+b'alias\0'+struct.pack('<Q',2)
    payload=[directory,b'Hi VM!\n',b'hello.txt']
    offset=64+52*3
    records=[]
    for mode,data in zip([0o40755,0o100644,0o120777],payload):
        records.append(struct.pack('<13I',mode,0,0,1,0,0,0,0,0,len(data),offset,zlib.crc32(data)&0xffffffff,0))
        offset+=len(data)
    blob=b'ATIT'+struct.pack('<I',64)+struct.pack('<I',3)+bytes(52)+b''.join(records)+b''.join(payload)
    path=Path(where)/'sample.bin';path.write_bytes(blob)
    return path

class AtitTests(unittest.TestCase):
    def test_reconstruct_and_verify(self):
        with tempfile.TemporaryDirectory() as d:
            reader=Atit(make_atit(d))
            try:
                manifest=reader.manifest()
                self.assertEqual(manifest['inode_count'],3)
                self.assertEqual({x['path'] for x in manifest['entries']},{'','hello.txt','alias'})
                reader.extract(Path(d)/'output')
            finally:reader.close()
            self.assertEqual((Path(d)/'output'/'hello.txt').read_bytes(),b'Hi VM!\n')
            self.assertEqual((Path(d)/'output'/'alias').readlink(),Path('hello.txt'))
    def test_crc_error(self):
        with tempfile.TemporaryDirectory() as d:
            p=make_atit(d)
            data=bytearray(p.read_bytes());data[-11]=0x42;p.write_bytes(data)
            reader=Atit(p)
            try:
                with self.assertRaises(FormatError):reader.extract(Path(d)/'output')
            finally:reader.close()
if __name__=='__main__':unittest.main()
