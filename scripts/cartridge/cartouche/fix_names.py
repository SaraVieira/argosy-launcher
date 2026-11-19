"""Post-process Godot GLB export: clean material and mesh names so the label material is exactly "label"."""
import re
import sys
from pathlib import Path

from pygltflib import GLTF2

for path in sorted(Path(sys.argv[1]).glob("*.glb")):
    g = GLTF2().load(str(path))
    for m in g.materials:
        if m.name:
            m.name = re.sub(r"\d+$", "", m.name)
    for node in g.nodes:
        if node.mesh is not None:
            g.meshes[node.mesh].name = node.name
    g.asset.generator = "Cartouche media export (Godot 4.7.2 + fix_names.py)"
    g.save_binary(str(path))
    print(path.name, [m.name for m in g.materials])
