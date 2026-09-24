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
    # Arcane family (Iron's spell wheel R and cast V collided with Tool Belt and Ultimine).
    "key_key.irons_spellbooks.spell_wheel": "key.keyboard.g:SHIFT",
    "key_key.irons_spellbooks.spellbook_cast": "key.keyboard.q:ALT",
    # Exploration family (Deeper and Darker B/V and Cataclysm V/C/Y/V collided with world keys).
    "key_key.deeperdarker.boost": "key.keyboard.b:ALT",
    "key_key.deeperdarker.transmit": "key.keyboard.n:ALT",
    "key_key.cataclysm.ability": "key.keyboard.j:ALT",
    "key_key.cataclysm.helmet_ability": "key.keyboard.k:ALT",
    "key_key.cataclysm.chestplate_ability": "key.keyboard.l:ALT",
    "key_key.cataclysm.boots_ability": "key.keyboard.i:ALT",
    # Apotheosis family: native defaults kept and pinned (Ctrl+T World Tier screen, Ctrl+O radial
    # mining toggle, Shift+T item link inside inventories); no world-context collision.
    "key_key.apotheosis.open_world_tier_select": "key.keyboard.t:CONTROL",
    "key_key.apotheosis.toggle_radial_mining": "key.keyboard.o:CONTROL",
    "key_key.apotheosis.link_item_to_chat": "key.keyboard.t:SHIFT",
    # Mod ping-pong rounds 1-3 (bytecode defaults V/R/M/G/H/Y/B/C/Z, the period and the up/down arrows
    # collided with Ultimine, Tool Belt, JourneyMap, the backpack, vanilla hotbar keys and each other).
    "key_key.modern_industrialization.toggle_flight": "key.keyboard.f4:SHIFT",
    "key_key.modern_industrialization.toggle_3x3": "key.keyboard.v:ALT",
    "key_key.ad_astra.toggle_suit_flight": "key.keyboard.f4:CONTROL",
    "key_key.ad_astra.open_radio": "key.keyboard.r:ALT",
    "key_key.deep_aether.stratus_dash_ability.desc": "key.keyboard.g:ALT",
    "key_key.deep_aether.slider_eye_ability": "key.keyboard.h:SHIFT",
    "key_key.deep_aether.toggle_skyjade_transparency": "key.keyboard.home:SHIFT",
    "key_key.drawMahoujin": "key.keyboard.m:ALT",
    "key_key.changeMysticCode": "key.keyboard.y:SHIFT",
    "key_key.settingsGUI": "key.keyboard.period:ALT",
    "key_key.selectiveDisplacement": "key.keyboard.h:CONTROL",
    "key_key.gunUp": "key.keyboard.page.up",
    "key_key.gunDown": "key.keyboard.page.down",
    "key_key.enderio.toggle_magnet": "key.keyboard.m:CONTROL",
    "key_key.enderio.travel_staff": "key.keyboard.g",
    "key_key.oritech.augment_screen": "key.keyboard.g:CONTROL",
    "key_key.eternal_starlight.switch_crest": "key.keyboard.h",
    "key_key.little.mirror": "key.keyboard.n",
    "key_key.little.mark": "key.keyboard.u",
    "key_key.little.config.item": "key.keyboard.i",
    "key_key.little.config_secondary.item": "key.keyboard.i:SHIFT",
    "key_key.little.building_mode": "key.keyboard.b:CONTROL",
    "key_key.little.undo": "key.keyboard.z:CONTROL",
    "key_key.little.redo": "key.keyboard.y:CONTROL",
    # Mod ping-pong round 4: the Psi master keybind defaults to C, the vanilla hotbar-save key.
    "key_psimisc.keybind": "key.keyboard.c:ALT",
}
# Operational context, NOT an assertion of a mod's declared conflict context.
GUI = {"key_key.jei.showRecipe", "key_key.jei.showRecipe2", "key_key.jei.showUses",
       "key_key.invtweaks_sort_inventory.desc", "key_key.craftingtweaks.compress_stack",
       "key_key.apotheosis.link_item_to_chat", "key_key.apotheosis.compare_equipment",
       "key_key.ftbquests.gui_editor.reward_tables"}
MAP = {"key_key.journeymap.fullscreen_create_waypoint"}
WORLD = set(PROPOSED) - MAP - GUI
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
