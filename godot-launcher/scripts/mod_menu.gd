extends VBoxContainer
## The mod menu of one instance, laid out like the Unity launcher's Mod Management: a search bar, tabs for mods,
## modpacks, resource packs, shaders and datapacks, and the results below it (icon, name, author, description), the
## most downloaded first. A modpack makes a new instance of the loader. "Installed" lists what the instance has.

const TABS := [
	["mod", "Mods", "a mod like: LambDynamicLights"],
	["modpack", "Modpacks", "a modpack like: Fabulously Optimized"],
	["resourcepack", "Resource Packs", "a resource pack like: Faithful"],
	["shader", "Shaders", "a shader like: Complementary"],
	["datapack", "Datapacks", "a datapack like: Terralith"],
]

var ui: Control           # ui.gd: its look (buttons, labels) and its status line
var modrinth: Node
var inst: Dictionary
var iname := ""
var mc := ""
var loader := ""
var categories: Array = []

var type := "mod"
var version := ""          # Minecraft version searched for: the instance's, only modpacks can be for another
var version_options: Array = []
var sort_index := 0
var offset := 0
var total := 0
var busy := false
var generation := 0        # a search that is over when a newer one has started is dropped
var installed_view := false

var query: LineEdit
var count: Label
var scroll: ScrollContainer
var results: VBoxContainer
var more_button: Button
var tab_buttons := {}
var installed_button: Button
var sort_button: Button
var version_label: Label
var version_buttons: Array = []


func setup(p_ui: Control, p_inst: Dictionary) -> void:
	ui = p_ui
	modrinth = ui.modrinth
	inst = p_inst
	iname = str(inst.get("name", ""))
	mc = str(inst.get("version", ""))
	loader = str(inst.get("loader", ""))
	version = mc
	categories = Bridge.api("modrinthCategories", [iname]) as Array
	size_flags_vertical = Control.SIZE_EXPAND_FILL
	add_theme_constant_override("separation", 6)
	_build()
	search(true)


func _build() -> void:
	var bar := HBoxContainer.new()
	bar.add_theme_constant_override("separation", 8)
	add_child(bar)
	query = ui._line_edit("")
	query.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	query.text_submitted.connect(func(_t): installed_view = false; search(true))
	bar.add_child(query)
	bar.add_child(ui._icon_button("search", func(): installed_view = false; search(true)))
	var prev: Button = ui._button("<", func(): _step_version(-1), Vector2(48, 54))
	bar.add_child(prev)
	version_label = ui._label(version, 24)
	version_label.custom_minimum_size = Vector2(96, 0)
	version_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	version_label.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	bar.add_child(version_label)
	var next: Button = ui._button(">", func(): _step_version(1), Vector2(48, 54))
	bar.add_child(next)
	version_buttons = [prev, next]

	var tabs := HBoxContainer.new()
	tabs.add_theme_constant_override("separation", 8)
	add_child(tabs)
	for t in TABS:
		var kind: String = t[0]
		var b: Button = ui._button(t[1], func(): select_tab(kind), Vector2(0, 46))
		tabs.add_child(b)
		tab_buttons[kind] = b
	var spacer := Control.new()
	spacer.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	tabs.add_child(spacer)
	sort_button = ui._button("", _next_sort, Vector2(190, 46))
	tabs.add_child(sort_button)
	installed_button = ui._button("", _toggle_installed, Vector2(190, 46))
	tabs.add_child(installed_button)

	count = ui._label("", 22, ui.TEXT_DIM)
	add_child(count)

	scroll = ScrollContainer.new()
	scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	add_child(scroll)
	results = VBoxContainer.new()
	results.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	results.add_theme_constant_override("separation", 6)
	scroll.add_child(results)
	scroll.get_v_scroll_bar().value_changed.connect(_on_scrolled)
	_refresh_controls()


# ---------------------------------------------------------------- tabs, version, sort

func select_tab(kind: String) -> void:
	type = kind
	installed_view = false
	if kind == "modpack":
		version_options = Bridge.api("supportedVersions", [loader]) as Array
		if version_options.is_empty():
			version_options = [mc]
		version = mc if version_options.has(mc) else str(version_options[0])
	else:
		version = mc   # what is added to an instance has to be for its version
	_refresh_controls()
	search(true)


func _step_version(delta: int) -> void:
	if type != "modpack" or version_options.is_empty():
		return
	var i := version_options.find(version)
	version = str(version_options[(i + delta + version_options.size()) % version_options.size()])
	_refresh_controls()
	search(true)


