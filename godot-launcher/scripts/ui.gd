extends Control
## The launcher menu, laid out like the Unity launcher's: the Minecraft picture with the QuestCraft logo, your account
## top left, and at the bottom the version picker, Play, Instances, Mods and settings. It is drawn at 1200x600 into a
## viewport that the headset shows on the big screen in the room. Everything is built in code.

signal request_start_game

const W := 1200
const H := 600

const Modrinth := preload("res://scripts/modrinth.gd")
const VKeyboard := preload("res://scripts/vkeyboard.gd")

const ACCENT := Color("2fbf5f")
const PANEL := Color(0.05, 0.06, 0.07, 0.86)
const TEXT_DIM := Color("b4bec7")
const ERROR := Color("ff7b7b")

var _font: Font
var _button_texture: Texture2D
var _tex := {}

var modrinth: Node
var bg: TextureRect
var dim: ColorRect
var page: Control
var toast: Label
var keyboard: PanelContainer
var poll_timer: Timer

var account_name := ""
var _poll := Callable()
var _saw_working := false
var _loaders: Array = []
var _logged_in := false
var _device_shown := false


func _ready() -> void:
	size = Vector2(W, H)
	custom_minimum_size = Vector2(W, H)
	_font = load("res://assets/fonts/Sunflower-Bold.ttf")
	_button_texture = load("res://assets/ui/button.png")
	for n in ["logo", "background", "gear", "notebook", "exit", "folder", "search", "up", "back", "trash", "reload"]:
		_tex[n] = load("res://assets/ui/%s.png" % n)
	theme = _make_theme()
	modrinth = Modrinth.new()
	add_child(modrinth)
	poll_timer = Timer.new()
	poll_timer.wait_time = 0.5
	poll_timer.timeout.connect(_on_poll)
	add_child(poll_timer)
	poll_timer.start()
	_build_frame()
	_loaders = Bridge.api("modLoaders") as Array
	Bridge.api("refreshVersions")
	if Config.memory_mb != "":
		Bridge.api("setMemory", [Config.memory_mb])
	if Bridge.init_error != "":
		set_status("Pojlib failed to start: " + Bridge.init_error, true)
	_startup_login()


# ---------------------------------------------------------------- look

func _button_style(tint: Color) -> StyleBoxTexture:
	var s := StyleBoxTexture.new()
	s.texture = _button_texture
	s.texture_margin_left = 10
	s.texture_margin_right = 10
	s.texture_margin_top = 10
	s.texture_margin_bottom = 16
	s.content_margin_left = 16
	s.content_margin_right = 16
	s.content_margin_top = 6
	s.content_margin_bottom = 12
	s.modulate_color = tint
	return s


func _flat(color: Color, radius := 12, margin := 12, border := Color(0, 0, 0, 0)) -> StyleBoxFlat:
	var s := StyleBoxFlat.new()
	s.bg_color = color
	s.set_corner_radius_all(radius)
	s.set_content_margin_all(margin)
	if border.a > 0.0:
		s.set_border_width_all(3)
		s.border_color = border
	return s


func _make_theme() -> Theme:
	var t := Theme.new()
	t.default_font = _font
	t.default_font_size = 26
	t.set_stylebox("normal", "Button", _button_style(Color.WHITE))
	t.set_stylebox("hover", "Button", _button_style(Color(1.18, 1.18, 1.18)))
	t.set_stylebox("pressed", "Button", _button_style(Color(0.78, 0.78, 0.78)))
	t.set_stylebox("disabled", "Button", _button_style(Color(0.45, 0.5, 0.47, 0.8)))
	t.set_stylebox("focus", "Button", _button_style(Color(1.18, 1.18, 1.18)))
	t.set_color("font_color", "Button", Color.WHITE)
	t.set_color("font_disabled_color", "Button", Color(0.8, 0.85, 0.82))
	t.set_stylebox("normal", "LineEdit", _flat(Color(0.03, 0.04, 0.05, 0.92), 8, 12))
	t.set_stylebox("focus", "LineEdit", _flat(Color(0.03, 0.04, 0.05, 0.92), 8, 12, ACCENT))
	t.set_stylebox("panel", "PanelContainer", _flat(PANEL, 14, 14))
	t.set_stylebox("fill", "ProgressBar", _flat(ACCENT, 8, 0))
	t.set_stylebox("background", "ProgressBar", _flat(Color(0.03, 0.04, 0.05, 0.9), 8, 0))
	return t


