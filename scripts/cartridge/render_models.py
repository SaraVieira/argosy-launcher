"""Bake physical media GLBs into the launcher's orthographic rotation atlas (NumPy, Numba, SciPy, Pillow)."""

import argparse
import io
import json
import math
import struct
from pathlib import Path

import numpy as np
from numba import njit
from PIL import Image, ImageDraw
from scipy.spatial.transform import Rotation

HERE = Path(__file__).resolve().parent
OUT = HERE.parents[1] / "app/src/main/assets/physical-media"
WIDTH, HEIGHT = 420, 272
PITCH = math.radians(9)


def read_glb(path, skip_presentation_pose=False):
    data = path.read_bytes()
    size = struct.unpack_from("<I", data, 12)[0]
    doc = json.loads(data[20 : 20 + size])
    binary = data[28 + size :]

    def buffer(i):
        v = doc["bufferViews"][i]
        start = v.get("byteOffset", 0)
        return binary[start : start + v["byteLength"]]

    def accessor(i):
        a = doc["accessors"][i]
        dt = np.dtype(
            {5121: "u1", 5123: "<u2", 5125: "<u4", 5126: "<f4"}[a["componentType"]]
        )
        n = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4}[a["type"]]
        stride = doc["bufferViews"][a["bufferView"]].get("byteStride", dt.itemsize * n)
        return np.ndarray(
            (a["count"], n),
            dt,
            buffer=buffer(a["bufferView"]),
            offset=a.get("byteOffset", 0),
            strides=(stride, dt.itemsize),
        ).copy()

    parts = []

    def visit(index, parent):
        node = doc["nodes"][index]
        local = np.eye(4)
        if "matrix" in node:
            local = np.array(node["matrix"]).reshape(4, 4).T
        else:
            local[:3, :3] = Rotation.from_quat(
                node.get("rotation", [0, 0, 0, 1])
            ).as_matrix() @ np.diag(node.get("scale", [1, 1, 1]))
            local[:3, 3] = node.get("translation", [0, 0, 0])
        world = parent @ local
        if "mesh" in node:
            for prim in doc["meshes"][node["mesh"]]["primitives"]:
                attrs = prim["attributes"]
                xyz = accessor(attrs["POSITION"]) @ world[:3, :3].T + world[:3, 3]
                normals = accessor(attrs["NORMAL"]) @ np.linalg.inv(world[:3, :3])
                normals /= np.maximum(
                    np.linalg.norm(normals, axis=1, keepdims=True), 1e-9
                )
                uv = (
                    accessor(attrs["TEXCOORD_0"])
                    if "TEXCOORD_0" in attrs
                    else np.zeros((len(xyz), 2))
                )
                faces = (
                    accessor(prim["indices"]).reshape(-1, 3)
                    if "indices" in prim
                    else np.arange(len(xyz)).reshape(-1, 3)
                )
                mat = doc["materials"][prim["material"]] if "material" in prim else {"name": "default"}
                pbr = mat.get("pbrMetallicRoughness", {})

                def texture(info, default):
                    if info is None:
                        return np.array([[default]], np.uint8)
                    source = doc["textures"][info["index"]]["source"]
                    data = buffer(doc["images"][source]["bufferView"])
                    return np.array(Image.open(io.BytesIO(data)).convert("RGBA"))

                tex = texture(pbr.get("baseColorTexture"), [255, 255, 255, 255])
                normal_info = mat.get("normalTexture")
                normal_map = texture(normal_info, [128, 128, 255, 255])
                tangent = np.zeros_like(normals)
                bitangent = np.zeros_like(normals)
                normal_uv = uv
                if normal_info and "TANGENT" in attrs:
                    raw_tangent = accessor(attrs["TANGENT"])
                    tangent = raw_tangent[:, :3] @ world[:3, :3].T
                    tangent /= np.maximum(
                        np.linalg.norm(tangent, axis=1, keepdims=True), 1e-9
                    )
                    bitangent = (
                        np.cross(normals, tangent)
                        * raw_tangent[:, 3:]
                        * np.sign(np.linalg.det(world[:3, :3]))
                    )
                    normal_uv = accessor(
                        attrs["TEXCOORD_" + str(normal_info.get("texCoord", 0))]
                    )
                    tangent *= normal_info.get("scale", 1)
                    bitangent *= normal_info.get("scale", 1)
                base = np.array(pbr.get("baseColorFactor", [1, 1, 1, 1]), dtype=float)
                colors = np.ones((len(xyz), 3), np.float32)
                if "COLOR_0" in attrs:
                    color = accessor(attrs["COLOR_0"])[:, :3]
                    maximum = (
                        np.iinfo(color.dtype).max if color.dtype.kind == "u" else 1
                    )
                    colors = (color.astype(np.float32) / maximum) ** (1 / 2.2)
                parts.append(
                    (
                        xyz,
                        normals,
                        tangent,
                        bitangent,
                        normal_uv,
                        normal_map,
                        uv,
                        faces.astype(np.int32),
                        tex,
                        base,
                        colors,
                        mat["name"],
                    )
                )
        for child in node.get("children", []):
            visit(child, world)

    for root in doc["scenes"][doc.get("scene", 0)]["nodes"]:
        if skip_presentation_pose:
            for child in doc["nodes"][root]["children"]:
                visit(child, np.eye(4))
        else:
            visit(root, np.eye(4))
    return sorted(parts, key=lambda part: part[-3][3] < 1)


