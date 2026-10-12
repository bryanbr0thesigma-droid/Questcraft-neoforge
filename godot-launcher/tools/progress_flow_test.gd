extends SceneTree
## Making an instance and then playing it: the progress screen has to move on from "creating" to "preparing" whether
## the creation takes a while or was over before the first poll. The mock backend plays both (mock_create_polls).
##   godot --headless --path godot-launcher --script res://tools/progress_flow_test.gd

func _initialize() -> void:
	var cfg: Node = root.get_node("Config")
	var bridge: Node = root.get_node("Bridge")
	var ui: Control = load("res://scripts/ui.gd").new()
	root.add_child(ui)
	await process_frame
	var ok := true
	for polls in [3, 0]:
		bridge.mock_create_polls = polls
		bridge._mock_phase = "idle"
		bridge._mock_prepared = ""
		cfg.last_loader = "NeoForge"
		cfg.last_is_version = true
		cfg.last_version = "1.21.11"
		ui._play_selected()
		var ticks := 0
		while bridge._mock_prepared == "" and ticks < 40:
			ui._on_poll()
			ticks += 1
		var moved: bool = bridge._mock_prepared != ""
		print("creation takes ", polls, " polls: prepare started after ", ticks, " polls -> ", "ok" if moved else "STUCK")
		ok = ok and moved
		# let it finish so the next round starts clean
		ticks = 0
		while bridge._mock_phase != "ready" and ticks < 40:
			ui._on_poll()
			ticks += 1
		bridge._mock_instances = bridge._mock_instances.filter(func(i): return i["name"] != "1.21.11 NeoForge")
	print("PROGRESS FLOW OK" if ok else "PROGRESS FLOW FAILED")
	quit()
