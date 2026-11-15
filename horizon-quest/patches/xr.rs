//! A headset (QuestCraft: horizon-quest's OpenXR core, horizon-xr): when one is installed before
//! the renderer starts, the renderer opens its device through it and draws each frame once per eye
//! into the headset's images instead of the window.
//!
//! The headset's poses are in its tracking space (OpenXR's: x right, y up, -z ahead, metres, from
//! where the head was when it started). The world is seen from the player's eyes as the game puts
//! them, turned to the player's yaw: the head's own turn, lean and the eyes' spacing come on top.
use crate::gfx::View;
use glam::{Mat4, Quat, Vec3, Vec4};
use std::sync::{Mutex, MutexGuard, OnceLock, PoisonError};

/// How far an eye sees each way, in radians: left and down negative.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct Fov {
    pub left: f32,
    pub right: f32,
    pub up: f32,
    pub down: f32,
}

/// Where something is and which way it points (x, y, z, w), in the tracking space.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct Pose {
    pub position: [f32; 3],
    pub orientation: [f32; 4],
}

impl Pose {
    fn at(&self) -> Vec3 {
        Vec3::from(self.position)
    }
    fn turn(&self) -> Quat {
        let [x, y, z, w] = self.orientation;
        Quat::from_xyzw(x, y, z, w).normalize()
    }
    /// The way it points: its forward (-z).
    fn forward(&self) -> Vec3 {
        self.turn() * Vec3::NEG_Z
    }
}

/// One eye this frame: where it is and which way it looks (x, y, z, w), and its lens.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct Eye {
    pub position: [f32; 3],
    pub orientation: [f32; 4],
    pub fov: Fov,
}

/// A frame the headset wants drawn: both eyes, at the size of its images.
#[derive(Clone, Copy, Debug)]
pub struct Frame {
    pub eyes: [Eye; 2],
    pub size: [u32; 2],
}

/// The headset as the renderer uses it.
pub trait Headset: Send {
    /// The wgpu instance the headset's runtime can share a device with.
    fn instance(&mut self, flags: wgpu::InstanceFlags) -> Result<wgpu::Instance, String>;
    /// The GPU the headset draws with.
    fn adapter(&mut self, instance: &wgpu::Instance) -> Result<wgpu::Adapter, String>;
    /// The device, as `request_device` would open it with `desc`, and the headset's session on it.
    fn device(
        &mut self,
        adapter: &wgpu::Adapter,
        desc: &wgpu::DeviceDescriptor<'_>,
    ) -> Result<(wgpu::Device, wgpu::Queue), String>;
    /// The format of the eyes' images (8-bit sRGB), known once `device` has run.
    fn format(&self) -> Option<wgpu::TextureFormat>;
    /// Waits for the headset's next frame: its eyes, or `None` when it shows none (hidden, or
    /// not started yet). `true` in the error's place means the runtime ends the game.
    fn begin_frame(&mut self) -> Result<Option<Frame>, String>;
    /// The image of `eye` (0 left, 1 right) for this frame.
    fn eye_texture(&self, eye: usize) -> Option<wgpu::Texture>;
    /// Hands the frame to the headset, after its drawing was submitted.
    fn end_frame(&mut self) -> Result<(), String>;
    /// Whether the runtime asked the game to end.
    fn exiting(&self) -> bool;
    /// The controllers as a gamepad, as of the last `begin_frame` (at rest without any).
    fn pad(&self) -> crate::gamepad::PadState {
        crate::gamepad::PadState::default()
    }
    /// Where the controllers (left, right) point, as of the last `begin_frame`, when tracked.
    fn hands(&self) -> [Option<Pose>; 2] {
        [None; 2]
    }
    /// Whether the input is the game's: the headset shows it, not the system's menu over it.
    fn focused(&self) -> bool {
        true
    }
}

/// The controllers as of the headset's last frame, for the game's pad (`pad`).
static PAD: Mutex<crate::gamepad::PadState> = Mutex::new(crate::gamepad::PadState {
    buttons: 0,
    left: [0.0; 2],
    right: [0.0; 2],
    triggers: [0.0; 2],
});

/// The headset's controllers as a gamepad, merged into the game's pad; none without a headset.
pub fn pad() -> Option<crate::gamepad::PadState> {
    active().then(|| *PAD.lock().unwrap_or_else(PoisonError::into_inner))
}

pub(crate) fn set_pad(state: crate::gamepad::PadState) {
    *PAD.lock().unwrap_or_else(PoisonError::into_inner) = state;
}

/// The HUD while playing: on a panel 1.6 m ahead of the head and a little low, 25 degrees to
/// either side, turning with the head.
const HUD_DISTANCE: f32 = 1.6;
const HUD_BELOW: f32 = 0.15;
const HUD_HALF_ANGLE: f32 = 0.436;
/// A menu: on a panel put 1.5 m ahead where the player looks as it opens, level and upright,
/// 28 degrees to either side, which stays there (as Vivecraft's and Visor's menus do). Looked
/// away from (more than 70 degrees) for 1.5 s, it comes round to where the player looks.
const MENU_DISTANCE: f32 = 1.5;
const MENU_BELOW: f32 = 0.1;
const MENU_HALF_ANGLE: f32 = 0.489;
const MENU_AWAY: f32 = 1.22;
const MENU_AWAY_FOR: std::time::Duration = std::time::Duration::from_millis(1500);
/// A trigger pulled past this clicks; let go under the other, it releases.
const TRIGGER_PRESS: f32 = 0.6;
const TRIGGER_RELEASE: f32 = 0.35;
/// A trigger let go: under this.
const TRIGGER_AT_REST: f32 = 0.1;

/// A flat panel in the tracking space the HUD and menus are drawn on: its middle, the ways its
/// right and up go (unit), and half its width and height in metres. The HUD's screen fills it.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct Panel {
    pub center: Vec3,
    pub right: Vec3,
    pub up: Vec3,
    pub half: [f32; 2],
}

impl Panel {
    /// Half its width `distance` m away, `half_angle` radians either side, and its height so the
    /// HUD's pixels (laid out for `size`) are square.
    fn half(distance: f32, half_angle: f32, size: [u32; 2]) -> [f32; 2] {
        let width = distance * half_angle.tan();
        [width, width * size[1] as f32 / size[0].max(1) as f32]
    }

