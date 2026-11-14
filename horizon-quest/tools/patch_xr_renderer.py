"""Gives Horizon's renderer a headset (horizon-client-render/src/xr.rs, from patches/xr.rs). build.sh runs
it on the Horizon tree after patches/*.patch:

    python3 -I patch_xr_renderer.py HORIZON_DIR PATCHES_DIR

- the device: with a headset installed, `open_device` takes its instance and GPU, and `open_adapter`
  opens the device through it (the features and limits Horizon asks for, the runtime's extensions on top);
- the frame: offscreen at the eyes' size and format (sRGB-free, so a frame is copied byte for byte into
  the headset's sRGB images), drawn once per eye from that eye's view, then handed to the headset.
"""
import shutil
import sys
from pathlib import Path

horizon, patches = Path(sys.argv[1]), Path(sys.argv[2])
render = horizon / "crates/horizon-client-render/src"


def edit(path, old, new, count=1):
    text = path.read_text()
    found = text.count(old)
    if found != count:
        raise SystemExit(f"{path}: expected {count} of {old[:80]!r}, found {found}")
    path.write_text(text.replace(old, new))


shutil.copy(patches / "xr.rs", render / "xr.rs")
edit(render / "lib.rs", "pub mod gfx;\n", "pub mod gfx;\n/// QuestCraft: a headset to draw for (horizon-quest).\npub mod xr;\n")

gfx = render / "gfx.rs"
edit(gfx, "const NEAR_PLANE: f32 = 0.05;", "pub(crate) const NEAR_PLANE: f32 = 0.05;")

# --- the device: through the headset
edit(gfx, """async fn open_device(
    window: &Arc<Window>,
    offscreen: bool,
    api: GraphicsApi,
    layers: u32,
) -> Result<Opened> {
""", """async fn open_device(
    window: &Arc<Window>,
    offscreen: bool,
    api: GraphicsApi,
    layers: u32,
) -> Result<Opened> {
    if crate::xr::active() {
        return open_headset_device(window, layers).await;
    }
""")
edit(gfx, """/// The tries `open_device` makes, in turn:""", """/// The headset's device (QuestCraft, `xr`): its runtime's instance and GPU, the device opened
/// through it as `open_adapter` opens any. Drawn offscreen: the headset shows the frames.
async fn open_headset_device(window: &Arc<Window>, layers: u32) -> Result<Opened> {
    let (instance, adapter) = {
        let mut headset = crate::xr::headset().ok_or("no headset")?;
        let instance = headset.instance(wgpu::InstanceFlags::from_build_config().with_env())?;
        let adapter = headset.adapter(&instance)?;
        (instance, adapter)
    };
    let adapted = match open_adapter("Vulkan (headset)", window, None, &adapter, layers)
        .await
    {
        Ok(adapted) => adapted,
        Err(AdapterFailure::Failed(e) | AdapterFailure::CannotPresent(e)) => return Err(e.into()),
    };
    Ok(Opened {
        instance,
        surface: None,
        adapter,
        device: adapted.device,
        queue: adapted.queue,
        device_lost: adapted.device_lost,
        configured: None,
    })
}
/// The tries `open_device` makes, in turn:""")

setup = render / "gfx/surface_setup.rs"
edit(setup, """    #[cfg(not(target_os = "macos"))]
    let opened = match info.backend {
        wgpu::Backend::Vulkan => horizon_hdr::open_vulkan_device(adapter, &desc),
        _ => None,
    };""", """    // A headset's device (QuestCraft): opened through its runtime, which adds its extensions.
    if let Some(mut headset) = crate::xr::headset() {
        let (device, queue) = headset
            .device(adapter, &desc)
            .map_err(|e| AdapterFailure::Failed(format!("{name} device for the headset: {e}")))?;
        drop(headset);
        let device_lost = Arc::new(std::sync::atomic::AtomicBool::new(false));
        let lost = device_lost.clone();
        device.set_device_lost_callback(move |reason, message| {
            if reason != wgpu::DeviceLostReason::Destroyed {
                log::warn!("Horizon renderer: the graphics device was lost ({reason:?}: {message})");
                lost.store(true, std::sync::atomic::Ordering::Relaxed);
            }
        });
        return Ok(Adapted {
            device,
            queue,
            device_lost,
            configured: None,
        });
    }
    #[cfg(not(target_os = "macos"))]
    let opened = match info.backend {
        wgpu::Backend::Vulkan => horizon_hdr::open_vulkan_device(adapter, &desc),
        _ => None,
    };""")

