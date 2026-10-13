extends Node3D
## Starts OpenXR, shows the launcher UI on a panel in front of the player and turns controller rays into mouse input.
## On a desktop without a headset the UI is simply shown full screen so it can be tried with a mouse.
##
## When Minecraft starts, the launcher has to give up the headset first: Minecraft (Vivecraft) opens its own OpenXR
## session in this same process, so ours is shut down and the rendering is switched off.

const UIScript := preload("res://scripts/ui.gd")

const VIEW_SIZE := Vector2i(1200, 600)

# The menu hangs on the wall of the room as one big screen, 8 m x 4 m, like in the Unity launcher
# (positions below are the Unity scene's, converted to this model's coordinates).
const SCREEN_SIZE := Vector2(8.0, 4.0)
# 6 cm in front of the wall it hangs on, so the two surfaces do not fight over the same pixels
const SCREEN_POSITION := Vector3(-1.0, 128.0, 21.94)

# The Minecraft room the launcher is in: the QCWorld model of the Unity launcher (a woodland-mansion library).
# The player stands on the floor of its main hall, looking down the hall (+X).
const WORLD_MESH := "res://assets/world/QCWorld.obj"
const SPAWN_POSITION := Vector3(-1.084, 124.06, 16.42)
const SPAWN_YAW_DEGREES := 180.0   # looking along +Z, at the screen (until the first headset pose arrives)

var xr_interface: XRInterface
var ui: Control
var viewport: SubViewport
var panel: MeshInstance3D
var camera: XRCamera3D
var origin: XROrigin3D
var laser: Array = []
var pointer_down := {}
var in_xr := false
var game_running := false   # Minecraft is in front, this menu is paused
var recentered := false


func _ready() -> void:
	get_viewport().gui_embed_subwindows = true
	xr_interface = XRServer.find_interface("OpenXR")
	if xr_interface and xr_interface.is_initialized():
		in_xr = true
		DisplayServer.window_set_vsync_mode(DisplayServer.VSYNC_DISABLED)
		get_viewport().use_xr = true
		_build_xr_scene()
		xr_interface.pose_recentered.connect(_recenter)   # the Oculus button, held, recenters
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
	e.ambient_light_color = Color(0.95, 0.74, 0.55)   # the room is lit by lanterns
	env.environment = e
	add_child(env)
	var sun := DirectionalLight3D.new()
	sun.rotation_degrees = Vector3(-50, 30, 0)
	sun.light_energy = 0.35
	sun.shadow_enabled = false
	add_child(sun)
	var mesh := load(WORLD_MESH) as Mesh
	if mesh == null:
		return
	# blocky textures: no smoothing, and cut-out transparency for plants, glass and leaves. The light is baked into the
	# vertex colours (tools/prepare_world.py), so the materials show texture x vertex colour and nothing else.
	for i in range(mesh.get_surface_count()):
		var mat := mesh.surface_get_material(i) as BaseMaterial3D
		if mat:
			mat.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
			mat.vertex_color_use_as_albedo = true
			mat.vertex_color_is_srgb = true
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
	camera.near = 0.1    # more depth precision than the default, which makes coplanar surfaces flicker less
	camera.far = 300.0
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

	_build_panel()


func _build_panel() -> void:
	panel = MeshInstance3D.new()
	var quad := QuadMesh.new()
	quad.size = SCREEN_SIZE
	panel.mesh = quad
	var pm := StandardMaterial3D.new()
	pm.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	pm.albedo_texture = viewport.get_texture()
	panel.material_override = pm
	panel.position = SCREEN_POSITION
	panel.rotation_degrees = Vector3(0, 180, 0)   # the quad faces +Z, the player is on the -Z side
	add_child(panel)


func _process(_delta: float) -> void:
	if not in_xr:
		return
	# the first frames have no head pose yet; once there is one (the head is above the floor), line up with the room
	if not recentered and camera.position.y > 0.3:
		_recenter()
	for l in laser:
		_update_pointer(l)


## Turns and moves the tracking space so the player stands at the spawn point looking at the screen, whichever way
## the headset's own axes point (they follow where you were facing when the app started, or the room set-up).
func _recenter() -> void:
	if camera == null or origin == null:
		return
	var head: Transform3D = camera.transform   # relative to the origin
	var head_yaw := head.basis.get_euler().y
	origin.rotation = Vector3(0.0, deg_to_rad(SPAWN_YAW_DEGREES) - head_yaw, 0.0)
	var flat := Vector3(head.origin.x, 0.0, head.origin.z)
	origin.position = SPAWN_POSITION - origin.basis * flat
	recentered = true


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


## Starts Minecraft in its own activity and process (GameActivity). This menu pauses while the game is in front, and
## comes back, showing the main screen, when the game ends.
func _start_game() -> void:
	game_running = true
	Config.save_config()
	Bridge.api("startGameProcess", [Config.last_instance, Config.last_account_uuid])


func _notification(what: int) -> void:
	if what == NOTIFICATION_APPLICATION_RESUMED and game_running:
		game_running = false
		if ui:
			ui.hide_logo()
			ui.show_home()
