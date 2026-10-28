# PrimeApps VM - Real ROM filesystem converter

Status: real filesystem extraction. THIS IS NOT A FUNCTIONING VM APK OR A BOOTABLE DISK.

The user-owned ROMs were examined locally; no proprietary ROMs or native SO files are published.

## Real results, each with CRC32 verified

| Guest | Partition | Inodes | Files | Directories | Symlinks |
|---|---|---:|---:|---:|---:|
| Android 7.1.2 | root | 43 | 21 | 14 | 8 |
| Android 7.1.2 | system | 1666 | 1309 | 183 | 174 |
| Android 10.0 | root | 10 | 2 | 8 | 0 |
| Android 10.0 | system | 4509 | 3757 | 451 | 301 |
| Android 10.0 | vendor | 503 | 300 | 29 | 174 |

Recovered real binaries: Android 7 root/init (ELF ARM64, Android 25), Android 10 root/init (ELF ARM64, Android 29), plus system ARM and ARM64 runtime.

ATIT format: 64 byte header, 52 byte inode table records, recoverable path graph, file offset, length and CRC32.

## Use on Linux after extracting partition readonly.bin from your own ROM archive

    python3 atitreconstruction.py system/readonly.bin --index system-index.json
    python3 atitreconstruction.py system/readonly.bin --extract extracted-system/
    python3 -m unittest discover -s . -p 'test_*.py' -v

No pip dependencies. Tested with readonly.bin partition files from original user supplied archives.

LIMIT: the ROMs also have writable overlays, xattrs and custom metadata files that this converter does not merge. Extracted rootfs files do NOT mean QEMU can boot them. A compatible kernel, init configuration, guest drivers and actual VM engine are still needed.

## What remains before an APK is called functional

1. Merge per-inode writable overlays and restore user/group, SELinux and mount layout.
2. Choose and test a compatible ARM64 guest kernel plus host VM engine (QubeVM/QEMU or a licensed equivalent).
3. Boot the actual Android guest on an Android 15/16 physical device.
4. Prove root inside guest and two independent simultaneous guest instances.
5. Only then distribute a complete APK through GitHub Actions.

Hardware acceleration is not guaranteed; RAM alone does not supply /dev/kvm or /dev/gzvm.

References:
- https://github.com/rhn-1k/QubeVM
- https://github.com/Droid-VM/DroidVM
- https://developer.android.com/guide/practices/page-sizes