# --- the renderer: offscreen at the eyes' format, the eyes' views
edit(gfx, """        display: output::DisplayReading,
    ) -> Result<Self> {
        let Opened {""", """        display: output::DisplayReading,
    ) -> Result<Self> {
        // A headset shows the frames: none on the window.
        let offscreen = offscreen || crate::xr::active();
        let Opened {""")
edit(gfx, """        } else {
            let format = wgpu::TextureFormat::Bgra8Unorm;
            let config = wgpu::SurfaceConfiguration {""", """        } else {
            // A headset's eyes: their format without sRGB, so a frame copies into them byte for
            // byte (the frame holds sRGB-encoded colour, as a window's does).
            let format = crate::xr::headset()
                .and_then(|h| h.format())
                .map_or(wgpu::TextureFormat::Bgra8Unorm, |f| f.remove_srgb_suffix());
            let config = wgpu::SurfaceConfiguration {""")
edit(gfx, """    /// The offscreen colour target, sized like `config`.
    offscreen: Option<wgpu::Texture>,""", """    /// The offscreen colour target, sized like `config`.
    offscreen: Option<wgpu::Texture>,
    /// QuestCraft's headset: the eye drawn now, and the view both eyes are culled with.
    xr_eye: Option<View>,
    xr_cull: Option<View>,
    /// QuestCraft's headset: the eyes' size, which the frames keep whatever the window's is
    /// (on a Quest an immersive app's window can be none, which suspended the renderer), and
    /// whether a frame for it was not drawn (said once).
    xr_size: Option<[u32; 2]>,
    xr_undrawn: bool,""")
edit(gfx, """            spare_surface: std::sync::Mutex::new(None),
            offscreen,
            in_flight: std::collections::VecDeque::new(),""", """            spare_surface: std::sync::Mutex::new(None),
            offscreen,
            xr_eye: None,
            xr_cull: None,
            xr_size: None,
            xr_undrawn: false,
            in_flight: std::collections::VecDeque::new(),""")
edit(gfx, """    pub fn views(&self, frame: &Frame) -> ViewSet {
        ViewSet::single(self.screen_view(""", """    pub fn views(&self, frame: &Frame) -> ViewSet {
        if let Some(eye) = self.xr_eye {
            return ViewSet::single(eye);
        }
        ViewSet::single(self.screen_view(""")
edit(gfx, """    fn screen_view(&self, fov_degrees: f32, yaw: f32, pitch: f32, far: f32) -> View {
""", """    fn screen_view(&self, fov_degrees: f32, yaw: f32, pitch: f32, far: f32) -> View {
        // A headset's frames: what is built before them is culled with both eyes' view.
        if let Some(cull) = self.xr_cull {
            return cull;
        }
""")

# A headset's frames keep the eyes' size whatever the window's (`resize` is the window's)
edit(gfx, """    pub fn resize(&mut self, width: u32, height: u32) {
        let max = self.device.limits().max_texture_dimension_2d;""", """    pub fn resize(&mut self, width: u32, height: u32) {
        let [width, height] = self.xr_size.unwrap_or([width, height]);
        let max = self.device.limits().max_texture_dimension_2d;""")

