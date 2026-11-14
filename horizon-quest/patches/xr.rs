//! A headset (QuestCraft: horizon-quest's OpenXR core, horizon-xr): when one is installed before
//! the renderer starts, the renderer opens its device through it and draws each frame once per eye
//! into the headset's images instead of the window.
//!
//! The headset's poses are in its tracking space (OpenXR's: x right, y up, -z ahead, metres, from
//! where the head was when it started). The world is seen from the player's eyes as the game puts
//! them, turned to the player's yaw: the head's own turn, lean and the eyes' spacing come on top.
use crate::gfx::View;
use glam::{Mat4, Quat, Vec3};
use std::sync::{Mutex, MutexGuard, OnceLock, PoisonError};

/// How far an eye sees each way, in radians: left and down negative.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct Fov {
    pub left: f32,
    pub right: f32,
    pub up: f32,
    pub down: f32,
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
}

static LOOK: Mutex<Look> = Mutex::new(Look {
    body_yaw: 0.0,
    head: None,
    aligned: false,
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

pub(crate) fn set_head(eye: &Eye) {
    look().head = Some(head_look(eye.orientation));
}

/// The pixel at the middle of `texture` (8-bit RGBA or BGRA, as stored), read back and waited for:
/// what the headset is handed, for the log while its picture is checked.
pub(crate) fn middle_pixel(
    device: &wgpu::Device,
    queue: &wgpu::Queue,
    texture: &wgpu::Texture,
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
                x: size.width / 2,
                y: size.height / 2,
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
    let data = slice.get_mapped_range();
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

/// Where the HUD and menus are in `eye`'s image: a panel `distance` metres ahead of the head and
/// `below` metres under eye level, `half_angle` radians to either side, the same for both eyes so
/// it is seen at that depth. As the HUD shader's `vr`: its screen coordinates scaled by x and y,
/// moved by z and w. `size` is the eye image's (the HUD is laid out for it).
pub fn hud_transform(
    eyes: &[Eye; 2],
    index: usize,
    size: [u32; 2],
    distance: f32,
    below: f32,
    half_angle: f32,
) -> [f32; 4] {
    let eye = &eyes[index];
    let [x, y, z, w] = eye.orientation;
    let turn = Quat::from_xyzw(x, y, z, w).normalize();
    let center = (Vec3::from(eyes[0].position) + Vec3::from(eyes[1].position)) / 2.0;
    // the panel's middle, in the eye's own space
    let middle = turn.inverse() * (center - Vec3::from(eye.position)) + Vec3::new(0.0, -below, -distance);
    let clip = projection(&eye.fov, 1000.0) * middle.extend(1.0);
    let across = eye.fov.right.tan() - eye.fov.left.tan();
    let up = eye.fov.up.tan() - eye.fov.down.tan();
    let scale_x = 2.0 * half_angle.tan() / across;
    // the HUD's pixels stay square: an image pixel is across / width wide and up / height high
    let scale_y = scale_x * (across / size[0].max(1) as f32) / (up / size[1].max(1) as f32);
    [scale_x, scale_y, clip.x / clip.w, clip.y / clip.w]
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

    #[test]
    fn the_hud_panel_is_ahead_and_seen_at_its_depth() {
        let lens = Fov { left: -0.8, right: 0.8, up: 0.8, down: -0.8 };
        let eye = |x: f32| Eye {
            position: [x, 0.0, 0.0],
            orientation: [0.0, 0.0, 0.0, 1.0],
            fov: lens,
        };
        let eyes = [eye(-0.032), eye(0.032)];
        let [sx, sy, left_x, left_y] = hud_transform(&eyes, 0, [1000, 1000], 1.6, 0.0, 0.4);
        let [_, _, right_x, _] = hud_transform(&eyes, 1, [1000, 1000], 1.6, 0.0, 0.4);
        // the left eye sees it to its right, the right eye to its left: crossed, so it is nearer
        // than far away, by the eyes' spacing over the distance
        assert!(left_x > 0.0 && right_x < 0.0 && (left_x + right_x).abs() < 1e-6);
        let tan = 0.8f32.tan();
        assert!((left_x - 0.032 / 1.6 / tan).abs() < 1e-4, "{left_x}");
        assert!(left_y.abs() < 1e-6);
        // 0.4 radians to either side of a lens 0.8 radians to either side
        assert!((sx - 0.4f32.tan() / tan).abs() < 1e-5 && (sx - sy).abs() < 1e-6);
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