func _build_frame() -> void:
	bg = TextureRect.new()
	bg.texture = _tex["background"]
	bg.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	bg.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_COVERED
	bg.size = Vector2(W, H)
	bg.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(bg)
	dim = ColorRect.new()
	dim.color = Color(0, 0, 0, 0)
	dim.size = Vector2(W, H)
	dim.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(dim)
	page = Control.new()
	page.size = Vector2(W, H)
	add_child(page)

	toast = Label.new()
	toast.position = Vector2(0, 112)
	toast.size = Vector2(W, 40)
	toast.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	toast.add_theme_font_size_override("font_size", 24)
	toast.add_theme_color_override("font_outline_color", Color.BLACK)
	toast.add_theme_constant_override("outline_size", 8)
	toast.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(toast)

	keyboard = VKeyboard.new()
	keyboard.position = Vector2(110, H - 345)
	add_child(keyboard)


# ---------------------------------------------------------------- widgets

func _button(text: String, cb: Callable, min_size := Vector2(0, 54), icon_name := "") -> Button:
	var b := Button.new()
	b.text = text
	b.custom_minimum_size = min_size
	if icon_name != "":
		b.icon = _tex[icon_name]
		b.expand_icon = true
		b.add_theme_constant_override("icon_max_width", 30)
	if cb.is_valid():
		b.pressed.connect(cb)
	return b


func _icon_button(icon_name: String, cb: Callable, size_px := 56) -> Button:
	var b := Button.new()
	b.icon = _tex[icon_name]
	b.expand_icon = true
	b.icon_alignment = HORIZONTAL_ALIGNMENT_CENTER
	b.add_theme_constant_override("icon_max_width", size_px - 22)
	b.custom_minimum_size = Vector2(size_px, size_px)
	b.pressed.connect(cb)
	return b


func _label(text: String, size := 26, color := Color.WHITE) -> Label:
	var l := Label.new()
	l.text = text
	if text.length() > 48:   # long text wraps; short labels keep their width (a wrapping label in a row collapses)
		l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		l.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	l.add_theme_font_size_override("font_size", size)
	l.add_theme_color_override("font_color", color)
	return l


func _line_edit(placeholder: String, text := "") -> LineEdit:
	var e := LineEdit.new()
	e.placeholder_text = placeholder
	e.text = text
	e.custom_minimum_size = Vector2(0, 54)
	e.virtual_keyboard_enabled = false   # the system keyboard does not exist in here, use the panel keyboard
	e.focus_entered.connect(func(): keyboard.attach(e))
	return e


## A "< value >" chooser, used instead of drop-down lists (pop-up windows do not work well on a VR screen).
## Read the choice with .get_meta("value").
func _selector(options: Array, selected: String, changed := Callable()) -> HBoxContainer:
	var row := HBoxContainer.new()
	row.add_theme_constant_override("separation", 8)
	var shown := Label.new()
	shown.custom_minimum_size = Vector2(230, 0)
	shown.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	shown.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	# the position lives in a dictionary: a plain variable would be copied into each handler below, and the buttons
	# would move their own copy while the label kept showing the first value
	var st := {"index": maxi(0, options.find(selected))}
	var apply := func():
		var v: String = str(options[st["index"]]) if options.size() > 0 else ""
		shown.text = v
		row.set_meta("value", v)
		if changed.is_valid():
			changed.call(v)
	var step := func(delta: int):
		if options.size() == 0:
			return
		st["index"] = (int(st["index"]) + delta + options.size()) % options.size()
		apply.call()
	row.add_child(_button("<", func(): step.call(-1), Vector2(56, 54)))
	row.add_child(shown)
	row.add_child(_button(">", func(): step.call(1), Vector2(56, 54)))
	apply.call()
	return row


func _clear(dim_alpha := 0.78) -> void:
	_poll = Callable()
	keyboard.visible = false
	dim.color = Color(0, 0, 0, dim_alpha)
	for c in page.get_children():
		c.queue_free()
	set_status("")


