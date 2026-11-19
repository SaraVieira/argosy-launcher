# Physical media model sources

The two floppy models in this directory are original GLB exports by Kami Rapacz (kuroderuta). Both carry the [Creative Commons Attribution-NonCommercial 4.0 license](https://creativecommons.org/licenses/by-nc/4.0/). Argosy renders them into rotation frames and adds per-game label artwork. The app shows the author, source links, license and adaptations in Settings under Open Source Licenses.

| Local GLB | Original model | SHA-256 of the supplied export |
| --- | --- | --- |
| `floppy35.glb` | [3.5" High Density Floppy Disk](https://sketchfab.com/3d-models/35-high-density-floppy-disk-free-version-daaf642dbd084517a371b3ba03453a5f) | `a669f0965c6fb0701fdae6e7365ac1c11c27a426689edee1db44c25b3b6ba161` |
| `floppy525.glb` | [5.25" Floppy Disk](https://sketchfab.com/3d-models/525-floppy-disk-free-version-bfdda98888784956b085b10fc7466525) | `3f9f218a6d0634cab90856ef7857e75b1dbc81b883da97c04f61f75f44c9e4ee` |

From the repository root, run the renderer in a Python environment with NumPy, Numba, SciPy and Pillow installed.

```sh
python3 scripts/cartridge/render_models.py scripts/cartridge floppy35 floppy525
```

For the ZNeko cartridge and optical disc models, `fetch_models.py` downloads and verifies the pinned collection into a working directory. Pass that directory and the desired model names to `render_models.py`. The dedicated Game Boy Advance renderer, `render.py`, reads the local `gba.glb`.

The Atari 2600 cartridge uses [Ms. Pacman ATARI 2600 by Shon](https://sketchfab.com/3d-models/ms-pacman-atari-2600-2e1bd4f652444913bb32a2e6e5054392) under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). To rebuild its neutral shell and label masks from the supplied export, run `python3 scripts/cartridge/render_models.py scripts/cartridge atari2600`.

## Cartouche media

The GameCube mini DVD, Dreamcast GD-ROM and Famicom cartridge come from Hakimba’s [Cartouche media scenes at revision ce1de3d](https://github.com/Hakimba/cartouche/tree/ce1de3d4cb66a4db7810ac79886ec874f91808fa/app/carousel/media). Their exported GLBs are included under [GPL-3.0-or-later](../../app/src/main/assets/licenses/cartouche/LICENSE).

To bake all three from the repository root, run:

```sh
python3 scripts/cartridge/render_models.py scripts/cartridge gc_mini_dvd famicom_cart dreamcast_gd
```

To regenerate the GLBs, check out the pinned Cartouche revision and run these commands from its `app` directory with Godot 4.7.2 and the Python package `pygltflib`. Set `export_tools` to this repository’s `scripts/cartridge/cartouche` directory and `model_output` to a working directory for the generated GLBs.

```sh
godot --headless --import
godot --headless --script "$export_tools/export_media.gd" -- "$model_output"
python3 "$export_tools/fix_names.py" "$model_output"
```

The exporter names each printed surface `label`. The optical discs use their own annular label meshes, clear rims, center holes and reflective undersides. Their recording bands come from the source mesh’s groove masks. A GameCube mini DVD measures 80 mm across, compared with the Dreamcast GD-ROM’s 120 mm.

## Cassette tape

`cassette.glb` is the supplied export of [Cassette Tape by Ozen](https://sketchfab.com/3d-models/cassette-tape-1333f3d1d41d4d779cb93e3fd26509fe), licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Its SHA-256 is `6e843f1877d88e0d3b3070b01c1e360809d8dc6a99c61c8e2c62132bc5a23e94`.

Run `python3 scripts/cartridge/render_models.py scripts/cartridge cassette` to regenerate the atlas. The source shares one texture across the entire cassette. The baker separates each sticker’s triangles by UV coordinates, applies the game label on the front and leaves blank paper on the back. The reels, window, shell and screws keep their original texture.

The MSX and MSX2 viewer shares the user-supplied Blender export `msx.glb` (original filename `Untitled.glb`). Its LABEL mesh defines one sticker that folds across the top and onto the back. The baker exports the sticker’s edge profile and visibility masks for every rotation frame. The viewer fits the scan on the front and prints the game title on the folded spine. Rebuild with `python3 scripts/cartridge/render_models.py scripts/cartridge msx`. The export contains no creator or licensing metadata.

## Arcade cabinet

`arcade.glb` is the supplied export of [Arcade Cabinet by Lluc Guardiolaa](https://sketchfab.com/3d-models/arcade-cabinet-122e641e25ee4fe9904fc79399d822b0), licensed under [CC BY-NC 4.0](https://creativecommons.org/licenses/by-nc/4.0/). Its SHA-256 is `2dad0c0cb9d16867b6f9a223fdcc94919ad2177704316415972e72bf0ea5c914`. Run `python3 scripts/cartridge/render_models.py scripts/cartridge arcade` to rebuild it.

Each WebP shell has an opaque lossless RGB PNG with the same dimensions and frame order, named `front-uv.png`, `carousel-uv.png` or `0-uv.png` through `5-uv.png`. The carousel uses a dedicated near-frontal render cropped below the controls. The detail viewer uses the full rotation frames. Blue stores the material ID. Red stores U from left to right, green stores V from top to bottom, each in the range 0–255. Read UV maps as byte data using nearest-neighbor sampling at the native resolution without mipmaps or color conversion.

| Blue | Surface | Artwork width ÷ height |
| --- | --- | --- |
| 0 | Original shell and controls | — |
| 1 | Marquee | 2.58 |
| 2 | Screen | 0.835 |
| 3 | Left or right side panel | 0.526 |

Each side is mapped upright and reads left to right when facing that side. The side silhouette, controls and bezel clip artwork according to the source geometry.