def oriented(parts, axes, scale_fraction=1):
    def axis(name):
        return np.eye(3)["xyz".index(name[-1])] * (-1 if name.startswith("-") else 1)

    front, up = (axis(name) for name in axes)
    basis = np.array([np.cross(up, front), up, front]).T
    raw = np.concatenate([p[0] for p in parts]) @ basis
    extent = np.ptp(raw, axis=0)
    center = (raw.max(0) + raw.min(0)) / 2
    # A rotating cuboid's horizontal and vertical extents stay inside the sprite.
    span = math.hypot(extent[0], extent[2])
    scale = scale_fraction * min(
        WIDTH * 0.95 / span,
        HEIGHT * 0.95 / (extent[1] * math.cos(PITCH) + span * math.sin(PITCH)),
    )
    return [
        ((p @ basis - center) * scale, n @ basis, t @ basis, bt @ basis, *rest)
        for p, n, t, bt, *rest in parts
    ], extent * scale


@njit(cache=True)
def raster(
    image,
    mask,
    uv_map,
    slot,
    depth,
    points,
    pos,
    normals,
    uv,
    faces,
    tex,
    base,
    colors,
    light,
    normal_uv,
    normal_map,
    tangent_light,
    bitangent_light,
    label_mat,
    wrap_label,
    spine_left,
    spine_right,
    clear_hub,
    region,
    extent,
):
    for a, b, c in faces:
        ax, ay, az = points[a]
        bx, by, bz = points[b]
        cx, cy, cz = points[c]
        d = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
        if abs(d) < 1e-8:
            continue
        for y in range(
            max(0, int(min(ay, by, cy))),
            min(image.shape[0], math.ceil(max(ay, by, cy))),
        ):
            for x in range(
                max(0, int(min(ax, bx, cx))),
                min(image.shape[1], math.ceil(max(ax, bx, cx))),
            ):
                wa = ((by - cy) * (x + 0.5 - cx) + (cx - bx) * (y + 0.5 - cy)) / d
                wb = ((cy - ay) * (x + 0.5 - cx) + (ax - cx) * (y + 0.5 - cy)) / d
                wc = 1 - wa - wb
                if min(wa, wb, wc) < 0:
                    continue
                z = wa * az + wb * bz + wc * cz
                if z < depth[y, x]:
                    continue
                u = (wa * uv[a, 0] + wb * uv[b, 0] + wc * uv[c, 0]) % 1
                v = (wa * uv[a, 1] + wb * uv[b, 1] + wc * uv[c, 1]) % 1
                col = tex[int(v * tex.shape[0]), int(u * tex.shape[1])]
                hub = clear_hub and col[3] < 20
                if col[3] < 20 and not hub:
                    continue
                nx = wa * normals[a, 0] + wb * normals[b, 0] + wc * normals[c, 0]
                ny = wa * normals[a, 1] + wb * normals[b, 1] + wc * normals[c, 1]
                nz = wa * normals[a, 2] + wb * normals[b, 2] + wc * normals[c, 2]
                diffuse = nx * light[0] + ny * light[1] + nz * light[2]
                if normal_map.shape[0] > 1:
                    nu = (
                        wa * normal_uv[a, 0]
                        + wb * normal_uv[b, 0]
                        + wc * normal_uv[c, 0]
                    ) % 1
                    nv = (
                        wa * normal_uv[a, 1]
                        + wb * normal_uv[b, 1]
                        + wc * normal_uv[c, 1]
                    ) % 1
                    vector = normal_map[
                        int(nv * normal_map.shape[0]), int(nu * normal_map.shape[1])
                    ]
                    tx = (
                        wa * tangent_light[a]
                        + wb * tangent_light[b]
                        + wc * tangent_light[c]
                    )
                    ty = (
                        wa * bitangent_light[a]
                        + wb * bitangent_light[b]
                        + wc * bitangent_light[c]
                    )
                    diffuse = (
                        (vector[0] / 127.5 - 1) * tx
                        + (vector[1] / 127.5 - 1) * ty
                        + (vector[2] / 127.5 - 1) * diffuse
                    )
                brightness = 0.59 + 0.59 * max(0.0, diffuse)
                alpha = col[3] / 255.0 * base[3]
                behind = image[y, x, 3] / 255.0 * (1 - alpha)
                for i in range(3):
                    color = wa * colors[a, i] + wb * colors[b, i] + wc * colors[c, i]
                    source = min(
                        255.0, col[i] * base[i] ** 0.4545 * color * brightness + 4
                    )
                    image[y, x, i] = (source * alpha + image[y, x, i] * behind) / max(
                        1e-6, alpha + behind
                    )
                image[y, x, 3] = min(255.0, (alpha + behind) * 255.0)
                depth[y, x] = z
                if uv_map.size:
                    if slot:
                        for axis in range(2):
                            coordinate = wa * uv[a, axis] + wb * uv[b, axis] + wc * uv[c, axis]
                            uv_map[y, x, axis] = round(255 * max(0.0, min(1.0, coordinate)))
                    else:
                        uv_map[y, x, 0] = uv_map[y, x, 1] = 0
                    uv_map[y, x, 2] = slot
                lx = (wa * pos[a, 0] + wb * pos[b, 0] + wc * pos[c, 0]) / extent[
                    0
                ] + 0.5
                ly = (
                    0.5 - (wa * pos[a, 1] + wb * pos[b, 1] + wc * pos[c, 1]) / extent[1]
                )
                ix = int(lx * region.shape[1])
                iy = int(ly * region.shape[0])
                covered = (
                    label_mat
                    and nz > 0.20
                    and 0 <= ix < region.shape[1]
                    and 0 <= iy < region.shape[0]
                    and region[iy, ix]
                )
                covered = covered or (wrap_label and label_mat)
                mask[y, x] = 255 if covered else 0
                if spine_left <= lx <= spine_right and ly < 0.025 and ny > 0.4:
                    # The stock game title wraps across the upper edge on NES and Mega Drive.
                    image[y, x, :3] = int(25 * brightness)
                if hub:
                    # The clear plastic hub extends inward to the GLB's actual spindle hole.
                    image[y, x] = (168, 168, 170, 92)
                elif covered:
                    image[y, x, 0] = image[y, x, 1] = image[y, x, 2] = 64