func set_status(text: String, is_error := false) -> void:
	toast.text = text
	toast.add_theme_color_override("font_color", ERROR if is_error else Color.WHITE)


func _on_poll() -> void:
	if _poll.is_valid():
		_poll.call()


## A screen with a header (back button, title, account) and a body to fill.
func _screen(title: String, back: Callable) -> VBoxContainer:
	_clear()
	var margin := MarginContainer.new()
	margin.size = Vector2(W, H)
	for side in ["left", "right", "top", "bottom"]:
		margin.add_theme_constant_override("margin_" + side, 22)
	page.add_child(margin)
	var root := VBoxContainer.new()
	root.add_theme_constant_override("separation", 10)
	margin.add_child(root)
	var header := HBoxContainer.new()
	header.add_theme_constant_override("separation", 14)
	root.add_child(header)
	if back.is_valid():
		header.add_child(_button("Back", back, Vector2(150, 54), "back"))
	header.add_child(_label(title, 36))
	var sp := Control.new()
	sp.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	header.add_child(sp)
	header.add_child(_label(account_name, 24, TEXT_DIM))
	var body := VBoxContainer.new()
	body.size_flags_vertical = Control.SIZE_EXPAND_FILL
	body.add_theme_constant_override("separation", 10)
	root.add_child(body)
	return body


# ---------------------------------------------------------------- sign in

func _startup_login() -> void:
	var accounts: Array = Bridge.api("listAccounts") as Array
	for a in accounts:
		if a.get("uuid", "") == Config.last_account_uuid:
			_sign_in(Config.last_account_uuid)
			return
	show_login()


func show_login() -> void:
	var body := _screen("Sign in", show_home if _logged_in else Callable())
	body.add_child(_label("Minecraft needs a Microsoft account that owns Java Edition.", 26, TEXT_DIM))
	var accounts: Array = Bridge.api("listAccounts") as Array
	for a in accounts:
		var uuid: String = str(a.get("uuid", ""))
		body.add_child(_button("Continue as " + str(a.get("name", "?")), func(): _sign_in(uuid), Vector2(0, 62)))
	body.add_child(_button("Add a Microsoft account", func(): _sign_in(""), Vector2(0, 62)))


func _sign_in(uuid: String) -> void:
	var body := _screen("Signing in...", Callable())
	_logged_in = false
	_device_shown = false
	body.add_child(_label("Talking to Microsoft...", 28, TEXT_DIM))
	Bridge.api("login", [uuid])
	_poll = _poll_login


func _poll_login() -> void:
	var state = Bridge.api("loginState")
	if typeof(state) != TYPE_DICTIONARY:
		return
	if state.get("loggedIn", false):
		_logged_in = true
		_device_shown = false
		Config.last_account_uuid = str(state.get("uuid", ""))
		Config.save_config()
		account_name = str(state.get("name", ""))
		if state.get("demo", false):
			account_name += " (demo)"
		show_home()
		return
	var message := str(state.get("message", ""))
	if message != "" and not _device_shown:
		_device_shown = true
		_show_device_code(message)
	elif message.begins_with("MicrosoftLogin") or message.begins_with("Sign in failed"):
		set_status(message, true)


func _show_device_code(message: String) -> void:
	var poll := _poll
	var body := _screen("Sign in with Microsoft", Callable())
	_poll = poll
	var url_re := RegEx.create_from_string("https?://[^\\s]+")
	var code_re := RegEx.create_from_string("code\\s+([A-Za-z0-9]+)")
	var url_match := url_re.search(message)
	var code_match := code_re.search(message)
	var url := (url_match.get_string() if url_match else "https://www.microsoft.com/link").rstrip(".,")
	body.add_child(_label("1. Open " + url + " on any device (the headset browser works too).", 26))
	if code_match:
		body.add_child(_label("2. Enter this code:", 26))
		body.add_child(_label(code_match.get_string(1), 64, ACCENT))
	else:
		body.add_child(_label(message, 26))
	body.add_child(_button("Open the page in the browser", func(): OS.shell_open(url), Vector2(0, 60)))
	body.add_child(_label("This screen continues by itself once you are signed in.", 22, TEXT_DIM))


