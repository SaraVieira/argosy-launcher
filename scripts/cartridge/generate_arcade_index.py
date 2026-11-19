#!/usr/bin/env python3
"""Index exact arcade identities and game-specific artwork from LaunchBox Metadata.zip."""
import argparse
from collections import defaultdict
from pathlib import Path
from xml.etree import ElementTree as ET
from zipfile import ZipFile

from generate_launchbox_index import IMAGE_FILENAME, REGION_PRIORITY, image_priority, title_key

OUTPUT = Path(__file__).resolve().parents[2] / "app/src/main/assets/arcade-art"
SIDE_PANELS = Path(__file__).with_name("arcade_side_panels.tsv")
TYPES = ("Arcade - Marquee", "Screenshot - Gameplay")


def records(stream):
    events = ET.iterparse(stream, events=("start", "end"))
    _, root = next(events)
    depth = 1
    for event, row in events:
        depth += 1 if event == "start" else -1
        if depth == 1:
            yield row
            root.clear()


def generate(archive, output):
    platform_ids = set()
    identities = defaultdict(set)
    selected = {}
    with ZipFile(archive) as db, db.open("Metadata.xml") as source:
        for row in records(source):
            game = row.findtext("DatabaseID")
            if row.tag == "Game" and row.findtext("Platform") == "Arcade":
                platform_ids.add(game)
                identities[title_key(row.findtext("Name"))].add(game)
            elif row.tag == "GameAlternateName":
                identities[title_key(row.findtext("AlternateName") or "")].add(game)
            elif row.tag == "GameImage" and (kind := row.findtext("Type")) in TYPES:
                file = row.findtext("FileName") or ""
                if IMAGE_FILENAME.fullmatch(file):
                    rank = image_priority(row.findtext("Region") or "", file, REGION_PRIORITY)
                    if (game, kind) not in selected or rank < selected[game, kind]:
                        selected[game, kind] = rank
    # Include competing games even when they have no art; otherwise an ambiguous name
    # would silently resolve to whichever game happened to have an image.
    titles = {}
    for key, games in identities.items():
        candidates = games & platform_ids
        if key and len(candidates) == 1:
            titles[key] = next(iter(candidates))
    filenames = {}
    with ZipFile(archive) as db, db.open("Mame.xml") as source:
        for row in records(source):
            game = titles.get(title_key(row.findtext("Name") or ""))
            if game and row.tag == "MameFile" and row.findtext("IsNonArcade") != "true":
                filenames[row.findtext("FileName")] = game
    sides = {}
    for line in SIDE_PANELS.read_text().splitlines():
        shortname, url = line.split("\t")
        if game := filenames.get(shortname):
            sides[game] = url
    output.mkdir(parents=True, exist_ok=True)
    with (output / "images.tsv").open("w", encoding="utf-8") as out:
        has_art = set()
        for game in sorted(platform_ids, key=int):
            files = [selected.get((game, kind), ("", "", ""))[-1] for kind in TYPES]
            fields = files + [sides.get(game, "")]
            if any(fields):
                has_art.add(game)
                out.write("\t".join([game] + fields) + "\n")
    for name, mapping in (("titles", titles), ("roms", filenames)):
        with (output / (name + ".tsv")).open("w", encoding="utf-8") as out:
            for key, game in sorted(mapping.items()):
                if game in has_art:
                    out.write(f"{key}\t{game}\n")
    print(f"{len(has_art)} arcade records; {len(sides)} verified cabinet sides")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("--output", type=Path, default=OUTPUT)
    args = parser.parse_args()
    generate(args.archive, args.output)
