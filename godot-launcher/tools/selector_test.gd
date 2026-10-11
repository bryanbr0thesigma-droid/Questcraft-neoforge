extends SceneTree
## Presses the < and > buttons of the chooser and the loader switch, and checks the value really changes:
##   godot --headless --path godot-launcher --script res://tools/selector_test.gd

func _initialize() -> void:
	var ui: Control = load("res://scripts/ui.gd").new()
	root.add_child(ui)
	await process_frame
	var cfg: Node = root.get_node("Config")
	var ok := true
	var seen := []
	var row: HBoxContainer = ui._selector(["a", "b", "c"], "a", func(v): seen.append(v))
	var left: Button = row.get_child(0)
	var right: Button = row.get_child(2)
	var shown: Label = row.get_child(1)
	var steps := [[right, "b"], [right, "c"], [right, "a"], [left, "c"], [left, "b"]]
	for step in steps:
		step[0].pressed.emit()
		var good: bool = row.get_meta("value") == step[1] and shown.text == step[1]
		print("press -> ", row.get_meta("value"), " label=", shown.text, " expected=", step[1], " ", good)
		ok = ok and good
	# the loader switch on the main screen
	ui.show_home()
	await process_frame
	var before: String = cfg.last_loader
	ui._cycle_loader()
	await process_frame
	var changed: bool = cfg.last_loader != before
	print("loader ", before, " -> ", cfg.last_loader, " ", changed)
	ok = ok and changed
	var cycle := [cfg.last_loader]
	for i in range(3):
		ui._cycle_loader()
		cycle.append(cfg.last_loader)
	print("cycle ", cycle)
	ok = ok and cycle.size() == 4 and cycle[0] == cycle[3] and cycle[0] != cycle[1]
	print("SELECTOR OK" if ok else "SELECTOR FAILED")
	quit()
