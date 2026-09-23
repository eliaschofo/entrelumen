"""Build original FancyMenu 3.9.12 backgrounds while retaining native controls/progress."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile
import struct

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'pack/config/fancymenu'
SCREEN_CLASSES = {
    'title_screen': 'net.minecraft.client.gui.screens.TitleScreen',
    'select_world_screen': 'net.minecraft.client.gui.screens.worldselection.SelectWorldScreen',
    'connect_screen': 'net.minecraft.client.gui.screens.ConnectScreen',
    'level_loading_screen': 'net.minecraft.client.gui.screens.LevelLoadingScreen',
    'progress_screen': 'net.minecraft.client.gui.screens.ProgressScreen',
    'receiving_level_screen': 'net.minecraft.client.gui.screens.ReceivingLevelScreen',
    'generic_dirt_message_screen': 'net.minecraft.client.gui.screens.GenericMessageScreen',
    'drippy_loading_overlay': 'de.keksuccino.drippyloadingscreen.customization.DrippyOverlayScreen',
}
SCREENS = tuple(SCREEN_CLASSES)


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def dependency(mod):
    lock = read(ROOT / 'catalog/curated.json')
    paths = read(ROOT / 'catalog/local-paths.json')
    entry = next(e for e in lock['mods'] if any(m['id'] == mod for m in e['metadata']['mods']))
    path = Path(paths[entry['filename']])
    if hashlib.sha256(path.read_bytes()).hexdigest() != entry['sha256']:
        raise ValueError('Dependency hash mismatch: ' + mod)
    return entry, path


def block(name, values):
    return name + ' {\n' + ''.join(f'  {key} = {value}\n' for key, value in values.items()) + '}\n\n'


def build():
    entry, jar = dependency('fancymenu')
    version = next(m['version'] for m in entry['metadata']['mods'] if m['id'] == 'fancymenu')
    if version != '3.9.12':
        raise ValueError('Revalidate serialization against new FancyMenu version before generating.')
    outputs = {}
    outputs[BASE / 'customizablemenus.txt'] = ('type = customizablemenus\n\n' +
        ''.join(block(screen_class, {}) for screen_class in SCREEN_CLASSES.values())).rstrip().encode('utf-8') + b'\n'
    assets = {}
    policy = read(ROOT / 'art/menu/theme-policy.json')
    approval_path = ROOT / 'art/menu/pixel-art-approved.json'
    approved = read(approval_path).get('sha256', {}) if approval_path.is_file() else {}
    for name in ('title-background', 'loading-background', 'logo'):
        source = ROOT / 'art/menu' / (name + '.png')
        target = BASE / 'assets/entrelumen' / (name + '.png')
        digest = hashlib.sha256(source.read_bytes()).hexdigest() if source.is_file() else None
        integrity_verified = digest is not None and approved.get(name + '.png') == digest
        available = integrity_verified and policy[name] == 'native-pixel-art-candidate'
        assets[name] = {'source': source.relative_to(ROOT).as_posix(),
                        'destination': target.relative_to(ROOT / 'pack').as_posix(),
                        'available': available, 'sha256': digest,
                        'integrityVerified': integrity_verified,
                        'artStatus': policy[name]}
        if available:
            outputs[target] = source.read_bytes()
    for screen in SCREENS:
        name = 'title-background' if screen == 'title_screen' else 'loading-background'
        background = {'instance_identifier': 'entrelumen_' + screen + '_background',
                      'background_type': 'image' if assets[name]['available'] else 'color_fancymenu',
                      'show_background': 'true'}
        if assets[name]['available']:
            background.update(image_path='[source:local]/' + assets[name]['destination'],
                              slide='false', repeat_texture='false', parallax='false',
                              restart_animated_on_menu_load='false')
        else:
            background['color'] = '#132B29FF'
        text = 'type = fancymenu_layout\n\n'
        text += block('layout-meta', {'identifier': screen, 'is_enabled': 'true',
                                     'render_custom_elements_behind_vanilla': 'true', 'layout_index': '0'})
        text += block('customization', {'action': 'backgroundoptions', 'keepaspectratio': 'true'})
        text += block('menu_background', background)
        text += block('scroll_list_customization', {'apply_vanilla_background_blur': 'false',
                                                   'show_screen_background_overlay_on_custom_background': 'false'})
        if screen == 'title_screen':
            for index, widget in enumerate(('mc_titlescreen_singleplayer_button', 'mc_titlescreen_multiplayer_button',
                                             'forge_titlescreen_mods_button', 'mc_titlescreen_options_button',
                                             'mc_titlescreen_quit_button', 'mc_titlescreen_realms_button')):
                text += block('vanilla_button', {'element_type': 'vanilla_button', 'instance_identifier': widget,
                                                'anchor_point': 'mid-left', 'sticky_anchor': 'false',
                                                'x': 20, 'y': -40 + index * 22, 'width': 160, 'height': 20,
                                                'is_hidden': 'false', 'label_scale': '1.0', 'label_shadow': 'true',
                                                'backgroundnormal': '[source:local]/config/fancymenu/assets/entrelumen/gui/button_normal.png',
                                                'backgroundhovered': '[source:local]/config/fancymenu/assets/entrelumen/gui/button_hover.png',
                                                'background_texture_inactive': '[source:local]/config/fancymenu/assets/entrelumen/gui/button_disabled.png',
                                                'nine_slice_custom_background': 'false'})
            for index, widget in enumerate(('entrelumen_title_language', 'entrelumen_title_accessibility',
                                             'entrelumen_title_create', 'entrelumen_title_supplementaries')):
                text += block('vanilla_button', {'element_type': 'vanilla_button', 'instance_identifier': widget,
                                                'anchor_point': 'top-right', 'sticky_anchor': 'false',
                                                'x': -100 + index * 24, 'y': 4, 'width': 20, 'height': 20,
                                                'is_hidden': 'false'})
            if assets['logo']['available']:
                logo = outputs[BASE / 'assets/entrelumen/logo.png']
                if logo[:8] != b'\x89PNG\r\n\x1a\n':
                    raise ValueError('Logo must be PNG')
                width, height = struct.unpack('>II', logo[16:24])
                if width > 256 or height > 64:
                    raise ValueError('Supply native pixel-art logo <=256x64; generator never fractionally resizes it.')
                text += block('element', {'element_type': 'image', 'instance_identifier': 'entrelumen_logo',
                                         'anchor_point': 'mid-left', 'sticky_anchor': 'false',
                                         'x': 12, 'y': -50 - height, 'width': width, 'height': height,
                                         'source': '[source:local]/config/fancymenu/assets/entrelumen/logo.png',
                                         'repeat_texture': 'false', 'nine_slice_texture': 'false'})
                for widget in ('minecraft_logo_widget', 'minecraft_splash_widget'):
                    text += block('vanilla_button', {'element_type': 'vanilla_button',
                                                    'instance_identifier': widget, 'is_hidden': 'true'})
        outputs[BASE / 'customization' / ('entrelumen_' + screen + '.txt')] = text.rstrip().encode('utf-8') + b'\n'
    manifest = {'schemaVersion': 2, 'status': 'native PixelLab scenes, copper wordmark and tuff/copper buttons; in-game visual acceptance pending',
                'fancymenu': {'version': version, 'jarSha256': entry['sha256']}, 'assets': assets,
                'screens': list(SCREENS),
                'logoStatus': 'copper wordmark at native size above the left column; artistic acceptance pending',
                'pixelArtPolicy': 'Integrity is separate from artistic status. Scenes are native pixel art exported at integer x5; the wordmark is drawn at 1:1 GUI units. No filtered illustration or fractional sprite scaling.',
                'nativeControls': ['menu.singleplayer', 'menu.multiplayer', 'menu.options', 'menu.quit', 'NeoForge Mods button'],
                'localization': 'Native labels and loading messages follow selected Minecraft language; no baked translated labels.',
                'facts': 'Not added: preserve actual loading status and progress. EN/ES fact elements require editor-verified serialization.',
                'exclusions': ['NeoForge early loading window', 'launcher', 'crash/error dialogs'],
                'visualQA': 'Pending root client at 1028 and 1920; no synthetic screenshots or performance claim.'}
    outputs[BASE / 'assets/entrelumen/manifest.json'] = (json.dumps(manifest, ensure_ascii=False, indent=2) + '\n').encode()
    return outputs, jar


def verify_parser(java, jar, outputs):
    _, konkrete = dependency('konkrete')
    candidates = sorted((Path.home() / '.gradle/caches/modules-2/files-2.1/org.apache.logging.log4j/log4j-api').glob('*/*/*.jar'))
    if not candidates:
        raise ValueError('log4j-api required for the original FancyMenu parser probe')
    import os
    classpath = os.pathsep.join(map(str, (jar, konkrete, candidates[0])))
    source = '''import de.keksuccino.fancymenu.util.properties.*;
import java.nio.file.*;
class MenuParserProbe {
 public static void main(String[] args) throws Exception {
  for (String path: args) {
   var set = PropertiesParser.deserializeSetFromFancyString(Files.readString(Path.of(path)));
   if (!"fancymenu_layout".equals(set.getType()) || set.getContainers().size()<3
       || set.getFirstContainerOfType("menu_background")==null
       || set.getFirstContainerOfType("layout-meta").getValue("identifier")==null)
     throw new IllegalStateException(path);
  }
  System.out.println("PASS: original FancyMenu parser accepted "+args.length+" layouts");
 }
}'''
    with tempfile.TemporaryDirectory(prefix='entrelumen-menu-parser-') as temp:
        probe = Path(temp) / 'MenuParserProbe.java'
        probe.write_text(source, encoding='utf-8', newline='\n')
        files = []
        for path, data in outputs.items():
            if path.suffix == '.txt' and path.parent.name == 'customization':
                target = Path(temp) / path.name
                target.write_bytes(data)
                files.append(str(target))
        subprocess.run([java, '--class-path', classpath, str(probe), *files], check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--verify-parser', metavar='JAVA_EXE')
    args = parser.parse_args()
    outputs, jar = build()
    for path, data in outputs.items():
        if args.check:
            if not path.is_file() or path.read_bytes() != data:
                raise SystemExit('Menu identity output is stale: ' + str(path))
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
    if args.verify_parser:
        verify_parser(args.verify_parser, jar, outputs)
    print(f'PASS: {len(SCREENS)} deterministic FancyMenu layouts; runtime visual QA pending.')


if __name__ == '__main__':
    main()