# ---------------------------------------------------------------- main screen

func show_home() -> void:
	_clear(0.0)
	# the account, top left
	var chip := PanelContainer.new()
	chip.position = Vector2(22, 18)
	page.add_child(chip)
	var chip_row := HBoxContainer.new()
	chip_row.add_theme_constant_override("separation", 14)
	chip.add_child(chip_row)
	var who := _label(account_name if account_name != "" else "Not signed in", 28)
	who.custom_minimum_size = Vector2(200, 0)
	chip_row.add_child(who)
	chip_row.add_child(_icon_button("exit", show_login, 48))

	# the logo, top centre
	var logo := TextureRect.new()
	logo.texture = _tex["logo"]
	logo.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	logo.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	logo.position = Vector2(350, 6)
	logo.size = Vector2(500, 105)
	logo.mouse_filter = Control.MOUSE_FILTER_IGNORE
	page.add_child(logo)

	# the log, top right
	var notebook := _icon_button("notebook", func(): show_log(true), 56)
	notebook.position = Vector2(W - 78, 16)
	page.add_child(notebook)

	# the bottom bar
	var bar := ColorRect.new()
	bar.color = Color(0, 0, 0, 0.72)
	bar.position = Vector2(0, H - 128)
	bar.size = Vector2(W, 128)
	bar.mouse_filter = Control.MOUSE_FILTER_IGNORE
	page.add_child(bar)

	var instances: Array = Bridge.api("listInstances") as Array
	var version_button := _button(_selection_label(instances), Callable(), Vector2(330, 70), "up")
	version_button.icon_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	version_button.position = Vector2(26, H - 100)
	version_button.size = Vector2(330, 70)
	page.add_child(version_button)
	version_button.pressed.connect(_open_version_menu.bind(version_button))

	# which mod loader quick-play versions are made with (Fabric, NeoForge, Quilt); the version list follows it
	var loader_button := _button("Loader: " + Config.last_loader, _cycle_loader, Vector2(330, 44))
	loader_button.position = Vector2(26, H - 176)
	loader_button.size = Vector2(330, 44)
	page.add_child(loader_button)

	var play := _button("Play", _play_selected, Vector2(340, 78))
	play.add_theme_font_size_override("font_size", 38)
	play.position = Vector2(430, H - 104)
	play.size = Vector2(340, 78)
	page.add_child(play)

	var instances_button := _button("Instances", show_instances, Vector2(290, 52), "folder")
	instances_button.icon_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	instances_button.position = Vector2(830, H - 116)
	instances_button.size = Vector2(290, 52)
	page.add_child(instances_button)
	var mods_button := _button("Mods", _open_mods_for_selected, Vector2(290, 52), "search")
	mods_button.icon_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	mods_button.position = Vector2(830, H - 60)
	mods_button.size = Vector2(290, 52)
	page.add_child(mods_button)

	var gear := _icon_button("gear", show_settings, 64)
	gear.position = Vector2(W - 70, H - 100)
	page.add_child(gear)


## Switches the loader for new quick-play instances to the next one (Fabric -> NeoForge -> Quilt -> ...).
func _cycle_loader() -> void:
	if _loaders.is_empty():
		return
	var i := _loaders.find(Config.last_loader)
	Config.last_loader = str(_loaders[(i + 1) % _loaders.size()])
	Config.save_config()
	show_home()
	set_status("New versions will be made with " + Config.last_loader)


## What the main screen starts: an instance picked by name, or a Minecraft version made with the loader chosen on the
## main screen. Returns {"instance": <dictionary>} or {"version": <string>} ("" when the loader has no versions).
func _selection(instances: Array) -> Dictionary:
	if not Config.last_is_version:
		for inst in instances:
			if str(inst.get("name", "")) == Config.last_instance:
				return {"instance": inst}
	var versions := _supported_versions(Config.last_loader)
	var v := Config.last_version
	if not versions.has(v):
		v = str(versions[0]) if versions.size() > 0 else ""
	return {"version": v}


