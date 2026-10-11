extends Control
## The launcher's screens. Built in code so the whole UI is a few readable files: sign in, instances, new instance,
## mods and the progress screen. It is drawn into a viewport that the headset shows on a panel in front of you.

signal request_start_game

const Modrinth := preload("res://scripts/modrinth.gd")
const VKeyboard := preload("res://scripts/vkeyboard.gd")

const ACCENT := Color("4caf50")
const BG := Color("14181c")
const PANEL := Color("1e252b")
const BUTTON := Color("2c363e")
const BUTTON_HOVER := Color("3a4852")
const TEXT_DIM := Color("9aa7b1")

var modrinth: Node
var content: VBoxContainer
var status_label: Label
var account_label: Label
var keyboard: PanelContainer
var poll_timer: Timer

var _poll := Callable()
var _saw_working := false
var _loaders: Array = []
var _logged_in := false


func _ready() -> void:
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


# ---------------------------------------------------------------- frame

func _make_theme() -> Theme:
	var t := Theme.new()
	t.default_font_size = 28
	for kind in ["Button", "OptionButton"]:
		t.set_stylebox("normal", kind, _flat(BUTTON))
		t.set_stylebox("hover", kind, _flat(BUTTON_HOVER))
		t.set_stylebox("pressed", kind, _flat(ACCENT.darkened(0.2)))
		t.set_stylebox("disabled", kind, _flat(BUTTON.darkened(0.3)))
		t.set_stylebox("focus", kind, _flat(BUTTON_HOVER))
		t.set_color("font_disabled_color", kind, TEXT_DIM)
	t.set_stylebox("normal", "LineEdit", _flat(Color("0e1114")))
	t.set_stylebox("focus", "LineEdit", _flat(Color("0e1114"), 10, 14, ACCENT))
	t.set_stylebox("panel", "PanelContainer", _flat(PANEL, 14, 14))
	t.set_stylebox("fill", "ProgressBar", _flat(ACCENT, 8, 0))
	t.set_stylebox("background", "ProgressBar", _flat(Color("0e1114"), 8, 0))
	return t


func _flat(color: Color, radius := 10, margin := 14, border := Color(0, 0, 0, 0)) -> StyleBoxFlat:
	var s := StyleBoxFlat.new()
	s.bg_color = color
	s.set_corner_radius_all(radius)
	s.set_content_margin_all(margin)
	if border.a > 0.0:
		s.set_border_width_all(3)
		s.border_color = border
	return s


func _build_frame() -> void:
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	var bg := ColorRect.new()
	bg.color = BG
	bg.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	add_child(bg)
	var margin := MarginContainer.new()
	margin.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	for side in ["left", "right", "top", "bottom"]:
		margin.add_theme_constant_override("margin_" + side, 24)
	add_child(margin)
	var root := VBoxContainer.new()
	root.add_theme_constant_override("separation", 12)
	margin.add_child(root)

	var header := HBoxContainer.new()
	header.add_theme_constant_override("separation", 14)
	root.add_child(header)
	var title := Label.new()
	title.text = "QuestCraft"
	title.add_theme_font_size_override("font_size", 44)
	title.add_theme_color_override("font_color", ACCENT)
	header.add_child(title)
	var spacer := Control.new()
	spacer.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	header.add_child(spacer)
	account_label = Label.new()
	account_label.add_theme_color_override("font_color", TEXT_DIM)
	header.add_child(account_label)
	header.add_child(_button("Memory", show_settings))
	header.add_child(_button("Accounts", show_login))

	content = VBoxContainer.new()
	content.size_flags_vertical = Control.SIZE_EXPAND_FILL
	content.add_theme_constant_override("separation", 10)
	root.add_child(content)

	status_label = Label.new()
	status_label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	status_label.add_theme_color_override("font_color", TEXT_DIM)
	root.add_child(status_label)

	keyboard = VKeyboard.new()
	root.add_child(keyboard)


# ---------------------------------------------------------------- widgets

func _button(text: String, cb: Callable, min_size := Vector2(0, 60)) -> Button:
	var b := Button.new()
	b.text = text
	b.custom_minimum_size = min_size
	if cb.is_valid():
		b.pressed.connect(cb)
	return b


func _label(text: String, size := 28, color := Color.WHITE) -> Label:
	var l := Label.new()
	l.text = text
	if text.length() > 50:   # long text wraps, short labels keep their natural width (a wrapping label in a row collapses)
		l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		l.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	l.add_theme_font_size_override("font_size", size)
	l.add_theme_color_override("font_color", color)
	return l


