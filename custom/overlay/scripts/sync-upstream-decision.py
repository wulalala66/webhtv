#!/usr/bin/env python3
import os, subprocess, sys
from pathlib import Path

def run(cmd):
    return subprocess.check_output(cmd, text=True).strip()

root = Path(__file__).resolve().parents[1]
os.chdir(root)
base = os.environ.get('UPSTREAM_BASE', '').strip()
upstream = run(['git', 'rev-parse', 'upstream/main'])
if base == upstream:
    print('skip_reason=already_up_to_date')
    print('should_sync=false')
    print('should_build=false')
    print(f'upstream_sha={upstream}')
    sys.exit(0)

removed = []
removed_file = root / 'custom' / 'removed-paths.txt'
if removed_file.exists():
    for line in removed_file.read_text(encoding='utf-8').splitlines():
        line=line.strip()
        if line and not line.startswith('#'):
            removed.append(line)

changed = []
if base:
    try:
        out = run(['git', 'diff', '--name-only', f'{base}...upstream/main'])
        changed = [x for x in out.splitlines() if x]
    except Exception:
        changed = ['*']
else:
    changed = ['*']

only_removed = bool(changed) and all(any(path == r or path.startswith(r.rstrip('/') + '/') for r in removed) for path in changed) if removed else False

print(f'upstream_sha={upstream}')
print(f'base_sha={base}')
print(f'changed_count={len(changed)}')
print('should_sync=true')
print('should_build=' + ('false' if only_removed else 'true'))
if only_removed:
    print('skip_reason=upstream_only_removed_paths')
for path in changed[:50]:
    print(f'changed={path}')
