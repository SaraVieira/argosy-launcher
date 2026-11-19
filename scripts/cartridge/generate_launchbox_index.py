#!/usr/bin/env python3
"""Build platform-specific physical media indexes from the official LaunchBox metadata feed."""
import argparse
import re
import shutil
import tempfile
import unicodedata
import urllib.request
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path
from zipfile import ZipFile

METADATA_URL = "https://gamesdb.launchbox-app.com/Metadata.zip"
ASSETS = Path(__file__).resolve().parents[2] / "app/src/main/assets/physical-media-index"
CARTS = {
    "Sony Playstation Vita": "vita", "ColecoVision": "coleco", "Atari 2600": "atari2600", "Atari 7800": "atari7800",
    "Nintendo Game Boy": "gb", "Nintendo Game Boy Color": "gbc", "Nintendo Game Boy Advance": "gba",
    "Nintendo Entertainment System": "nes", "Super Nintendo Entertainment System": "snes",
    "Nintendo 64": "n64", "Nintendo DS": "nds", "Nintendo 3DS": "3ds", "Nintendo Switch": "switch",
    "Sega Genesis": "genesis", "Sega Master System": "sms", "Sega Game Gear": "gg",
    "NEC TurboGrafx-16": "tg16", "PC Engine SuperGrafx": "supergrafx"
}
DISCS = {
    "ScummVM": "scummvm",
    "Sony PSP": "psp", "Sony Playstation": "psx", "Sony Playstation 2": "ps2",
    "Sony Playstation 3": "ps3", "Sony Playstation 4": "ps4", "Sony Playstation 5": "ps5",
    "Nintendo GameCube": "gc", "Nintendo Wii": "wii", "Nintendo Wii U": "wiiu",
    "Microsoft Xbox": "xbox", "Microsoft Xbox 360": "xbox360", "Microsoft Xbox One": "xboxone",
    "Microsoft Xbox Series X/S": "xboxseriesx", "Sega CD": "scd", "Sega Saturn": "saturn",
    "Sega Dreamcast": "dreamcast", "NEC TurboGrafx-CD": "tgcd", "NEC PC-FX": "pcfx",
    "SNK Neo Geo CD": "neogeocd", "Atari Jaguar CD": "jaguarcd", "Commodore Amiga CD32": "amigacd32",
    "Commodore CDTV": "cdtv", "Philips CD-i": "cdi", "3DO Interactive Multiplayer": "3do"
}
FLOPPIES = {
    "Sharp X68000": "x68000", "Atari 800": "atari8bit",
    "Commodore Amiga": "amiga", "Commodore 64": "c64", "Commodore 128": "c128",
    "Commodore PET": "cpet", "Commodore VIC-20": "vic20", "Commodore Plus 4": "plus4",
    "Microsoft MSX": "msx", "Microsoft MSX2": "msx2", "MS-DOS": "dos", "Amstrad CPC": "amstradcpc"
}
CASSETTES = {
    "Sinclair ZX Spectrum": "zx", "Atari 800": "atari8bit", "Commodore 64": "c64",
    "Commodore 128": "c128", "Commodore VIC-20": "vic20", "Commodore Plus 4": "plus4",
    "Commodore PET": "cpet", "Amstrad CPC": "amstradcpc", "Microsoft MSX": "msx",
    "Microsoft MSX2": "msx2"
}
PLATFORMS = CARTS | DISCS | FLOPPIES | CASSETTES
REGION_PRIORITY = ("United States", "North America", "World", "Europe", "United Kingdom")
PAL_PRIORITY = ("Europe", "United Kingdom", "Japan", "Australia", "France", "Germany")
IMAGE_FILENAME = re.compile(r"[\w-]+\.(?:png|jpg|jpeg|webp)", re.ASCII | re.IGNORECASE)


