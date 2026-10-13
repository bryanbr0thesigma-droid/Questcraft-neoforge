extends SceneTree
## The New instance screen shows every version of the loader at once and takes the one that is pressed.
##   godot --headless --path godot-launcher --script res://tools/create_screen_test.gd

var ok := true


func check(cond: bool, what: String) -> void:
	print(("ok      " if cond else "FAILED  ") + what)
	ok = ok and cond


func _initialize() -> void:
	var bridge: Node = root.get_node("Bridge")
	var cfg: Node = root.get_node("Config")
	var ui: Control = load("res://scripts/ui.gd").new()
	root.add_child(ui)
	await process_frame
	ui.hide_logo()
	for loader in ["Fabric", "NeoForge", "Quilt", "Forge"]:
		cfg.last_loader = loader
		ui.show_create()
		await process_frame
		await process_frame
		var buttons := _version_buttons(ui)
		var expected: Array = bridge.api("supportedVersions", [loader])
		var shown: Array = buttons.map(func(b): return b.text)
		check(shown == expected, "%s: every version is a button (%d of %d), 1.21.1 among them: %s" % [loader, shown.size(), expected.size(), str(shown.has("1.21.1"))])
		var target: Button = null
		for b in buttons:
			if b.text == "1.21.1":
				target = b
		check(target != null, loader + ": 1.21.1 is on the screen")
		target.pressed.emit()
		await process_frame
		check(target.disabled, loader + ": the chosen one is greyed")
		var name_edit: LineEdit = ui.find_children("*", "LineEdit", true, false)[0]
		check(name_edit.text == "1.21.1", loader + ": the name follows: " + name_edit.text)
	# creating hands the chosen loader and version to Pojlib
	var created: Array = []
	bridge.api("modLoaders")
	ui.show_create()
	await process_frame
	var forge_btn: Button = null
	for b in ui.find_children("*", "Button", true, false):
		if b.text == "Forge":
			forge_btn = b
	forge_btn.pressed.emit()
	await process_frame
	for b in _version_buttons(ui):
		if b.text == "1.21.1":
			b.pressed.emit()
	await process_frame
	for b in ui.find_children("*", "Button", true, false):
		if b.text == "Create":
			b.pressed.emit()
	await process_frame
	check(bridge._mock_instances.any(func(i): return i["version"] == "1.21.1" and i["loader"] == "Forge"), "Create makes a Forge 1.21.1 instance")
	# the VR mod: Visor where there is a build of it
	ui.show_create()
	await process_frame
	for b in ui.find_children("*", "Button", true, false):
		if b.text == "Forge":
			b.pressed.emit()
	await process_frame
	for b in _version_buttons(ui):
		if b.text == "1.21.1":
			b.pressed.emit()
	await process_frame
	var visor: Button = null
	for b in ui.find_children("*", "Button", true, false):
		if b.text == "Visor":
			visor = b
	check(visor != null, "Visor is offered for Forge 1.21.1")
	visor.pressed.emit()
	await process_frame
	check(visor.disabled, "the chosen VR mod is greyed")
	for b in ui.find_children("*", "Button", true, false):
		if b.text == "Create":
			b.pressed.emit()
	await process_frame
	check(bridge._mock_instances.any(func(i): return i["version"] == "1.21.1" and i["loader"] == "Forge" and i["vr"] == "Visor"), "Create hands the Visor choice on")
	ui.show_create()
	await process_frame
	for b in ui.find_children("*", "Button", true, false):
		if b.text == "Forge":
			b.pressed.emit()
	await process_frame
	for b in _version_buttons(ui):
		if b.text == "1.20.4":
			b.pressed.emit()
	await process_frame
	var no_visor := true
	for b in ui.find_children("*", "Button", true, false):
		if b.text == "Visor":
			no_visor = false
	check(no_visor, "no Visor button where there is no Visor build (Forge 1.20.4)")
	print("CREATE SCREEN OK" if ok else "CREATE SCREEN FAILED")
	quit()


## the buttons of the version grid: the ones named like a Minecraft version
func _version_buttons(ui: Control) -> Array:
	var out: Array = []
	for g in ui.find_children("*", "GridContainer", true, false):
		for b in g.get_children():
			if (b as Button).text.begins_with("1."):
				out.append(b)
	return out
