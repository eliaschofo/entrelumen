"""Minimal PixelLab v2 client for ENTRELUMEN artwork batches.

The API key is read only from the PIXELLAB_API_KEY environment variable (use
`cred-run Llavero/PixelLab/api-key PIXELLAB_API_KEY -- python tools/pixellab.py ...`).
It is never written to receipts, logs or outputs.

A job spec is a JSON file: {"endpoint": "/generate-with-style-v2", "body": {...},
"refs": {"style_images": ["path.png", ...]}}. Paths under "refs" are encoded as
base64 image objects in the shape each endpoint expects. The dispatch receipt is
stored before polling so an interrupted run resumes the same job instead of paying
for another one.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import io
import json
import os
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

from PIL import Image, ImageDraw

API = 'https://api.pixellab.ai/v2'


def _request(method: str, path: str, body: dict | None = None, timeout: int = 120) -> dict:
    key = os.environ.get('PIXELLAB_API_KEY')
    if not key:
        sys.exit('PIXELLAB_API_KEY is not set; run through cred-run')
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(API + path, data=data, method=method, headers={
        'Authorization': 'Bearer ' + key, 'Content-Type': 'application/json'})
    for attempt in range(90):
        try:
            with urllib.request.urlopen(req, timeout=timeout) as r:
                return json.loads(r.read().decode())
        except urllib.error.HTTPError as e:
            detail = e.read().decode()[:800]
            # Tier 1 allows 8 concurrent background jobs; a 429 is a queue signal, not a failure.
            if e.code == 429 and attempt < 89:
                time.sleep(20)
                continue
            raise RuntimeError(f'{method} {path} -> HTTP {e.code}: {detail}') from None


def _b64(path: Path) -> tuple[dict, int, int]:
    im = Image.open(path).convert('RGBA')
    buf = io.BytesIO()
    im.save(buf, 'PNG')
    return {'type': 'base64', 'base64': base64.b64encode(buf.getvalue()).decode(), 'format': 'png'}, im.width, im.height


def _encode_refs(endpoint: str, body: dict, refs: dict, base: Path) -> dict:
    body = dict(body)
    for field, paths in refs.items():
        items = []
        for entry in (paths if isinstance(paths, list) else [paths]):
            p, usage = (entry, None) if isinstance(entry, str) else (entry['path'], entry.get('usage'))
            img, w, h = _b64(base / p)
            if field == 'image':
                # image-to-pixelart endpoints take the Base64Image itself; the basic one also needs its size.
                items.append(img)
                if endpoint == '/image-to-pixelart':
                    body['image_size'] = {'width': w, 'height': h}
            elif endpoint == '/generate-with-style-v2' or field == 'style_images':
                items.append({'image': img, 'width': w, 'height': h})
            else:
                item = {'image': img, 'size': {'width': w, 'height': h}}
                if usage:
                    item['usage_description'] = usage
                items.append(item)
        body[field] = items if isinstance(paths, list) else items[0]
    return body


def _decode_images(resp: dict) -> list[Image.Image]:
    found = []

    def walk(node):
        if isinstance(node, dict):
            if isinstance(node.get('base64'), str):
                raw = node['base64'].split(',', 1)[-1]
                found.append(Image.open(io.BytesIO(base64.b64decode(raw))).convert('RGBA'))
                return
            for v in node.values():
                walk(v)
        elif isinstance(node, list):
            for v in node:
                walk(v)
    walk(resp)
    return found


def contact_sheet(images: list[Image.Image], out: Path, scale: int = 5, cols: int = 8) -> None:
    w = max(i.width for i in images) * scale
    h = max(i.height for i in images) * scale
    pad = 14
    rows = (len(images) + cols - 1) // cols
    sheet = Image.new('RGBA', (cols * (w + 6) + 6, rows * (h + pad + 6) + 6), (40, 40, 44, 255))
    d = ImageDraw.Draw(sheet)
    for n, im in enumerate(images):
        x = 6 + (n % cols) * (w + 6)
        y = 6 + (n // cols) * (h + pad + 6)
        d.rectangle([x, y, x + w - 1, y + h - 1], fill=(139, 139, 139, 255))
        big = im.resize((im.width * scale, im.height * scale), Image.NEAREST)
        sheet.alpha_composite(big, (x + (w - big.width) // 2, y + (h - big.height) // 2))
        d.text((x + 2, y + h + 1), str(n + 1), fill=(230, 230, 230, 255))
    sheet.save(out)


def run(spec_path: Path, out_dir: Path, poll: int = 8, max_wait: int = 900) -> Path:
    spec = json.loads(spec_path.read_text(encoding='utf-8'))
    out_dir.mkdir(parents=True, exist_ok=True)
    receipt_path = out_dir / 'receipt.json'
    receipt = json.loads(receipt_path.read_text(encoding='utf-8')) if receipt_path.exists() else {}
    if receipt.get('status') == 'completed':
        return out_dir
    if not receipt.get('background_job_id') and not receipt.get('sync_done'):
        body = _encode_refs(spec['endpoint'], spec['body'], spec.get('refs', {}), spec_path.parent)
        resp = _request('POST', spec['endpoint'], body)
        receipt = {'provider': 'PixelLab', 'endpoint': spec['endpoint'], 'spec': spec_path.name,
                   'request': spec['body'], 'refs': spec.get('refs', {}),
                   'background_job_id': resp.get('background_job_id'), 'dispatch_usage': resp.get('usage'),
                   'dispatched_at': time.strftime('%Y-%m-%dT%H:%M:%S%z')}
        receipt_path.write_text(json.dumps(receipt, indent=1), encoding='utf-8')
        if not resp.get('background_job_id'):
            final = resp
        else:
            final = None
    else:
        final = None
    if final is None:
        waited = 0
        while True:
            job = _request('GET', f"/background-jobs/{receipt['background_job_id']}")
            if job['status'] in ('completed', 'failed'):
                final = job.get('last_response') or {}
                receipt['usage'] = job.get('usage')
                receipt['job_status'] = job['status']
                break
            time.sleep(poll)
            waited += poll
            if waited > max_wait:
                receipt['job_status'] = 'timeout-pending'
                receipt_path.write_text(json.dumps(receipt, indent=1), encoding='utf-8')
                raise TimeoutError(f'{spec_path.name}: still processing; rerun to resume the same job')
    images = _decode_images(final)
    files = []
    for n, im in enumerate(images, 1):
        f = out_dir / f'c{n:02d}.png'
        im.save(f)
        files.append({'file': f.name, 'size': list(im.size), 'sha256': hashlib.sha256(f.read_bytes()).hexdigest()})
    if images:
        contact_sheet(images, out_dir / 'sheet.png')
    receipt['status'] = 'completed' if images else 'failed'
    receipt['images'] = files
    if not images:
        receipt['last_response_keys'] = list(final.keys()) if isinstance(final, dict) else str(type(final))
        receipt['error'] = str(final)[:600]
    receipt_path.write_text(json.dumps(receipt, indent=1), encoding='utf-8')
    return out_dir


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument('specs', nargs='+', type=Path)
    ap.add_argument('--out', type=Path, required=True, help='parent directory; one subfolder per spec')
    args = ap.parse_args()
    from concurrent.futures import ThreadPoolExecutor
    def one(s):
        try:
            d = run(s, args.out / s.stem)
            r = json.loads((d / 'receipt.json').read_text(encoding='utf-8'))
            return f"{s.stem}: {r['status']} {len(r.get('images', []))} images usage={r.get('usage')}"
        except Exception as e:  # report and continue with the other jobs
            return f'{s.stem}: ERROR {e}'
    with ThreadPoolExecutor(max_workers=6) as ex:
        for line in ex.map(one, args.specs):
            print(line, flush=True)
    print(json.dumps(_request('GET', '/balance').get('subscription')))


if __name__ == '__main__':
    main()