func _selection_label(instances: Array) -> String:
	var sel := _selection(instances)
	if sel.has("instance"):
		return str(sel["instance"].get("name", ""))
	var v: String = sel["version"]
	return "No versions" if v == "" else "%s (%s)" % [v, Config.last_loader]


func _supported_versions(loader: String) -> Array:
	var v = Bridge.api("supportedVersions", [loader])
	return v if typeof(v) == TYPE_ARRAY else []


## The name of the instance quick play makes of a version with a loader: the plain version for Fabric (what the Unity
## launcher does), the version and the loader for the others, so each loader has its own instance of a version.
## An instance of that name that was made with another loader is not reused.
func _quick_name(version: String, loader: String, instances: Array) -> String:
	var plain := version if loader == "Fabric" else "%s %s" % [version, loader]
	for inst in instances:
		if str(inst.get("name", "")) == plain and str(inst.get("loader", "")) != loader:
			return "%s %s" % [version, loader]
	return plain


func _open_version_menu(anchor: Button) -> void:
	var existing := page.get_node_or_null("VersionMenu")
	if existing:
		existing.queue_free()
		return
	var menu := PanelContainer.new()
	menu.name = "VersionMenu"
	var scroll := ScrollContainer.new()
	scroll.custom_minimum_size = Vector2(380, 300)
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	menu.add_child(scroll)
	var list := VBoxContainer.new()
	list.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	scroll.add_child(list)
	var instances: Array = Bridge.api("listInstances") as Array
	for v in _supported_versions(Config.last_loader):
		list.add_child(_button("%s  (%s)" % [v, Config.last_loader], _choose.bind(str(v), true), Vector2(0, 50)))
	for inst in instances:
		var iname := str(inst.get("name", ""))
		list.add_child(_button("Instance: %s  (%s)" % [iname, inst.get("loader", "")], _choose.bind(iname, false), Vector2(0, 50)))
	menu.position = Vector2(anchor.position.x, anchor.position.y - 330)
	page.add_child(menu)


func _choose(name_or_version: String, is_version: bool) -> void:
	if is_version:
		Config.last_version = name_or_version
	else:
		Config.last_instance = name_or_version
	Config.last_is_version = is_version
	Config.save_config()
	show_home()


func _play_selected() -> void:
	var instances: Array = Bridge.api("listInstances") as Array
	var sel := _selection(instances)
	if sel.has("instance"):
		start_play(str(sel["instance"].get("name", "")))
		return
	var version: String = sel["version"]
	if version == "":
		set_status("%s has no Minecraft versions listed. Check the connection, or pick another loader." % Config.last_loader, true)
		return
	var iname := _quick_name(version, Config.last_loader, instances)
	for inst in instances:
		if str(inst.get("name", "")) == iname:
			start_play(iname)
			return
	# not made yet: make it with the loader that is picked, then play it
	Bridge.api("createInstance", [iname, version, Config.last_loader])
	show_progress("Installing %s (%s %s)" % [iname, Config.last_loader, version], start_play.bind(iname))


func _open_mods_for_selected() -> void:
	var instances: Array = Bridge.api("listInstances") as Array
	var sel := _selection(instances)
	var iname := ""
	if sel.has("instance"):
		iname = str(sel["instance"].get("name", ""))
	elif sel["version"] != "":
		iname = _quick_name(sel["version"], Config.last_loader, instances)
	for inst in instances:
		if str(inst.get("name", "")) == iname:
			show_mods(inst)
			return
	set_status("Start %s once first, then you can add mods to it." % (iname if iname != "" else "a version"))


# ---------------------------------------------------------------- instances

func show_instances() -> void:
	var body := _screen("Instances", show_home)
	body.add_child(_button("New instance", show_create, Vector2(0, 54)))
	var scroll := ScrollContainer.new()
	scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	body.add_child(scroll)
	var list := VBoxContainer.new()
	list.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	list.add_theme_constant_override("separation", 8)
	scroll.add_child(list)
	var instances: Array = Bridge.api("listInstances") as Array
	if instances.is_empty():
		list.add_child(_label("No instances yet. Make one with \"New instance\", or just press Play on the main screen.", 24, TEXT_DIM))
	for inst in instances:
		list.add_child(_instance_row(inst))


