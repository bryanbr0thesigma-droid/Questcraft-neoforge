extends SceneTree
## The QuestCraft logo: shown on every fresh start of the app, not when the app was started again by a game that just
## ended, and held over the menu while Minecraft boots.
##   godot --headless --path godot-launcher --script res://tools/logo_screen_test.gd

func _initialize() -> void:
	var bridge: Node = root.get_node("Bridge")
	var ok := true

	bridge.mock_returned_from_game = false
	var fresh: Control = load("res://scripts/ui.gd").new()
	root.add_child(fresh)
	await process_frame
	var shown: bool = fresh.logo_visible()
	print("fresh start: logo ", "shown" if shown else "MISSING")
	ok = ok and shown
	await create_timer(5.0).timeout   # 0.35 in, 2.4 held, 0.6 out, and a slow machine
	var gone: bool = not fresh.logo_visible()
	print("fresh start: logo gone after its time -> ", "ok" if gone else "STUCK")
	ok = ok and gone
	fresh.queue_free()

	bridge.mock_returned_from_game = true
	var back: Control = load("res://scripts/ui.gd").new()
	root.add_child(back)
	await process_frame
	var skipped: bool = not back.logo_visible()
	print("restart after a game: logo ", "skipped" if skipped else "SHOWN")
	ok = ok and skipped

	# playing: the logo comes up when the game is about to start and stays until the menu is back
	var started := {"n": 0}
	back.request_start_game.connect(func(): started["n"] += 1)
	bridge._mock_phase = "ready"
	back.show_progress("Preparing", back.show_home)
	back._on_poll()
	var held: bool = back.logo_visible() and started["n"] == 1
	print("game starting: logo ", "held" if held else "MISSING")
	ok = ok and held
	await create_timer(0.6).timeout
	held = back.logo_visible()
	print("still held after the fade in: ", "ok" if held else "GONE")
	ok = ok and held
	back.hide_logo()
	ok = ok and not back.logo_visible()

	print("LOGO SCREEN OK" if ok else "LOGO SCREEN FAILED")
	quit()
