#!/usr/bin/env python3
"""Download and normalize the public CANARD map layers."""
from __future__ import annotations

import datetime as dt
import json
import math
import re
import urllib.request

URL = "https://www.canard.gitd.gov.pl/cms/o-nas/mapa-urzadzen"
ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/="


class Bits:
    def __init__(self, value: str):
        self.value = value
        self.index = 0
        self.mask = 32
        self.current = ALPHABET.index(value[0])

    def read(self, count: int) -> int:
        result = 0
        for bit in range(count):
            if self.current & self.mask:
                result |= 1 << bit
            self.mask >>= 1
            if self.mask == 0:
                self.mask = 32
                self.index += 1
                self.current = ALPHABET.index(self.value[self.index]) if self.index < len(self.value) else 0
                if self.index > len(self.value) + 1:
                    raise ValueError("truncated LZ-string stream")
        return result


def decompress_base64(value: str) -> str:
    """Python equivalent of the decoder used by the Android application."""
    if not value or len(value) > 2_000_000:
        raise ValueError("invalid compressed CANARD layer")
    bits = Bits(value)
    dictionary = ["", "", ""]
    first = bits.read(2)
    if first == 2:
        return ""
    word = chr(bits.read(8 if first == 0 else 16))
    dictionary.append(word)
    output = [word]
    output_size = len(word)
    enlarge, width = 4, 3
    while output_size < 4_000_000:
        code = bits.read(width)
        if code in (0, 1):
            dictionary.append(chr(bits.read(8 if code == 0 else 16)))
            code = len(dictionary) - 1
            enlarge -= 1
        elif code == 2:
            return "".join(output)
        if enlarge == 0:
            enlarge = 1 << width
            width += 1
        if code < len(dictionary):
            entry = dictionary[code]
        elif code == len(dictionary):
            entry = word + word[0]
        else:
            raise ValueError("invalid LZ-string dictionary reference")
        output.append(entry)
        output_size += len(entry)
        dictionary.append(word + entry[0])
        enlarge -= 1
        word = entry
        if enlarge == 0:
            enlarge = 1 << width
            width += 1
        if width > 22:
            raise ValueError("CANARD dictionary too large")
    raise ValueError("decompressed CANARD layer too large")


def download(user_agent: str) -> str:
    request = urllib.request.Request(URL, headers={"Accept": "text/html", "User-Agent": user_agent})
    with urllib.request.urlopen(request, timeout=45) as response:
        if response.status != 200:
            raise RuntimeError(f"CANARD returned HTTP {response.status}")
        body = response.read(3_000_001)
    if len(body) > 3_000_000:
        raise ValueError("CANARD page exceeds safety limit")
    return body.decode("utf-8")


def normalize(html: str, now: dt.datetime | None = None) -> dict:
    cameras, identifiers = [], set()
    layer_counts = {}
    for key, section in (("fotoradaryPP", False), ("fotoradaryOPP", True)):
        match = re.search(rf'{key}\s*:\s*"([^"]+)"', html)
        if not match:
            raise ValueError(f"CANARD layer {key} not found; source format may have changed")
        rows = json.loads(decompress_base64(match.group(1)))
        layer_counts[key] = len(rows)
        if len(rows) < 10:
            raise ValueError(f"CANARD layer {key} is unexpectedly small")
        for source in rows:
            identifier = f"{key}:{source['id']}"
            lat, lon = float(source["lat"]), float(source["lon"])
            if identifier in identifiers or not (48 <= lat <= 56 and 13 <= lon <= 25):
                raise ValueError("duplicate identifier or invalid CANARD coordinates")
            identifiers.add(identifier)
            row = {"id": identifier, "lat": lat, "lon": lon, "section": section}
            if section:
                end_lat = float(source["lok2PktSzerokosc"])
                end_lon = float(source["lok2PktDlugosc"])
                length = distance(lat, lon, end_lat, end_lon)
                if not (48 <= end_lat <= 56 and 13 <= end_lon <= 25 and 100 <= length <= 100_000):
                    raise ValueError("invalid CANARD section endpoint")
                row.update(endLat=end_lat, endLon=end_lon)
            cameras.append(row)
    if not (100 <= len(cameras) <= 5_000):
        raise ValueError("unexpected total CANARD record count")
    timestamp = (now or dt.datetime.now(dt.timezone.utc)).replace(microsecond=0).isoformat().replace("+00:00", "Z")
    return {"schema": 1, "date": timestamp, "source": URL, "layer_counts": layer_counts, "cameras": cameras}


def distance(a: float, b: float, c: float, d: float) -> float:
    x, y = math.radians(c - a), math.radians(d - b)
    h = math.sin(x / 2) ** 2 + math.cos(math.radians(a)) * math.cos(math.radians(c)) * math.sin(y / 2) ** 2
    return 6_371_000 * 2 * math.atan2(math.sqrt(h), math.sqrt(max(0, 1 - h)))
