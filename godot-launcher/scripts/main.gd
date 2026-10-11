extends Node3D
## Starts OpenXR, shows the launcher UI on a panel in front of the player and turns controller rays into mouse input.
## On a desktop without a headset the UI is simply shown full screen so it can be tried with a mouse.
##
## When Minecraft starts, the launcher has to give up the headset first: Minecraft (Vivecraft) opens its own OpenXR
## session in this same process, so ours is shut down and the rendering is switched off.

const UIScript := preload("res://scripts/ui.gd")

const PANEL_WIDTH := 3.0
const PANEL_DISTANCE := 2.4
const VIEW_SIZE := Vector2i(1280, 720)

# The Minecraft room the launcher is in: the QCWorld model of the Unity launcher (a woodland-mansion library).
# The player stands on the floor of its main hall, looking down the hall (+X).
const WORLD_MESH := "res://assets/world/QCWorld.obj"
const SPAWN_POSITION := Vector3(-15.0, 124.0, 5.0)
const SPAWN_YAW_DEGREES := -90.0

var xr_interface: XRInterface
var ui: Control
var viewport: SubViewport
var panel: MeshInstance3D
var camera: XRCamera3D
var origin: XROrigin3D
var laser: Array = []
var pointer_down := {}
var placed := false
var in_xr := false


func _ready() -> void:
	get_viewport().gui_embed_subwindows = true
	xr_interface = XRServer.find_interface("OpenXR")
	if xr_interface and xr_interface.is_initialized():
		in_xr = true
		DisplayServer.window_set_vsync_mode(DisplayServer.VSYNC_DISABLED)
		get_viewport().use_xr = true
		_build_xr_scene()
	else:
		_build_desktop_scene()


func _build_desktop_scene() -> void:
	var layer := CanvasLayer.new()
	add_child(layer)
	ui = UIScript.new()
	layer.add_child(ui)
	ui.request_start_game.connect(_start_game)


func _build_world() -> void:
	var env := WorldEnvironment.new()
	var e := Environment.new()
	e.background_mode = Environment.BG_COLOR
	e.background_color = Color("87b4ff")
	e.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	e.ambient_light_color = Color(0.82, 0.82, 0.86)
	env.environment = e
	add_child(env)
	var sun := DirectionalLight3D.new()
	sun.rotation_degrees = Vector3(-50, 30, 0)
	sun.light_energy = 0.7
	sun.shadow_enabled = false
	add_child(sun)
	var mesh := load(WORLD_MESH) as Mesh
	if mesh == null:
		return
	# blocky textures: no smoothing, and cut-out transparency for plants, glass and leaves
	for i in range(mesh.get_surface_count()):
		var mat := mesh.surface_get_material(i) as BaseMaterial3D
		if mat:
			mat.texture_filter = BaseMaterial3D.TEXTURE_FILTER_NEAREST
			mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA_SCISSOR
			mat.alpha_scissor_threshold = 0.5
			mat.cull_mode = BaseMaterial3D.CULL_DISABLED
	var room := MeshInstance3D.new()
	room.mesh = mesh
	add_child(room)


func _build_xr_scene() -> void:
	_build_world()

	origin = XROrigin3D.new()
	origin.position = SPAWN_POSITION
	origin.rotation_degrees = Vector3(0, SPAWN_YAW_DEGREES, 0)
	add_child(origin)
	camera = XRCamera3D.new()
	origin.add_child(camera)
	for hand in ["left_hand", "right_hand"]:
		var c := XRController3D.new()
		c.tracker = hand
		c.pose = "aim"
		c.button_pressed.connect(_on_button.bind(hand, true))
		c.button_released.connect(_on_button.bind(hand, false))
		origin.add_child(c)
		var ray := MeshInstance3D.new()
		var box := BoxMesh.new()
		box.size = Vector3(0.004, 0.004, 1.0)
		ray.mesh = box
		var mat := StandardMaterial3D.new()
		mat.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
		mat.albedo_color = Color("4caf50")
		ray.material_override = mat
		ray.visible = false
		add_child(ray)
		laser.append({"controller": c, "ray": ray, "hand": hand, "last": Vector2(-1, -1)})

	viewport = SubViewport.new()
	viewport.size = VIEW_SIZE
	viewport.disable_3d = true
	viewport.transparent_bg = false
	viewport.gui_embed_subwindows = true
	viewport.render_target_update_mode = SubViewport.UPDATE_ALWAYS
	add_child(viewport)
	ui = UIScript.new()
	viewport.add_child(ui)
	ui.request_start_game.connect(_start_game)

	panel = MeshInstance3D.new()
	var quad := QuadMesh.new()
	quad.size = Vector2(PANEL_WIDTH, PANEL_WIDTH * VIEW_SIZE.y / VIEW_SIZE.x)
	panel.mesh = quad
	var pm := StandardMaterial3D.new()
	pm.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	pm.albedo_texture = viewport.get_texture()
	panel.material_override = pm
	add_child(panel)
	panel.visible = false


