"""Read-only, scoped ENTRELUMEN keybinding audit. No profile writes."""
import argparse
import json
from pathlib import Path

# Exact options.txt names, preserving the key_ prefix.
PROPOSED = {
    "key_key.ftbquests.quests": "key.keyboard.f8",
    "key_key.ftbultimine": "key.keyboard.v",
    "key_key.ars_nouveau.selection_hud": "key.keyboard.f6",
    "key_key.sophisticatedbackpacks.open_backpack": "key.keyboard.b",
    "key_key.journeymap.create_waypoint": "key.keyboard.m",
    "key_key.journeymap.map_toggle_alt": "key.keyboard.j",
    "key_key.journeymap.fullscreen_create_waypoint": "key.keyboard.b",
    "key_key.mekanism.head_mode": "key.keyboard.up:ALT",
    "key_key.mekanism.chest_mode": "key.keyboard.left:ALT",
    "key_key.mekanism.legs_mode": "key.keyboard.down:ALT",
    "key_key.mekanism.feet_mode": "key.keyboard.right:ALT",
    "key_key.toolbelt.open": "key.keyboard.r",
    "key_key.immersive_aircraft.dismount": "key.keyboard.f12",
    "key_key.toolbelt.slot": "key.keyboard.r:SHIFT",
    "key_key.aether.invisibility_toggle.desc": "key.keyboard.home",
    "key_key.twilightforest.swap_hotbar": "key.keyboard.semicolon",
    "key_supplementaries.keybind.quiver": "key.keyboard.apostrophe",
    "key_key.occultism.ender_bag": "key.keyboard.o",
    "key_key.occultism.backpack": "key.keyboard.insert",
    "key_key.toastcontrol.clear": "key.keyboard.f10",
    "key_key.buildinggadgets2.range": "key.keyboard.end",
    # Industrial family (bytecode defaults V/H/V/K/P/C/U collided in the world context).
    "key_keybind.ironjetpacks.engine": "key.keyboard.f4",
    "key_keybind.ironjetpacks.hover": "key.keyboard.h:ALT",
    "key_justdirethings.key.toggle_tool": "key.keyboard.t:ALT",
    "key_justdirethings.key.toolUI": "key.keyboard.t:SHIFT",
    "key_key.draconicevolution.place_item": "key.keyboard.p:ALT",
    "key_key.draconicevolution.tool_config": "key.keyboard.y:ALT",
    "key_key.hostilenetworks.open_deep_learner": "key.keyboard.u:ALT",
    # QoL family (bytecode defaults V/H collided with Ultimine and the H group).
    "key_key.easy_villagers.pick_up": "key.keyboard.v:SHIFT",
    "key_simplemagnets.keys.toggle": "key.keyboard.m:SHIFT",
}
# Operational context, NOT an assertion of a mod's declared conflict context.
GUI = {"key_key.jei.showRecipe", "key_key.jei.showRecipe2", "key_key.jei.showUses",
       "key_key.invtweaks_sort_inventory.desc", "key_key.craftingtweaks.compress_stack"}
MAP = {"key_key.journeymap.fullscreen_create_waypoint"}
WORLD = set(PROPOSED) - MAP
WORLD.update({"key_key.mekanism.mode", "key_key.mekanism.module_tweaker",
              "key_key.journeymap.minimap_preset", "key_key.buildinggadgets2.settings_menu"})
RAW = {"key_key.ars_nouveau.selection_hud", "key_key.ars_nouveau.next_slot",
       "key_key.ars_nouveau.previous_slot", "key_key.ars_nouveau.open_book"}

def parse(text):
    out = {}
    for line in text.splitlines():
        if line.startswith("key_") and ":" in line:
            name, value = line.split(":", 1)
            if name in out:
                raise ValueError("Duplicate binding: " + name)
            out[name] = value
    return out

def context(name):
    if name in GUI or name.startswith("key_key.jei."):
        return "gui"
    if name in MAP or name.startswith("key_key.journeymap.fullscreen."):
        return "map"
    if name in WORLD:
        return "world"
    return "unknown"

def audit(bindings):
    findings = []
    for left in sorted(bindings):
        for right in sorted(bindings):
            if left >= right or not ({left, right} & set(PROPOSED)):
                continue
            a, b = bindings[left], bindings[right]
            if "unknown" in (a, b) or a == "key.keyboard.unknown" or b == "key.keyboard.unknown":
                continue
            same = a == b
            raw_overlap = bool({left, right} & RAW) and a.split(":")[0] == b.split(":")[0]
            modifier_overlap = a.split(":")[0] == b.split(":")[0] and (":" in a) != (":" in b)
            if not (same or raw_overlap or modifier_overlap):
                continue
            ca, cb = context(left), context(right)
            exclusive = {ca, cb} in ({"world", "gui"}, {"world", "map"})
            severity = "context-separated" if exclusive else ("world-overlap" if ca == cb == "world" and (same or raw_overlap) else "review-context")
            findings.append({"keys": [left, right], "binding": [a, b], "classification": severity,
                             "rawCodeRisk": raw_overlap and not same})
    return findings

def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("options", type=Path)
    ap.add_argument("--simulate-preset", action="store_true", help="Apply proposed values in memory only")
    ap.add_argument("--check-preset", action="store_true", help="Exit 1 if proposed values are not installed")
    args = ap.parse_args()
    data = parse(args.options.read_text(encoding="utf-8"))
    missing = sorted(set(PROPOSED) - set(data))
    mismatch = {k: {"actual": data.get(k), "proposed": v} for k, v in PROPOSED.items() if data.get(k) != v}
    if args.simulate_preset:
        data.update({k: v for k, v in PROPOSED.items() if k in data})
    findings = audit(data)
    print(json.dumps({"scope": "QoL target keys only; contexts outside evidence remain review-context",
                      "simulated": args.simulate_preset, "missing": missing,
                      "presetDifferences": mismatch, "findings": findings}, indent=2))
    return int(bool(missing) or (args.check_preset and bool(mismatch)) or
               any(f["classification"] == "world-overlap" for f in findings))

if __name__ == "__main__":
    raise SystemExit(main())
