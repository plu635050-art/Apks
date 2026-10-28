import re
import shutil
import struct
import subprocess
import tempfile
from pathlib import Path
import unittest
from test_atitreconstruction import make_atit
from build_ext4_from_atit import make_image


@unittest.skipUnless(all(shutil.which(x) for x in ('mke2fs','debugfs','e2fsck')),
                     'Install Linux e2fsprogs tools')
class BuildExt4Tests(unittest.TestCase):
    def test_real_ext4_inode_contents_and_ownership(self):
        with tempfile.TemporaryDirectory() as d:
            p = make_atit(d)
            b = bytearray(p.read_bytes())
            # Inode 1 (hello.txt), starting at byte 64 + 52.
            struct.pack_into('<I', b, 64 + 52 + 4, 1000)
            struct.pack_into('<I', b, 64 + 52 + 8, 1001)
            p.write_bytes(b)
            out = Path(d)/'system.ext4'
            result = make_image(p,out,label='system')
            self.assertTrue(out.is_file())
            self.assertFalse(result['bootable'])
            self.assertEqual(result['file_count'],1)
            s = subprocess.run(['debugfs','-R','stat /hello.txt',str(out)],text=True,
                               capture_output=True,check=True).stdout
            self.assertRegex(s, r'User:\s+1000')
            self.assertRegex(s, r'Group:\s+1001')
            data = Path(d)/'restored.txt'
            r = subprocess.run(['debugfs','-R',f'dump /hello.txt {data}',str(out)],
                               text=True,capture_output=True,check=True)
            self.assertEqual(data.read_bytes(),b'Hi VM!\n')
            s = subprocess.run(['debugfs','-R','stat /alias',str(out)],text=True,
                               capture_output=True,check=True).stdout
            self.assertIn('hello.txt',s)

    def test_refuse_undersized_image(self):
        with tempfile.TemporaryDirectory() as d:
            p=make_atit(d)
            with self.assertRaises(ValueError):
                make_image(p,Path(d)/'undersized.ext4',size_mib=1)


if __name__=='__main__':unittest.main()