    /// Ahead of `head` as it is turned, `below` m under its eyes: the HUD, which turns with it.
    pub fn ahead(head: &Pose, distance: f32, below: f32, half_angle: f32, size: [u32; 2]) -> Self {
        let turn = head.turn();
        Self {
            center: head.at() + turn * Vec3::new(0.0, -below, -distance),
            right: turn * Vec3::X,
            up: turn * Vec3::Y,
            half: Self::half(distance, half_angle, size),
        }
    }

    /// Ahead of `head` the way it faces across the floor, `below` m under its eyes, level and
    /// upright whatever the head's pitch and roll: a menu, which stays where it was put.
    pub fn placed(head: &Pose, distance: f32, below: f32, half_angle: f32, size: [u32; 2]) -> Self {
        let forward = head.forward();
        let flat = Vec3::new(forward.x, 0.0, forward.z)
            .try_normalize()
            .unwrap_or(Vec3::NEG_Z);
        Self {
            center: head.at() + flat * distance - Vec3::Y * below,
            right: Vec3::new(-flat.z, 0.0, flat.x),
            up: Vec3::Y,
            half: Self::half(distance, half_angle, size),
        }
    }

    /// The way its face looks: towards whoever reads it.
    fn normal(&self) -> Vec3 {
        self.right.cross(self.up)
    }

    /// Where a ray from `from` going `toward` (unit) meets the panel's plane ahead of it: the
    /// point, and the panel's coordinates there (-1 to 1 across and up inside it).
    pub fn hit(&self, from: Vec3, toward: Vec3) -> Option<(Vec3, [f32; 2])> {
        let normal = self.normal();
        let facing = toward.dot(normal);
        if facing.abs() < 1e-4 {
            return None;
        }
        let along = (self.center - from).dot(normal) / facing;
        if along <= 0.0 {
            return None;
        }
        let point = from + toward * along;
        let local = point - self.center;
        Some((
            point,
            [local.dot(self.right) / self.half[0], local.dot(self.up) / self.half[1]],
        ))
    }

    /// As the HUD shader's `vr`: the HUD's screen coordinates (-1 to 1, y up; z 0, w 1) to
    /// `eye`'s clip space, the panel seen in perspective.
    pub fn transform(&self, eye: &Eye) -> Mat4 {
        let corner = Mat4::from_cols(
            (self.right * self.half[0]).extend(0.0),
            (self.up * self.half[1]).extend(0.0),
            Vec4::ZERO,
            self.center.extend(1.0),
        );
        projection(&eye.fov, 1000.0) * tracking_view(eye) * corner
    }
}

/// The view from `eye` of the tracking space itself (not the world, which turns with the body).
fn tracking_view(eye: &Eye) -> Mat4 {
    let [x, y, z, w] = eye.orientation;
    Mat4::from_rotation_translation(Quat::from_xyzw(x, y, z, w).normalize(), Vec3::from(eye.position))
        .inverse()
}

/// The head: between the eyes, turned as they are.
fn head_pose(eyes: &[Eye; 2]) -> Pose {
    let [a, b] = eyes;
    Pose {
        position: std::array::from_fn(|i| (a.position[i] + b.position[i]) / 2.0),
        orientation: a.orientation,
    }
}

/// The controllers in the menus (Vivecraft's way): a laser from the pointing hand is the mouse
/// over the menu's panel, its trigger the left button. The game says whether a menu is open
/// (`set_menu`) and takes the pointer's moves and clicks (`pointer_events`) each poll.
struct Ui {
    focused: bool,
    menu: bool,
    panel: Option<Panel>,
    /// Since when the head has looked away from the menu.
    away: Option<std::time::Instant>,
    hands: [Option<Pose>; 2],
    /// The pointing hand (0 left, 1 right): the last whose trigger was pulled in a menu.
    hand: usize,
    held: [bool; 2],
    /// Where the laser meets the menu: the point, and the HUD's pixels there.
    hit: Option<Vec3>,
    pointer: Option<[f64; 2]>,
    pressed: bool,
    /// What the game was last told.
    told_pointer: Option<[f64; 2]>,
    told_pressed: bool,
    /// A trigger held as a menu closed stays out of the game until let go (a click on Back to
    /// Game is not a swing).
    hushed: [bool; 2],
    /// What the main hand aims at while playing, in the tracking space (`set_target`).
    target: Option<Vec3>,
    /// The right stick turned the body a step and has not come back yet (snap turning).
    snapped: bool,
}

static UI: Mutex<Ui> = Mutex::new(Ui {
    focused: false,
    menu: false,
    panel: None,
    away: None,
    hands: [None; 2],
    hand: 1,
    held: [false; 2],
    hit: None,
    pointer: None,
    pressed: false,
    told_pointer: None,
    told_pressed: false,
    hushed: [false; 2],
    target: None,
    snapped: false,
});

fn ui() -> MutexGuard<'static, Ui> {
    UI.lock().unwrap_or_else(PoisonError::into_inner)
}

/// Whether the input is the game's: the headset shows it, not the system's menu over it.
pub fn focused() -> bool {
    ui().focused
}

/// The game says whether a menu (or the title, a dialog...) is open: it goes on a panel of its
/// own, and the laser points at it.
pub fn set_menu(open: bool) {
    ui().menu = open;
}

/// The laser's doing over a menu, as the mouse's: a move to the HUD's pixels, the left button.
#[derive(Clone, Copy, Debug, PartialEq)]
pub enum PointerEvent {
    Moved([f64; 2]),
    Button(bool),
}

/// What the laser did since the last call.
pub fn pointer_events() -> Vec<PointerEvent> {
    let mut ui = ui();
    let mut events = Vec::new();
    if let Some(at) = ui.pointer
        && ui
            .told_pointer
            .is_none_or(|told| (told[0] - at[0]).abs() >= 1.0 || (told[1] - at[1]).abs() >= 1.0)
    {
        ui.told_pointer = Some(at);
        events.push(PointerEvent::Moved(at));
    }
    if ui.pressed != ui.told_pressed {
        ui.told_pressed = ui.pressed;
        events.push(PointerEvent::Button(ui.pressed));
    }
    events
}