func _line_edit(placeholder: String, text := "") -> LineEdit:
	var e := LineEdit.new()
	e.placeholder_text = placeholder
	e.text = text
	e.custom_minimum_size = Vector2(0, 60)
	e.virtual_keyboard_enabled = false   # the system keyboard does not exist in here, use the panel keyboard
	e.focus_entered.connect(func(): keyboard.attach(e))
	return e


## A "< value >" chooser, used instead of drop-down lists (pop-up windows do not work well on a VR panel).
## Read the choice with .get_meta("value").
func _selector(options: Array, selected: String, changed := Callable()) -> HBoxContainer:
	var row := HBoxContainer.new()
	row.add_theme_constant_override("separation", 8)
	var shown := Label.new()
	shown.custom_minimum_size = Vector2(260, 0)
	shown.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	shown.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	var index := maxi(0, options.find(selected))
	var apply := func():
		var v: String = str(options[index]) if options.size() > 0 else ""
		shown.text = v
		row.set_meta("value", v)
		if changed.is_valid():
			changed.call(v)
	var step := func(delta: int):
		if options.size() == 0:
			return
		index = (index + delta + options.size()) % options.size()
		apply.call()
	row.add_child(_button("<", func(): step.call(-1), Vector2(64, 60)))
	row.add_child(shown)
	row.add_child(_button(">", func(): step.call(1), Vector2(64, 60)))
	apply.call()
	return row


func _clear() -> void:
	_poll = Callable()
	keyboard.visible = false
	for c in content.get_children():
		c.queue_free()


func set_status(text: String, is_error := false) -> void:
	status_label.text = text
	status_label.add_theme_color_override("font_color", Color("ff6b6b") if is_error else TEXT_DIM)


func _on_poll() -> void:
	if _poll.is_valid():
		_poll.call()


# ---------------------------------------------------------------- sign in

func _startup_login() -> void:
	var accounts: Array = Bridge.api("listAccounts") as Array
	for a in accounts:
		if a.get("uuid", "") == Config.last_account_uuid:
			_sign_in(Config.last_account_uuid)
			return
	show_login()


func show_login() -> void:
	_clear()
	content.add_child(_label("Sign in", 40))
	content.add_child(_label("Minecraft needs a Microsoft account that owns Java Edition.", 26, TEXT_DIM))
	var accounts: Array = Bridge.api("listAccounts") as Array
	for a in accounts:
		var uuid: String = str(a.get("uuid", ""))
		content.add_child(_button("Continue as " + str(a.get("name", "?")), func(): _sign_in(uuid), Vector2(0, 70)))
	content.add_child(_button("Add a Microsoft account", func(): _sign_in(""), Vector2(0, 70)))
	if _logged_in:
		content.add_child(_button("Back", show_home))


func _sign_in(uuid: String) -> void:
	_clear()
	_logged_in = false
	content.add_child(_label("Signing in...", 36))
	Bridge.api("login", [uuid])
	_poll = _poll_login


var _device_shown := false


func _poll_login() -> void:
	var state = Bridge.api("loginState")
	if typeof(state) != TYPE_DICTIONARY:
		return
	if state.get("loggedIn", false):
		_logged_in = true
		_device_shown = false
		Config.last_account_uuid = str(state.get("uuid", ""))
		Config.save_config()
		account_label.text = str(state.get("name", ""))
		if state.get("demo", false):
			account_label.text += " (demo)"
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
	_clear()
	_poll = poll
	var url_re := RegEx.create_from_string("https?://[^\\s]+")
	var code_re := RegEx.create_from_string("code\\s+([A-Za-z0-9]+)")
	var url_match := url_re.search(message)
	var code_match := code_re.search(message)
	var url := url_match.get_string() if url_match else "https://www.microsoft.com/link"
	url = url.rstrip(".,")
	content.add_child(_label("Sign in with your Microsoft account", 38))
	content.add_child(_label("1. Open " + url + " (on the headset browser, or on any phone or computer).", 28))
	if code_match:
		content.add_child(_label("2. Enter this code:", 28))
		content.add_child(_label(code_match.get_string(1), 72, ACCENT))
	else:
		content.add_child(_label(message, 28))
	content.add_child(_button("Open the page in the browser", func(): OS.shell_open(url), Vector2(0, 70)))
	content.add_child(_label("This screen continues by itself once you have signed in.", 24, TEXT_DIM))


# ---------------------------------------------------------------- instances

