extends SceneTree
## Pictures of the room from a few spots, to choose where the player stands (needs a display, e.g. xvfb-run).

func _initialize() -> void:
	var out := "/tmp/claude-0/godot/world"
	var args := OS.get_cmdline_user_args()
	if args.size() > 0:
		out = args[0]
	DirAccess.make_dir_recursive_absolute(out)
	root.size = Vector2i(1280, 720)
	var mesh: Mesh = load("res://assets/world/QCWorld.obj")
	var inst := MeshInstance3D.new()
	inst.mesh = mesh
	root.add_child(inst)
	var env := WorldEnvironment.new()
	var e := Environment.new()
	e.background_mode = Environment.BG_COLOR
	e.background_color = Color("87b4ff")
	e.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	e.ambient_light_color = Color(0.8, 0.8, 0.85)
	env.environment = e
	root.add_child(env)
	var sun := DirectionalLight3D.new()
	sun.rotation_degrees = Vector3(-50, 30, 0)
	root.add_child(sun)
	var cam := Camera3D.new()
	root.add_child(cam)
	cam.current = true
	cam.fov = 80.0
	cam.near = 0.05
	var views := {
		"in_a": [Vector3(-15, 125.6, 5), Vector3(-5, 125.6, 5)],
		"in_b": [Vector3(-15, 125.6, -5), Vector3(-5, 125.6, -5)],
		"in_c": [Vector3(-15, 125.6, 5), Vector3(-15, 125.6, 15)],
		"in_d": [Vector3(-15, 125.6, -5), Vector3(-15, 125.6, -15)],
		"in_e": [Vector3(0, 125.6, 0), Vector3(10, 125.6, 0)],
	}
	for key in views:
		cam.global_position = views[key][0]
		cam.look_at(views[key][1], Vector3.UP)
		for i in range(6):
			await process_frame
		root.get_texture().get_image().save_png("%s/%s.png" % [out, key])
	quit()