/// The headset's input for a frame: its focus, the controllers as a pad (`pad`), and in a menu
/// the panel it is on and the laser. `frame` is the frame begun, `None` while it shows none.
pub(crate) fn frame_input(
    mut pad: crate::gamepad::PadState,
    hands: [Option<Pose>; 2],
    focused: bool,
    frame: Option<&Frame>,
) {
    let mut ui = ui();
    ui.focused = focused;
    ui.hands = hands;
    let triggers = pad.triggers;
    match frame {
        Some(frame) if ui.menu => {
            menu_input(&mut ui, triggers, &head_pose(&frame.eyes), frame.size);
            for (hushed, trigger) in ui.hushed.iter_mut().zip(triggers) {
                *hushed |= trigger > TRIGGER_AT_REST;
            }
            // the triggers click on the menu, not in the game's pad
            pad.triggers = [0.0; 2];
        }
        _ => {
            snap_turn(&mut ui, &mut pad);
            ui.panel = None;
            ui.hit = None;
            ui.pointer = None;
            ui.pressed = false;
            ui.told_pointer = None;
            for (hushed, trigger) in ui.hushed.iter_mut().zip(&mut pad.triggers) {
                *hushed &= *trigger > TRIGGER_AT_REST;
                if *hushed {
                    *trigger = 0.0;
                }
            }
        }
    }
    drop(ui);
    set_pad(pad);
}

/// Snap turning (Vivecraft's default way to turn): the right stick pushed across turns the body
/// 30 degrees, once until it comes back; the game's own turning by the stick is left out.
const SNAP_DEGREES: f32 = 30.0;
fn snap_turn(ui: &mut Ui, pad: &mut crate::gamepad::PadState) {
    let across = pad.right[0];
    if ui.snapped {
        ui.snapped = across.abs() > 0.35;
    } else if across.abs() > 0.7 {
        ui.snapped = true;
        turn_body(SNAP_DEGREES * across.signum());
    }
    pad.right[0] = 0.0;
}

fn menu_input(ui: &mut Ui, triggers: [f32; 2], head: &Pose, size: [u32; 2]) {
    let place = || Panel::placed(head, MENU_DISTANCE, MENU_BELOW, MENU_HALF_ANGLE, size);
    let panel = match ui.panel {
        // looked away from long enough: it comes round
        Some(panel) if flat_angle(head.forward(), panel.center - head.at()) > MENU_AWAY => {
            let since = *ui.away.get_or_insert_with(std::time::Instant::now);
            if since.elapsed() >= MENU_AWAY_FOR {
                ui.away = None;
                place()
            } else {
                panel
            }
        }
        Some(panel) => {
            ui.away = None;
            panel
        }
        None => {
            let panel = place();
            log::info!(
                "XR: a menu at {:?}, the head at {:?} facing {:?}",
                panel.center,
                head.at(),
                head.forward()
            );
            panel
        }
    };
    ui.panel = Some(panel);
    // the other hand's trigger pulled: that hand points now
    for (hand, &trigger) in triggers.iter().enumerate() {
        let down = trigger > TRIGGER_PRESS;
        if down && !ui.held[hand] && hand != ui.hand && ui.hands[hand].is_some() && !ui.pressed {
            ui.hand = hand;
        }
        ui.held[hand] = down;
    }
    // a press drags on past the panel's edge, as a mouse held down does past a slider's
    let pressed = ui.pressed;
    let hit = ui.hands[ui.hand]
        .and_then(|hand| panel.hit(hand.at(), hand.forward()))
        .filter(|(_, [u, v])| pressed || (u.abs() <= 1.0 && v.abs() <= 1.0));
    ui.hit = hit.map(|(point, _)| point);
    ui.pointer = hit.map(|(_, [u, v])| {
        let [width, height] = size.map(|n| f64::from(n.max(1)));
        [
            ((f64::from(u) + 1.0) / 2.0 * width).clamp(0.0, width - 1.0),
            ((1.0 - f64::from(v)) / 2.0 * height).clamp(0.0, height - 1.0),
        ]
    });
    let trigger = triggers[ui.hand];
    ui.pressed = if ui.pressed {
        trigger > TRIGGER_RELEASE
    } else {
        trigger > TRIGGER_PRESS && ui.pointer.is_some()
    };
}

/// The angle between two ways across the floor (radians), heights aside.
fn flat_angle(a: Vec3, b: Vec3) -> f32 {
    let (a, b) = (Vec3::new(a.x, 0.0, a.z), Vec3::new(b.x, 0.0, b.z));
    match (a.try_normalize(), b.try_normalize()) {
        (Some(a), Some(b)) => a.dot(b).clamp(-1.0, 1.0).acos(),
        _ => 0.0,
    }
}

/// The panel the HUD is drawn on this frame: an open menu's, where it was put, else ahead of the
/// head. `size` the HUD's (the eyes').
pub fn hud_panel(eyes: &[Eye; 2], size: [u32; 2]) -> Panel {
    ui().panel.unwrap_or_else(|| {
        Panel::ahead(&head_pose(eyes), HUD_DISTANCE, HUD_BELOW, HUD_HALF_ANGLE, size)
    })
}

/// The title's panorama around the player in `eye`: as far as the sky, turned only as the head
/// is (vanilla's `CubeMap.render` projection, its rotation the head's instead of the spin).
pub fn panorama_view(eye: &Eye) -> Mat4 {
    let [x, y, z, w] = eye.orientation;
    projection(&eye.fov, 10.0)
        * Mat4::from_quat(Quat::from_xyzw(x, y, z, w).normalize().inverse())
        * Mat4::from_rotation_x(std::f32::consts::PI)
}

/// What is drawn over each eye's picture, in the tracking space: the controllers, and in a menu
/// the laser and its dot where it meets the panel.
pub(crate) struct Overlay {
    pipeline: wgpu::RenderPipeline,
    buffer: wgpu::Buffer,
    format: wgpu::TextureFormat,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, bytemuck::Pod, bytemuck::Zeroable)]
pub(crate) struct OverlayVertex {
    clip: [f32; 4],
    color: [f32; 4],
}

const OVERLAY_VERTICES: usize = 1024;
const OVERLAY_SHADER: &str = "
struct Out { @builtin(position) clip: vec4<f32>, @location(0) color: vec4<f32> }
@vertex fn vs(@location(0) clip: vec4<f32>, @location(1) color: vec4<f32>) -> Out {
    var out: Out;
    out.clip = clip;
    out.color = color;
    return out;
}
// The frame holds sRGB-encoded colour in an 8-bit linear format: encode the overlay's colours so.
fn encode(c: f32) -> f32 {
    return select(1.055 * pow(c, 1.0 / 2.4) - 0.055, c * 12.92, c <= 0.0031308);
}
@fragment fn fs(in: Out) -> @location(0) vec4<f32> {
    return vec4(encode(in.color.r), encode(in.color.g), encode(in.color.b), in.color.a);
}
";

