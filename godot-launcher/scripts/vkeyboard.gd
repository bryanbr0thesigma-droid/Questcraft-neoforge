extends PanelContainer
## An on-screen keyboard for the VR panel (the headset's system keyboard is not available inside the game launcher).
## Attach it to a LineEdit with attach(); its buttons never take focus so the LineEdit keeps it.
##
## Two layers: letters and numbers, and symbols (# $ @ : ; ! ? and the rest), switched with the "#+=" / "abc" key.

var target: LineEdit = null
var shift := false
var layer := "letters"
var _letter_buttons: Array = []
var _rows: Array = []
var _shift_button: Button
var _layer_button: Button

const LAYERS := {
	"letters": ["1234567890-_", "qwertyuiop", "asdfghjkl", "zxcvbnm.,/"],
	"symbols": ["1234567890-_", "!@#$%^&*()+=", "[]{}<>|" + char(92) + ";:'", "\"`~?,./"],
}


func _init() -> void:
	visible = false
	var box := VBoxContainer.new()
	box.add_theme_constant_override("separation", 6)
	add_child(box)
	for i in range(4):
		var h := HBoxContainer.new()
		h.alignment = BoxContainer.ALIGNMENT_CENTER
		h.add_theme_constant_override("separation", 6)
		box.add_child(h)
		_rows.append(h)
	var last := HBoxContainer.new()
	last.alignment = BoxContainer.ALIGNMENT_CENTER
	last.add_theme_constant_override("separation", 6)
	box.add_child(last)
	_shift_button = _key("Shift", Vector2(130, 64))
	_shift_button.toggle_mode = true
	_shift_button.toggled.connect(_set_shift)
	last.add_child(_shift_button)
	_layer_button = _key("#+=", Vector2(110, 64))
	_layer_button.pressed.connect(toggle_layer)
	last.add_child(_layer_button)
	var space := _key("space", Vector2(330, 64))
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
	_build_rows()


func _key(label: String, min_size: Vector2) -> Button:
	var b := Button.new()
	b.text = label
	b.custom_minimum_size = min_size
	b.focus_mode = Control.FOCUS_NONE
	return b


func _build_rows() -> void:
	_letter_buttons.clear()
	var rows: Array = LAYERS[layer]
	for i in range(_rows.size()):
		var h: HBoxContainer = _rows[i]
		for c in h.get_children():
			h.remove_child(c)
			c.queue_free()
		for ch in rows[i]:
			var b := _key(ch, Vector2(72, 64))
			b.pressed.connect(_type.bind(ch))
			h.add_child(b)
			if ch >= "a" and ch <= "z":
				_letter_buttons.append(b)
				if shift:
					b.text = b.text.to_upper()
	_layer_button.text = "abc" if layer == "symbols" else "#+="
	_shift_button.disabled = layer == "symbols"   # the symbols are what they are


func toggle_layer() -> void:
	layer = "symbols" if layer == "letters" else "letters"
	_build_rows()


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
