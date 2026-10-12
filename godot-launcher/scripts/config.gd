extends Node
## Launcher settings, kept in user://config.json (the instances themselves are kept by Pojlib).

const PATH := "user://config.json"

var last_account_uuid := ""
var last_loader := "NeoForge"
var last_instance := ""      # an instance picked by name from the version menu
var last_version := ""       # a Minecraft version picked for quick play (made with last_loader)
var last_is_version := true  # which of the two the main screen starts
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
	last_version = str(data.get("last_version", ""))
	last_is_version = bool(data.get("last_is_version", true))
	memory_mb = str(data.get("memory_mb", ""))


func save_config() -> void:
	var f := FileAccess.open(PATH, FileAccess.WRITE)
	if f == null:
		return
	f.store_string(JSON.stringify({
		"last_account_uuid": last_account_uuid,
		"last_loader": last_loader,
		"last_instance": last_instance,
		"last_version": last_version,
		"last_is_version": last_is_version,
		"memory_mb": memory_mb,
	}, "\t"))
