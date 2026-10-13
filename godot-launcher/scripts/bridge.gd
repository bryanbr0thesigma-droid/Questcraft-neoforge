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
# how many progress polls creating an instance takes in the mock, then it is "idle" again (0: done before the first poll)
var mock_create_polls := 3
var _mock_creating := false
var _mock_mods := {}   # instance name -> [{"slug", "version", "type", "file"}] added by the mock's addMod
var mock_returned_from_game := false   # the app was started again by the game that just ended (no logo screen then)
var _mock_prepared := ""   # the instance the last "prepare" was for


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
		"modLoaders": return ["Fabric", "NeoForge", "Quilt", "Forge"]
		"supportedVersions":
			# the lists the app carries (pojlib/src/main/assets/loaders/supportedVersions-*.json)
			match str(args[0]):
				"NeoForge", "Forge": return ["1.21.11", "1.21.8", "1.21.5", "1.21.4", "1.21.1", "1.20.6", "1.20.4"]
				_: return ["1.21.11", "1.21.10", "1.21.8", "1.21.5", "1.21.4", "1.21.1", "1.20.6", "1.20.4", "1.20.1", "1.19.4", "1.19.2"]
		"vrMods":
			# Visor where the app has a build: not for Forge 1.20.4 here, so that the screen shows both cases
			if str(args[0]) == "Forge" and str(args[1]) == "1.20.4":
				return ["Vivecraft"]
			return ["Vivecraft", "Visor"]
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
		"listMods":
			return [{"slug": "sodium", "version": "0.6.0", "type": "mod", "file": "sodium.jar"}] + _mock_mods.get(str(args[0]), [])
		"hasMod":
			for m in _mock_mods.get(str(args[0]), []):
				if m["slug"] == str(args[1]):
					return "true"
			return "false"
		"addMod":
			if not _mock_mods.has(str(args[0])):
				_mock_mods[str(args[0])] = []
			_mock_mods[str(args[0])].append({"slug": str(args[1]), "version": str(args[3]), "type": str(args[5]), "file": str(args[2])})
			return "true"
		"removeMod":
			var kept: Array = []
			for m in _mock_mods.get(str(args[0]), []):
				if m["slug"] != str(args[1]):
					kept.append(m)
			_mock_mods[str(args[0])] = kept
			return "true"
		"deleteInstance": return "true"
		"installModpack":
			print("MOCK installModpack ", args)
			_mock_instances.append({"name": args[0], "version": "1.21.1", "loader": args[1], "image": args[3], "mods": 0})
			_mock_phase = "working"
			_mock_creating = true
			_mock_task_polls = 0
			return ""
		"createInstance":
			_mock_instances.append({"name": args[0], "version": args[1], "loader": args[2], "image": "", "mods": 0,
					"vr": str(args[3]) if args.size() > 3 else "Vivecraft"})
			_mock_phase = "working"
			_mock_creating = true
			_mock_task_polls = 0
			if mock_create_polls == 0:
				_mock_phase = "idle"   # the work was over before anyone looked
				_mock_creating = false
			return ""
		"prepare":
			_mock_phase = "working"
			_mock_creating = false
			_mock_prepared = str(args[0])
			_mock_task_polls = 0
			return ""
		"progress":
			if _mock_phase == "working" and _mock_creating:
				_mock_task_polls += 1
				if _mock_task_polls > mock_create_polls:
					_mock_phase = "idle"
					_mock_creating = false
			elif _mock_phase == "working":
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
		"returnedFromGame": return str(mock_returned_from_game)
		"logTail": return "Mock log line 1\nMock log line 2"
		"crashInfo": return ""
		"hasConnection": return "true"
		_: return ""