def cartouche_colors(material, colors, positions, normals, light, view, groove_nm):
    # Cartouche stores groove and rim masks in vertex colors, separate from the material's tint.
    if material == "mirror":
        radial = positions[:, :2] / np.maximum(np.linalg.norm(positions[:, :2], axis=1, keepdims=True), 1e-9)
        tangent = np.column_stack((-radial[:, 1], radial[:, 0]))
        half = (light + view) / max(np.linalg.norm(light + view), 1e-9)
        streak = np.exp(-((tangent @ half[:2]) / 0.12) ** 2)
        wavelength = groove_nm * np.abs(radial @ (light + view)[:2])
        bands = np.zeros_like(colors)
        for order in (1, 2, 3):
            nm = wavelength[:, None] / order
            bands += np.exp(-((nm - np.array([620, 535, 455])) / 39) ** 2)
        recorded = colors[:, :1]
        mirror = 1.06 - 0.32 * recorded
        return mirror + 0.68 * bands * streak[:, None] * (0.35 + 0.65 * recorded)
    if material == "shell" and groove_nm:
        edge = np.maximum(1 - np.abs(normals[:, 2]), colors[:, 1])
        return np.broadcast_to((1 - 0.5 * edge)[:, None], colors.shape)
    return np.ones_like(colors)