impl Overlay {
    pub(crate) fn new(device: &wgpu::Device, format: wgpu::TextureFormat) -> Self {
        let module = device.create_shader_module(wgpu::ShaderModuleDescriptor {
            label: Some("XR overlay"),
            source: wgpu::ShaderSource::Wgsl(OVERLAY_SHADER.into()),
        });
        let layout = device.create_pipeline_layout(&wgpu::PipelineLayoutDescriptor {
            label: Some("XR overlay"),
            ..Default::default()
        });
        let pipeline = device.create_render_pipeline(&wgpu::RenderPipelineDescriptor {
            label: Some("XR overlay"),
            layout: Some(&layout),
            vertex: wgpu::VertexState {
                module: &module,
                entry_point: Some("vs"),
                compilation_options: Default::default(),
                buffers: &[Some(wgpu::VertexBufferLayout {
                    array_stride: std::mem::size_of::<OverlayVertex>() as u64,
                    step_mode: wgpu::VertexStepMode::Vertex,
                    attributes: &wgpu::vertex_attr_array![0 => Float32x4, 1 => Float32x4],
                })],
            },
            primitive: wgpu::PrimitiveState::default(),
            depth_stencil: None,
            multisample: wgpu::MultisampleState::default(),
            fragment: Some(wgpu::FragmentState {
                module: &module,
                entry_point: Some("fs"),
                compilation_options: Default::default(),
                targets: &[Some(wgpu::ColorTargetState {
                    format,
                    blend: Some(wgpu::BlendState::ALPHA_BLENDING),
                    write_mask: wgpu::ColorWrites::ALL,
                })],
            }),
            multiview_mask: None,
            cache: None,
        });
        let buffer = device.create_buffer(&wgpu::BufferDescriptor {
            label: Some("XR overlay"),
            size: (OVERLAY_VERTICES * std::mem::size_of::<OverlayVertex>()) as u64,
            usage: wgpu::BufferUsages::VERTEX | wgpu::BufferUsages::COPY_DST,
            mapped_at_creation: false,
        });
        Self {
            pipeline,
            buffer,
            format,
        }
    }

    pub(crate) fn format(&self) -> wgpu::TextureFormat {
        self.format
    }

    /// Draws `vertices` (from `overlay`) in `pass`; written to the buffer now, so before the
    /// pass's commands are submitted and after the last eye's were.
    pub(crate) fn draw(
        &self,
        queue: &wgpu::Queue,
        pass: &mut wgpu::RenderPass<'_>,
        vertices: &[OverlayVertex],
    ) {
        let vertices = &vertices[..vertices.len().min(OVERLAY_VERTICES)];
        if vertices.is_empty() {
            return;
        }
        queue.write_buffer(&self.buffer, 0, bytemuck::cast_slice(vertices));
        pass.set_pipeline(&self.pipeline);
        pass.set_vertex_buffer(0, self.buffer.slice(..));
        pass.draw(0..vertices.len() as u32, 0..1);
    }
}

/// The overlay's triangles for `eye` (`Overlay::draw`), as of the last frame's input.
pub(crate) fn overlay(eye: &Eye) -> Vec<OverlayVertex> {
    let ui = ui();
    let view = projection(&eye.fov, 100.0) * tracking_view(eye);
    let seen_from = Vec3::from(eye.position);
    let mut out = Vec::new();
    let mut triangle = |points: [Vec3; 3], colors: [[f32; 4]; 3]| {
        for (point, color) in points.into_iter().zip(colors) {
            out.push(OverlayVertex {
                clip: (view * point.extend(1.0)).to_array(),
                color,
            });
        }
    };
    // the controllers, the farther first
    let mut hands: Vec<(usize, Pose)> = ui
        .hands
        .iter()
        .enumerate()
        .filter_map(|(i, hand)| hand.map(|pose| (i, pose)))
        .collect();
    hands.sort_by(|a, b| {
        let far = |pose: &Pose| pose.at().distance_squared(seen_from);
        far(&b.1).total_cmp(&far(&a.1))
    });
    for (index, pose) in hands {
        controller(&mut triangle, &pose, index, seen_from);
    }
    // where the main hand aims while playing: a dot facing the eye, the same size seen from anywhere
    if let (false, Some(target)) = (ui.menu, ui.target) {
        let toward = (target - seen_from).normalize_or_zero();
        let right = toward.cross(Vec3::Y).try_normalize().unwrap_or(Vec3::X);
        let up = right.cross(toward);
        let size = target.distance(seen_from).max(0.1);
        let middle = target - toward * 0.01;
        disc_facing(&mut triangle, middle, right, up, size * 0.008, [0.0, 0.0, 0.0, 0.6]);
        disc_facing(&mut triangle, middle - toward * 0.001, right, up, size * 0.005, [1.0, 1.0, 1.0, 0.9]);
    }
    if let (true, Some(panel), Some(hand)) = (ui.menu, ui.panel, ui.hands[ui.hand]) {
        // the laser, to the dot where it meets the menu or fading out a little way off
        let from = hand.at();
        let (to, end_alpha) = match ui.hit {
            Some(point) => (point, 0.9),
            None => (from + hand.forward() * 1.2, 0.0),
        };
        let side = (to - from)
            .cross((from + to) / 2.0 - seen_from)
            .try_normalize()
            .unwrap_or(Vec3::X)
            * 0.002;
        let beam = [0.75, 0.85, 1.0];
        let start = [beam[0], beam[1], beam[2], 0.9];
        let end = [beam[0], beam[1], beam[2], end_alpha];
        triangle([from - side, from + side, to + side], [start, start, end]);
        triangle([from - side, to + side, to - side], [start, end, end]);
        if let Some(point) = ui.hit {
            let middle = point + panel.normal() * 0.004;
            disc(&mut triangle, middle, &panel, 0.02, [0.0, 0.0, 0.0, 0.6]);
            disc(&mut triangle, middle + panel.normal() * 0.001, &panel, 0.012, [1.0, 1.0, 1.0, 1.0]);
        }
    }
    out
}

