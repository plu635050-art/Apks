import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from instance_storage import plan_group


def invoke(*args):
    return subprocess.run([str(x) for x in args],capture_output=True,text=True,check=True)


@unittest.skipUnless(all(shutil.which(x) for x in ('qemu-img','qemu-io','mke2fs','e2fsck')),
                     'Install Linux qemu-utils and e2fsprogs')
class InstanceDiskTests(unittest.TestCase):
    def test_four_independent_disks_and_immutable_base(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td)
            base=root/'base.ext4'
            with base.open('wb') as f:f.truncate(64*1024*1024)
            invoke('mke2fs','-q','-F','-t','ext4','-b','4096',base)
            old=hashlib.sha256(base.read_bytes()).hexdigest()
            workspace=root/'instances'
            result=plan_group(base,workspace,count=4,data_mib=64,prefix='android7')
            self.assertEqual(4,result['instance_count'])
            self.assertFalse(result['four_simultaneous_boots_verified'])
            # Modify first system copy-on-write sector; the second must remain pristine.
            first=workspace/'android7-01'/'system.qcow2'
            second=workspace/'android7-02'/'system.qcow2'
            invoke('qemu-io','-f','qcow2','-c','write -P 0x47 0 512',first)
            invoke('qemu-io','-f','qcow2','-c','read -P 0x47 0 512',first)
            invoke('qemu-io','-f','qcow2','-c','read -P 0x00 0 512',second)
            self.assertEqual(old,hashlib.sha256(base.read_bytes()).hexdigest())
            # Userdata disks must be physically separate guest ext4 filesystems.
            data1=workspace/'android7-01'/'userdata.ext4'
            data2=workspace/'android7-02'/'userdata.ext4'
            self.assertNotEqual(data1, data2)
            invoke('debugfs','-w','-R','mkdir /only_one',data1)
            info1=invoke('debugfs','-R','stat /only_one',data1).stdout
            probe=invoke('debugfs','-R','stat /only_one',data2)
            info2=probe.stdout+probe.stderr
            self.assertIn('Inode:',info1)
            self.assertIn('File not found',info2)
            self.assertEqual(4,len(json.loads((workspace/'instances.json').read_text())['instances']))


    def test_two_android7_and_two_android10_storage_groups(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td)
            bases=[]
            for guest in ('android7','android10'):
                b=root/(guest+'.ext4')
                with b.open('wb') as f:f.truncate(32*1024*1024)
                invoke('mke2fs','-q','-F','-t','ext4',b)
                bases.append(b)
            g7=plan_group(bases[0],root/'group7',count=2,data_mib=64,prefix='android7',port_base=6300)
            g10=plan_group(bases[1],root/'group10',count=2,data_mib=64,prefix='android10',port_base=6400)
            ports=[x['adb_port_proposed'] for x in g7['instances']+g10['instances']]
            self.assertEqual(4,len(ports))
            self.assertEqual(4,len(set(ports)))
            self.assertEqual({'android7-01','android7-02','android10-01','android10-02'},
                             {x['id'] for x in g7['instances']+g10['instances']})
            self.assertFalse(g7['guest_boot_verified'])
            self.assertFalse(g10['guest_boot_verified'])

    def test_refuse_invalid_original_and_existing_workspace(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td)
            base=root/'fake.raw';base.write_bytes(b'\0'*8192)
            with self.assertRaises(ValueError):plan_group(base,root/'vms')
            self.assertFalse((root/'vms').exists())

if __name__=='__main__':unittest.main()