func show_home() -> void:
	_clear()
	set_status("")
	var bar := HBoxContainer.new()
	bar.add_theme_constant_override("separation", 12)
	content.add_child(bar)
	bar.add_child(_label("Instances", 38))
	var sp := Control.new()
	sp.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	bar.add_child(sp)
	bar.add_child(_button("New instance", show_create, Vector2(260, 64)))

	var scroll := ScrollContainer.new()
	scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	content.add_child(scroll)
	var list := VBoxContainer.new()
	list.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	list.add_theme_constant_override("separation", 10)
	scroll.add_child(list)

	var instances: Array = Bridge.api("listInstances") as Array
	if instances.is_empty():
		list.add_child(_label("No instances yet. Make one with \"New instance\", then press Play.", 28, TEXT_DIM))
	for inst in instances:
		list.add_child(_instance_row(inst))


func _instance_row(inst: Dictionary) -> Control:
	var iname: String = str(inst.get("name", ""))
	var panel := PanelContainer.new()
	var row := HBoxContainer.new()
	row.add_theme_constant_override("separation", 12)
	panel.add_child(row)
	var info := VBoxContainer.new()
	info.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	row.add_child(info)
	info.add_child(_label(iname, 32))
	info.add_child(_label("%s  |  %s  |  %d mods" % [inst.get("version", "?"), inst.get("loader", "?"), int(inst.get("mods", 0))], 24, TEXT_DIM))
	row.add_child(_button("Play", func(): start_play(iname), Vector2(150, 64)))
	row.add_child(_button("Mods", func(): show_mods(inst), Vector2(150, 64)))
	var del := _button("Delete", Callable(), Vector2(150, 64))
	var do_delete := func():
		if del.text == "Delete":
			del.text = "Sure?"
		else:
			Bridge.api("deleteInstance", [iname])
			show_home()
	del.pressed.connect(do_delete)
	row.add_child(del)
	return panel


func show_create() -> void:
	_clear()
	content.add_child(_label("New instance", 40))
	var loader_row := HBoxContainer.new()
	loader_row.add_theme_constant_override("separation", 14)
	content.add_child(loader_row)
	loader_row.add_child(_label("Mod loader", 28))
	var version_holder := HBoxContainer.new()
	var name_edit := _line_edit("Name")
	var versions_info := _label("", 24, TEXT_DIM)
	var state := {"version": ""}

	var rebuild_versions := func(loader: String):
		for c in version_holder.get_children():
			c.queue_free()
		var versions: Array = Bridge.api("supportedVersions", [loader]) as Array
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
	content.add_child(version_row)
	version_row.add_child(_label("Minecraft version", 28))
	version_row.add_child(version_holder)
	content.add_child(versions_info)
	content.add_child(_label("Name", 28))
	content.add_child(name_edit)

	var buttons := HBoxContainer.new()
	buttons.add_theme_constant_override("separation", 12)
	content.add_child(buttons)
	var do_create := func():
		var loader: String = str(loader_sel.get_meta("value", ""))
		var version: String = str(state["version"])
		var nm := name_edit.text.strip_edges()
		if version == "" or nm == "":
			set_status("Pick a version and give the instance a name.", true)
			return
		Config.save_config()
		Bridge.api("createInstance", [nm, version, loader])
		show_progress("Installing " + nm + " (" + loader + " " + version + ")", show_home)
	buttons.add_child(_button("Create", do_create, Vector2(240, 64)))
	buttons.add_child(_button("Back", show_home, Vector2(200, 64)))
	rebuild_versions.call(str(loader_sel.get_meta("value", "")))


# ---------------------------------------------------------------- progress / play

func show_progress(title: String, when_done: Callable) -> void:
	_clear()
	_saw_working = false
	content.add_child(_label(title, 38))
	var task := _label("", 28, TEXT_DIM)
	content.add_child(task)
	var bar := ProgressBar.new()
	bar.custom_minimum_size = Vector2(0, 34)
	bar.max_value = 100.0
	bar.show_percentage = false
	content.add_child(bar)
	var logbox := _label("", 20, TEXT_DIM)
	logbox.max_lines_visible = 9
	content.add_child(logbox)
	_poll = func():
		var p = Bridge.api("progress")
		if typeof(p) != TYPE_DICTIONARY:
			return
		var phase := str(p.get("phase", ""))
		task.text = str(p.get("task", ""))
		bar.value = float(p.get("percent", 0.0))
		logbox.text = str(Bridge.api("logTail", [9]))
		if phase == "working":
			_saw_working = true
		elif phase == "error":
			Bridge.api("acknowledge")
			set_status(str(p.get("error", "Something went wrong")), true)
			show_home()
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


