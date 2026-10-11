extends Node
## Launcher settings, kept in user://config.json (the instances themselves are kept by Pojlib).

const PATH := "user://config.json"

var last_account_uuid := ""
var last_loader := "NeoForge"
var last_instance := ""
var memory_mb := ""      # empty = Pojlib's default


func _ready() -> void:
	load_config()


func load_config() -> void:
	if not FileAccess.file_exists(PATH):
		return
	var data = JSON.parse_string(FileAccess.get_file_as_string(PATH))
	if typeof(data) != TYPE_DICTIONARY:
		return
	last_account_uuid = str(data.get("last_account_uuid", ""))
	last_loader = str(data.get("last_loader", "NeoForge"))
	last_instance = str(data.get("last_instance", ""))
	memory_mb = str(data.get("memory_mb", ""))


func save_config() -> void:
	var f := FileAccess.open(PATH, FileAccess.WRITE)
	if f == null:
		return
	f.store_string(JSON.stringify({
		"last_account_uuid": last_account_uuid,
		"last_loader": last_loader,
		"last_instance": last_instance,
		"memory_mb": memory_mb,
	}, "\t"))
