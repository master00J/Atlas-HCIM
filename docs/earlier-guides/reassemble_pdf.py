#!/usr/bin/env python3
"""Reassemble the large Atlas-HCIM PDF uploaded in parts due to a GitHub API limit."""
from pathlib import Path
import hashlib

HERE = Path(__file__).resolve().parent
NAME = 'OSRS_HCIM_Bank_per_Bank_Detailroute_2026.pdf'
SHA256 = '14f0255c40b0cc1099460edbe2c7527bd0016005599e73eced416f32bf0337d9'
PARTS = [HERE / 'pdf-parts' / f'detailroute.part{i:02}' for i in range(1, 5)]


def main():
    missing = [str(p) for p in PARTS if not p.is_file()]
    if missing:
        raise SystemExit('Missing PDF parts:\n' + '\n'.join(missing))
    dst = HERE / NAME
    temp = dst.with_suffix('.pdf.tmp')
    hasher = hashlib.sha256()
    with temp.open('wb') as out:
        for part in PARTS:
            with part.open('rb') as source:
                while True:
                    chunk = source.read(1024 * 1024)
                    if not chunk:
                        break
                    out.write(chunk)
                    hasher.update(chunk)
    if hasher.hexdigest() != SHA256:
        temp.unlink(missing_ok=True)
        raise SystemExit('SHA-256 mismatch; PDF parts are incomplete or corrupted')
    temp.replace(dst)
    print('Recovered', dst.name, 'and verified SHA-256')


if __name__ == '__main__':
    main()
