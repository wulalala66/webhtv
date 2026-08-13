#!/usr/bin/env python3
import os
import subprocess
from pathlib import Path


def run(cmd):
    return subprocess.check_output(cmd, text=True).strip()


root = Path(__file__).resolve().parents[1]
os.chdir(root)

base = os.environ.get("UPSTREAM_BASE", "").strip()
upstream = run(["git", "rev-parse", "upstream/main"])


def latest_stable_tag():
    out = run(["git", "tag", "--merged", "upstream/main", "-l", "v[0-9]*", "--sort=-v:refname"])
    for tag in out.splitlines():
        tag = tag.strip()
        if tag and "-beta" not in tag:
            return tag
    return ""


latest_tag = latest_stable_tag()
tag_file = root / "custom" / "upstream-release.tag"
last_tag = tag_file.read_text(encoding="utf-8").strip() if tag_file.exists() else ""

should_sync = base != upstream
should_build = bool(latest_tag) and latest_tag != last_tag

print(f"upstream_sha={upstream}")
print(f"base_sha={base}")
print(f"latest_release_tag={latest_tag}")
print(f"last_release_tag={last_tag}")
print(f"should_sync={'true' if should_sync else 'false'}")
print(f"should_build={'true' if should_build else 'false'}")

if not should_sync and not should_build:
    print("skip_reason=already_up_to_date")
elif not should_sync:
    print("skip_reason=build_only_new_release")
elif not should_build:
    print("skip_reason=no_new_release")
