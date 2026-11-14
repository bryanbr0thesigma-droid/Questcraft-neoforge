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
	# Vanilla is not offered (it cannot run on the headset); Horizon is: 26.3 only, on a screen in VR
	ui.show_create()
	await process_frame
	check(not ui.find_children("*", "Button", true, false).any(func(b): return b.text == "Vanilla"), "no Vanilla button")
	for b in ui.find_children("*", "Button", true, false):
		if b.text == "Horizon":
			b.pressed.emit()
	await process_frame
	var horizon_versions: Array = _version_buttons(ui).map(func(b): return (b as Button).text)
	check(horizon_versions == ["26.3"], "Horizon offers 26.3: " + str(horizon_versions))
	var horizon_modes: Array = ui.find_children("*", "Button", true, false).filter(func(b): return b.text in ["VR", "Screen"]).map(func(b): return b.text)
	check(horizon_modes == ["VR", "Screen"], "Horizon offers VR or Screen: " + str(horizon_modes))
	for b in ui.find_children("*", "Button", true, false):
		if b.text == "Create":
			b.pressed.emit()
	await process_frame
	check(bridge._mock_instances.any(func(i): return i["version"] == "26.3" and i["loader"] == "Horizon" and i["vr"] == "VR"), "Create makes a Horizon 26.3 instance in VR")
	# Risugami (ModLoader): alpha to 1.6, its many versions under Release / Beta / Alpha tabs, on a screen in VR
	ui.show_create()
	await process_frame
	for b in ui.find_children("*", "Button", true, false):
		if b.text == "Risugami":
			b.pressed.emit()
	await process_frame
	var tabs: Array = []
	for b in ui.find_children("*", "Button", true, false):
		if b.text in ["Release", "Beta", "Alpha"]:
			tabs.append(b.text)
	check(tabs == ["Release", "Beta", "Alpha"], "ModLoader's versions by era: " + str(tabs))
	var release_versions: Array = _version_buttons(ui).map(func(b): return (b as Button).text)
	check(release_versions.size() > 0 and release_versions.all(func(v): return v.begins_with("1.")), "Release tab: " + str(release_versions))
	check(not _label_texts(ui).any(func(t): return t.begins_with("Vivecraft")), "no Vivecraft for ModLoader")
	check(_label_texts(ui).any(func(t): return t.begins_with("VR")), "ModLoader 1.6.2 runs in QuestCraft's own VR")
	for b in ui.find_children("*", "Button", true, false):
		if b.text == "Beta":
			b.pressed.emit()
	await process_frame
	var beta_versions: Array = _version_buttons(ui).map(func(b): return (b as Button).text)
	check(beta_versions.has("b1.7.3") and beta_versions.all(func(v): return v.begins_with("b")), "Beta tab: " + str(beta_versions))
	for b in _version_buttons(ui):
		if b.text == "b1.7.3":
			b.pressed.emit()
	await process_frame
	for b in ui.find_children("*", "Button", true, false):
		if b.text == "Create":
			b.pressed.emit()
	await process_frame
	check(bridge._mock_instances.any(func(i): return i["version"] == "b1.7.3" and i["loader"] == "Risugami" and i["vr"] == "VR"), "Create makes a Risugami b1.7.3 instance in VR")
	print("CREATE SCREEN OK" if ok else "CREATE SCREEN FAILED")
	quit()


## the buttons of the version grid: the ones named like a Minecraft version (1.x and 26.x, or b1.x and a1.x for beta and alpha)
func _version_buttons(ui: Control) -> Array:
	var version := RegEx.create_from_string("^[ab]?\\d+\\.\\d")
	var out: Array = []
	for g in ui.find_children("*", "GridContainer", true, false):
		for b in g.get_children():
			if version.search((b as Button).text) != null:
				out.append(b)
	return out


func _label_texts(ui: Control) -> Array:
	return ui.find_children("*", "Label", true, false).map(func(l): return (l as Label).text)