# ---------------------------------------------------------------- settings

func show_settings() -> void:
	_clear()
	content.add_child(_label("Memory for the game", 40))
	content.add_child(_label("In megabytes. Leave it empty for the default (1800). Quest 2 has little memory to spare, try 2048 to 3072 on Quest 3.", 26, TEXT_DIM))
	var mem := _line_edit("e.g. 2048", Config.memory_mb)
	content.add_child(mem)
	var row := HBoxContainer.new()
	row.add_theme_constant_override("separation", 12)
	content.add_child(row)
	var do_save := func():
		Config.memory_mb = mem.text.strip_edges()
		Config.save_config()
		if Config.memory_mb != "":
			Bridge.api("setMemory", [Config.memory_mb])
		show_home()
	row.add_child(_button("Save", do_save, Vector2(200, 64)))
	row.add_child(_button("Back", show_home, Vector2(200, 64)))
	var log_title := _label("Latest logbox", 28)
	content.add_child(log_title)
	var logbox := _label(str(Bridge.api("logTail", [12])), 18, TEXT_DIM)
	logbox.max_lines_visible = 12
	content.add_child(logbox)


# ---------------------------------------------------------------- mods

func show_mods(inst: Dictionary) -> void:
	_clear()
	var iname: String = str(inst.get("name", ""))
	var mc: String = str(inst.get("version", ""))
	var categories: Array = Bridge.api("modrinthCategories", [iname]) as Array
	var types := ["mod", "resourcepack", "shader", "datapack"]

	var top := HBoxContainer.new()
	top.add_theme_constant_override("separation", 12)
	content.add_child(top)
	top.add_child(_label("Mods: %s (%s %s)" % [iname, inst.get("loader", ""), mc], 34))
	var sp := Control.new()
	sp.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	top.add_child(sp)
	top.add_child(_button("Back", show_home, Vector2(160, 60)))

	var search_row := HBoxContainer.new()
	search_row.add_theme_constant_override("separation", 12)
	content.add_child(search_row)
	var query := _line_edit("Search Modrinth")
	query.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	search_row.add_child(query)
	var type_sel := _selector(types, "mod")
	search_row.add_child(type_sel)

	var columns := HBoxContainer.new()
	columns.size_flags_vertical = Control.SIZE_EXPAND_FILL
	columns.add_theme_constant_override("separation", 14)
	content.add_child(columns)

	var left := VBoxContainer.new()
	left.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	left.size_flags_stretch_ratio = 0.8
	columns.add_child(left)
	left.add_child(_label("Installed", 28, ACCENT))
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
	right.add_child(_label("Modrinth", 28, ACCENT))
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
			installed.add_child(_label("Nothing added yet.", 24, TEXT_DIM))
		for m in mods:
			var slug := str(m.get("slug", ""))
			var r := HBoxContainer.new()
			var l := _label(slug, 24)
			l.size_flags_horizontal = Control.SIZE_EXPAND_FILL
			r.add_child(l)
			var do_remove := func():
				Bridge.api("removeMod", [iname, slug])
				set_status("Removed " + slug)
			r.add_child(_button("Remove", do_remove, Vector2(130, 52)))
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
			results.add_child(_label("Nothing found.", 24, TEXT_DIM))
		for hit in hits:
			var row := PanelContainer.new()
			var h := HBoxContainer.new()
			row.add_child(h)
			var col := VBoxContainer.new()
			col.size_flags_horizontal = Control.SIZE_EXPAND_FILL
			h.add_child(col)
			col.add_child(_label(str(hit.get("title", "")), 26))
			var desc := _label(str(hit.get("description", "")), 18, TEXT_DIM)
			desc.max_lines_visible = 2
			col.add_child(desc)
			var slug := str(hit.get("slug", ""))
			var add := _button("Add", Callable(), Vector2(110, 52))
			add.disabled = Bridge.api_bool("hasMod", [iname, slug])
			var do_add := func():
				add.disabled = true
				await add_project.call(hit)
			add.pressed.connect(do_add)
			h.add_child(add)
			results.add_child(row)
		state["offset"] += hits.size()
		if hits.size() >= 12:
			var more := _button("More", Callable(), Vector2(0, 52))
			var do_more := func():
				more.queue_free()
				await fns["search"].call(false)
			more.pressed.connect(do_more)
			results.add_child(more)

	fns["search"] = run_search
	query.text_submitted.connect(func(_t): run_search.call(true))
	search_row.add_child(_button("Search", func(): run_search.call(true), Vector2(170, 60)))
