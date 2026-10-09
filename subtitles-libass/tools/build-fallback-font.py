"""Merge a subtitle fallback font with Noto Arabic, Hebrew and Thai, then report cmap coverage.

usage: python merge.py BASE.ttf OUT.ttf
"""
import os
import sys

from fontTools.merge import Merger
from fontTools.ttLib import TTFont
from fontTools.ttLib.scaleUpem import scale_upem

HERE = os.path.dirname(os.path.abspath(__file__))
ADDS = ['NotoSansArabic-Regular.ttf', 'NotoSansHebrew-Regular.ttf', 'NotoSansThai-Regular.ttf']
RANGES = {
    'Latin': (0x20, 0x7E), 'Cyrillic': (0x400, 0x4FF), 'Hebrew': (0x5D0, 0x5EA),
    'Arabic': (0x621, 0x64A), 'Thai': (0xE01, 0xE3A), 'Devanagari': (0x905, 0x939),
}


def coverage(path):
    cmap = TTFont(path).getBestCmap()
    return {name: f'{sum(c in cmap for c in range(lo, hi + 1))}/{hi - lo + 1}' for name, (lo, hi) in RANGES.items()}


def main(base, out):
    upm = TTFont(base)['head'].unitsPerEm
    fonts = [base]
    for name in ADDS:
        add = TTFont(os.path.join(HERE, name))
        if add['head'].unitsPerEm != upm:
            scale_upem(add, upm)
            scaled = os.path.join(HERE, f'{upm}-{name}')
            add.save(scaled)
            fonts.append(scaled)
        else:
            fonts.append(os.path.join(HERE, name))
    merged = Merger().merge(fonts)
    merged.save(out)
    print('before', coverage(base))
    print('after ', coverage(out), os.path.getsize(out), 'bytes')


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
