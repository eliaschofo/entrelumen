"""Superseded by tools/check_keybinds.py; kept so older receipts and commands still run. Read only.

    python tools/audit_keybindings.py OPTIONS [--simulate-preset] [--check-preset]

--simulate-preset applies the shipped preset in memory before checking; --check-preset also fails when the
file does not hold every preset value yet.
"""
import argparse
from pathlib import Path
import subprocess
import sys

CHECK = Path(__file__).resolve().parent / 'check_keybinds.py'


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('options', type=Path)
    ap.add_argument('--simulate-preset', action='store_true')
    ap.add_argument('--check-preset', action='store_true')
    args = ap.parse_args()
    base = [sys.executable, str(CHECK), '--options', str(args.options)]
    code = subprocess.call(base + (['--simulate-preset'] if args.simulate_preset else []))
    if args.check_preset:
        out = subprocess.run(base + ['--diff'], capture_output=True, text=True).stdout
        print(out, end='')
        code = code or int(not out.strip().splitlines()[-1].startswith('0 of'))
    return code


if __name__ == '__main__':
    raise SystemExit(main())