func _instance_row(inst: Dictionary) -> Control:
	var iname: String = str(inst.get("name", ""))
	var panel := PanelContainer.new()
	var row := HBoxContainer.new()
	row.add_theme_constant_override("separation", 10)
	panel.add_child(row)
	var info := VBoxContainer.new()
	info.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	row.add_child(info)
	info.add_child(_label(iname, 28))
	info.add_child(_label("%s  |  %s  |  %d mods" % [inst.get("version", "?"), inst.get("loader", "?"), int(inst.get("mods", 0))], 20, TEXT_DIM))
	row.add_child(_button("Play", start_play.bind(iname), Vector2(120, 54)))
	row.add_child(_button("Mods", show_mods.bind(inst), Vector2(120, 54)))
	var del := _button("Delete", Callable(), Vector2(130, 54))
	var do_delete := func():
		if del.text == "Delete":
			del.text = "Sure?"
		else:
			Bridge.api("deleteInstance", [iname])
			show_instances()
	del.pressed.connect(do_delete)
	row.add_child(del)
	return panel


func show_create() -> void:
	var body := _screen("New instance", show_instances)
	var loader_row := HBoxContainer.new()
	loader_row.add_theme_constant_override("separation", 14)
	body.add_child(loader_row)
	loader_row.add_child(_label("Mod loader", 26))
	var version_holder := HBoxContainer.new()
	var name_edit := _line_edit("Name")
	var versions_info := _label("", 22, TEXT_DIM)
	var state := {"version": ""}

	var rebuild_versions := func(loader: String):
		for c in version_holder.get_children():
			c.queue_free()
		var versions: Array = _supported_versions(loader)
		if versions.is_empty():
			versions_info.text = "No version list for %s yet. Check the connection and come back." % loader
			state["version"] = ""
			return
		versions_info.text = ""
		var on_version := func(v: String):
			if name_edit.text == "" or name_edit.text == state["version"]:
				name_edit.text = v
			state["version"] = v
		version_holder.add_child(_selector(versions, str(versions[0]), on_version))

	var on_loader := func(l: String):
		Config.last_loader = l
		rebuild_versions.call(l)
	var loader_sel := _selector(_loaders, Config.last_loader, on_loader)
	loader_row.add_child(loader_sel)

	var version_row := HBoxContainer.new()
	version_row.add_theme_constant_override("separation", 14)
	body.add_child(version_row)
	version_row.add_child(_label("Minecraft version", 26))
	version_row.add_child(version_holder)
	body.add_child(versions_info)
	body.add_child(_label("Name", 26))
	body.add_child(name_edit)

	var do_create := func():
		var loader: String = str(loader_sel.get_meta("value", ""))
		var version: String = str(state["version"])
		var nm := name_edit.text.strip_edges()
		if version == "" or nm == "":
			set_status("Pick a version and give the instance a name.", true)
			return
		Config.save_config()
		Bridge.api("createInstance", [nm, version, loader])
		show_progress("Installing " + nm + " (" + loader + " " + version + ")", show_instances)
	body.add_child(_button("Create", do_create, Vector2(260, 58)))
	rebuild_versions.call(str(loader_sel.get_meta("value", "")))


# ---------------------------------------------------------------- progress / play

func show_progress(title: String, when_done: Callable) -> void:
	var body := _screen(title, Callable())
	_saw_working = false
	var task := _label("", 26, TEXT_DIM)
	body.add_child(task)
	var bar := ProgressBar.new()
	bar.custom_minimum_size = Vector2(0, 32)
	bar.max_value = 100.0
	bar.show_percentage = false
	body.add_child(bar)
	var logbox := _label("", 18, TEXT_DIM)
	logbox.max_lines_visible = 10
	body.add_child(logbox)
	_poll = func():
		var p = Bridge.api("progress")
		if typeof(p) != TYPE_DICTIONARY:
			return
		var phase := str(p.get("phase", ""))
		task.text = str(p.get("task", ""))
		bar.value = float(p.get("percent", 0.0))
		logbox.text = str(Bridge.api("logTail", [10]))
		if phase == "working":
			_saw_working = true
		elif phase == "error":
			Bridge.api("acknowledge")
			show_home()
			set_status(str(p.get("error", "Something went wrong")), true)
		elif phase == "ready":
			_poll = Callable()
			task.text = "Starting Minecraft..."
			request_start_game.emit()
		elif phase == "idle" and _saw_working:
			_poll = Callable()
			when_done.call()


