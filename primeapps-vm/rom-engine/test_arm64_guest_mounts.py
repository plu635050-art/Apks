#!/usr/bin/env python3
"""Real QEMU ARM64 guest + EXT4 + MS_MOVE smoke test, no proprietary ROMs.
Requires Linux clang, lld, cpio, gzip, mke2fs, qemu-system-aarch64.
The user-owned Android10 system partition was separately tested in private.
"""
import argparse
import hashlib
from pathlib import Path
import subprocess
import tempfile

HERE=Path(__file__).resolve().parent
NEED=("mount_proc result=0", "mount_sys result=0", "mount_dev result=0",
      "mount_real_system_ext4 result=0", "MOVE_DEV_TO_SYSTEM_DEV result=0",
      "MOVE_DEV_BACK result=0", "PRIMEVM18_END")


def run(cmd,**opts):
    return subprocess.run(cmd,check=True,**opts)


def test(kernel:Path, output:Path, timeout:int=30)->dict:
    if not kernel.is_file() or kernel.stat().st_size<1_000_000:
        raise ValueError("Provide a real AOSP ARM64 QEMU kernel")
    with tempfile.TemporaryDirectory(prefix="primevm18-") as tmp:
        w=Path(tmp)
        elf=w/"init"
        run(["clang","--target=aarch64-linux-gnu","-fuse-ld=lld","-fno-builtin",
             "-fno-stack-protector","-nostdlib","-static","-Os","-Wl,-e,_start",
             str(HERE/"arm64_mount_move_probe.c"),"-o",str(elf)])
        guest=w/"root"; guest.mkdir()
        for path in ("dev","proc","sys","system"):
            (guest/path).mkdir()
        (guest/"init").write_bytes(elf.read_bytes())
        (guest/"init").chmod(0o755)
        archive=w/"initramfs.cpio.gz"
        with (w/"initramfs.cpio").open("wb") as out:
            files=[".","dev","proc","sys","system","init"]
            run(["cpio","-o","-H","newc"],input=("\n".join(files)+"\n").encode(),cwd=guest,stdout=out,stderr=subprocess.DEVNULL)
        with archive.open("wb") as out:
            run(["gzip","-c","-1",str(w/"initramfs.cpio")],stdout=out)
        source=w/"system"; (source/"dev").mkdir(parents=True)
        (source/"example.txt").write_text("PrimeApps VM QEMU guest EXT4 test\n")
        image=w/"system.ext4"
        with image.open("wb") as f:f.truncate(64*1024*1024)
        run(["mke2fs","-q","-F","-t","ext4","-d",str(source),str(image)])
        initial_sha=hashlib.sha256(image.read_bytes()).hexdigest()
        output.parent.mkdir(parents=True,exist_ok=True)
        cmd=["qemu-system-aarch64","-machine","virt","-cpu","cortex-a57",
             "-smp","2","-m","1024","-nographic","-monitor","none",
             "-serial",f"file:{output}","-nic","none","-no-reboot",
             "-kernel",str(kernel),"-initrd",str(archive),
             "-append","console=ttyAMA0 loglevel=4 androidboot.hardware=titan",
             "-drive",f"if=none,id=d0,file={image},format=raw,readonly=on",
             "-device","virtio-blk-device,drive=d0"]
        try:
            r=subprocess.run(cmd,timeout=timeout,check=False,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE)
            if r.returncode: print("QEMU returned",r.returncode, r.stderr.decode(errors="replace")[-1000:])
        except subprocess.TimeoutExpired:
            pass
        serial=output.read_bytes().replace(b'\x00',b'').decode("utf-8",errors="replace")
        if not all(marker in serial for marker in NEED):
            raise AssertionError("QEMU guest did not pass mount sequence: "+str([m for m in NEED if m not in serial])+"\n"+serial[-2500:])
        end_sha=hashlib.sha256(image.read_bytes()).hexdigest()
        if end_sha!=initial_sha:
            raise AssertionError("Readonly guest filesystem changed")
        print("PASS: ARM64 guest mounted EXT4 and moved /dev to /system/dev and back; base SHA unchanged")
        return {"status":"PASS","markers":list(NEED),"read_only_base_unchanged":True}

if __name__=="__main__":
    a=argparse.ArgumentParser()
    a.add_argument("kernel",type=Path)
    a.add_argument("--serial-log",type=Path,default=Path("stage18-serial.log"))
    args=a.parse_args()
    print(test(args.kernel,args.serial_log))