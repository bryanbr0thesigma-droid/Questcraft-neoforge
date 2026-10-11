extends PanelContainer
## An on-screen keyboard for the VR panel (the headset's system keyboard is not available inside the game launcher).
## Attach it to a LineEdit with attach(); its buttons never take focus so the LineEdit keeps it.

var target: LineEdit = null
var shift := false
var _letter_buttons: Array = []

const ROWS := ["1234567890-_", "qwertyuiop", "asdfghjkl", "zxcvbnm.,/"]


func _init() -> void:
	visible = false
	var box := VBoxContainer.new()
	box.add_theme_constant_override("separation", 6)
	add_child(box)
	for row in ROWS:
		var h := HBoxContainer.new()
		h.alignment = BoxContainer.ALIGNMENT_CENTER
		h.add_theme_constant_override("separation", 6)
		box.add_child(h)
		for ch in row:
			var b := _key(ch, Vector2(72, 64))
			b.pressed.connect(_type.bind(ch))
			h.add_child(b)
			if ch >= "a" and ch <= "z":
				_letter_buttons.append(b)
	var last := HBoxContainer.new()
	last.alignment = BoxContainer.ALIGNMENT_CENTER
	last.add_theme_constant_override("separation", 6)
	box.add_child(last)
	var shift_b := _key("Shift", Vector2(130, 64))
	shift_b.toggle_mode = true
	shift_b.toggled.connect(_set_shift)
	last.add_child(shift_b)
	var space := _key("space", Vector2(420, 64))
	space.pressed.connect(_type.bind(" "))
	last.add_child(space)
	var back := _key("<-", Vector2(110, 64))
	back.pressed.connect(_backspace)
	last.add_child(back)
	var clear := _key("Clear", Vector2(130, 64))
	clear.pressed.connect(func(): if target: target.text = "")
	last.add_child(clear)
	var done := _key("Done", Vector2(130, 64))
	done.pressed.connect(_done)
	last.add_child(done)


func _key(label: String, min_size: Vector2) -> Button:
	var b := Button.new()
	b.text = label
	b.custom_minimum_size = min_size
	b.focus_mode = Control.FOCUS_NONE
	return b


func attach(line_edit: LineEdit) -> void:
	target = line_edit
	visible = true


func _set_shift(on: bool) -> void:
	shift = on
	for b in _letter_buttons:
		b.text = b.text.to_upper() if on else b.text.to_lower()


func _type(ch: String) -> void:
	if target == null or not is_instance_valid(target):
		return
	if shift and ch >= "a" and ch <= "z":
		ch = ch.to_upper()
	target.insert_text_at_caret(ch)


func _backspace() -> void:
	if target and is_instance_valid(target):
		target.delete_char_at_caret()


func _done() -> void:
	if target and is_instance_valid(target):
		target.release_focus()
		target.text_submitted.emit(target.text)
	visible = false
	target = null