func start_play(instance_name: String) -> void:
	Config.last_instance = instance_name
	Config.save_config()
	Bridge.api("prepare", [instance_name])
	show_progress("Preparing " + instance_name, show_home)


# ---------------------------------------------------------------- settings and log

func show_settings() -> void:
	var body := _screen("Settings", show_home)
	var loader_row := HBoxContainer.new()
	loader_row.add_theme_constant_override("separation", 14)
	body.add_child(loader_row)
	loader_row.add_child(_label("Mod loader for new versions", 26))
	var on_loader := func(l: String):
		Config.last_loader = l
		Config.save_config()
	loader_row.add_child(_selector(_loaders, Config.last_loader, on_loader))
	body.add_child(_label("Memory for the game, in megabytes (empty = default 1800). Quest 2 has little to spare; try 2048 to 3072 on Quest 3.", 22, TEXT_DIM))
	var mem := _line_edit("e.g. 2048", Config.memory_mb)
	body.add_child(mem)
	var do_save := func():
		Config.memory_mb = mem.text.strip_edges()
		Config.save_config()
		if Config.memory_mb != "":
			Bridge.api("setMemory", [Config.memory_mb])
		show_home()
	body.add_child(_button("Save", do_save, Vector2(200, 56)))


func show_log(only_problems := true) -> void:
	var body := _screen("Log", show_home)
	var row := HBoxContainer.new()
	row.add_theme_constant_override("separation", 10)
	body.add_child(row)
	row.add_child(_button("Problems and VR only" if not only_problems else "Show everything", show_log.bind(not only_problems), Vector2(330, 48)))
	row.add_child(_button("Refresh", show_log.bind(only_problems), Vector2(160, 48)))
	var lines: PackedStringArray = str(Bridge.api("logTail", [200])).split("\n")
	var shown: Array = []
	var re := RegEx.create_from_string("(?i)error|exception|warn|fail|openxr|vivecraft|xr_|controller|action|session|loader")
	for line in lines:
		if line.strip_edges() == "":
			continue
		if not only_problems or re.search(line) != null:
			shown.append(line.left(150))
	shown = shown.slice(maxi(0, shown.size() - 40))
	var scroll := ScrollContainer.new()
	scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	body.add_child(scroll)
	var logbox := _label("\n".join(shown) if shown.size() > 0 else "Nothing in the log yet.", 15, TEXT_DIM)
	logbox.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	logbox.autowrap_mode = TextServer.AUTOWRAP_ARBITRARY
	scroll.add_child(logbox)


# ---------------------------------------------------------------- mods

