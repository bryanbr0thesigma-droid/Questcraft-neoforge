extends Node
## Modrinth's public API (the same one the Unity launcher's Mod Manager uses).

const BASE := "https://api.modrinth.com/v2"
const USER_AGENT := "User-Agent: QuestCraft-Godot/1.0 (github.com/bryanbr0thesigma-droid/Questcraft-neoforge)"


const PAGE := 12
## Modrinth's sort orders (its "index" parameter), with what the menu calls them. The most downloaded come first.
const SORTS := [["downloads", "Popular"], ["relevance", "Relevance"], ["follows", "Followed"], ["newest", "Newest"], ["updated", "Updated"]]

## Canned answers instead of the network (the desktop test rigs have none), see tools/mod_menu_test.gd.
var mock := false
var _icons := {}   # url -> Texture2D (or null when it could not be loaded)


func get_json(path: String, query: Dictionary = {}) -> Variant:
	var req := HTTPRequest.new()
	add_child(req)
	var parts := PackedStringArray()
	for key in query:
		parts.append(str(key) + "=" + str(query[key]).uri_encode())
	var url := BASE + path
	if parts.size() > 0:
		url += "?" + "&".join(parts)
	if req.request(url, [USER_AGENT]) != OK:
		req.queue_free()
		return null
	var done: Array = await req.request_completed
	req.queue_free()
	if done[0] != HTTPRequest.RESULT_SUCCESS or done[1] != 200:
		return null
	return JSON.parse_string((done[3] as PackedByteArray).get_string_from_utf8())


## Search for projects that work with a Minecraft version. `categories` are Modrinth loader categories
## (any of them is fine, Quilt accepts Fabric mods); they apply to mods and modpacks.
func search(text: String, mc_version: String, project_type: String, categories: Array, offset: int) -> Variant:
	var page = await search_page(text, mc_version, project_type, categories, offset, PAGE)
	return page.get("hits", []) if typeof(page) == TYPE_DICTIONARY else null


## One page of search results: {"hits": [...], "total_hits": n}, or null if Modrinth could not be reached. `sort` is one
## of SORTS' first entries; the most downloaded come first unless asked otherwise, also with no search text.
func search_page(text: String, mc_version: String, project_type: String, categories: Array, offset: int, limit: int,
		sort := "downloads") -> Variant:
	if mock:
		return _mock_page(text, project_type, offset, limit)
	var facets: Array = [["versions:" + mc_version], ["project_type:" + project_type]]
	if (project_type == "mod" or project_type == "modpack") and categories.size() > 0:
		var any: Array = []
		for c in categories:
			any.append("categories:" + str(c))
		facets.append(any)
	var found = await get_json("/search", {
		"query": text, "facets": JSON.stringify(facets), "offset": offset, "limit": limit, "index": sort,
	})
	if typeof(found) != TYPE_DICTIONARY:
		return null
	return {"hits": found.get("hits", []), "total_hits": int(found.get("total_hits", 0))}


## The picture of a project (its icon_url), or null. Pictures are kept, a project is shown again and again.
func load_icon(url: String) -> Texture2D:
	if url == "":
		return null
	if mock and url == "mock://icon":
		return load("res://assets/icon/main_192.png")
	if _icons.has(url):
		return _icons[url]
	_icons[url] = null   # a second request for it while this one runs does not start another
	var req := HTTPRequest.new()
	add_child(req)
	if req.request(url, [USER_AGENT]) != OK:
		req.queue_free()
		return null
	var done: Array = await req.request_completed
	req.queue_free()
	if done[0] != HTTPRequest.RESULT_SUCCESS or done[1] != 200:
		_icons.erase(url)
		return null
	var image := decode_image(done[3])
	if image == null:
		return null
	var tex := ImageTexture.create_from_image(image)
	_icons[url] = tex
	return tex


## Modrinth's pictures are PNG, JPEG or WebP (the file's own bytes tell which, the address does not always).
static func decode_image(bytes: PackedByteArray) -> Image:
	if bytes.size() < 12:
		return null
	var image := Image.new()
	var err := ERR_FILE_UNRECOGNIZED
	if bytes[0] == 0x89 and bytes[1] == 0x50:
		err = image.load_png_from_buffer(bytes)
	elif bytes[0] == 0xFF and bytes[1] == 0xD8:
		err = image.load_jpg_from_buffer(bytes)
	elif bytes[0] == 0x52 and bytes[1] == 0x49 and bytes[8] == 0x57:   # "RIFF....WEBP"
		err = image.load_webp_from_buffer(bytes)
	if err != OK or image.is_empty():
		return null
	return image


func _mock_page(text: String, project_type: String, offset: int, limit: int) -> Dictionary:
	var names := ["Fabric API", "Sodium", "Iris Shaders", "Entity Culling", "Lithium", "ModMenu", "Cloth Config",
			"Simple Voice Chat", "Xaero's Minimap", "JEI", "Create", "Terralith", "Faithful 32x", "Complementary", "Vein Miner"]
	var hits: Array = []
	var total := 40
	for i in range(offset, mini(offset + limit, total)):
		var title: String = names[i % names.size()] + ("" if i < names.size() else " " + str(i))
		if text != "" and title.to_lower().find(text.to_lower()) < 0:
			continue
		hits.append({
			"project_id": "mock" + str(i), "slug": title.to_lower().replace(" ", "-"), "title": title,
			"author": "someone" + str(i % 5), "icon_url": "mock://icon" if i % 3 != 2 else "", "project_type": project_type,
			"description": "A long description of " + title + " that goes on and on, so that the row has to cut it after two lines on a screen this size.",
			"downloads": 1000000 - i * 23000,
		})
	return {"hits": hits, "total_hits": total if text == "" else hits.size()}


## The newest file of a project for a Minecraft version (and loaders, for mods).
## Returns {"filename", "url", "version", "dependencies": [project ids]} or null.
func latest_file(project_id: String, mc_version: String, project_type: String, categories: Array) -> Variant:
	if mock:
		return {"filename": project_id + ".jar", "url": "https://cdn.example/" + project_id, "version": "1.0", "dependencies": []}
	var query := {"game_versions": JSON.stringify([mc_version])}
	if (project_type == "mod" or project_type == "modpack") and categories.size() > 0:
		query["loaders"] = JSON.stringify(categories)
	var versions = await get_json("/project/" + project_id + "/version", query)
	if typeof(versions) != TYPE_ARRAY or versions.is_empty():
		return null
	var version: Dictionary = versions[0]
	var files: Array = version.get("files", [])
	if files.is_empty():
		return null
	var file: Dictionary = files[0]
	for f in files:
		if f.get("primary", false):
			file = f
			break
	var deps: Array = []
	for d in version.get("dependencies", []):
		if d.get("dependency_type", "") == "required" and d.get("project_id") != null:
			deps.append(str(d["project_id"]))
	return {"filename": file.get("filename", ""), "url": file.get("url", ""),
			"version": version.get("version_number", ""), "dependencies": deps}


func project_slug(project_id: String) -> String:
	var project = await get_json("/project/" + project_id)
	if typeof(project) == TYPE_DICTIONARY:
		return str(project.get("slug", ""))
	return ""