func _process(_delta: float) -> void:
	if not in_xr:
		return
	if not placed:
		_place_panel()
	for l in laser:
		_update_pointer(l)


## Puts the panel in front of where the player looks the first time the headset has a pose.
func _place_panel() -> void:
	if camera.global_position.length() < 0.001 and camera.transform.basis == Basis.IDENTITY:
		return
	var head := camera.global_position
	var forward := -camera.global_transform.basis.z
	forward.y = 0.0
	if forward.length() < 0.01:
		forward = Vector3(0, 0, -1)
	forward = forward.normalized()
	panel.global_position = head + forward * PANEL_DISTANCE
	panel.look_at(head, Vector3.UP, true)
	panel.visible = true
	placed = true


func _ray_hit(controller: XRController3D) -> Variant:
	var o := controller.global_position
	var d := -controller.global_transform.basis.z
	var n := panel.global_transform.basis.z
	var denom := d.dot(n)
	if absf(denom) < 0.0001:
		return null
	var t := (panel.global_position - o).dot(n) / denom
	if t < 0.05:
		return null
	var hit := o + d * t
	var local := panel.global_transform.affine_inverse() * hit
	var size: Vector2 = (panel.mesh as QuadMesh).size
	var u := local.x / size.x + 0.5
	var v := 0.5 - local.y / size.y
	if u < 0.0 or u > 1.0 or v < 0.0 or v > 1.0:
		return {"hit": hit, "px": null, "t": t}
	return {"hit": hit, "px": Vector2(u * VIEW_SIZE.x, v * VIEW_SIZE.y), "t": t}


func _update_pointer(l: Dictionary) -> void:
	var c: XRController3D = l["controller"]
	var ray: MeshInstance3D = l["ray"]
	if not c.get_is_active() or not panel.visible:
		ray.visible = false
		return
	var h = _ray_hit(c)
	if h == null:
		ray.visible = false
		return
	var length: float = h["t"]
	ray.visible = true
	ray.global_position = c.global_position + (-c.global_transform.basis.z) * (length * 0.5)
	ray.global_transform.basis = c.global_transform.basis
	ray.scale = Vector3(1, 1, length)
	var px = h["px"]
	if px == null:
		return
	var last: Vector2 = l["last"]
	if px != last:
		var motion := InputEventMouseMotion.new()
		motion.position = px
		motion.global_position = px
		motion.relative = px - last if last.x >= 0.0 else Vector2.ZERO
		motion.button_mask = MOUSE_BUTTON_MASK_LEFT if pointer_down.get(l["hand"], false) else 0
		viewport.push_input(motion)
		l["last"] = px
	# thumbstick scrolls the list under the pointer
	var stick: Vector2 = c.get_vector2("primary")
	if absf(stick.y) > 0.6:
		var wheel := InputEventMouseButton.new()
		wheel.position = px
		wheel.global_position = px
		wheel.button_index = MOUSE_BUTTON_WHEEL_UP if stick.y > 0 else MOUSE_BUTTON_WHEEL_DOWN
		wheel.pressed = true
		viewport.push_input(wheel)
		wheel = wheel.duplicate()
		wheel.pressed = false
		viewport.push_input(wheel)


func _on_button(button_name: String, hand: String, pressed: bool) -> void:
	if button_name != "trigger_click" and button_name != "select_button":
		return
	for l in laser:
		if l["hand"] != hand:
			continue
		var h = _ray_hit(l["controller"])
		if h == null or h["px"] == null:
			if not pressed:
				pointer_down[hand] = false
			return
		pointer_down[hand] = pressed
		var click := InputEventMouseButton.new()
		click.position = h["px"]
		click.global_position = h["px"]
		click.button_index = MOUSE_BUTTON_LEFT
		click.pressed = pressed
		click.button_mask = MOUSE_BUTTON_MASK_LEFT if pressed else 0
		viewport.push_input(click)
		# haptic click
		(l["controller"] as XRController3D).trigger_haptic_pulse("haptic", 0.0, 0.3, 0.05, 0.0)


## Gives up the headset and lets Pojlib run Minecraft in this process.
func _start_game() -> void:
	if in_xr:
		panel.visible = false
		for l in laser:
			(l["ray"] as MeshInstance3D).visible = false
		await get_tree().create_timer(0.3).timeout
		get_viewport().use_xr = false
		if xr_interface and xr_interface.is_initialized():
			xr_interface.uninitialize()
		in_xr = false
		await get_tree().create_timer(0.5).timeout
	Bridge.api("startGame")
	# nothing more to draw; keep the engine from burning battery while the game runs
	Engine.max_fps = 5
	OS.low_processor_usage_mode = true