func show_mods(inst: Dictionary) -> void:
	var iname: String = str(inst.get("name", ""))
	var mc: String = str(inst.get("version", ""))
	var categories: Array = Bridge.api("modrinthCategories", [iname]) as Array
	var types := ["mod", "resourcepack", "shader", "datapack"]
	var body := _screen("Mods: %s (%s)" % [iname, inst.get("loader", "")], show_instances)

	var search_row := HBoxContainer.new()
	search_row.add_theme_constant_override("separation", 10)
	body.add_child(search_row)
	var query := _line_edit("Search Modrinth")
	query.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	search_row.add_child(query)
	var type_sel := _selector(types, "mod")
	search_row.add_child(type_sel)

	var columns := HBoxContainer.new()
	columns.size_flags_vertical = Control.SIZE_EXPAND_FILL
	columns.add_theme_constant_override("separation", 14)
	body.add_child(columns)

	var left := VBoxContainer.new()
	left.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	left.size_flags_stretch_ratio = 0.8
	columns.add_child(left)
	left.add_child(_label("Installed", 24, ACCENT))
	var installed_scroll := ScrollContainer.new()
	installed_scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	installed_scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	left.add_child(installed_scroll)
	var installed := VBoxContainer.new()
	installed.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	installed_scroll.add_child(installed)

	var right := VBoxContainer.new()
	right.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	columns.add_child(right)
	right.add_child(_label("Modrinth", 24, ACCENT))
	var results_scroll := ScrollContainer.new()
	results_scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	results_scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	right.add_child(results_scroll)
	var results := VBoxContainer.new()
	results.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	results_scroll.add_child(results)

	var refresh_installed := func():
		for c in installed.get_children():
			c.queue_free()
		var mods: Array = Bridge.api("listMods", [iname]) as Array
		if mods.is_empty():
			installed.add_child(_label("Nothing added yet.", 22, TEXT_DIM))
		for m in mods:
			var slug := str(m.get("slug", ""))
			var r := HBoxContainer.new()
			var l := _label(slug, 22)
			l.size_flags_horizontal = Control.SIZE_EXPAND_FILL
			r.add_child(l)
			var do_remove := func():
				Bridge.api("removeMod", [iname, slug])
				set_status("Removed " + slug)
				r.queue_free()
			r.add_child(_button("Remove", do_remove, Vector2(120, 44)))
			installed.add_child(r)
	refresh_installed.call()

	var add_project := func(hit: Dictionary):
		var title := str(hit.get("title", hit.get("slug", "")))
		var slug := str(hit.get("slug", ""))
		var ptype := str(hit.get("project_type", "mod"))
		set_status("Adding " + title + "...")
		var f = await modrinth.latest_file(str(hit.get("project_id", "")), mc, ptype, categories)
		if f == null:
			set_status("No file of " + title + " fits " + mc + " " + str(inst.get("loader", "")), true)
			return
		for dep_id in f["dependencies"]:
			var dep_slug: String = await modrinth.project_slug(dep_id)
			if dep_slug == "" or Bridge.api_bool("hasMod", [iname, dep_slug]):
				continue
			var df = await modrinth.latest_file(dep_id, mc, "mod", categories)
			if df != null:
				Bridge.api("addMod", [iname, dep_slug, df["filename"], df["version"], df["url"], "mod"])
		Bridge.api("addMod", [iname, slug, f["filename"], f["version"], f["url"], ptype])
		set_status("Added " + title + ". It downloads the next time you press Play.")
		refresh_installed.call()

	var state := {"offset": 0}
	var fns := {}   # lets the search function call itself for "More"
	var run_search := func(reset: bool):
		if reset:
			state["offset"] = 0
			for c in results.get_children():
				c.queue_free()
		set_status("Searching...")
		var hits = await modrinth.search(query.text.strip_edges(), mc, str(type_sel.get_meta("value", "mod")), categories, state["offset"])
		if hits == null:
			set_status("Could not reach Modrinth. Is the headset online?", true)
			return
		set_status("")
		if hits.is_empty() and reset:
			results.add_child(_label("Nothing found.", 22, TEXT_DIM))
		for hit in hits:
			var row := PanelContainer.new()
			var h := HBoxContainer.new()
			row.add_child(h)
			var col := VBoxContainer.new()
			col.size_flags_horizontal = Control.SIZE_EXPAND_FILL
			h.add_child(col)
			col.add_child(_label(str(hit.get("title", "")), 24))
			var desc := _label(str(hit.get("description", "")), 16, TEXT_DIM)
			desc.max_lines_visible = 2
			col.add_child(desc)
			var slug := str(hit.get("slug", ""))
			var add := _button("Add", Callable(), Vector2(100, 46))
			add.disabled = Bridge.api_bool("hasMod", [iname, slug])
			var do_add := func():
				add.disabled = true
				await add_project.call(hit)
			add.pressed.connect(do_add)
			h.add_child(add)
			results.add_child(row)
		state["offset"] += hits.size()
		if hits.size() >= 12:
			var more := _button("More", Callable(), Vector2(0, 46))
			var do_more := func():
				more.queue_free()
				await fns["search"].call(false)
			more.pressed.connect(do_more)
			results.add_child(more)

	fns["search"] = run_search
	query.text_submitted.connect(func(_t): run_search.call(true))
	search_row.add_child(_button("Search", func(): run_search.call(true), Vector2(150, 54)))
