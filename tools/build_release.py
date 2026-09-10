#!/usr/bin/env python3
"""Build an atomic static update set for Android and ESP32 clients."""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import os
import pathlib
import tempfile

from canard_source import download, normalize

ROOT = pathlib.Path(__file__).resolve().parents[1]
PUBLIC = ROOT / "public"
LIMITS = ROOT / "app/src/main/assets/osm-limits.json"


def compact(value: object) -> bytes:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"), sort_keys=True).encode("utf-8")


def validate_change(current: dict, previous: dict | None) -> None:
    if not previous:
        return
    old = len(previous.get("cameras", []))
    new = len(current["cameras"])
    if old and (new < old * 0.75 or new > old * 1.50):
        raise ValueError(f"CANARD count changed suspiciously: {old} -> {new}")


def validate_limits(pack: dict) -> int:
    if pack.get("schema") != 1 or pack.get("profile") != "motorcar-no-trailer":
        raise ValueError("unsupported OSM limits pack")
    rows = pack.get("entries")
    if not isinstance(rows, list) or not 1 <= len(rows) <= 5_000:
        raise ValueError("invalid OSM limits record count")
    identifiers, known = set(), 0
    for row in rows:
        if row.get("id") in identifiers:
            raise ValueError("duplicate OSM limits identifier")
        identifiers.add(row.get("id"))
        values = (row.get("forward"), row.get("backward"))
        if any(value != 0 and (not isinstance(value, int) or not 10 <= value <= 140) for value in values):
            raise ValueError("invalid speed limit")
        known += int(any(values))
    return known


def atomic_write(path: pathlib.Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=path.parent, delete=False) as handle:
        handle.write(data)
        temporary = pathlib.Path(handle.name)
    temporary.replace(path)


def descriptor(name: str, data: bytes) -> dict:
    return {"file": name, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--html", type=pathlib.Path, help="use a saved CANARD page instead of downloading")
    parser.add_argument("--canard", type=pathlib.Path, help="reuse an already normalized CANARD JSON file")
    args = parser.parse_args()
    if args.canard:
        canard = json.loads(args.canard.read_text(encoding="utf-8"))
        if canard.get("schema") != 1 or not isinstance(canard.get("cameras"), list):
            raise ValueError("invalid normalized CANARD file")
    else:
        html = args.html.read_text(encoding="utf-8") if args.html else download(os.getenv("RADARPL_USER_AGENT", "RadarPL/0.4 personal non-commercial updater"))
        canard = normalize(html)
    previous_path = PUBLIC / "canard.json"
    previous = json.loads(previous_path.read_text()) if previous_path.exists() else None
    validate_change(canard, previous)
    limits = json.loads(LIMITS.read_text(encoding="utf-8"))
    known = validate_limits(limits)
    canard_raw, limits_raw = compact(canard), compact(limits)
    canard_gz = gzip.compress(canard_raw, compresslevel=9, mtime=0)
    limits_gz = gzip.compress(limits_raw, compresslevel=9, mtime=0)
    canard_info = descriptor("canard.json.gz", canard_gz)
    limits_info = descriptor("osm-limits.json.gz", limits_gz)
    payload_hash = hashlib.sha256(canard_gz + limits_gz).hexdigest()
    manifest = {
        "schema": 1,
        "version": payload_hash[:16],
        "generated_at": canard["date"],
        "canard": {**canard_info, "records": len(canard["cameras"]), "source": canard["source"]},
        "limits": {**limits_info, "records": len(limits["entries"]), "known": known, "source_at": limits["date"]},
    }
    atomic_write(PUBLIC / "canard.json", canard_raw)
    atomic_write(PUBLIC / "osm-limits.json", limits_raw)
    atomic_write(PUBLIC / canard_info["file"], canard_gz)
    atomic_write(PUBLIC / limits_info["file"], limits_gz)
    atomic_write(PUBLIC / "manifest.json", compact(manifest))
    print(json.dumps(manifest, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