# --- the frame: once per eye, into the headset's images
edit(gfx, """    pub fn render(&mut self, frame: &Frame) -> Result<bool> {
        // A lost device cannot accept uploads either. Leave recovery to the
        // owner instead of submitting atlas patches to the invalid device.
        if self.device_lost() {
            return Err("the graphics device was lost".into());
        }""", """    pub fn render(&mut self, frame: &Frame) -> Result<bool> {
        // A lost device cannot accept uploads either. Leave recovery to the
        // owner instead of submitting atlas patches to the invalid device.
        if self.device_lost() {
            return Err("the graphics device was lost".into());
        }
        if crate::xr::active() {
            return self.render_headset(frame);
        }""")
edit(gfx, """    /// Whether the device's loss callback has fired. A surface reconfigure""", """    /// A frame for the headset (QuestCraft): waits for it, then draws `frame` once per eye from
    /// that eye's view at the eyes' size and copies it into the eye's image. Nothing is drawn
    /// while the headset shows none.
    fn render_headset(&mut self, frame: &Frame) -> Result<bool> {
        let begun = {
            let mut headset = crate::xr::headset().ok_or("no headset")?;
            let begun = headset.begin_frame()?;
            // the controllers, for the game's pad (`xr::pad`)
            crate::xr::set_pad(headset.pad());
            begun
        };
        let Some(headset_frame) = begun else {
            if let Some(copies) = self.take_section_copies() {
                self.queue.submit([copies]);
            }
            // hidden: no frames are wanted for now
            std::thread::sleep(std::time::Duration::from_millis(5));
            return Ok(false);
        };
        let [width, height] = headset_frame.size;
        self.xr_size = Some(headset_frame.size);
        if self.suspended || [self.config.width, self.config.height] != headset_frame.size {
            self.resize(width, height);
        }
        let eyes = &headset_frame.eyes;
        // the game looks where the head does (it reads `xr::head`); the world turns with the body
        crate::xr::set_head(&eyes[0]);
        let body = crate::xr::body_yaw();
        self.xr_cull = Some(crate::xr::cull_view(eyes, body, headset_frame.size, frame.depth_far));
        let mut drawn = true;
        for (index, eye) in eyes.iter().enumerate() {
            self.xr_eye = Some(crate::xr::eye_view(eye, body, headset_frame.size, frame.depth_far));
            // the HUD and menus on a panel 1.6 m ahead, a little low, 25 degrees to either side
            self.hud.vr = crate::xr::hud_transform(eyes, index, headset_frame.size, 1.6, 0.15, 25f32.to_radians());
            let result = self.draw_frame(frame, None, self.output);
            self.xr_eye = None;
            self.hud.vr = [1.0, 1.0, 0.0, 0.0];
            drawn &= matches!(result?, true);
            if !drawn && !self.xr_undrawn {
                self.xr_undrawn = true;
                log::warn!(
                    "XR: the renderer drew no frame for the headset (suspended {}, {}x{})",
                    self.suspended,
                    self.config.width,
                    self.config.height
                );
            }
            let target = crate::xr::headset().and_then(|h| h.eye_texture(index));
            if let (Some(source), Some(target)) = (&self.offscreen, target) {
                let mut encoder = self.device.create_command_encoder(&wgpu::CommandEncoderDescriptor {
                    label: Some("Horizon headset eye"),
                });
                encoder.copy_texture_to_texture(
                    source.as_image_copy(),
                    target.as_image_copy(),
                    wgpu::Extent3d {
                        width,
                        height,
                        depth_or_array_layers: 1,
                    },
                );
                // OpenXR takes the image back as a colour attachment (Vulkan's
                // COLOR_ATTACHMENT_OPTIMAL), not as the copy left it: a pass that keeps it as it is
                // leaves it so (a headset's compositor shows nothing otherwise)
                let view = target.create_view(&wgpu::TextureViewDescriptor::default());
                drop(encoder.begin_render_pass(&wgpu::RenderPassDescriptor {
                    label: Some("Horizon headset eye layout"),
                    color_attachments: &[Some(wgpu::RenderPassColorAttachment {
                        view: &view,
                        depth_slice: None,
                        resolve_target: None,
                        ops: wgpu::Operations {
                            load: wgpu::LoadOp::Load,
                            store: wgpu::StoreOp::Store,
                        },
                    })],
                    ..Default::default()
                }));
                self.queue.submit([encoder.finish()]);
            }
        }
        crate::xr::headset().ok_or("no headset")?.end_frame()?;
        Ok(drawn)
    }
    /// Whether the device's loss callback has fired. A surface reconfigure""")
