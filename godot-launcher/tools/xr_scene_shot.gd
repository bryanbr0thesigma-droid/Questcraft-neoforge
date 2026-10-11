extends SceneTree
## Builds the VR scene without a headset (head fixed 1.6 m above the floor) and takes a picture from the player's eyes:
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
	main.in_xr = true
	# without a headset the XR camera sits at the floor, so put the head where eyes are and place the panel from there
	var head: Transform3D = main.origin.global_transform
	head.origin += Vector3(0, 1.6, 0)
	main.camera.global_transform = head
	main._place_panel()
	main.camera.queue_free()   # the tracked camera has no headset to follow here
	print("forward=", -head.basis.z, " panel=", main.panel.global_position, " head=", head.origin)
	var eye := Camera3D.new()
	main.add_child(eye)
	eye.fov = 90
	eye.current = true
	eye.look_at_from_position(head.origin, head.origin + (-head.basis.z) * 5.0, Vector3.UP)
	for i in range(8):
		await process_frame
	root.get_texture().get_image().save_png("/tmp/claude-0/godot/xr_scene.png")
	quit()