/// A controller as a small box behind where it points, the faces towards `seen_from` only (so
/// it needs no depth), its front coloured by the hand (left blue, right orange).
fn controller(
    triangle: &mut impl FnMut([Vec3; 3], [[f32; 4]; 3]),
    pose: &Pose,
    hand: usize,
    seen_from: Vec3,
) {
    let (low, high) = (Vec3::new(-0.022, -0.032, 0.0), Vec3::new(0.022, 0.008, 0.11));
    let turn = pose.turn();
    let at = |x: f32, y: f32, z: f32| pose.at() + turn * Vec3::new(x, y, z);
    let front = if hand == 0 {
        [0.16, 0.36, 0.9, 1.0]
    } else {
        [0.92, 0.42, 0.12, 1.0]
    };
    let grey = |g: f32| [g, g, g * 1.08, 1.0];
    // each face: its outward normal and corners (counter-clockwise from outside), its colour
    let faces = [
        (Vec3::NEG_Z, [at(low.x, low.y, low.z), at(high.x, low.y, low.z), at(high.x, high.y, low.z), at(low.x, high.y, low.z)], front),
        (Vec3::Z, [at(high.x, low.y, high.z), at(low.x, low.y, high.z), at(low.x, high.y, high.z), at(high.x, high.y, high.z)], grey(0.1)),
        (Vec3::Y, [at(low.x, high.y, low.z), at(high.x, high.y, low.z), at(high.x, high.y, high.z), at(low.x, high.y, high.z)], grey(0.24)),
        (Vec3::NEG_Y, [at(low.x, low.y, high.z), at(high.x, low.y, high.z), at(high.x, low.y, low.z), at(low.x, low.y, low.z)], grey(0.06)),
        (Vec3::X, [at(high.x, low.y, low.z), at(high.x, low.y, high.z), at(high.x, high.y, high.z), at(high.x, high.y, low.z)], grey(0.15)),
        (Vec3::NEG_X, [at(low.x, low.y, high.z), at(low.x, low.y, low.z), at(low.x, high.y, low.z), at(low.x, high.y, high.z)], grey(0.15)),
    ];
    for (normal, [a, b, c, d], color) in faces {
        let middle = (a + b + c + d) / 4.0;
        if (turn * normal).dot(seen_from - middle) <= 0.0 {
            continue;
        }
        triangle([a, b, c], [color; 3]);
        triangle([a, c, d], [color; 3]);
    }
}

/// A round dot on `panel`'s face at `middle`.
fn disc(
    triangle: &mut impl FnMut([Vec3; 3], [[f32; 4]; 3]),
    middle: Vec3,
    panel: &Panel,
    radius: f32,
    color: [f32; 4],
) {
    disc_facing(triangle, middle, panel.right, panel.up, radius, color);
}

/// A round dot at `middle` in the plane of `right` and `up`.
fn disc_facing(
    triangle: &mut impl FnMut([Vec3; 3], [[f32; 4]; 3]),
    middle: Vec3,
    right: Vec3,
    up: Vec3,
    radius: f32,
    color: [f32; 4],
) {
    const SIDES: usize = 12;
    let rim = |i: usize| {
        let angle = i as f32 / SIDES as f32 * std::f32::consts::TAU;
        middle + (right * angle.cos() + up * angle.sin()) * radius
    };
    for i in 0..SIDES {
        triangle([middle, rim(i), rim(i + 1)], [color; 3]);
    }
}

type Start = Box<dyn FnOnce() -> Result<Box<dyn Headset>, String> + Send>;

/// How to start the headset, until it is started: the first look at it (`active`, once the log is
/// open, as the renderer starts) starts it.
static START: Mutex<Option<Start>> = Mutex::new(None);
static HEADSET: OnceLock<Option<Mutex<Box<dyn Headset>>>> = OnceLock::new();

/// Installs how to start the headset, before the renderer starts. When starting it fails the game
/// draws on the window, and the log says why.
pub fn install_with(start: impl FnOnce() -> Result<Box<dyn Headset>, String> + Send + 'static) {
    *START.lock().unwrap_or_else(PoisonError::into_inner) = Some(Box::new(start));
}

fn started() -> Option<&'static Mutex<Box<dyn Headset>>> {
    HEADSET
        .get_or_init(|| {
            let start = START.lock().unwrap_or_else(PoisonError::into_inner).take()?;
            match start() {
                Ok(headset) => Some(Mutex::new(headset)),
                Err(e) => {
                    log::error!("Horizon: no headset, the game is on the window: {e}");
                    None
                }
            }
        })
        .as_ref()
}

/// Whether the game draws for a headset.
pub fn active() -> bool {
    started().is_some()
}

/// The headset, when there is one.
pub fn headset() -> Option<MutexGuard<'static, Box<dyn Headset>>> {
    started().map(|h| h.lock().unwrap_or_else(PoisonError::into_inner))
}

/// The player's look in a headset: the body's yaw, which turning (the stick, the mouse) moves, and
/// the head's turn and pitch from the headset, which the game adds to it (vanilla's degrees).
struct Look {
    body_yaw: f32,
    head: Option<[f32; 2]>,
    /// The body was set from the player's yaw once there was a head to subtract.
    aligned: bool,
    /// Where the head is in the tracking space, and the place across the floor the player stands
    /// on there (room scale): walking moves the player (`room_step`), the head's lean on top.
    head_at: Option<Vec3>,
    room: Vec3,
}

static LOOK: Mutex<Look> = Mutex::new(Look {
    body_yaw: 0.0,
    head: None,
    aligned: false,
    head_at: None,
    room: Vec3::ZERO,
});

fn look() -> MutexGuard<'static, Look> {
    LOOK.lock().unwrap_or_else(PoisonError::into_inner)
}

/// The body's yaw: what the world is drawn turned to, the headset's own turn on top.
pub fn body_yaw() -> f32 {
    look().body_yaw
}

/// Turns the body (the stick, the mouse) by `degrees`.
pub fn turn_body(degrees: f32) {
    look().body_yaw += degrees;
}

/// The head's turn from the body and its pitch, in vanilla's degrees (yaw left negative, pitch up
/// negative), from the headset's last frame.
pub fn head() -> Option<[f32; 2]> {
    look().head
}

/// The body's yaw for a player looking along `yaw` now: once, so that the world the game put the
/// player in stays the way they face.
pub fn align_body(yaw: f32) {
    let mut look = look();
    if let (false, Some([head_yaw, _])) = (look.aligned, look.head) {
        look.body_yaw = yaw - head_yaw;
        look.aligned = true;
    }
}

