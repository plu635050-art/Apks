"""A real EXT4 extended-attribute round trip using libselinux and debugfs."""
import struct
import zlib
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

def make_atit(where):
    directory=(struct.pack('<I',10)+b'hello.txt\0'+struct.pack('<Q',1)
               +struct.pack('<I',6)+b'alias\0'+struct.pack('<Q',2))
    payload=[directory,b'Hi VM!\n',b'hello.txt']
    offset=64+52*3
    records=[]
    for mode,data in zip((0o40755,0o100644,0o120777),payload):
        records.append(struct.pack('<13I',mode,0,0,1,0,0,0,0,0,
                      len(data),offset,zlib.crc32(data)&0xffffffff,0))
        offset+=len(data)
    path=Path(where)/'sample.bin'
    path.write_bytes(b'ATIT'+struct.pack('<I',64)+struct.pack('<I',3)+bytes(52)
                     +b''.join(records)+b''.join(payload))
    return path

from selinux_relabel_ext4 import relabel,plan_labels


@unittest.skipUnless(all(shutil.which(t) for t in ('mke2fs','debugfs','e2fsck')),
    'Linux e2fsprogs required for EXT4 proof')
class SelinuxRelabelTests(unittest.TestCase):
    def test_guest_selinux_xattrs_on_real_ext4_and_original_unchanged(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)
            atit=make_atit(p)
            tree=p/'tree';tree.mkdir()
            (tree/'hello.txt').write_bytes(b'Hi VM!\n')
            (tree/'alias').symlink_to('hello.txt')
            original=p/'original.ext4'
            subprocess.run(['mke2fs','-q','-F','-t','ext4','-I','256',
                            '-d',str(tree),str(original),'96M'],check=True)
            ctx=p/'file_contexts'
            ctx.write_text('/.* u:object_r:primevm_generic_file:s0\n'
                           '/system/hello\\.txt u:object_r:primevm_hello_file:s0\n')
            out=p/'labeled.ext4'
            info=relabel(original,out,atit,ctx,'/system')
            self.assertEqual(info['labeled'],3)
            self.assertEqual(info['unmatched_count'],0)
            self.assertTrue(out.exists())
            self.assertEqual(len(info['not_validated']),4)
            f=subprocess.run(['debugfs','-R','ea_get /hello.txt security.selinux',str(out)],capture_output=True,text=True,check=True)
            self.assertIn('primevm_hello_file',f.stdout)
            untouched=subprocess.run(['debugfs','-R','ea_list /hello.txt',str(original)],capture_output=True,text=True,check=True)
            self.assertNotIn('security.selinux',untouched.stdout)
            self.assertTrue(Path(str(out)+'.labels.json').exists())

    def test_reject_reusing_source_or_existing_output(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d);atit=make_atit(p)
            ctx=p/'file_contexts';ctx.write_text('/.* u:object_r:system_file:s0\n')
            image=p/'disk.ext4';image.write_bytes(b'0'*1048576)
            with self.assertRaises(ValueError):relabel(image,image,atit,ctx)
            with self.assertRaises(FileExistsError):relabel(image,p/'file_contexts',atit,ctx)

if __name__=='__main__':unittest.main()