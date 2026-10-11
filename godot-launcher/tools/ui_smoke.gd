extends SceneTree
## Walks through every screen with the mock bridge (desktop, headless) and fails on script errors:
##   godot --headless --path godot-launcher --script res://tools/ui_smoke.gd

func _initialize() -> void:
	var ui_script = load("res://scripts/ui.gd")
	var ui: Control = ui_script.new()
	root.add_child(ui)
	await _frames(3)
	ui.show_login()
	await _frames(2)
	ui._sign_in("")
	for i in range(10):
		await create_timer(0.6).timeout   # mock: logs in after a few polls
	ui.show_home()
	await _frames(2)
	ui.show_create()
	await _frames(2)
	ui.show_settings()
	await _frames(2)
	ui.show_mods({"name": "1.21.1", "version": "1.21.1", "loader": "NeoForge"})
	await _frames(2)
	ui.start_play("1.21.1")
	for i in range(14):
		await create_timer(0.6).timeout
	print("SMOKE OK")
	quit()


func _frames(n: int) -> void:
	for i in range(n):
		await process_frame