func _next_sort() -> void:
	sort_index = (sort_index + 1) % modrinth.SORTS.size()
	_refresh_controls()
	installed_view = false
	search(true)


func _toggle_installed() -> void:
	installed_view = not installed_view
	_refresh_controls()
	if installed_view:
		show_installed()
	else:
		search(true)


func _refresh_controls() -> void:
	for t in TABS:
		(tab_buttons[t[0]] as Button).disabled = t[0] == type and not installed_view   # the tab you are on is greyed
		if t[0] == type:
			query.placeholder_text = "Search for " + str(t[2])
	var is_pack := type == "modpack"
	for b in version_buttons:
		(b as Button).disabled = not is_pack or installed_view
	version_label.text = version
	sort_button.text = "Sort: " + str(modrinth.SORTS[sort_index][1])
	installed_button.text = "Search" if installed_view else "Installed (%d)" % _installed().size()


func _installed() -> Array:
	var mods = Bridge.api("listMods", [iname])
	return mods if typeof(mods) == TYPE_ARRAY else []


# ---------------------------------------------------------------- results

func _clear_results() -> void:
	more_button = null
	for c in results.get_children():
		c.queue_free()


func search(reset: bool) -> void:
	if installed_view:
		return
	if reset:
		generation += 1
		offset = 0
		total = 0
		_clear_results()
	elif busy:
		return
	var mine := generation
	busy = true
	ui.set_status("Searching...")
	var page = await modrinth.search_page(query.text.strip_edges(), version, type, categories, offset,
			modrinth.PAGE, str(modrinth.SORTS[sort_index][0]))
	if mine != generation:
		return
	busy = false
	if page == null:
		count.text = ""
		ui.set_status("Could not reach Modrinth. Is the headset online?", true)
		return
	ui.set_status("")
	total = int(page["total_hits"])
	count.text = "Found %d Results" % total
	if more_button:
		more_button.queue_free()
		more_button = null
	var hits: Array = page["hits"]
	if hits.is_empty() and reset:
		results.add_child(ui._label("Nothing found.", 22, ui.TEXT_DIM))
	for hit in hits:
		results.add_child(_row(hit))
	offset += hits.size()
	if offset < total and not hits.is_empty():
		more_button = ui._button("More", func(): search(false), Vector2(0, 46))
		results.add_child(more_button)


func _on_scrolled(value: float) -> void:
	var bar := scroll.get_v_scroll_bar()
	if value + bar.page >= bar.max_value - 150.0 and more_button != null and not busy:
		search(false)


func _row(hit: Dictionary) -> Control:
	var row := PanelContainer.new()
	row.add_theme_stylebox_override("panel", ui._flat(Color(0.1, 0.12, 0.14, 0.82), 10, 10))
	var h := HBoxContainer.new()
	h.add_theme_constant_override("separation", 14)
	row.add_child(h)

	var icon_box := PanelContainer.new()
	icon_box.custom_minimum_size = Vector2(64, 64)
	icon_box.add_theme_stylebox_override("panel", ui._flat(Color(0.2, 0.23, 0.26), 8, 0))
	var icon := TextureRect.new()
	icon.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	icon.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	icon.custom_minimum_size = Vector2(64, 64)
	icon_box.add_child(icon)
	h.add_child(icon_box)
	_load_icon(icon, str(hit.get("icon_url", "")))

	var col := VBoxContainer.new()
	col.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	col.add_theme_constant_override("separation", 2)
	h.add_child(col)
	var title_row := HBoxContainer.new()
	title_row.add_theme_constant_override("separation", 10)
	col.add_child(title_row)
	title_row.add_child(ui._label(str(hit.get("title", "")), 26))
	var by: Label = ui._label("by %s    %s downloads" % [hit.get("author", ""), _compact(int(hit.get("downloads", 0)))], 19, ui.TEXT_DIM)
	by.vertical_alignment = VERTICAL_ALIGNMENT_BOTTOM
	title_row.add_child(by)
	var desc: Label = ui._label(str(hit.get("description", "")), 18, ui.TEXT_DIM)
	desc.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	desc.max_lines_visible = 2
	desc.custom_minimum_size = Vector2(0, 46)
	desc.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	col.add_child(desc)

	var slug := str(hit.get("slug", ""))
	if type == "modpack":
		var install: Button = ui._button("Install", Callable(), Vector2(130, 54))
		install.pressed.connect(func():
			install.disabled = true
			await install_pack(hit)
			install.disabled = false)
		h.add_child(install)
	else:
		var add: Button = ui._button("Add", Callable(), Vector2(130, 54))
		var have := Bridge.api_bool("hasMod", [iname, slug])
		add.text = "Installed" if have else "Add"
		add.disabled = have
		add.pressed.connect(func():
			add.disabled = true
			if await add_project(hit):
				add.text = "Installed"
			else:
				add.disabled = false)
		h.add_child(add)
	return row