/// The head's turn and pitch (`head`) from an orientation in the tracking space: its forward
/// (-z) as vanilla's yaw and pitch for a body facing yaw 0.
pub fn head_look(orientation: [f32; 4]) -> [f32; 2] {
    let [x, y, z, w] = orientation;
    let forward = Quat::from_xyzw(x, y, z, w).normalize() * Vec3::NEG_Z;
    [
        forward.x.atan2(-forward.z).to_degrees(),
        -forward.y.clamp(-1.0, 1.0).asin().to_degrees(),
    ]
}

pub(crate) fn set_head(eyes: &[Eye; 2]) {
    let head = head_pose(eyes);
    let mut look = look();
    look.head = Some(head_look(head.orientation));
    look.head_at = Some(head.at());
}

/// `eye` from where the player stands in the room (`room_step`): the camera is the player's eyes.
pub fn room_eye(eye: &Eye) -> Eye {
    let room = look().room;
    Eye {
        position: (Vec3::from(eye.position) - room).to_array(),
        ..*eye
    }
}

/// Room scale: how far the head has walked across the floor from where the player stands, in the
/// world's axes (x, z), when it has (a millimetre or more): the game moves the player by as much
/// as it can and says how far it went (`room_moved`). A jump of more than a metre (the headset
/// recentred) is no walk: the player stays.
pub fn room_step() -> Option<[f64; 2]> {
    let mut look = look();
    let head = look.head_at?;
    let walked = Vec3::new(head.x - look.room.x, 0.0, head.z - look.room.z);
    if walked.length_squared() > 1.0 {
        look.room = Vec3::new(head.x, 0.0, head.z);
        return None;
    }
    if walked.length_squared() < 1e-6 {
        return None;
    }
    let step = facing(look.body_yaw) * walked;
    Some([f64::from(step.x), f64::from(step.z)])
}

/// The player moved `moved` (the world's x and z) for the walk `room_step` gave.
pub fn room_moved(moved: [f64; 2]) {
    let mut look = look();
    let back = facing(look.body_yaw).inverse() * Vec3::new(moved[0] as f32, 0.0, moved[1] as f32);
    look.room += Vec3::new(back.x, 0.0, back.z);
}

/// The hand that aims, mines and uses (Vivecraft's main hand): the right.
const MAIN_HAND: usize = 1;

/// Where the main hand's controller points while playing (not in a menu), in the world's axes:
/// its place from the player's eyes, and the way it points (unit). The game aims along it
/// instead of the head's look.
pub fn aim() -> Option<([f64; 3], [f64; 3])> {
    let hand = {
        let ui = ui();
        if ui.menu {
            return None;
        }
        ui.hands[MAIN_HAND]?
    };
    let look = look();
    let turn = facing(look.body_yaw);
    let from = turn * (hand.at() - look.room);
    let toward = turn * hand.forward();
    Some((from.to_array().map(f64::from), toward.to_array().map(f64::from)))
}

/// What the main hand aims at (`aim`), from the player's eyes in the world's axes, for the dot
/// drawn there; `None` at nothing.
pub fn set_target(target: Option<[f64; 3]>) {
    let target = target.map(|t| {
        let look = look();
        facing(look.body_yaw).inverse() * Vec3::new(t[0] as f32, t[1] as f32, t[2] as f32) + look.room
    });
    ui().target = target;
}

/// The pixel at the middle of `texture` (8-bit RGBA or BGRA, as stored), read back and waited for:
/// what the headset is handed, for the log while its picture is checked.
pub(crate) fn middle_pixel(
    device: &wgpu::Device,
    queue: &wgpu::Queue,
    texture: &wgpu::Texture,
) -> Option<[u8; 4]> {
    pixel_at(device, queue, texture, [0.5, 0.5])
}

/// The pixel at `at` (0 to 1 across and down) of `texture`, as `middle_pixel`.
pub(crate) fn pixel_at(
    device: &wgpu::Device,
    queue: &wgpu::Queue,
    texture: &wgpu::Texture,
    at: [f32; 2],
) -> Option<[u8; 4]> {
    let size = texture.size();
    let buffer = device.create_buffer(&wgpu::BufferDescriptor {
        label: Some("XR pixel"),
        size: 256,
        usage: wgpu::BufferUsages::COPY_DST | wgpu::BufferUsages::MAP_READ,
        mapped_at_creation: false,
    });
    let mut encoder = device.create_command_encoder(&wgpu::CommandEncoderDescriptor {
        label: Some("XR pixel"),
    });
    encoder.copy_texture_to_buffer(
        wgpu::TexelCopyTextureInfo {
            texture,
            mip_level: 0,
            origin: wgpu::Origin3d {
                x: ((size.width as f32 * at[0]) as u32).min(size.width.saturating_sub(1)),
                y: ((size.height as f32 * at[1]) as u32).min(size.height.saturating_sub(1)),
                z: 0,
            },
            aspect: wgpu::TextureAspect::All,
        },
        wgpu::TexelCopyBufferInfo {
            buffer: &buffer,
            layout: wgpu::TexelCopyBufferLayout {
                offset: 0,
                bytes_per_row: Some(256),
                rows_per_image: Some(1),
            },
        },
        wgpu::Extent3d {
            width: 1,
            height: 1,
            depth_or_array_layers: 1,
        },
    );
    queue.submit([encoder.finish()]);
    let slice = buffer.slice(..);
    let (sent, received) = std::sync::mpsc::channel();
    slice.map_async(wgpu::MapMode::Read, move |result| {
        let _ = sent.send(result);
    });
    device.poll(wgpu::PollType::wait_indefinitely()).ok()?;
    received.recv().ok()?.ok()?;
    let data = slice.get_mapped_range().ok()?;
    Some([data[0], data[1], data[2], data[3]])
}

/// The tracking space turned to face where the player faces (`yaw` in degrees, vanilla's): the
/// headset's forward (-z) becomes the player's facing, as the screen camera's rotation does at
/// pitch 0 (`camera::view_matrix`).
fn facing(yaw: f32) -> Quat {
    Quat::from_rotation_y(std::f32::consts::PI - yaw.to_radians())
}

