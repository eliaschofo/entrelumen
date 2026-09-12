"""Apply the first-launch preset to the ENTRELUMEN profile created by CurseForge.

Never launches Minecraft, changes account metadata, or resets an existing player's
settings. The app-generated manifest remains the app's responsibility.
"""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("destination", type=Path)
    parser.add_argument("--language", choices=["en_us", "es_es"], default="en_us")
    args = parser.parse_args()
    destination = args.destination.resolve()
    profile = json.loads((destination / "minecraftinstance.json").read_text(encoding="utf-8-sig"))
    if (profile.get("name") != "ENTRELUMEN" or profile.get("gameVersion") != "1.21.1"
            or profile.get("baseModLoader", {}).get("name") != "neoforge-21.1.249"):
        raise SystemExit("This is not the expected app-created ENTRELUMEN profile")
    marker = destination / ".entrelumen-test-client.json"
    if marker.exists() or (destination / "logs/latest.log").exists() or (destination / "saves").exists():
        raise SystemExit("Initial setup was already applied or the profile has been played; preserving settings")
    preset = json.loads((ROOT / "pack/config/entrelumen/client-preset.json").read_text())
    options = destination / "options.txt"
    original = options.read_text(encoding="utf-8") if options.exists() else ""
    backup = destination / "options.before-entrelumen.txt"
    if backup.exists():
        raise SystemExit("Initial options backup already exists; inspect before retrying")
    values = dict(line.split(":", 1) for line in original.splitlines() if ":" in line)
    values.update(preset["options"])
    values["lang"] = args.language
    backup.write_text(original, encoding="utf-8")
    options.write_text("".join(f"{key}:{value}\n" for key, value in values.items()), encoding="utf-8")
    marker.write_text(json.dumps({"owner": "entrelumen", "minecraft": "1.21.1", "neoforge": "21.1.249",
                                  "initialPreset": 1, "language": args.language}, indent=2) + "\n")
    print("Initial 10/6-chunk preset, original resource pack and documented bindings installed; no client launched")


if __name__ == "__main__":
    main()
