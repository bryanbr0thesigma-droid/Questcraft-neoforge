extends SceneTree
## Builds the VR scene without a headset and takes a picture from where the player's eyes are:
##   xvfb-run -a godot --rendering-method gl_compatibility --path godot-launcher --script res://tools/xr_scene_shot.gd

func _initialize() -> void:
	root.size = Vector2i(1280, 720)
	var main: Node3D = load("res://scripts/main.gd").new()
	root.add_child(main)
	await process_frame
	for c in main.get_children():
		if c is CanvasLayer:
			c.queue_free()   # the flat desktop UI, not part of the VR scene
	main._build_xr_scene()
	main.camera.current = false
	var eye := Camera3D.new()
	main.add_child(eye)
	var head: Transform3D = main.origin.global_transform
	eye.fov = 100
	eye.current = true
	eye.look_at_from_position(head.origin + Vector3(0, 1.6, 0), head.origin + Vector3(0, 1.6, 0) + (-head.basis.z) * 5.0, Vector3.UP)
	for i in range(12):
		await process_frame
	root.get_texture().get_image().save_png("/tmp/claude-0/godot/xr_scene.png")
	main.viewport.get_texture().get_image().save_png("/tmp/claude-0/godot/xr_ui.png")
	quit()
