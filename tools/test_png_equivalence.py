"""Cross-platform PNG checks must preserve every native pixel and hash contract."""
import io
import hashlib
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

from PIL import Image

from png_equivalence import preserve_verified_encoding, same_png_pixels


def encode(image, **options):
    stream = io.BytesIO()
    image.save(stream, format='PNG', **options)
    return stream.getvalue()


class PngEquivalenceTests(unittest.TestCase):
    def setUp(self):
        self.image = Image.new('RGBA', (16, 16), (17, 53, 29, 255))
        self.image.putpixel((0, 0), (9, 8, 7, 0))
        self.original = encode(self.image, compress_level=0)

    def test_compression_only_difference_keeps_verified_tracked_bytes(self):
        regenerated = encode(self.image, compress_level=9)
        self.assertNotEqual(self.original, regenerated)
        self.assertTrue(same_png_pixels(self.original, regenerated))
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'tracked.png'
            path.write_bytes(self.original)
            self.assertEqual(preserve_verified_encoding(path, regenerated), self.original)

    def test_rgb_alpha_and_hidden_rgb_changes_are_rejected(self):
        for coordinate, value in [((1, 1), (18, 53, 29, 255)),
                                  ((1, 1), (17, 53, 29, 254)),
                                  ((0, 0), (10, 8, 7, 0))]:
            with self.subTest(coordinate=coordinate, value=value):
                changed = self.image.copy()
                changed.putpixel(coordinate, value)
                self.assertFalse(same_png_pixels(self.original, encode(changed)))

    def test_size_mode_format_and_invalid_data_are_rejected(self):
        self.assertFalse(same_png_pixels(self.original, encode(self.image.resize((8, 8)))))
        self.assertFalse(same_png_pixels(self.original, encode(self.image.convert('RGB'))))
        bmp = io.BytesIO()
        self.image.save(bmp, format='BMP')
        self.assertFalse(same_png_pixels(self.original, bmp.getvalue()))
        self.assertFalse(same_png_pixels(b'not a PNG', self.original))

    def test_palette_changes_are_rejected_even_when_indices_match(self):
        image = Image.new('P', (16, 16), 0)
        image.putpalette([17, 53, 29] + [0, 0, 0] * 255)
        original = encode(image)
        image.putpalette([18, 53, 29] + [0, 0, 0] * 255)
        self.assertFalse(same_png_pixels(original, encode(image)))

    def test_changed_or_missing_asset_uses_generated_bytes(self):
        changed = self.image.copy()
        changed.putpixel((1, 1), (1, 2, 3, 255))
        expected = encode(changed)
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'tracked.png'
            self.assertEqual(preserve_verified_encoding(path, expected), expected)
            path.write_bytes(self.original)
            self.assertEqual(preserve_verified_encoding(path, expected), expected)

    def test_animated_png_is_rejected(self):
        second = self.image.copy()
        second.putpixel((1, 1), (0, 0, 0, 255))
        animated = encode(self.image, save_all=True, append_images=[second], duration=100)
        self.assertFalse(same_png_pixels(animated, animated))

    def test_identity_import_and_manifest_use_only_pixel_verified_encoding(self):
        root = Path(__file__).resolve().parents[1]
        spec = importlib.util.spec_from_file_location('identity', root / 'art/build_pixel_identity.py')
        identity = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(identity)
        with tempfile.TemporaryDirectory() as folder:
            out = Path(folder)
            for name in ('title-scene.png', 'loading-scene.png', 'wordmark.py', 'logo-classic.png'):
                (out / name).write_bytes((identity.OUT / name).read_bytes())
            identity.OUT = out
            for path, data in identity.build()[0].items():
                path.write_bytes(data)
            path = out / 'logo.png'
            identity.LOGO = 'wordmark'
            tracked = encode(identity.wordmark(), compress_level=0)
            path.write_bytes(tracked)
            generated, scenes, logo = identity.build()
            identity.validate(generated, scenes, logo)
            self.assertEqual(generated[path], tracked)
            manifest = json.loads(generated[out / 'pixel-manifest.json'])
            self.assertEqual(manifest['files']['logo.png'], hashlib.sha256(tracked).hexdigest())
            changed = identity.wordmark()
            changed.putpixel((0, 0), (1, 2, 3, 255))
            invalid = encode(changed)
            path.write_bytes(invalid)
            regenerated = identity.build()[0]
            self.assertNotEqual(regenerated[path], invalid)
            self.assertTrue(same_png_pixels(regenerated[path], encode(identity.wordmark())))


if __name__ == '__main__':
    unittest.main()
