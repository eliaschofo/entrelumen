"""Read-only asset access for ENTRELUMEN previews and tools/quest_draft.py: the pinned JARs of
catalog/local-paths.json, the vanilla 1.21.1 client JAR, the companion's resources and the pack's resource pack
(highest priority). Nothing is copied: files are read from the JARs when asked for. The index of which JAR holds
which asset is cached outside the repository (E:/Elias/Codex/Entrelumen-ssd/research, or ENTRELUMEN_CACHE_DIR),
one file per set of JAR paths."""
import hashlib
import io
import json
import os
import pickle
import zipfile
from pathlib import Path

from PIL import Image

VANILLA = 'G:/Elias/Codex/Entrelumen-work/neoform-cache/artifacts/minecraft_1.21.1_client.jar'
CACHE_DIR = Path(os.environ.get('ENTRELUMEN_CACHE_DIR', 'E:/Elias/Codex/Entrelumen-ssd/research'))


def cache_file(jars):
    digest = hashlib.sha256(json.dumps(['preview-asset-index', 1, jars]).encode()).hexdigest()[:12]
    return CACHE_DIR / f'preview-asset-index-{digest}.pkl'


class Assets:
    def __init__(self, tree):
        tree = Path(tree)
        self.dirs = [tree / 'pack/resourcepacks/entrelumen', tree / 'companion/src/main/resources']
        paths = json.loads((tree / 'catalog/local-paths.json').read_text(encoding='utf-8'))
        self.jars = sorted(set(paths.values())) + [VANILLA]
        cache = cache_file(self.jars)
        idx = None
        if cache.exists():
            try:
                idx = pickle.loads(cache.read_bytes())['idx']
            except Exception:
                idx = None
        if idx is None:
            idx = {}
            for i, jar in enumerate(self.jars):
                try:
                    with zipfile.ZipFile(jar) as z:
                        for n in z.namelist():
                            if (n.startswith('assets/') or n.startswith('data/')) and n.endswith(('.png', '.json', '.mcmeta')):
                                idx.setdefault(n, i)
                except Exception:
                    pass
            try:   # one step, so a reader never loads half a file (several sessions share the cache)
                cache.parent.mkdir(parents=True, exist_ok=True)
                tmp = cache.with_name(f'{cache.name}.{os.getpid()}.tmp')
                tmp.write_bytes(pickle.dumps({'jars': self.jars, 'idx': idx}))
                os.replace(tmp, cache)
            except OSError:
                pass
        self.idx = idx
        self.zips = {}
        self.icons = {}

    def close(self):
        for z in self.zips.values():
            z.close()
        self.zips = {}

    def jar_of(self, rel):
        i = self.idx.get(rel)
        return None if i is None else self.jars[i]

    def raw(self, rel):
        for d in self.dirs:
            p = d / rel
            if p.is_file():
                return p.read_bytes()
        i = self.idx.get(rel)
        if i is None:
            return None
        if i not in self.zips:
            self.zips[i] = zipfile.ZipFile(self.jars[i])
        try:
            return self.zips[i].read(rel)
        except KeyError:
            return None

    def exists(self, rel):
        return rel in self.idx or any((d / rel).is_file() for d in self.dirs)

    def png(self, rel, first_frame=True):
        b = self.raw(rel)
        if not b:
            return None
        im = Image.open(io.BytesIO(b)).convert('RGBA')
        if first_frame and im.height > im.width and im.height % im.width == 0:
            im = im.crop((0, 0, im.width, im.width))
        return im

    def texture(self, ref, first_frame=True):
        """'ns:textures/x.png' or a model texture 'ns:item/x'."""
        ns, _, path = ref.partition(':') if ':' in ref else ('minecraft', '', ref)
        if not path.startswith('textures/'):
            path = 'textures/' + path
        if not path.endswith('.png'):
            path += '.png'
        return self.png(f'assets/{ns}/{path}', first_frame)

    def model(self, ref):
        ns, _, path = ref.partition(':') if ':' in ref else ('minecraft', '', ref)
        b = self.raw(f'assets/{ns}/models/{path}.json')
        try:
            return json.loads(b) if b else None
        except Exception:
            return None

    def model_textures(self, ref, depth=0):
        m = self.model(ref)
        if not m or depth > 8:
            return {}, None, None
        tex, parent, elements = {}, m.get('parent'), m.get('elements')
        if parent and not parent.startswith('builtin'):
            tex, _, pel = self.model_textures(parent, depth + 1)
            elements = elements or pel
            tex = dict(tex)
        tex.update(m.get('textures') or {})
        return tex, parent, elements

    def flat_icon(self, item_id):
        """The item's own flat texture ('ns:textures/item/<path>.png'), when its inventory icon is exactly that:
        an item/generated (or handheld) model with a single layer0 at the item's own path, square and not
        animated. None for 3D block models, layered or retextured items, and animated textures. This is what a
        [li:<item>] glyph can show faithfully (tools/quest_text.icon_texture)."""
        rel = self.own_layer(item_id)
        if rel is None or self.exists(rel + '.mcmeta'):
            return None
        im = self.png(rel, first_frame=False)
        if im is None or im.width != im.height:
            return None
        ns, path = rel[len('assets/'):].split('/textures/', 1)
        return f'{ns}:textures/{path}'

    def flat_sprite(self, item_id):
        """The block-atlas sprite ('ns:item/<path>') that draws the item's inventory icon, under the same rule as
        flat_icon but animated or not square too: the atlas animates the sprite, and an item/generated model draws
        its layer stretched to the slot all the same. None for 3D, layered, retextured or custom-loaded models.
        tools/quest_client.py draws such an item image as this sprite, in canvas order, instead of an item render."""
        rel = self.own_layer(item_id)
        if rel is None:
            return None
        ns, path = rel[len('assets/'):].split('/textures/', 1)
        return f'{ns}:{path[:-len(".png")]}'

    def own_layer(self, item_id):
        """'assets/ns/textures/item/<path>.png' when the item's model is item/generated (or handheld) with a single
        layer0 at the item's own path and the texture exists; None otherwise."""
        ns, path = item_id.split(':', 1) if ':' in item_id else ('minecraft', item_id)
        ref, parents = f'{ns}:item/{path}', []
        tex, elements = {}, None
        for _ in range(10):
            m = self.model(ref)
            if m is None:
                return None
            parents.append(ref)
            for k, v in (m.get('textures') or {}).items():
                tex.setdefault(k, v)
            elements = elements or m.get('elements')
            if m.get('loader'):
                return None
            parent = m.get('parent')
            if not parent:
                return None
            parent = parent if ':' in parent else 'minecraft:' + parent
            if parent in ('minecraft:item/generated', 'minecraft:item/handheld', 'minecraft:item/handheld_rod'):
                break
            if parent.startswith('minecraft:builtin/'):
                return None
            ref = parent
        else:
            return None
        if elements or set(k for k in tex if k.startswith('layer')) != {'layer0'}:
            return None
        layer = tex['layer0']
        layer = layer if ':' in layer else 'minecraft:' + layer
        if layer != f'{ns}:item/{path}':
            return None
        rel = f'assets/{ns}/textures/item/{path}.png'
        return rel if self.exists(rel) else None

    def item_textures(self, item_id):
        """Candidate flat textures for an item: (kind, 'ns:textures/...png') in preference order."""
        ns, path = item_id.split(':', 1) if ':' in item_id else ('minecraft', item_id)
        tex, parent, elements = self.model_textures(f'{ns}:item/{path}')

        def res(v, n=0):
            while isinstance(v, str) and v.startswith('#') and n < 8:
                v = tex.get(v[1:])
                n += 1
            return v
        out = []
        for k in ('layer0', 'all', 'front', 'side', 'texture', 'top', 'end', 'particle', 'north', 'cross', 'plant', '0', '1'):
            v = res(tex.get(k))
            if isinstance(v, str):
                vns, _, vpath = v.partition(':') if ':' in v else ('minecraft', '', v)
                out.append((k, f'{vns}:textures/{vpath}.png'))
        for sub in ('item', 'block'):
            out.append(('guess', f'{ns}:textures/{sub}/{path}.png'))
        seen, uniq = set(), []
        for k, t in out:
            if t not in seen:
                seen.add(t)
                uniq.append((k, t))
        return uniq

    def item_icon(self, item_id):
        if item_id in self.icons:
            return self.icons[item_id]
        im = None
        try:
            for kind, t in self.item_textures(item_id):
                im = self.texture(t)
                if im is not None:
                    if kind == 'layer0':
                        ns, path = item_id.split(':', 1)
                        tex, _, _ = self.model_textures(f'{ns}:item/{path}')
                        l1 = tex.get('layer1')
                        if isinstance(l1, str) and not l1.startswith('#'):
                            im2 = self.texture(l1)
                            if im2 is not None and im2.size == im.size:
                                im = Image.alpha_composite(im, im2)
                    break
        except Exception:
            im = None
        self.icons[item_id] = im
        return im

    def advancement_icon(self, adv):
        ns, path = adv.split(':', 1)
        for folder in ('advancement', 'advancements'):
            b = self.raw(f'data/{ns}/{folder}/{path}.json')
            if b:
                try:
                    icon = json.loads(b).get('display', {}).get('icon', {})
                    return icon.get('id') or icon.get('item')
                except Exception:
                    return None
        return None
