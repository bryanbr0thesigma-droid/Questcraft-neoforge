extends SceneTree
## The mod menu with canned Modrinth answers (the mock bridge and Modrinth.mock): results, paging, tabs, adding, the
## installed list, modpacks, and the keyboard's symbols.
##   godot --headless --path godot-launcher --script res://tools/mod_menu_test.gd

var ok := true


func check(cond: bool, what: String) -> void:
	print(("ok      " if cond else "FAILED  ") + what)
	ok = ok and cond


func _initialize() -> void:
	var bridge: Node = root.get_node("Bridge")
	var ui: Control = load("res://scripts/ui.gd").new()
	root.add_child(ui)
	await process_frame
	ui.modrinth.mock = true
	ui.hide_logo()

	var inst := {"name": "Test", "version": "1.21.1", "loader": "NeoForge"}
	ui.show_mods(inst)
	await _frames(4)
	var menu: Control = _find_menu(ui)
	check(menu != null, "the mod menu opens")
	check(menu.count.text == "Found 40 Results", "it says how many results there are: " + menu.count.text)
	var rows := _rows(menu)
	check(rows == 12, "the first page has 12 results (%d), without anything typed" % rows)
	check(menu.results.get_child(0).find_children("*", "Label", true, false).any(func(l): return l.text == "Fabric API"), "the most popular comes first")
	check(menu.more_button != null, "a More button follows the page")

	menu.search(false)
	await _frames(4)
	check(_rows(menu) == 24, "More adds the next page (%d)" % _rows(menu))

	# adding
	var add: Button = _first_button(menu.results.get_child(0), "Add")
	check(add != null, "a result has an Add button")
	add.pressed.emit()
	await _frames(4)
	check(add.text == "Installed" and add.disabled, "after Add the row says Installed")
	check(bridge.api_bool("hasMod", ["Test", "fabric-api"]), "the mod is on the instance")
	check(menu.installed_button.text == "Installed (2)", "the Installed button counts it: " + menu.installed_button.text)

	menu.installed_button.pressed.emit()
	await _frames(3)
	check(menu.installed_view and menu.count.text == "2 installed", "the installed list shows 2: " + menu.count.text)
	var remove: Button = _first_button(menu.results.get_child(1), "Remove")
	remove.pressed.emit()
	await _frames(2)
	check(not bridge.api_bool("hasMod", ["Test", "fabric-api"]), "Remove takes it off the instance")
	menu.installed_button.pressed.emit()
	await _frames(4)
	check(not menu.installed_view and _rows(menu) == 12, "Search goes back to the results")

	# searching and the sort order
	menu.query.text = "sodium"
	menu.query.text_submitted.emit("sodium")
	await _frames(4)
	check(_rows(menu) == 1 and menu.count.text == "Found 1 Results", "a search finds just Sodium: " + menu.count.text)
	menu.sort_button.pressed.emit()
	check(menu.sort_button.text == "Sort: Relevance", "the sort button changes it: " + menu.sort_button.text)
	check(ui.modrinth.SORTS[0][0] == "downloads", "most popular is the default sort")
	menu.query.text = ""

	# tabs
	menu.select_tab("shader")
	await _frames(3)
	check((menu.tab_buttons["shader"] as Button).disabled and not (menu.tab_buttons["mod"] as Button).disabled, "the open tab is greyed")
	check(menu.version == "1.21.1" and (menu.version_buttons[0] as Button).disabled, "shaders are for the instance's version, the picker is locked")
	menu.select_tab("modpack")
	await _frames(3)
	check(not (menu.version_buttons[0] as Button).disabled, "modpacks can be searched for another version")
	menu._step_version(1)
	await _frames(3)
	check(menu.version != "1.21.1", "the version picker moves: " + menu.version)
	menu.version = "1.21.1"
	var install: Button = _first_button(menu.results.get_child(0), "Install")
	check(install != null, "a modpack result has an Install button")
	install.pressed.emit()
	await _frames(6)
	check(bridge._mock_instances.any(func(i): return i["name"] == "Fabric API" and i["loader"] == "NeoForge"), "Install makes an instance of the loader from the pack")

	# names
	var names: Array = ["Cool Pack", "Cool Pack 2"]
	check(load("res://scripts/mod_menu.gd").instance_name_for("Cool Pack", names) == "Cool Pack 3", "taken names get a number")
	check(load("res://scripts/mod_menu.gd").instance_name_for("A/B!", []) == "A B", "/ and ! are not in instance names")
	check(load("res://scripts/mod_menu.gd").instance_name_for("", []) == "Modpack", "an empty name becomes Modpack")

	# pictures of every kind Modrinth has
	var img := Image.create(8, 8, false, Image.FORMAT_RGBA8)
	img.fill(Color.RED)
	var Modrinth = load("res://scripts/modrinth.gd")
	check(Modrinth.decode_image(img.save_png_to_buffer()) != null, "PNG icons decode")
	check(Modrinth.decode_image(img.save_jpg_to_buffer(0.9)) != null, "JPEG icons decode")
	check(Modrinth.decode_image(img.save_webp_to_buffer(true)) != null, "WebP icons decode")
	check(Modrinth.decode_image(PackedByteArray([1, 2, 3])) == null, "garbage does not")

	# the keyboard
	var kb: Control = ui.keyboard
	var edit := LineEdit.new()
	ui.add_child(edit)
	kb.attach(edit)
	var typed := ""
	for layer in ["letters", "symbols"]:
		if kb.layer != layer:
			kb.toggle_layer()
		var keys: String = ""
		for row in kb._rows:
			for b in row.get_children():
				keys += (b as Button).text
		typed += keys
	for ch in "#$@:;!?%&*()[]{}<>|\\'\"`~+=/,.-_":
		check(typed.contains(ch), "the keyboard has " + ch)
	kb.toggle_layer()   # symbols -> letters
	kb.toggle_layer()   # letters -> symbols
	for row in kb._rows:
		for b in row.get_children():
			if (b as Button).text == "#":
				(b as Button).pressed.emit()
	for row in kb._rows:
		for b in row.get_children():
			if (b as Button).text == "@":
				(b as Button).pressed.emit()
	check(edit.text == "#@", "typing # and @ works: " + edit.text)
	check(kb._shift_button.disabled, "shift is off on the symbols layer")
	kb.toggle_layer()
	check(not kb._shift_button.disabled and kb._layer_button.text == "#+=", "back to letters")

	print("MOD MENU OK" if ok else "MOD MENU FAILED")
	quit()


func _find_menu(node: Node) -> Control:
	for c in node.find_children("*", "VBoxContainer", true, false):
		if c.get_script() != null and str(c.get_script().resource_path).ends_with("mod_menu.gd"):
			return c
	return null


func _rows(menu: Control) -> int:
	var n := 0
	for c in menu.results.get_children():
		if c is PanelContainer and not c.is_queued_for_deletion():
			n += 1
	return n


func _first_button(node: Node, text: String) -> Button:
	for b in node.find_children("*", "Button", true, false):
		if (b as Button).text == text:
			return b
	return null


func _frames(n: int) -> void:
	for i in range(n):
		await process_frame