/// `eye`'s view of the camera-relative world for a player facing `yaw`: the camera is the
/// player's eyes, the head's turn and lean since the headset started and the eye's place come on
/// top. `far` as the screen view's; `size` the eye image's.
pub fn eye_view(eye: &Eye, yaw: f32, size: [u32; 2], far: f32) -> View {
    let body = facing(yaw);
    let [x, y, z, w] = eye.orientation;
    let orientation = body * Quat::from_xyzw(x, y, z, w).normalize();
    let position = body * Vec3::from(eye.position);
    View {
        view: Mat4::from_rotation_translation(orientation, position).inverse(),
        projection: projection(&eye.fov, far),
        aspect: size[0] as f32 / size[1].max(1) as f32,
        // The shaders that rebuild view rays take the lens as symmetric: the mean of up and down
        tan_half_fov: (eye.fov.up.tan() - eye.fov.down.tan()) / 2.0,
    }
}

/// The view the frame's geometry is culled with: both eyes' lenses together, from between them.
pub fn cull_view(eyes: &[Eye; 2], yaw: f32, size: [u32; 2], far: f32) -> View {
    let [a, b] = eyes;
    let between = Eye {
        position: std::array::from_fn(|i| (a.position[i] + b.position[i]) / 2.0),
        orientation: a.orientation,
        fov: Fov {
            left: a.fov.left.min(b.fov.left),
            right: a.fov.right.max(b.fov.right),
            up: a.fov.up.max(b.fov.up),
            down: a.fov.down.min(b.fov.down),
        },
    };
    eye_view(&between, yaw, size, far)
}

/// The eye's lens as the renderer's projections are: right-handed, reversed Z (the near plane at
/// depth 1, `far` at 0), with each side at its own angle.
pub fn projection(fov: &Fov, far: f32) -> Mat4 {
    let near = crate::gfx::NEAR_PLANE;
    let (l, r) = (fov.left.tan(), fov.right.tan());
    let (d, u) = (fov.down.tan(), fov.up.tan());
    // As `Mat4::perspective_rh` with the planes swapped (the screen view's `world_projection`)
    let z = near / (far - near);
    Mat4::from_cols_array(&[
        2.0 / (r - l),
        0.0,
        0.0,
        0.0,
        0.0,
        2.0 / (u - d),
        0.0,
        0.0,
        (r + l) / (r - l),
        (u + d) / (u - d),
        z,
        -1.0,
        0.0,
        0.0,
        far * z,
        0.0,
    ])
}

#[cfg(test)]
mod tests {
    use super::*;

    fn close(a: Mat4, b: Mat4) -> bool {
        a.to_cols_array()
            .iter()
            .zip(b.to_cols_array())
            .all(|(x, y)| (x - y).abs() <= 1e-4 * x.abs().max(y.abs()).max(1.0))
    }

    #[test]
    fn a_symmetric_lens_is_the_screen_projection() {
        for (fov, aspect, far) in [(70.0f32, 1.0f32, 512.0f32), (95.0, 0.9, 2048.0)] {
            let half = (fov.to_radians() / 2.0).tan();
            let lens = Fov {
                left: -(half * aspect).atan(),
                right: (half * aspect).atan(),
                up: half.atan(),
                down: -half.atan(),
            };
            assert!(close(
                projection(&lens, far),
                crate::gfx::world_projection(fov, aspect, far)
            ));
        }
    }

    #[test]
    fn a_level_head_sees_what_the_screen_camera_sees() {
        let eye = Eye {
            position: [0.0; 3],
            orientation: [0.0, 0.0, 0.0, 1.0],
            fov: Fov { left: -0.8, right: 0.8, up: 0.8, down: -0.8 },
        };
        for yaw in [0.0f32, 90.0, -135.0, 270.5] {
            let view = eye_view(&eye, yaw, [100, 100], 256.0);
            assert!(close(view.view, crate::camera::view_matrix(yaw, 0.0)), "yaw {yaw}");
        }
    }

    #[test]
    fn the_head_looking_up_is_pitching_up() {
        // OpenXR: a turn about +x lifts the view; vanilla's pitch is negative looking up
        let up = Quat::from_rotation_x(0.3);
        let eye = Eye {
            position: [0.0; 3],
            orientation: up.to_array(),
            fov: Fov { left: -0.8, right: 0.8, up: 0.8, down: -0.8 },
        };
        let view = eye_view(&eye, 30.0, [100, 100], 256.0);
        assert!(close(view.view, crate::camera::view_matrix(30.0, -0.3f32.to_degrees())));
    }

    #[test]
    fn the_head_look_is_what_the_eye_view_turns_by() {
        // a head turned and tilted: the game's yaw and pitch from it draw what the headset shows
        let head = Quat::from_rotation_y(0.7) * Quat::from_rotation_x(-0.4);
        let eye = Eye {
            position: [0.0; 3],
            orientation: head.to_array(),
            fov: Fov { left: -0.8, right: 0.8, up: 0.8, down: -0.8 },
        };
        let [yaw, pitch] = head_look(eye.orientation);
        let body = 20.0;
        let view = eye_view(&eye, body, [100, 100], 256.0);
        assert!(close(view.view, crate::camera::view_matrix(body + yaw, pitch)), "{yaw} {pitch}");
        // turned left (+y in OpenXR) is negative yaw, as vanilla's left
        assert!(yaw < 0.0 && pitch > 0.0);
    }

    fn level_eyes() -> [Eye; 2] {
        let eye = |x: f32| Eye {
            position: [x, 1.6, 0.0],
            orientation: [0.0, 0.0, 0.0, 1.0],
            fov: Fov { left: -0.8, right: 0.8, up: 0.8, down: -0.8 },
        };
        [eye(-0.032), eye(0.032)]
    }

    #[test]
    fn a_menu_is_put_level_ahead_whatever_the_heads_pitch() {
        // looking down and to the left (+y turns left in OpenXR)
        let turn = Quat::from_rotation_y(0.5) * Quat::from_rotation_x(-0.6);
        let head = Pose { position: [0.0, 1.6, 0.0], orientation: turn.to_array() };
        let panel = Panel::placed(&head, 1.5, 0.1, 0.5, [1000, 500]);
        let ahead = Vec3::new(-0.5f32.sin(), 0.0, -0.5f32.cos());
        assert!((panel.center - (Vec3::new(0.0, 1.5, 0.0) + ahead * 1.5)).length() < 1e-5);
        assert!((panel.up - Vec3::Y).length() < 1e-6);
        // it faces the head
        assert!(panel.normal().dot(-ahead) > 0.999);
        assert!((panel.half[0] - 1.5 * 0.5f32.tan()).abs() < 1e-6);
        assert!((panel.half[1] - panel.half[0] / 2.0).abs() < 1e-6);
    }

