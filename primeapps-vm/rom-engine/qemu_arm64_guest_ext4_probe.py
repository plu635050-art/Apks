#!/usr/bin/env python3
"""Real QEMU ARM64 TCG test: mount and read a generated EXT4 inside guest Linux.

This is a Linux guest storage-protocol smoke test, not proof of booting user Android ROMs
or of running on an Android 16 physical phone. Requires Alpine arm64 kernel/initramfs.
"""
from __future__ import annotations
import argparse
from pathlib import Path
import re
import subprocess
import tempfile
import threading
import time

from build_ext4_from_atit import make_image
from test_atitreconstruction import make_atit

BOOT_PROMPTS = (b'Launching initramfs emergency recovery shell.', b'~ #')


def smoke(kernel: Path, initrd: Path, serial_output: Path, *, timeout_seconds: int = 140) -> None:
    for path in (kernel, initrd):
        if not path.is_file() or path.stat().st_size < 1024 * 1024:
            raise FileNotFoundError('Missing real ARM64 boot asset: ' + str(path))
    serial_output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='prime-vm-guest-disk-') as td:
        td = Path(td)
        image = td / 'test-system.ext4'
        atit = make_atit(td)
        make_image(atit, image, label='primevm-test')
        command = [
            'qemu-system-aarch64', '-machine', 'virt', '-cpu', 'cortex-a72',
            '-smp', '2', '-m', '1024', '-accel', 'tcg,thread=multi',
            '-nographic', '-monitor', 'none', '-serial', 'stdio', '-no-reboot',
            '-kernel', str(kernel), '-initrd', str(initrd),
            '-append', 'console=ttyAMA0 loglevel=6 modules=virtio,virtio_pci,virtio_blk,ext4',
            '-drive', f'file={image},if=none,format=raw,readonly=on,id=primevm',
            '-device', 'virtio-blk-device,drive=primevm',
        ]
        proc = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                stderr=subprocess.STDOUT, bufsize=0)
        output = bytearray()
        def drain():
            with serial_output.open('wb') as log:
                while True:
                    chunk = proc.stdout.read(16384)
                    if not chunk:
                        break
                    output.extend(chunk)
                    log.write(chunk)
                    log.flush()
        reader = threading.Thread(target=drain, daemon=True)
        reader.start()
        started = time.monotonic()
        sent = False
        try:
            while time.monotonic() - started < timeout_seconds:
                snapshot = bytes(output)
                if not sent and any(x in snapshot for x in BOOT_PROMPTS):
                    # Give the initramfs recovery shell time to start reading input.
                    time.sleep(2)
                    proc.stdin.write(b'\nmodprobe virtio_blk 2>/dev/null\n')
                    proc.stdin.write(b'modprobe ext4\nmdev -s\nmkdir -p /mnt/primevm\n')
                    proc.stdin.write(b'mount -t ext4 -o ro /dev/vda /mnt/primevm && echo PRIMEVM_EXT4_MOUNT_OK\n')
                    proc.stdin.write(b'cat /mnt/primevm/hello.txt\n')
                    proc.stdin.write(b'ls -ln /mnt/primevm/hello.txt\n')
                    proc.stdin.write(b'echo PRIMEVM_GUEST_COMMANDS_DONE\n')
                    proc.stdin.flush()
                    sent = True
                if (sent and re.search(rb'(?m)^PRIMEVM_EXT4_MOUNT_OK\r?$', snapshot)
                        and re.search(rb'(?m)^Hi VM!\r?$', snapshot)):
                    print('PASS: Linux ARM64 guest mounted EXT4 produced by ATIT converter and read its file')
                    print('Guest proved file content: Hi VM!')
                    return
                if proc.poll() is not None:
                    break
                time.sleep(0.25)
            raise RuntimeError('Real guest failed to mount/read EXT4 within timeout. Last output:\n'
                               + bytes(output)[-7000:].decode('utf-8', 'replace'))
        finally:
            if proc.poll() is None:
                proc.terminate()
                try:
                    proc.wait(timeout=8)
                except subprocess.TimeoutExpired:
                    proc.kill()
                    proc.wait(timeout=5)
            reader.join(timeout=5)


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('kernel', type=Path)
    p.add_argument('initramfs', type=Path)
    p.add_argument('--serial-log', type=Path, default=Path('primevm-arm64-guest-ext4.log'))
    args = p.parse_args()
    smoke(args.kernel, args.initramfs, args.serial_log)