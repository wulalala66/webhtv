#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OVERLAY="$ROOT/custom/overlay"
MANIFEST="$ROOT/custom/manifest.json"
if [[ ! -d "$OVERLAY" ]]; then
  echo "overlay missing: $OVERLAY" >&2
  exit 1
fi
python3 - <<'PY' "$ROOT" "$OVERLAY" "$MANIFEST"
import hashlib, json, shutil, sys
from pathlib import Path
root, overlay, manifest = map(Path, sys.argv[1:4])
files = []
if manifest.exists():
    data = json.loads(manifest.read_text(encoding='utf-8'))
    files = [item['path'] for item in data.get('files', [])]
if not files:
    files = [str(p.relative_to(overlay)) for p in overlay.rglob('*') if p.is_file()]
changed = []
for rel in files:
    src = overlay / rel
    dst = root / rel
    if not src.exists():
        raise SystemExit(f'missing overlay file: {rel}')
    dst.parent.mkdir(parents=True, exist_ok=True)
    before = hashlib.sha256(dst.read_bytes()).hexdigest() if dst.exists() else ''
    shutil.copy2(src, dst)
    after = hashlib.sha256(dst.read_bytes()).hexdigest()
    if before != after:
        changed.append(rel)
        print(f'applied {rel}')
    else:
        print(f'unchanged {rel}')
print(f'overlay done, changed={len(changed)}')
PY
