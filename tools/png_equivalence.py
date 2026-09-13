"""Check native pixels without depending on a platform's PNG compressor."""
import io
from pathlib import Path

from PIL import Image


def _pixels(data: bytes):
    with Image.open(io.BytesIO(data)) as image:
        if image.format != 'PNG' or image.n_frames != 1:
            raise ValueError('Expected a single-frame PNG')
        # Keep native bytes as well as resolved palette colors: RGBA conversion
        # alone can lose precision in 16-bit images, while P bytes omit colors.
        return image.mode, image.size, image.tobytes(), image.convert('RGBA').tobytes()


def same_png_pixels(actual: bytes, expected: bytes) -> bool:
    try:
        return _pixels(actual) == _pixels(expected)
    except (OSError, ValueError, SyntaxError):
        return False


def preserve_verified_encoding(path: Path, generated: bytes) -> bytes:
    """Retain a tracked encoding only after checking every expected pixel."""
    if path.is_file():
        actual = path.read_bytes()
        if same_png_pixels(actual, generated):
            if actual != generated:
                print(f'PASS identical PNG mode, size and pixels; encoding differs: {path.name}')
            return actual
    return generated