# --- the HUD and menus: a panel ahead in a headset (the HUD shader's `vr`: scale, then offset)
hud_wgsl = render / "hud.wgsl"
edit(hud_wgsl, """    end_portal: array<vec4<f32>, 3>,
}
@group(0) @binding(0) var<uniform> hud: Hud;""", """    end_portal: array<vec4<f32>, 3>,
    // QuestCraft's headset: the screen coordinates scaled by xy and moved by zw, a panel ahead
    // (xr::hud_transform); (1, 1, 0, 0) on a screen
    vr: vec4<f32>,
}
@group(0) @binding(0) var<uniform> hud: Hud;""")
edit(hud_wgsl, """    out.clip = vec4(pixels.x / hud.size.x * 2.0 - 1.0, 1.0 - pixels.y / hud.size.y * 2.0, depth, 1.0);""",
     """    let screen = vec2(pixels.x / hud.size.x * 2.0 - 1.0, 1.0 - pixels.y / hud.size.y * 2.0);
    out.clip = vec4(screen * hud.vr.xy + hud.vr.zw, depth, 1.0);""")
hud = render / "hud.rs"
def edit_bytes(path, old, new, count=1):
    text = path.read_bytes().decode("utf-8", "surrogateescape")
    found = text.count(old)
    if found != count:
        raise SystemExit(f"{path}: expected {count} of {old[:80]!r}, found {found}")
    path.write_bytes(text.replace(old, new).encode("utf-8", "surrogateescape"))
edit_bytes(hud, """    /// `EndPortalFill`: the two sprites, then the game time in x.
    end_portal: [[f32; 4]; 3],
}
impl HudUniform {
    fn new(size: [f32; 4], fill: EndPortalFill, glint_colour: u32) -> Self {""", """    /// `EndPortalFill`: the two sprites, then the game time in x.
    end_portal: [[f32; 4]; 3],
    /// QuestCraft's headset: the panel the HUD is on (`HudRenderer::vr`).
    vr: [f32; 4],
}
impl HudUniform {
    fn new(size: [f32; 4], fill: EndPortalFill, glint_colour: u32) -> Self {""")
edit_bytes(hud, """                [fill.game_time, tint[0], tint[1], tint[2]],
            ],
        }
    }
}""", """                [fill.game_time, tint[0], tint[1], tint[2]],
            ],
            vr: [1.0, 1.0, 0.0, 0.0],
        }
    }
}""")
edit_bytes(hud, """            bytemuck::bytes_of(&HudUniform::new(
                [
                    size[0] as f32,
                    size[1] as f32,
                    batch.scale * self.zoom,
                    batch.glint_alpha,
                ],
                batch.end_portal,
                batch.glint_colour,
            )),""", """            bytemuck::bytes_of(&HudUniform {
                vr: self.vr,
                ..HudUniform::new(
                    [
                        size[0] as f32,
                        size[1] as f32,
                        batch.scale * self.zoom,
                        batch.glint_alpha,
                    ],
                    batch.end_portal,
                    batch.glint_colour,
                )
            }),""")
edit_bytes(hud, """    pub zoom: f32,
""", """    pub zoom: f32,
    /// QuestCraft's headset: the panel the HUD and menus are drawn on in the eye drawn now, as
    /// `xr::hud_transform` gives it; (1, 1, 0, 0) on a screen.
    pub vr: [f32; 4],
""")
edit_bytes(hud, """            zoom: 1.0,
""", """            zoom: 1.0,
            vr: [1.0, 1.0, 0.0, 0.0],
""")
print("patched", render)
