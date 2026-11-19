#!/usr/bin/env python3
"""Fetch verified Physical View GLBs into a renderer working directory."""

import argparse
import hashlib
import json
import os
import tempfile
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from urllib.request import urlopen


REVISION = "4419be7fc213ce92da2ea080d91caffe7740a6a4"
SOURCE = f"https://raw.githubusercontent.com/zneko-org/zneko-launcher/{REVISION}/models"


def download(name):
    with urlopen(f"{SOURCE}/{name}", timeout=60) as response:
        return response.read()


def verify(data, entry):
    return len(data) == entry["size"] and hashlib.sha256(data).hexdigest() == entry["sha256"]


def fetch(directory, name, entry):
    destination = directory / name
    if destination.exists() and verify(destination.read_bytes(), entry):
        return
    data = download(name)
    if not verify(data, entry):
        raise ValueError(f"{name} does not match the pinned manifest")
    with tempfile.NamedTemporaryFile(dir=directory, delete=False) as temp:
        temp.write(data)
    os.replace(temp.name, destination)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path, help="working directory for the source GLBs")
    args = parser.parse_args()
    manifest = json.loads(download("manifest.json"))
    if manifest["version"] != 1:
        raise ValueError("Unsupported Physical View manifest")
    args.directory.mkdir(parents=True, exist_ok=True)
    with ThreadPoolExecutor(max_workers=6) as pool:
        jobs = [pool.submit(fetch, args.directory, name, entry) for name, entry in manifest["models"].items()]
        for job in jobs:
            job.result()
    print(f"Verified {len(jobs)} models from {REVISION} in {args.directory}")


if __name__ == "__main__":
    main()