def render(
    parts, extent, config, angle, pitch=PITCH, supersample=1, measure_depth=False
):
    yaw = math.radians(angle)
    cy, sy, cp, sp = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch)
    axes = np.array([[cy, 0, -sy], [-sy * sp, cp, -cy * sp], [sy * cp, sp, cy * cp]])
    light = np.array([-0.4, 0.6, 0.69]) @ axes
    w, h = WIDTH * supersample, HEIGHT * supersample
    image = np.zeros((h, w, 4), np.uint8)
    mask = np.zeros((h, w), np.uint8)
    slots = config.get("slots", {})
    uv_map = np.zeros((h, w, 3) if slots else (0, 0, 3), np.uint8)
    depth = np.full((h, w), -np.inf)
    region = Image.new("L", (384, 384))
    draw = ImageDraw.Draw(region)
    if "box" in config:
        box = tuple(round(x * 383) for x in config["box"])
        if "polygon" in config:
            draw.polygon(
                [(round(x * 383), round(y * 383)) for x, y in config["polygon"]],
                fill=255,
            )
        elif config.get("round"):
            draw.ellipse(box, fill=255)
        else:
            draw.rounded_rectangle(
                box, radius=int(config.get("corner", 0.012) * 383), fill=255
            )
        if "hole" in config:
            draw.ellipse(tuple(round(x * 383) for x in config["hole"]), fill=0)
    region = np.asarray(region)
    for (
        pos,
        normals,
        tangent,
        bitangent,
        normal_uv,
        normal_map,
        uv,
        faces,
        tex,
        base,
        colors,
        mat,
    ) in parts:
        if config.get("cartouche"):
            colors = cartouche_colors(mat, colors, pos, normals, light, axes[2], config.get("groove_nm"))
        pts = pos @ axes.T
        pts[:, 0] = w / 2 + pts[:, 0] * supersample
        pts[:, 1] = h / 2 - pts[:, 1] * supersample
        if base[3] < 1:
            faces = faces[np.argsort(pts[faces, 2].mean(axis=1))]
        raster(
            image,
            mask,
            uv_map,
            slots.get(mat, 0),
            depth,
            pts,
            pos,
            normals,
            uv,
            faces,
            tex,
            base,
            colors,
            light,
            normal_uv,
            normal_map,
            tangent @ light,
            bitangent @ light,
            mat in config.get("materials", [mat]),
            config.get("wrap_label", False),
            *config.get("printed_spine", (1.0, 0.0)),
            config.get("clear_hub", False),
            region,
            extent,
        )
    if measure_depth:
        return float(np.median(depth[mask > 0]))
    return Image.fromarray(image), Image.fromarray(uv_map if slots else mask)


def store_mask(mask, path):
    indexed = mask.point(lambda value: 1 if value else 0).convert("P")
    indexed.putpalette([0, 0, 0, 255, 255, 255])
    indexed.save(path, transparency=0, bits=1, optimize=True)


def separate_cassette_stickers(parts, sticker_uvs):
    result = []
    for part in parts:
        uv, faces = part[6:8]
        centers = uv[faces].mean(axis=1)
        keep = np.ones(len(faces), dtype=bool)
        for name, (left, top, right, bottom) in zip(("label", "reverse_label"), sticker_uvs):
            on_sticker = (
                (centers[:, 0] >= left) & (centers[:, 0] <= right)
                & (centers[:, 1] >= top) & (centers[:, 1] <= bottom)
            )
            keep &= ~on_sticker
            if on_sticker.any():
                paper = np.array([[[211, 208, 183, 255]]], dtype=np.uint8)
                result.append((*part[:7], faces[on_sticker], paper, *part[9:-1], name))
        result.append((*part[:7], faces[keep], *part[8:]))
    return result