def title_key(title):
    title = unicodedata.normalize("NFKD", title.lower())
    title = "".join(c for c in title if not unicodedata.category(c).startswith("M"))
    title = title.replace("&", " and ").replace("'", "").replace("’", "")
    return re.sub(r"[\W_]+", " ", title).strip()


def image_priority(region, filename, priority):
    rank = priority.index(region) if region in priority else len(priority)
    return rank, region, filename


def read_index(source):
    games = {}
    names = defaultdict(set)
    images = defaultdict(list)
    events = ET.iterparse(source, events=("start", "end"))
    _, root = next(events)
    depth = 1
    for event, row in events:
        if event == "start":
            depth += 1
            continue
        depth -= 1
        if depth != 1:
            continue
        game_id = row.findtext("DatabaseID")
        if row.tag == "Game":
            platform = row.findtext("Platform")
            if platform in PLATFORMS:
                games[game_id] = platform
                names[game_id].add(title_key(row.findtext("Name") or ""))
        elif row.tag == "GameAlternateName":
            names[game_id].add(title_key(row.findtext("AlternateName") or ""))
        elif row.tag == "GameImage" and row.findtext("Type") in ("Disc", "Cart - Front"):
            filename = row.findtext("FileName") or ""
            if IMAGE_FILENAME.fullmatch(filename):
                images[game_id].append((row.findtext("Type"), row.findtext("Region") or "", filename))
        root.clear()

    by_slug = defaultdict(dict)
    for game_id, platform in games.items():
        slug = PLATFORMS[platform]
        if platform in FLOPPIES or platform in CASSETTES:
            partitions = []
            if platform in FLOPPIES:
                partitions.append(f"floppy-{slug}")
            if platform in CASSETTES:
                partitions.append(f"front-{slug}" if slug in ("msx", "msx2") else f"cassette-{slug}")
        elif slug == "nes":
            partitions = [slug, "famicom"]
        elif slug == "snes":
            partitions = [slug, "snes-pal"]
        else:
            partitions = [slug]
        for index_slug in partitions:
            priority = PAL_PRIORITY if index_slug == "snes-pal" else REGION_PRIORITY
            accepted = "Cart - Front" if platform in CARTS or index_slug.startswith(("cassette-", "front-")) else "Disc"
            candidates = [(r, f) for t, r, f in images[game_id] if t == accepted]
            if index_slug == "famicom":
                candidates = [(r, f) for r, f in candidates if r == "Japan"]
            if not candidates and slug == "psp":
                candidates = [(r, f) for t, r, f in images[game_id] if t == "Cart - Front"]
            pick = min(candidates, key=lambda x: image_priority(*x, priority), default=None)
            for title in names[game_id]:
                if title:
                    by_slug[index_slug][title] = None if title in by_slug[index_slug] else pick
    return by_slug


def generate(archive, output):
    with ZipFile(archive) as db, db.open("Metadata.xml") as source:
        indexes = read_index(source)
    output.mkdir(parents=True, exist_ok=True)
    for slug, index in sorted(indexes.items()):
        rows = [(title, image) for title, image in sorted(index.items()) if image]
        content = "".join(f"{title}\t{region}\t{file}\n" for title, (region, file) in rows)
        if not rows:
            continue
        (output / f"{slug}.tsv").write_text(content, encoding="utf-8")
        print(f"{slug}: {len(rows)} title keys, {len({image[1] for _, image in rows})} scans")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", type=Path, help="use a previously downloaded Metadata.zip")
    parser.add_argument("--output", type=Path, default=ASSETS)
    args = parser.parse_args()
    if args.archive:
        generate(args.archive, args.output)
    else:
        with tempfile.TemporaryDirectory() as work:
            archive = Path(work) / "Metadata.zip"
            request = urllib.request.Request(METADATA_URL, headers={"User-Agent": "Argosy/3.0 (artwork index)"})
            with urllib.request.urlopen(request, timeout=60) as response, archive.open("wb") as output:
                shutil.copyfileobj(response, output)
            generate(archive, args.output)


if __name__ == "__main__":
    main()
