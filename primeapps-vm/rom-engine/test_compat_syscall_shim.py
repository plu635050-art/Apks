import tempfile
from pathlib import Path
import unittest
from compat_syscall_shim import convert, SIGNATURE, PATCH

class CompatTests(unittest.TestCase):
    def test_one_thunk_away_from_original(self):
        # Minimal synthetic 64-bit ARM64 ELF header plus the exact trampoline prologue.
        elf=bytearray(64)
        elf[0:5]=b'\x7fELF\x02';elf[18:20]=b'\xb7\x00'
        pre=bytes.fromhex('e8831ff808000010e8031ff8')
        with tempfile.TemporaryDirectory() as d:
            src=Path(d)/'orig';dst=Path(d)/'patched'
            src.write_bytes(bytes(elf)+pre+SIGNATURE)
            v=convert(src,dst)
            self.assertEqual(v['replaced_thunks'],1)
            self.assertTrue(dst.read_bytes().endswith(PATCH))
            self.assertTrue(src.read_bytes().endswith(SIGNATURE))
    def test_reject_in_place(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'same'
            p.write_bytes(b'junk')
            with self.assertRaises(ValueError):convert(p,p)
    def test_reject_unexpected_prologue(self):
        with tempfile.TemporaryDirectory() as d:
            src=Path(d)/'bad';dst=Path(d)/'out'
            elf=bytearray(64);elf[0:5]=b'\x7fELF\x02';elf[18:20]=b'\xb7\x00'
            src.write_bytes(bytes(elf)+bytes(12)+SIGNATURE)
            with self.assertRaises(ValueError):convert(src,dst)

if __name__=='__main__':unittest.main()