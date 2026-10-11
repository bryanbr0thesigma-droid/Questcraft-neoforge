extends SceneTree
## Checks the recenter maths: whatever the headset's start pose, afterwards the head is at the spawn point looking +Z.
##   godot --headless --path godot-launcher --script res://tools/recenter_test.gd

func _initialize() -> void:
	var main: Node3D = load("res://scripts/main.gd").new()
	root.add_child(main)
	await process_frame
	main._build_xr_scene()
	var ok := true
	for case in [[0.0, Vector3(0.3, 1.6, 0.2)], [1.0, Vector3(-0.7, 1.2, 0.4)], [-2.5, Vector3(0, 1.7, 0)], [PI, Vector3(1, 1.5, -1)]]:
		main.camera.transform = Transform3D(Basis(Vector3.UP, case[0]) * Basis(Vector3.RIGHT, -0.2), case[1])
		main._recenter()
		var g: Transform3D = main.camera.global_transform
		var fwd := -g.basis.z
		fwd.y = 0.0
		fwd = fwd.normalized()
		var pos_ok: bool = absf(g.origin.x - main.SPAWN_POSITION.x) < 0.001 and absf(g.origin.z - main.SPAWN_POSITION.z) < 0.001
		var dir_ok: bool = fwd.distance_to(Vector3(0, 0, 1)) < 0.001
		if not (pos_ok and dir_ok):
			ok = false
		print(case[0], " pos_ok=", pos_ok, " dir_ok=", dir_ok, " head=", g.origin, " floor_y=", main.origin.global_position.y)
	print("RECENTER OK" if ok else "RECENTER FAILED")
	quit()