def prepare_cabinet(parts):
    tones = {"marquee": (41, 42, 47), "screen": (18, 23, 27), "side_art": (36, 37, 42)}
    prepared = []
    for part in parts:
        pos, normals = part[:2]
        material = part[-1]
        if material not in tones:
            prepared.append(part)
            continue
        across = pos[:, 0]
        if material == "side_art":
            # Screen-right points backwards on the cabinet's right and forwards on its left.
            across = np.where(normals[:, 0] > 0, -pos[:, 2], pos[:, 2])
        uv = np.column_stack((
            (across - across.min()) / np.ptp(across),
            (pos[:, 1].max() - pos[:, 1]) / np.ptp(pos[:, 1]),
        ))
        neutral = np.array([[(*tones[material], 255)]], dtype=np.uint8)
        prepared.append((*part[:6], uv, part[7], neutral, *part[9:]))
    return prepared


def bake(path, config):
    parts, extent = oriented(
        read_glb(path, config.get("skip_presentation_pose", False)),
        config["axes"],
        scale_fraction=config.get("diameter_mm", 120) / 120,
    )
    if "sticker_uvs" in config:
        parts = separate_cassette_stickers(parts, config["sticker_uvs"])
    if "shell_color" in config:
        # Remove the original game's printing on both the front and end label.
        for part in parts:
            part[8][:, :, :3] = config["shell_color"]
    if config.get("scan_front"):
        # A smoked shell keeps the board recessed behind the replaceable cartridge face.
        parts = [part for part in parts if part[-1] != "glitter"]
        for part in parts:
            if part[-1] == "body":
                part[-3][:] = [0.075, 0.079, 0.086, 0.92]
    output = OUT / path.stem
    output.mkdir(parents=True, exist_ok=True)
    if config.get("wrap_label"):
        label = next(part[0] for part in parts if part[-1] in config["materials"])
        edge = label[np.isclose(label[:, 0], label[:, 0].min()), 1:]
        profile = sorted(np.unique(edge.round(4), axis=0).tolist(), key=lambda yz: (yz[1], -yz[0] if yz[1] > 0 else yz[0]))
        (output / "wrap.json").write_text(json.dumps([label[:, 0].min(), label[:, 0].max(), profile]) + "\n")
    slots = config.get("slots")
    if slots:
        parts = prepare_cabinet(parts)
    else:
        x, y, r, b = config["box"]
        depth = render(parts, extent, config, 0, pitch=0, measure_depth=True)
        corners = [
            (x - 0.5) * extent[0],
            (0.5 - y) * extent[1],
            (r - x) * extent[0],
            (b - y) * extent[1],
            depth,
        ]
        (output / "label.json").write_text(
            json.dumps([round(v, 5) for v in corners]) + "\n"
        )
    if config.get("scan_front"):
        width, height, thickness = extent
        face = [-width / 2, height / 2, width, height, thickness * 0.47]
        (output / "face.json").write_text(
            json.dumps([round(v, 5) for v in face]) + "\n"
        )
    for group in range(6):
        sheet = Image.new("RGBA", (5 * WIDTH, 2 * HEIGHT))
        masks = Image.new("RGB" if slots else "L", sheet.size)
        for index in range(10):
            angle = (group * 10 + index) * 6
            art, mask = render(parts, extent, config, angle)
            xy = (index % 5 * WIDTH, index // 5 * HEIGHT)
            sheet.paste(art, xy)
            masks.paste(mask, xy)
        sheet.save(output / f"{group}.webp", quality=88, method=3)
        if slots:
            masks.save(output / f"{group}-uv.png", optimize=True)
        else:
            store_mask(masks, output / f"{group}-mask.png")
    art, mask = render(parts, extent, config, -18, supersample=2)
    art.save(output / "front.webp", quality=92)
    if slots:
        mask.save(output / "front-uv.png", optimize=True)
        preview, uv = render(parts, extent, config, -6, supersample=3)
        left, top, right, bottom = preview.getbbox()
        controls = top + round((bottom - top) * 0.58)
        crop = (left, top, right, controls)
        preview.crop(crop).save(output / "carousel.webp", quality=94)
        uv.crop(crop).save(output / "carousel-uv.png", optimize=True)
    else:
        store_mask(mask, output / "front-mask.png")
    print("baked", path.stem, flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_dir", type=Path)
    parser.add_argument("models", nargs="*")
    args = parser.parse_args()
    config = json.loads((HERE / "model_faces.json").read_text())
    for name in args.models or config:
        path = args.source_dir / (name + ".glb")
        if not path.exists() and (HERE / path.name).is_file():
            path = HERE / path.name
        bake(path, config[name])
