import subprocess
import tempfile
from pathlib import Path
import unittest
from guest_file_bridge import GuestBridge

class FakeAdb:
    def __init__(self):self.calls=[]
    def __call__(self,args,**kwargs):
        self.calls.append(args)
        op=args[3]
        result=''
        if op=='get-state':result='device\n'
        elif op=='shell':result='package:/data/app/base.apk\npackage:/data/app/split_config.arm64_v8a.apk\n'
        elif op=='pull':Path(args[-1]).write_bytes(b'APK')
        return subprocess.CompletedProcess(args,0,result,'')

class BridgeTests(unittest.TestCase):
    def test_four_separate_guest_serials_and_transfer(self):
        with tempfile.TemporaryDirectory() as td:
            d=Path(td);apk=d/'signed.apk';apk.write_bytes(b'APK')
            fake=FakeAdb()
            bridges=[GuestBridge(f'127.0.0.1:{6300+i}',runner=fake) for i in range(1,5)]
            for b in bridges:
                self.assertEqual('/sdcard/Download/signed.apk',b.host_to_guest(apk))
                b.install_host_apk([apk])
            self.assertEqual(4,len({call[2] for call in fake.calls if call[3]=='push'}))
            exported=bridges[0].export_guest_apk('com.example.app',d/'exported')
            self.assertEqual(2,len(exported))
            self.assertEqual('APK',exported[0].read_text())
            self.assertEqual('APK',(bridges[2].guest_to_host('saved.txt',d/'out')).read_text())
            self.assertTrue(any(call[3]=='install' for call in fake.calls))

    def test_refuse_unsafe_ports_and_paths(self):
        with self.assertRaises(ValueError):GuestBridge('192.168.0.1:6301')
        with self.assertRaises(ValueError):GuestBridge('127.0.0.1:22')
        b=GuestBridge('127.0.0.1:6301',runner=FakeAdb())
        with self.assertRaises(ValueError):b.guest_to_host('../secret','/tmp')
        with self.assertRaises(ValueError):b.export_guest_apk('bad pkg','/tmp/out')

if __name__=='__main__':unittest.main()