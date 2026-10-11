extends SceneTree
## Writes the default OpenXR action map (controller bindings) next to the project:
##   godot --headless --path godot-launcher --script res://tools/make_action_map.gd

func _initialize() -> void:
	var map := OpenXRActionMap.new()
	map.create_default_action_sets()
	print("save: ", ResourceSaver.save(map, "res://openxr_action_map.tres"))
	quit()
