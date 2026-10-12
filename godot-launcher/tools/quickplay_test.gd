extends SceneTree
## Quick play with different loaders must make and start different instances (the mock backend has a Fabric "1.21.1"):
##   godot --headless --path godot-launcher --script res://tools/quickplay_test.gd

func _initialize() -> void:
	var cfg: Node = root.get_node("Config")
	var bridge: Node = root.get_node("Bridge")
	var ui: Control = load("res://scripts/ui.gd").new()
	root.add_child(ui)
	await process_frame
	var ok := true
	var instances: Array = bridge.api("listInstances")
	cfg.last_is_version = true
	cfg.last_version = "1.21.1"
	for loader in ["Fabric", "NeoForge", "Quilt"]:
		cfg.last_loader = loader
		var name: String = ui._quick_name("1.21.1", loader, instances)
		var label: String = ui._selection_label(instances)
		print(loader, " -> instance '", name, "'  button '", label, "'")
	ok = ok and ui._quick_name("1.21.1", "Fabric", instances) == "1.21.1"
	ok = ok and ui._quick_name("1.21.1", "NeoForge", instances) == "1.21.1 NeoForge"
	ok = ok and ui._quick_name("1.21.1", "Quilt", instances) == "1.21.1 Quilt"
	# playing with NeoForge picked creates a NeoForge instance and does not start the Fabric one
	cfg.last_loader = "NeoForge"
	ui._play_selected()
	var after: Array = bridge.api("listInstances")
	var made := false
	for inst in after:
		if inst["name"] == "1.21.1 NeoForge" and inst["loader"] == "NeoForge":
			made = true
	print("NeoForge instance made: ", made)
	ok = ok and made
	print("QUICKPLAY OK" if ok else "QUICKPLAY FAILED")
	quit()
