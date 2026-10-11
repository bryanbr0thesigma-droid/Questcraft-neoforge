extends SceneTree
## Saves a picture of each screen (needs a display, e.g. xvfb-run) to check the layout without a headset:
##   xvfb-run -a godot --rendering-method gl_compatibility --path godot-launcher --script res://tools/screenshots.gd -- /tmp/shots

func _initialize() -> void:
	var out := "/tmp/shots"
	var args := OS.get_cmdline_user_args()
	if args.size() > 0:
		out = args[0]
	DirAccess.make_dir_recursive_absolute(out)
	var window := root
	window.size = Vector2i(1200, 600)
	var ui: Control = load("res://scripts/ui.gd").new()
	ui.size = Vector2(1200, 600)
	root.add_child(ui)
	await _frames(4)
	ui.show_login()
	await _snap(out, "login")
	ui._sign_in("")
	for i in range(8):
		await create_timer(0.6).timeout
	await _snap(out, "device_code")
	for i in range(6):
		await create_timer(0.6).timeout
	await _snap(out, "home")
	ui.show_create()
	await _snap(out, "create")
	ui.show_instances()
	await _snap(out, "instances")
	ui.show_home()
	ui._open_version_menu(ui.page.get_child(ui.page.get_child_count() - 1))
	await _snap(out, "home_menu")
	ui.show_settings()
	await _snap(out, "settings")
	ui.show_mods({"name": "1.21.1", "version": "1.21.1", "loader": "NeoForge"})
	await _snap(out, "mods")
	ui.start_play("1.21.1")
	await create_timer(2.0).timeout
	await _snap(out, "progress")
	quit()


func _frames(n: int) -> void:
	for i in range(n):
		await process_frame


func _snap(out: String, name: String) -> void:
	await _frames(6)
	root.get_texture().get_image().save_png("%s/%s.png" % [out, name])
