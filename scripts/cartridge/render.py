"""Bake the GBA cartridge sprite sheets. Requires NumPy, Pillow and Numba."""
import io
import json
import math
import struct
from pathlib import Path

import numpy as np
from numba import njit
from PIL import Image

HERE = Path(__file__).resolve().parent
OUT = HERE.parents[1] / "app/src/main/res/drawable-nodpi"
WIDTH, HEIGHT = 420, 272
FRAMES = 60
PITCH = math.radians(9)
SCALE = .14
CENTER = np.array([0, 2.0798, .30553])


def read_parts():
    glb = (HERE / "gba.glb").read_bytes()
    size = struct.unpack_from("<I", glb, 12)[0]
    scene = json.loads(glb[20:20 + size])
    binary = glb[28 + size:]

    def buffer(index):
        view = scene["bufferViews"][index]
        offset = view.get("byteOffset", 0)
        return binary[offset:offset + view["byteLength"]]

    def accessor(index):
        desc = scene["accessors"][index]
        dtype = {5121: "u1", 5123: "<u2", 5125: "<u4", 5126: "<f4"}[desc["componentType"]]
        components = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4}[desc["type"]]
        return np.frombuffer(buffer(desc["bufferView"]), dtype, count=desc["count"] * components,
                             offset=desc.get("byteOffset", 0))

    primitive, = scene["meshes"][0]["primitives"]
    attrs = primitive["attributes"]
    material = scene["materials"][primitive["material"]]

    def texture(info):
        source = scene["textures"][info["index"]]["source"]
        return np.array(Image.open(io.BytesIO(buffer(scene["images"][source]["bufferView"]))).convert("RGB"))

    albedo = texture(material["pbrMetallicRoughness"]["baseColorTexture"])
    normal_map = texture(material["normalTexture"])
    # The original FireRed sticker shares the front shell's texture atlas.
    sticker = np.zeros(albedo.shape[:2], dtype=np.bool_)
    for y in range(410, 645):
        row = albedo[y, 106:539].astype(int)
        ink = np.flatnonzero(np.ptp(row, axis=1) > 40)
        if ink.size:
            sticker[y, 105 + ink[0]:108 + ink[-1]] = True

    return (
        accessor(attrs["POSITION"]).reshape(-1, 3) - CENTER,
        accessor(attrs["NORMAL"]).reshape(-1, 3).astype(float),
        accessor(attrs["TANGENT"]).reshape(-1, 4).astype(float),
        accessor(attrs["TEXCOORD_0"]).reshape(-1, 2).astype(float),
        accessor(primitive["indices"]).reshape(-1, 3).astype(int),
        albedo, normal_map, sticker
    )


@njit
def raster(image, depths, points, normals, tangents, uvs, faces, texture, normal_map, sticker, light, view):
    half = (light + view) / np.linalg.norm(light + view)
    for a, b, c in faces:
        ax, ay, az = points[a]
        bx, by, bz = points[b]
        cx, cy, cz = points[c]
        det = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
        if det >= -1e-8:
            continue
        for y in range(max(0, int(min(ay, by, cy))), min(image.shape[0], math.ceil(max(ay, by, cy)))):
            for x in range(max(0, int(min(ax, bx, cx))), min(image.shape[1], math.ceil(max(ax, bx, cx)))):
                w = ((by - cy) * (x + .5 - cx) + (cx - bx) * (y + .5 - cy)) / det
                v = ((cy - ay) * (x + .5 - cx) + (ax - cx) * (y + .5 - cy)) / det
                t = 1 - w - v
                if min(w, v, t) < -1e-8:
                    continue
                depth = w * az + v * bz + t * cz
                if depth < depths[y, x]:
                    continue
                uv = w * uvs[a] + v * uvs[b] + t * uvs[c]
                tx = min(texture.shape[1]-1, max(0, int(uv[0]*texture.shape[1])))
                ty = min(texture.shape[0]-1, max(0, int(uv[1]*texture.shape[0])))
                depths[y, x] = depth
                if sticker[ty, tx]:
                    image[y, x] = 0
                    continue
                color = (texture[ty, tx] / 255.) ** 2.2
                n = w * normals[a] + v * normals[b] + t * normals[c]
                n /= max(np.linalg.norm(n), 1e-8)
                tangent = w * tangents[a] + v * tangents[b] + t * tangents[c]
                axis = tangent[:3] - n * (tangent[:3] @ n)
                axis /= max(np.linalg.norm(axis), 1e-8)
                mapped = normal_map[ty, tx] / 127.5 - 1.
                n = mapped[0] * axis + mapped[1] * np.cross(n, axis) * tangent[3] + mapped[2] * n
                n /= max(np.linalg.norm(n), 1e-8)
                diffuse = .45 + .96 * max(0., n @ light)
                spec = .072 * max(0., n @ half)**24
                for channel in range(3):
                    image[y, x, channel] = min(255, 255 * (color[channel] * diffuse + spec)**(1/2.2))
                image[y, x, 3] = 255


def render(parts, frame, width, height):
    yaw = math.radians(frame * 360 / FRAMES)
    sy, cy = math.sin(yaw), math.cos(yaw)
    sp, cp = math.sin(PITCH), math.cos(PITCH)
    right = np.array([cy, 0, -sy])
    up = np.array([-sy * sp, cp, -cy * sp])
    view = np.array([sy * cp, sp, cy * cp])
    light = .78 * up - .45 * right + .51 * view
    light /= np.linalg.norm(light)
    width, height = width * 2, height * 2
    image = np.zeros((height, width, 4), np.uint8)
    depths = np.full((height, width), -np.inf)
    pos, *part = parts
    points = np.stack((width * (.5 + SCALE * (pos @ right)), height / 2 - width * SCALE * (pos @ up), pos @ view), axis=1)
    raster(image, depths, points, *part, light, view)
    return Image.fromarray(image).resize((width // 2, height // 2), Image.Resampling.LANCZOS)


if __name__ == "__main__":
    parts = read_parts()
    OUT.mkdir(parents=True, exist_ok=True)
    render(parts, -3, WIDTH * 2, HEIGHT * 2).save(OUT / "gba_cartridge_front.webp", lossless=True)
    for group in range(6):
        sheet = Image.new("RGBA", (5 * WIDTH, 2 * HEIGHT))
        for index in range(10):
            frame = render(parts, group * 10 + index, WIDTH, HEIGHT)
            sheet.paste(frame, (index % 5 * WIDTH, index // 5 * HEIGHT))
        sheet.save(OUT / f"gba_cartridge_angles_{group}.webp", lossless=True)
