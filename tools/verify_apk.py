#!/usr/bin/env python3
"""Check the built module entry and DEX definitions, not just DEX strings/references."""
import struct
import sys
import zipfile
from pathlib import Path


def u32(data, offset):
    return struct.unpack_from('<I', data, offset)[0]


def descriptors(data):
    if not data.startswith(b'dex\n'):
        raise ValueError('Expected a standard DEX file')
    strings_size, strings_off = struct.unpack_from('<II', data, 0x38)
    strings = []
    for i in range(strings_size):
        pos = u32(data, strings_off + i * 4)
        while data[pos] & 0x80:  # skip ULEB128 UTF-16 length
            pos += 1
        pos += 1
        strings.append(data[pos:data.index(b'\0', pos)].decode('utf-8', errors='replace'))
    types_size, types_off = struct.unpack_from('<II', data, 0x40)
    types = [strings[u32(data, types_off + i * 4)] for i in range(types_size)]
    classes_size, classes_off = struct.unpack_from('<II', data, 0x60)
    return {types[u32(data, classes_off + i * 32)] for i in range(classes_size)}


def main(path):
    with zipfile.ZipFile(path) as apk:
        entry = apk.read('assets/xposed_init').decode().strip()
        assert entry == 'com.fold8.secondsclock.SecondsHook', entry
        assert 'AndroidManifest.xml' in apk.namelist()
        classes = set()
        for name in apk.namelist():
            if name.startswith('classes') and name.endswith('.dex'):
                classes.update(descriptors(apk.read(name)))
        assert 'Lcom/fold8/secondsclock/SecondsHook;' in classes
        assert 'Lcom/fold8/secondsclock/ClockText;' in classes
        forbidden = sorted(c for c in classes if c.startswith('Lde/robv/android/xposed/'))
        assert not forbidden, f'Xposed stubs accidentally bundled: {forbidden}'
    print(f'PASS: {Path(path).name}: Xposed entry found; {len(classes)} class definitions; no bundled Xposed API stubs')


if __name__ == '__main__':
    main(sys.argv[1])