func _load_icon(rect: TextureRect, url: String) -> void:
	var tex: Texture2D = await modrinth.load_icon(url)
	if tex != null and is_instance_valid(rect):
		rect.texture = tex


static func _compact(n: int) -> String:
	if n >= 1000000:
		return "%.1fM" % (n / 1000000.0)
	if n >= 1000:
		return "%dK" % (n / 1000)
	return str(n)


# ---------------------------------------------------------------- adding

## Adds a mod, resource pack, shader or datapack with the required mods it needs. True if it was added.
func add_project(hit: Dictionary) -> bool:
	var title := str(hit.get("title", hit.get("slug", "")))
	var slug := str(hit.get("slug", ""))
	var ptype := str(hit.get("project_type", type))
	ui.set_status("Adding " + title + "...")
	var f = await modrinth.latest_file(str(hit.get("project_id", "")), mc, ptype, categories)
	if f == null:
		ui.set_status("No file of " + title + " fits " + mc + " " + loader, true)
		return false
	for dep_id in f["dependencies"]:
		var dep_slug: String = await modrinth.project_slug(dep_id)
		if dep_slug == "" or Bridge.api_bool("hasMod", [iname, dep_slug]):
			continue
		var df = await modrinth.latest_file(dep_id, mc, "mod", categories)
		if df != null:
			Bridge.api("addMod", [iname, dep_slug, df["filename"], df["version"], df["url"], "mod"])
	Bridge.api("addMod", [iname, slug, f["filename"], f["version"], f["url"], ptype])
	ui.set_status("Added " + title + ". It downloads the next time you press Play.")
	_refresh_controls()
	return true


## Makes a new instance of this loader from a modpack (for the Minecraft version picked above).
func install_pack(hit: Dictionary) -> void:
	var title := str(hit.get("title", hit.get("slug", "")))
	ui.set_status("Looking for " + title + "...")
	var f = await modrinth.latest_file(str(hit.get("project_id", "")), version, "modpack", categories)
	if f == null:
		ui.set_status("No " + loader + " version of " + title + " for " + version, true)
		return
	var name := instance_name_for(title, _instance_names())
	Bridge.api("installModpack", [name, loader, f["url"], str(hit.get("icon_url", ""))])
	ui.show_progress("Installing " + name + " (modpack)", ui.show_instances)


func _instance_names() -> Array:
	var names: Array = []
	var list = Bridge.api("listInstances")
	if typeof(list) == TYPE_ARRAY:
		for i in list:
			names.append(str(i.get("name", "")))
	return names


## A name for the instance of a modpack: the pack's, without what instance names cannot have, and not one already taken.
static func instance_name_for(title: String, taken: Array) -> String:
	var base := title.replace("/", " ").replace("!", "").strip_edges()
	if base == "":
		base = "Modpack"
	base = base.substr(0, 40).strip_edges()
	var name := base
	var n := 2
	while taken.has(name):
		name = "%s %d" % [base, n]
		n += 1
	return name


# ---------------------------------------------------------------- installed

func show_installed() -> void:
	generation += 1   # a search still running must not fill this list
	busy = false
	_clear_results()
	var mods := _installed()
	count.text = "%d installed" % mods.size()
	if mods.is_empty():
		results.add_child(ui._label("Nothing added yet.", 22, ui.TEXT_DIM))
	for m in mods:
		results.add_child(_installed_row(m))


func _installed_row(m: Dictionary) -> Control:
	var slug := str(m.get("slug", ""))
	var row := PanelContainer.new()
	row.add_theme_stylebox_override("panel", ui._flat(Color(0.1, 0.12, 0.14, 0.82), 10, 10))
	var h := HBoxContainer.new()
	h.add_theme_constant_override("separation", 14)
	row.add_child(h)
	var name_label: Label = ui._label(slug, 24)
	name_label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	h.add_child(name_label)
	h.add_child(ui._label(str(m.get("type", "mod")) + "  " + str(m.get("version", "")), 18, ui.TEXT_DIM))
	h.add_child(ui._button("Remove", func():
		Bridge.api("removeMod", [iname, slug])
		ui.set_status("Removed " + slug)
		row.queue_free()
		_refresh_controls(), Vector2(130, 54)))
	return row