    #[test]
    fn the_laser_meets_the_panel_where_it_points() {
        let head = Pose { position: [0.0; 3], orientation: [0.0, 0.0, 0.0, 1.0] };
        let panel = Panel::placed(&head, 2.0, 0.0, 0.5, [100, 100]);
        // from 30 cm right of the head, at the panel's top left corner
        let from = Vec3::new(0.3, 0.0, 0.0);
        let corner = panel.center - panel.right * panel.half[0] + panel.up * panel.half[1];
        let (point, [u, v]) = panel.hit(from, (corner - from).normalize()).unwrap();
        assert!((point - corner).length() < 1e-5 && (u + 1.0).abs() < 1e-5 && (v - 1.0).abs() < 1e-5);
        // pointing away misses
        assert!(panel.hit(from, Vec3::Z).is_none());
    }

    #[test]
    fn the_panel_is_drawn_where_it_is() {
        let eyes = level_eyes();
        let turn = Quat::from_rotation_y(-0.3);
        let head = Pose { position: [0.1, 1.5, -0.2], orientation: turn.to_array() };
        let panel = Panel::placed(&head, 1.5, 0.1, 0.5, [800, 600]);
        for eye in &eyes {
            let transform = panel.transform(eye);
            let view = projection(&eye.fov, 1000.0) * tracking_view(eye);
            for (sx, sy) in [(0.0, 0.0), (1.0, 1.0), (-1.0, 0.5)] {
                let point = panel.center + panel.right * panel.half[0] * sx + panel.up * panel.half[1] * sy;
                let a = transform * Vec4::new(sx, sy, 0.0, 1.0);
                let b = view * point.extend(1.0);
                assert!((a.truncate().truncate() / a.w - b.truncate().truncate() / b.w).length() < 1e-4);
                assert!(a.w > 0.0);
            }
        }
    }

    #[test]
    fn the_hud_turns_with_the_head() {
        let eyes = level_eyes();
        let panel = Panel::ahead(&head_pose(&eyes), HUD_DISTANCE, HUD_BELOW, HUD_HALF_ANGLE, [100, 100]);
        assert!((panel.center - Vec3::new(0.0, 1.6 - HUD_BELOW, -HUD_DISTANCE)).length() < 1e-5);
        // seen by both eyes at its depth: crossed, the left eye has it to its right
        let middle = |eye: &Eye| {
            let clip = panel.transform(eye) * Vec4::new(0.0, 0.0, 0.0, 1.0);
            clip.x / clip.w
        };
        assert!(middle(&eyes[0]) > 0.0 && middle(&eyes[1]) < 0.0);
    }

    #[test]
    fn the_laser_moves_and_clicks_the_menu() {
        use crate::gamepad::PadState;
        let eyes = level_eyes();
        let frame = Frame { eyes, size: [200, 100] };
        // the right hand at the head, pointing straight ahead: the menu's middle, a little up
        let hand = Pose { position: [0.0, 1.6, 0.0], orientation: [0.0, 0.0, 0.0, 1.0] };
        let pulled = |right: f32| PadState { triggers: [0.0, right], ..PadState::default() };
        set_menu(true);
        frame_input(pulled(0.0), [None, Some(hand)], true, Some(&frame));
        let events = pointer_events();
        let [PointerEvent::Moved([x, y])] = events[..] else { panic!("{events:?}") };
        assert!((x - 100.0).abs() < 0.5 && y < 50.0, "{x} {y}");
        assert!(focused());
        // the trigger clicks, and is not the game's pad's
        frame_input(pulled(0.9), [None, Some(hand)], true, Some(&frame));
        assert_eq!(pointer_events(), [PointerEvent::Button(true)]);
        assert_eq!(PAD.lock().unwrap().triggers, [0.0; 2]);
        // the menu closes on the click: let go after, the trigger stays out of the game
        set_menu(false);
        frame_input(pulled(0.9), [None, Some(hand)], true, Some(&frame));
        assert_eq!(pointer_events(), [PointerEvent::Button(false)]);
        assert_eq!(PAD.lock().unwrap().triggers, [0.0; 2]);
        frame_input(pulled(0.0), [None, Some(hand)], true, Some(&frame));
        frame_input(pulled(0.9), [None, Some(hand)], true, Some(&frame));
        assert_eq!(PAD.lock().unwrap().triggers, [0.0, 0.9]);
        assert!(pointer_events().is_empty());
    }

    #[test]
    fn the_panorama_turns_with_the_head() {
        let lens = Fov { left: -0.8, right: 0.8, up: 0.8, down: -0.8 };
        let level = Eye { position: [0.0; 3], orientation: [0.0, 0.0, 0.0, 1.0], fov: lens };
        // level, it is vanilla's at yaw and pitch 0
        let vanilla = Mat4::perspective_rh(1.6, 1.0, 0.05, 10.0) * Mat4::from_rotation_x(std::f32::consts::PI);
        let ahead = |m: Mat4, p: Vec3| {
            let c = m * p.extend(1.0);
            c.truncate().truncate() / c.w
        };
        for p in [Vec3::new(0.2, 0.3, 1.0), Vec3::new(-0.5, -0.1, 1.0)] {
            assert!((ahead(panorama_view(&level), p) - ahead(vanilla, p)).length() < 1e-4);
        }
        // turned left, what was off to the left is ahead
        let left = Eye { orientation: Quat::from_rotation_y(0.5).to_array(), ..level };
        let off_left = Mat4::from_rotation_x(std::f32::consts::PI).inverse()
            * (Quat::from_rotation_y(0.5) * Vec3::NEG_Z).extend(0.0);
        assert!(ahead(panorama_view(&left), off_left.truncate()).length() < 1e-4);
    }

    #[test]
    fn the_eyes_sit_either_side_of_the_camera() {
        // the left eye 3 cm to the player's left: facing yaw 0 (+z), left is +x
        let eye = Eye {
            position: [-0.03, 0.0, 0.0],
            orientation: [0.0, 0.0, 0.0, 1.0],
            fov: Fov { left: -0.8, right: 0.8, up: 0.8, down: -0.8 },
        };
        let view = eye_view(&eye, 0.0, [100, 100], 256.0);
        let at = view.view.inverse().transform_point3(Vec3::ZERO);
        assert!((at - Vec3::new(0.03, 0.0, 0.0)).length() < 1e-5, "{at}");
    }
}
