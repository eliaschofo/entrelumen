"""Render the voxel dumps of the ruin terrain survey (RuinTerrainSurvey.java) with art/structures/voxrender.py.

    python tools/render_ruin_terrain.py DUMP.after.txt [...] [--scale 2] [--cut] [--out DIR]

A dump holds one block per line, "x y z state", relative to the ruin's centre and ground level; lines
starting with # are its header. Each dump renders to a PNG beside it (or in --out). An after dump whose
before dump sits beside it renders the two side by side, before on the left. --cut keeps the half with
z <= 0, so the blend, the fill and the piers show in section. Preview only, like voxrender itself.
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "art/structures"))


def load(path: Path) -> dict[tuple[int, int, int], str]:
    vox = {}
    with path.open(encoding="utf-8") as f:
        for line in f:
            if not line.strip() or line.startswith("#"):
                continue
            x, y, z, state = line.split(maxsplit=3)
            vox[(int(x), int(y), int(z))] = state.strip()
    return vox


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("dumps", nargs="+", type=Path)
    parser.add_argument("--scale", type=int, default=2)
    parser.add_argument("--cut", action="store_true", help="keep z <= 0: a section through the centre")
    parser.add_argument("--out", type=Path)
    args = parser.parse_args(argv)
    import voxrender
    from PIL import Image

    keep = (lambda x, y, z: z <= 0) if args.cut else None
    for dump in args.dumps:
        suffix = ".cut.png" if args.cut else ".png"
        stem = dump.name[:-len(".txt")] if dump.name.endswith(".txt") else dump.name
        folder = args.out or dump.parent
        folder.mkdir(parents=True, exist_ok=True)
        after = voxrender.render(load(dump), str(folder / (stem + suffix)), scale=args.scale, keep=keep)
        before_dump = dump.with_name(dump.name.replace(".after.", ".before."))
        if ".after." in dump.name and before_dump.exists() and after is not None:
            before = voxrender.render(load(before_dump), str(folder / (before_dump.name[:-4] + suffix)),
                                      scale=args.scale, keep=keep)
            if before is not None:
                pair = Image.new("RGB", (before.width + after.width, max(before.height, after.height)), (255, 255, 255))
                pair.paste(before.convert("RGB"), (0, 0))
                pair.paste(after.convert("RGB"), (before.width, 0))
                pair.save(folder / (stem.replace(".after", "") + ".pair" + suffix))
        print("rendered", dump)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
