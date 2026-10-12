extends Node
## The connection to Pojlib. On the headset this talks to the "QuestCraft" Android plugin (QuestCraftPlugin.java),
## which forwards to pojlib.LauncherApi. Everywhere else (desktop, for trying the UI) it answers from a small mock.

var plugin: Object = null
var is_mock := true
var init_error := ""

# mock state
var _mock_instances := [
	{"name": "1.21.1", "version": "1.21.1", "loader": "Fabric", "image": "", "mods": 6},
	{"name": "Fabric 1.20.4", "version": "1.20.4", "loader": "Fabric", "image": "", "mods": 9},
]
var _mock_login_polls := 0
var _mock_logged_in := false
var _mock_task_polls := 0
var _mock_phase := "idle"


func _ready() -> void:
	if Engine.has_singleton("QuestCraft"):
		plugin = Engine.get_singleton("QuestCraft")
		is_mock = false
		init_error = str(api("init", []))
		api("setModel", [OS.get_model_name()])   # e.g. "Quest 3", handed to the game as VR_MODEL like the Unity launcher does


## Calls LauncherApi.<method>(args). Lists and objects come back parsed, everything else as a string.
func api(method: String, args: Array = []) -> Variant:
	if is_mock:
		return _mock(method, args)
	var raw = plugin.call("invoke", method, JSON.stringify(args))
	var text := str(raw)
	if text.begins_with("[") or text.begins_with("{"):
		var parsed = JSON.parse_string(text)
		if parsed != null:
			return parsed
	return text


func api_bool(method: String, args: Array = []) -> bool:
	return str(api(method, args)) == "true"


# ---- mock (desktop only) ----

func _mock(method: String, args: Array) -> Variant:
	match method:
		"init": return ""
		"modLoaders": return ["Fabric", "NeoForge", "Quilt"]
		"supportedVersions":
			match str(args[0]):
				"NeoForge": return ["1.21.11", "1.21.8", "1.21.5", "1.21.4", "1.21.1", "1.20.6", "1.20.4"]
				"Quilt": return ["1.21.11", "1.21.10", "1.21.8", "1.20.1", "1.19.4", "1.19.2"]
				_: return ["1.21.11", "1.21.10", "1.21.8", "1.21.1", "1.20.4", "1.20.1"]
		"listInstances": return _mock_instances
		"listAccounts": return [{"name": "Steve", "uuid": "abc"}]
		"login":
			_mock_login_polls = 0
			_mock_logged_in = false
			return ""
		"loginState":
			_mock_login_polls += 1
			if args.size() == 0 and _mock_login_polls > 6:
				_mock_logged_in = true
			return {
				"loggedIn": _mock_logged_in, "name": "Steve", "uuid": "abc", "image": "", "demo": false,
				"message": "To sign in, use a web browser to open the page https://www.microsoft.com/link and enter the code AB12CD34 to authenticate.",
			}
		"modrinthCategories": return ["neoforge"]
		"listMods": return [{"slug": "sodium", "version": "0.6.0", "type": "mod", "file": "sodium.jar"}]
		"hasMod": return "false"
		"addMod", "removeMod", "deleteInstance": return "true"
		"createInstance":
			_mock_instances.append({"name": args[0], "version": args[1], "loader": args[2], "image": "", "mods": 0})
			_mock_phase = "working"
			_mock_task_polls = 0
			return ""
		"prepare":
			_mock_phase = "working"
			_mock_task_polls = 0
			return ""
		"progress":
			if _mock_phase == "working":
				_mock_task_polls += 1
				if _mock_task_polls > 8:
					_mock_phase = "ready"
			return {"phase": _mock_phase, "task": "Downloading", "percent": minf(_mock_task_polls * 12.0, 100.0), "error": "", "gameReady": false}
		"acknowledge":
			_mock_phase = "idle"
			return ""
		"startGameProcess":
			print("MOCK startGameProcess ", args)
			return ""
		"logTail": return "Mock log line 1\nMock log line 2"
		"crashInfo": return ""
		"hasConnection": return "true"
		_: return ""
