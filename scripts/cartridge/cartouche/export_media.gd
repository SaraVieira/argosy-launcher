extends SceneTree
## Exports every Cartouche medium (cartridge / disc) scene to a standalone GLB.
## Custom Godot shaders are mapped to plain glTF PBR materials. The per-game artwork
## surface is always the material named "label" so a client can swap its texture.
## Units: Cartouche shapes are 1 unit = 100 mm; output is metres (x0.1).

const MEDIA_DIR := "res://carousel/media"
var out_dir := ""


func _initialize() -> void:
	var args := OS.get_cmdline_user_args()
	out_dir = args[0] if args.size() > 0 else "/tmp/glb"
	DirAccess.make_dir_recursive_absolute(out_dir)
	var report: Array[Dictionary] = []
	for medium: String in DirAccess.get_directories_at(MEDIA_DIR):
		var path := "%s/%s/%s.tscn" % [MEDIA_DIR, medium, medium]
		if not ResourceLoader.exists(path):
			push_warning("no scene for " + medium)
			continue
		var packed: PackedScene = load(path)
		report.append(_export(medium, packed))
	var f := FileAccess.open(out_dir + "/report.json", FileAccess.WRITE)
	f.store_string(JSON.stringify(report, "  "))
	quit()


func _export(medium: String, packed: PackedScene) -> Dictionary:
	var src: Node = packed.instantiate()
	var top := Node3D.new()
	top.name = medium
	var bounds := AABB()
	var first := true
	var tris := 0
	var parts: Array[String] = []
	for mi: MeshInstance3D in _mesh_instances(src):
		var mesh := _to_array_mesh(mi.mesh)
		if mesh == null or mesh.get_surface_count() == 0:
			push_warning("%s/%s: empty mesh" % [medium, mi.name])
			continue
		var node := MeshInstance3D.new()
		var part := String(mi.name).to_snake_case()
		node.name = part
		node.mesh = mesh
		node.transform = Transform3D(Basis().scaled(Vector3.ONE * 0.1), Vector3.ZERO) * _relative_xf(mi, src)
		var mat := _material(mi, part)
		for s in mesh.get_surface_count():
			mesh.surface_set_material(s, mat)
			var arrays := mesh.surface_get_arrays(s)
			var verts: PackedVector3Array = arrays[Mesh.ARRAY_VERTEX]
			if arrays[Mesh.ARRAY_INDEX] != null:
				var idx: PackedInt32Array = arrays[Mesh.ARRAY_INDEX]
				tris += floori(idx.size() / 3.0)
			else:
				tris += floori(verts.size() / 3.0)
		parts.append(part)
		top.add_child(node)
		node.owner = top
		var b: AABB = node.transform * mesh.get_aabb()
		bounds = b if first else bounds.merge(b)
		first = false
	var doc := GLTFDocument.new()
	var state := GLTFState.new()
	var err := doc.append_from_scene(top, state)
	if err == OK:
		err = doc.write_to_filesystem(state, "%s/%s.glb" % [out_dir, medium])
	src.free()
	top.free()
	var mm := bounds.size * 1000.0
	print("%-14s err=%d tris=%d size_mm=(%.1f, %.1f, %.1f) parts=%s" % [medium, err, tris, mm.x, mm.y, mm.z, parts])
	return {"name": medium, "ok": err == OK, "triangles": tris,
		"size_mm": [snappedf(mm.x, 0.1), snappedf(mm.y, 0.1), snappedf(mm.z, 0.1)], "parts": parts}


func _mesh_instances(n: Node) -> Array[MeshInstance3D]:
	var out: Array[MeshInstance3D] = []
	if n is MeshInstance3D and (n as MeshInstance3D).visible:
		out.append(n as MeshInstance3D)
	for c in n.get_children():
		out.append_array(_mesh_instances(c))
	return out


func _relative_xf(n: Node3D, stop: Node) -> Transform3D:
	var xf := Transform3D.IDENTITY
	var cur: Node = n
	while cur != stop and cur is Node3D:
		xf = (cur as Node3D).transform * xf
		cur = cur.get_parent()
	return xf


func _to_array_mesh(m: Mesh) -> ArrayMesh:
	if m == null:
		return null
	var out := ArrayMesh.new()
	for s in m.get_surface_count():
		var arrays := m.surface_get_arrays(s)
		if arrays.is_empty() or arrays[Mesh.ARRAY_VERTEX] == null:
			continue
		out.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays)
	return out


func _p(sm: ShaderMaterial, key: String, fallback: Variant) -> Variant:
	var v: Variant = sm.get_shader_parameter(key)
	return fallback if v == null else v


func _f(sm: ShaderMaterial, key: String, fallback: float) -> float:
	var v: Variant = sm.get_shader_parameter(key)
	if v is float or v is int:
		var x: float = v
		return x
	return fallback


func _rgb(v: Variant, alpha: float = 1.0) -> Color:
	if v is Color:
		var c: Color = v
		return Color(c.r, c.g, c.b, alpha)
	if v is Vector3:
		var w: Vector3 = v
		return Color(w.x, w.y, w.z, alpha)
	return Color(1, 1, 1, alpha)


func _material(mi: MeshInstance3D, part: String) -> StandardMaterial3D:
	var src: Material = mi.get_surface_override_material(0)
	if src == null and mi.mesh:
		src = mi.mesh.surface_get_material(0)
	var m := StandardMaterial3D.new()
	m.resource_name = part
	if not (src is ShaderMaterial):
		return m
	var sm := src as ShaderMaterial
	var shader := sm.shader.resource_path.get_file()
	if shader.begins_with("solid"):
		m.albedo_color = _rgb(_p(sm, "albedo", Color.WHITE))
		m.roughness = _f(sm, "roughness", 1.0)
		m.metallic = _f(sm, "metallic", 0.0)
		var cc := _f(sm, "clearcoat", 0.0)
		if cc > 0.0:
			m.clearcoat_enabled = true
			m.clearcoat = cc
			m.clearcoat_roughness = _f(sm, "clearcoat_roughness", 0.5)
	elif shader.begins_with("clear_plastic"):
		var a := _f(sm, "opacity", 0.6)
		m.albedo_color = _rgb(_p(sm, "tint", Color(0.5, 0.51, 0.54)), a)
		if a < 0.999:
			m.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
		m.roughness = _f(sm, "roughness", 0.08)
	elif shader.begins_with("disc_mirror"):
		m.albedo_color = _rgb(_p(sm, "mirror_color", Color(0.5, 0.51, 0.54)))
		m.metallic = 1.0
		m.roughness = _f(sm, "roughness", 0.2)
	elif shader.begins_with("label"):
		var tex: Variant = sm.get_shader_parameter("label_texture")
		if sm.get_shader_parameter("has_label") == true and tex is Texture2D:
			var t2d: Texture2D = tex
			m.albedo_texture = t2d
			m.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA_SCISSOR
		else:
			m.albedo_color = _rgb(_p(sm, "blank_color", Color(0.92, 0.92, 0.92)))
		m.roughness = _f(sm, "roughness", 0.4)
		m.metallic = _f(sm, "silver", 0.0)
	return m
