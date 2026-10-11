extends Node
## Modrinth's public API (the same one the Unity launcher's Mod Manager uses).

const BASE := "https://api.modrinth.com/v2"
const USER_AGENT := "User-Agent: QuestCraft-Godot/1.0 (github.com/bryanbr0thesigma-droid/Questcraft-neoforge)"


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
## (any of them is fine, Quilt accepts Fabric mods); they only apply to mods.
func search(text: String, mc_version: String, project_type: String, categories: Array, offset: int) -> Variant:
	var facets: Array = [["versions:" + mc_version], ["project_type:" + project_type]]
	if project_type == "mod" and categories.size() > 0:
		var any: Array = []
		for c in categories:
			any.append("categories:" + str(c))
		facets.append(any)
	var found = await get_json("/search", {
		"query": text, "facets": JSON.stringify(facets), "offset": offset, "limit": 12,
	})
	if typeof(found) != TYPE_DICTIONARY:
		return null
	return found.get("hits", [])


## The newest file of a project for a Minecraft version (and loaders, for mods).
## Returns {"filename", "url", "version", "dependencies": [project ids]} or null.
func latest_file(project_id: String, mc_version: String, project_type: String, categories: Array) -> Variant:
	var query := {"game_versions": JSON.stringify([mc_version])}
	if project_type == "mod" and categories.size() > 0:
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
