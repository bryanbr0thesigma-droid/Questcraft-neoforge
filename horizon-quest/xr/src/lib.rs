//! QuestCraft's OpenXR core for Horizon: the headset's session, the Vulkan device it shares with
//! Horizon's wgpu, and its frames.
//!
//! The device is wgpu's own: OpenXR (`XR_KHR_vulkan_enable`) names the Vulkan extensions it needs
//! on the instance and the device and the GPU to use, and wgpu creates both with those added
//! (`init_with_callback`, `open_with_callback`), so Horizon's renderer runs on it unchanged. Each
//! eye has a swapchain whose images are wgpu textures; a frame is `begin_frame` (waits for the
//! headset, says where the eyes are), drawing into `eye_texture(0)` and `(1)`, then `end_frame`.
//!
//! Poses are in OpenXR's tracking space: x right, y up, -z ahead, metres, from where the head was
//! when the session started (the `LOCAL` space: seated or standing in place).
use ash::vk::{self, Handle};
use openxr as xr;
use std::ffi::{CStr, CString};
use wgpu::hal::api::Vulkan;

pub type Result<T> = std::result::Result<T, String>;

const VIEWS: xr::ViewConfigurationType = xr::ViewConfigurationType::PRIMARY_STEREO;

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

/// A frame the headset wants drawn: both eyes, at the swapchains' size.
#[derive(Clone, Copy, Debug)]
pub struct Frame {
    pub eyes: [Eye; 2],
    pub size: [u32; 2],
}

/// What the session is doing, from its events.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Status {
    /// Not shown yet, or hidden (the headset is off the head, the system menu is up): no frames.
    Idle,
    /// Frames are wanted.
    Running,
    /// The runtime ends the session: the game should quit.
    Exit,
}

struct Swapchain {
    handle: xr::Swapchain<xr::Vulkan>,
    images: Vec<wgpu::Texture>,
    current: Option<usize>,
}

struct Session {
    session: xr::Session<xr::Vulkan>,
    waiter: xr::FrameWaiter,
    stream: xr::FrameStream<xr::Vulkan>,
    space: xr::Space,
    swapchains: Vec<Swapchain>,
    size: [u32; 2],
    format: wgpu::TextureFormat,
    running: bool,
    /// The frame begun and not ended yet: its display time and views.
    open: Option<(xr::Time, Vec<xr::View>)>,
}

pub struct Xr {
    instance: xr::Instance,
    system: xr::SystemId,
    blend: xr::EnvironmentBlendMode,
    session: Option<Session>,
    events: xr::EventDataBuffer,
    status: Status,
}

fn err(what: &str) -> impl Fn(xr::sys::Result) -> String + '_ {
    move |e| format!("OpenXR {what}: {e}")
}

/// OpenXR's extension names (space separated), as the `'static` names wgpu's callbacks take. Made
/// once per instance or device.
fn leak_names(names: &str) -> Vec<&'static CStr> {
    names
        .split_ascii_whitespace()
        .filter_map(|n| CString::new(n).ok())
        .map(|n| &*Box::leak(n.into_boxed_c_str()))
        .collect()
}

impl Xr {
    /// The runtime (the headset's OpenXR, or Monado on a desktop) and its headset. Fails when there
    /// is none, or it can't share a Vulkan device.
    pub fn start(application: &str) -> Result<Self> {
        #[cfg(target_os = "android")]
        let platform = {
            let context = ndk_context::android_context();
            // Safety: the JavaVM and the activity of this process, as ndk-context keeps them.
            unsafe { xr::AndroidPlatformInfo::new(context.vm(), context.context()) }
        };
        #[cfg(not(target_os = "android"))]
        let platform = ();
        // Safety: the system's OpenXR loader (on Android the one in the APK).
        let entry = unsafe { xr::Entry::load(&platform) }
            .map_err(|e| format!("no OpenXR loader: {e}"))?;
        let offered = entry.enumerate_extensions().map_err(err("extensions"))?;
        if !offered.khr_vulkan_enable {
            return Err("the OpenXR runtime has no Vulkan (XR_KHR_vulkan_enable)".into());
        }
        let mut wanted = xr::ExtensionSet::default();
        wanted.khr_vulkan_enable = true;
        #[cfg(target_os = "android")]
        {
            wanted.khr_android_create_instance = true;
        }
        let instance = entry
            .create_instance(
                &xr::ApplicationInfo {
                    application_name: application,
                    application_version: 1,
                    engine_name: "Horizon",
                    engine_version: 1,
                    api_version: xr::Version::new(1, 0, 0),
                },
                &wanted,
                &[],
                &platform,
            )
            .map_err(err("instance"))?;
        let properties = instance.properties().map_err(err("instance properties"))?;
        let system = instance
            .system(xr::FormFactor::HEAD_MOUNTED_DISPLAY)
            .map_err(err("headset"))?;
        let blend = instance
            .enumerate_environment_blend_modes(system, VIEWS)
            .map_err(err("blend modes"))?
            .first()
            .copied()
            .unwrap_or(xr::EnvironmentBlendMode::OPAQUE);
        log::info!(
            "XR: {} {}, headset {}",
            properties.runtime_name,
            properties.runtime_version,
            instance
                .system_properties(system)
                .map(|p| p.system_name)
                .unwrap_or_default()
        );
        Ok(Self {
            instance,
            system,
            blend,
            session: None,
            events: xr::EventDataBuffer::new(),
            status: Status::Idle,
        })
    }

    /// wgpu's Vulkan instance with the extensions the runtime needs.
    pub fn wgpu_instance(&self, flags: wgpu::InstanceFlags) -> Result<wgpu::Instance> {
        let names = self
            .instance
            .vulkan_legacy_instance_extensions(self.system)
            .map_err(err("Vulkan instance extensions"))?;
        let wanted = leak_names(&names);
        let desc = wgpu::hal::InstanceDescriptor {
            name: "Horizon",
            flags,
            memory_budget_thresholds: Default::default(),
            backend_options: Default::default(),
            telemetry: None,
            display: None,
        };
        // Safety: the callback only adds extensions, which the runtime says the instance has.
        let hal = unsafe {
            wgpu::hal::vulkan::Instance::init_with_callback(
                &desc,
                Some(Box::new(move |args: wgpu::hal::vulkan::CreateInstanceCallbackArgs| {
                    for name in &wanted {
                        if !args.extensions.contains(name) {
                            args.extensions.push(name);
                        }
                    }
                })),
            )
        }
        .map_err(|e| format!("Vulkan instance for OpenXR: {e}"))?;
        // Safety: a Vulkan instance wgpu made and owns.
        Ok(unsafe { wgpu::Instance::from_hal::<Vulkan>(hal) })
    }

    /// The GPU the runtime draws with, among `instance`'s.
    pub fn adapter(&self, instance: &wgpu::Instance) -> Result<wgpu::Adapter> {
        let raw = {
            // Safety: read only, while the instance lives.
            let hal = unsafe { instance.as_hal::<Vulkan>() }.ok_or("not a Vulkan instance")?;
            hal.shared_instance().raw_instance().handle().as_raw()
        };
        // Safety: a live VkInstance with the runtime's extensions.
        let wanted = unsafe { self.instance.vulkan_graphics_device(self.system, raw as _) }
            .map_err(err("Vulkan GPU"))? as u64;
        for adapter in pollster::block_on(instance.enumerate_adapters(wgpu::Backends::VULKAN)) {
            // Safety: read only.
            let phd = unsafe { adapter.as_hal::<Vulkan>() }.map(|a| a.raw_physical_device().as_raw());
            if phd == Some(wanted) {
                return Ok(adapter);
            }
        }
        Err("wgpu offers no adapter for the GPU OpenXR names".into())
    }

    /// The device, with the extensions the runtime needs: `desc` as wgpu's `request_device` takes it.
    pub fn device(
        &self,
        adapter: &wgpu::Adapter,
        desc: &wgpu::DeviceDescriptor<'_>,
    ) -> Result<(wgpu::Device, wgpu::Queue)> {
        // The runtime must be asked before a session (and the Vulkan versions it takes are wide).
        let requirements = self
            .instance
            .graphics_requirements::<xr::Vulkan>(self.system)
            .map_err(err("Vulkan requirements"))?;
        log::info!(
            "XR: Vulkan {} to {}",
            requirements.min_api_version_supported,
            requirements.max_api_version_supported
        );
        let names = self
            .instance
            .vulkan_legacy_device_extensions(self.system)
            .map_err(err("Vulkan device extensions"))?;
        let open = {
            // Safety: read only, dropped before wgpu takes the device.
            let hal = unsafe { adapter.as_hal::<Vulkan>() }.ok_or("not a Vulkan adapter")?;
            // The ones the GPU has: one it lacks would fail the whole device. A headset's GPU has
            // them all; a desktop test bed's software GPU (llvmpipe) has no fd semaphores or fences.
            // Safety: the adapter's own physical device on its own instance.
            let offered = unsafe {
                hal.shared_instance()
                    .raw_instance()
                    .enumerate_device_extension_properties(hal.raw_physical_device())
            }
            .map_err(|e| format!("Vulkan device extensions: {e}"))?;
            let (wanted, missing): (Vec<_>, Vec<_>) = leak_names(&names).into_iter().partition(|name| {
                offered
                    .iter()
                    .any(|e| e.extension_name_as_c_str() == Ok(*name))
            });
            if !missing.is_empty() {
                log::warn!("XR: the GPU lacks device extensions the runtime asks for: {missing:?}");
            }
            // Safety: what wgpu's request_device does, with the runtime's extensions added.
            unsafe {
                hal.open_with_callback(
                    desc.required_features,
                    &desc.required_limits,
                    &desc.memory_hints,
                    Some(Box::new(move |args: wgpu::hal::vulkan::CreateDeviceCallbackArgs| {
                        for name in &wanted {
                            if !args.extensions.contains(name) {
                                args.extensions.push(name);
                            }
                        }
                    })),
                )
            }
            .map_err(|e| format!("Vulkan device for OpenXR: {e}"))?
        };
        // Safety: opened from this adapter with `desc`'s features and limits.
        unsafe { adapter.create_device_from_hal(open, desc) }.map_err(|e| e.to_string())
    }

    /// The session on `device` (from `device`), with a swapchain per eye at the runtime's
    /// recommended size times `scale`.
    pub fn create_session(&mut self, device: &wgpu::Device, scale: f32) -> Result<()> {
        let info = {
            // Safety: read only, the handles stay wgpu's.
            let hal = unsafe { device.as_hal::<Vulkan>() }.ok_or("not a Vulkan device")?;
            xr::vulkan::SessionCreateInfo {
                instance: hal.shared_instance().raw_instance().handle().as_raw() as _,
                physical_device: hal.raw_physical_device().as_raw() as _,
                device: hal.raw_device().handle().as_raw() as _,
                queue_family_index: hal.queue_family_index(),
                queue_index: hal.queue_index(),
            }
        };
        // Safety: the device and queue wgpu keeps for as long as the session (`Drop` ends it first).
        let (session, waiter, stream) =
            unsafe { self.instance.create_session::<xr::Vulkan>(self.system, &info) }
                .map_err(err("session"))?;
        let space = session
            .create_reference_space(xr::ReferenceSpaceType::LOCAL, xr::Posef::IDENTITY)
            .map_err(err("space"))?;
        let views = self
            .instance
            .enumerate_view_configuration_views(self.system, VIEWS)
            .map_err(err("views"))?;
        let view = views.first().ok_or("the headset has no views")?;
        let size = [
            ((view.recommended_image_rect_width as f32 * scale) as u32)
                .clamp(16, view.max_image_rect_width),
            ((view.recommended_image_rect_height as f32 * scale) as u32)
                .clamp(16, view.max_image_rect_height),
        ];
        let formats = session.enumerate_swapchain_formats().map_err(err("formats"))?;
        // sRGB, so what is drawn in linear light is shown right (`eye_texture`'s format says which)
        let (vk_format, format) = [
            (vk::Format::R8G8B8A8_SRGB, wgpu::TextureFormat::Rgba8UnormSrgb),
            (vk::Format::B8G8R8A8_SRGB, wgpu::TextureFormat::Bgra8UnormSrgb),
        ]
        .into_iter()
        .find(|(vk, _)| formats.contains(&(vk.as_raw() as u32)))
        .ok_or("the headset offers no 8-bit sRGB swapchain")?;
        let mut swapchains = Vec::new();
        for eye in 0..views.len().min(2) {
            let handle = session
                .create_swapchain(&xr::SwapchainCreateInfo {
                    create_flags: xr::SwapchainCreateFlags::EMPTY,
                    usage_flags: xr::SwapchainUsageFlags::COLOR_ATTACHMENT
                        | xr::SwapchainUsageFlags::SAMPLED
                        | xr::SwapchainUsageFlags::TRANSFER_DST,
                    format: vk_format.as_raw() as u32,
                    sample_count: 1,
                    width: size[0],
                    height: size[1],
                    face_count: 1,
                    array_size: 1,
                    mip_count: 1,
                })
                .map_err(err("swapchain"))?;
            let images = handle
                .enumerate_images()
                .map_err(err("swapchain images"))?
                .into_iter()
                .map(|image| wrap_image(device, image, size, format, eye))
                .collect::<Result<Vec<_>>>()?;
            swapchains.push(Swapchain { handle, images, current: None });
        }
        log::info!("XR: session with {}x{} per eye ({format:?})", size[0], size[1]);
        self.session = Some(Session {
            session,
            waiter,
            stream,
            space,
            swapchains,
            size,
            format,
            running: false,
            open: None,
        });
        Ok(())
    }

    /// The eyes' texture format (sRGB).
    pub fn format(&self) -> Option<wgpu::TextureFormat> {
        self.session.as_ref().map(|s| s.format)
    }

    /// Handles the runtime's events: begins and ends the session as it is shown and hidden.
    pub fn poll(&mut self) -> Result<Status> {
        while let Some(event) = self.instance.poll_event(&mut self.events).map_err(err("events"))? {
            match event {
                xr::Event::SessionStateChanged(change) => {
                    let Some(s) = &mut self.session else { continue };
                    log::info!("XR: session {:?}", change.state());
                    match change.state() {
                        xr::SessionState::READY => {
                            s.session.begin(VIEWS).map_err(err("begin"))?;
                            s.running = true;
                        }
                        xr::SessionState::STOPPING => {
                            s.session.end().map_err(err("end"))?;
                            s.running = false;
                        }
                        xr::SessionState::EXITING | xr::SessionState::LOSS_PENDING => {
                            self.status = Status::Exit;
                        }
                        _ => {}
                    }
                }
                xr::Event::InstanceLossPending(_) => self.status = Status::Exit,
                _ => {}
            }
        }
        if self.status != Status::Exit {
            self.status = match &self.session {
                Some(s) if s.running => Status::Running,
                _ => Status::Idle,
            };
        }
        Ok(self.status)
    }

    /// Waits until the headset wants the next frame and begins it: the eyes to draw, or `None`
    /// when this one is not shown (it is ended already). Call `end_frame` after drawing.
    pub fn begin_frame(&mut self) -> Result<Option<Frame>> {
        if self.poll()? != Status::Running {
            return Ok(None);
        }
        let blend = self.blend;
        let s = self.session.as_mut().ok_or("no session")?;
        let state = s.waiter.wait().map_err(err("wait"))?;
        s.stream.begin().map_err(err("begin frame"))?;
        if !state.should_render {
            s.stream
                .end(state.predicted_display_time, blend, &[])
                .map_err(err("end frame"))?;
            return Ok(None);
        }
        let (_, views) = s
            .session
            .locate_views(VIEWS, state.predicted_display_time, &s.space)
            .map_err(err("views"))?;
        for chain in &mut s.swapchains {
            let index = chain.handle.acquire_image().map_err(err("acquire"))?;
            chain
                .handle
                .wait_image(xr::Duration::INFINITE)
                .map_err(err("wait image"))?;
            chain.current = Some(index as usize);
        }
        let eye = |v: &xr::View| Eye {
            position: [v.pose.position.x, v.pose.position.y, v.pose.position.z],
            orientation: [
                v.pose.orientation.x,
                v.pose.orientation.y,
                v.pose.orientation.z,
                v.pose.orientation.w,
            ],
            fov: Fov {
                left: v.fov.angle_left,
                right: v.fov.angle_right,
                up: v.fov.angle_up,
                down: v.fov.angle_down,
            },
        };
        let frame = Frame {
            eyes: [eye(&views[0]), eye(views.get(1).unwrap_or(&views[0]))],
            size: s.size,
        };
        s.open = Some((state.predicted_display_time, views));
        Ok(Some(frame))
    }

    /// The image of `eye` (0 left, 1 right) to draw this frame into.
    pub fn eye_texture(&self, eye: usize) -> Option<&wgpu::Texture> {
        let chain = self.session.as_ref()?.swapchains.get(eye)?;
        chain.images.get(chain.current?)
    }

    /// Hands both eyes to the headset. The frame's drawing must be submitted to the queue first.
    pub fn end_frame(&mut self) -> Result<()> {
        let blend = self.blend;
        let s = self.session.as_mut().ok_or("no session")?;
        let Some((time, views)) = s.open.take() else {
            return Ok(());
        };
        for chain in &mut s.swapchains {
            if chain.current.take().is_some() {
                chain.handle.release_image().map_err(err("release"))?;
            }
        }
        let rect = xr::Rect2Di {
            offset: xr::Offset2Di { x: 0, y: 0 },
            extent: xr::Extent2Di {
                width: s.size[0] as i32,
                height: s.size[1] as i32,
            },
        };
        let projected: Vec<_> = views
            .iter()
            .zip(&s.swapchains)
            .map(|(view, chain)| {
                xr::CompositionLayerProjectionView::new()
                    .pose(view.pose)
                    .fov(view.fov)
                    .sub_image(
                        xr::SwapchainSubImage::new()
                            .swapchain(&chain.handle)
                            .image_array_index(0)
                            .image_rect(rect),
                    )
            })
            .collect();
        let layer = xr::CompositionLayerProjection::new()
            .space(&s.space)
            .views(&projected);
        s.stream
            .end(time, blend, &[&layer])
            .map_err(err("end frame"))
    }
}

/// A swapchain image as a wgpu texture the swapchain keeps owning.
fn wrap_image(
    device: &wgpu::Device,
    image: u64,
    size: [u32; 2],
    format: wgpu::TextureFormat,
    eye: usize,
) -> Result<wgpu::Texture> {
    let label = if eye == 0 { "XR left eye" } else { "XR right eye" };
    let extent = wgpu::Extent3d {
        width: size[0],
        height: size[1],
        depth_or_array_layers: 1,
    };
    let hal_texture = {
        // Safety: read only.
        let hal = unsafe { device.as_hal::<Vulkan>() }.ok_or("not a Vulkan device")?;
        // Safety: the runtime made the image with this size and format, for colour and sampling;
        // the drop callback leaves it to the swapchain.
        unsafe {
            hal.texture_from_raw(
                vk::Image::from_raw(image),
                &wgpu::hal::TextureDescriptor {
                    label: Some(label),
                    size: extent,
                    mip_level_count: 1,
                    sample_count: 1,
                    dimension: wgpu::TextureDimension::D2,
                    format,
                    usage: wgpu::TextureUses::COLOR_TARGET
                        | wgpu::TextureUses::RESOURCE
                        | wgpu::TextureUses::COPY_DST,
                    memory_flags: wgpu::hal::MemoryFlags::empty(),
                    view_formats: Vec::new(),
                },
                Some(Box::new(|| {})),
                wgpu::hal::vulkan::TextureMemory::External,
            )
        }
    };
    // Safety: the hal texture just made, with the same description.
    Ok(unsafe {
        device.create_texture_from_hal::<Vulkan>(
            hal_texture,
            &wgpu::TextureDescriptor {
                label: Some(label),
                size: extent,
                mip_level_count: 1,
                sample_count: 1,
                dimension: wgpu::TextureDimension::D2,
                format,
                usage: wgpu::TextureUsages::RENDER_ATTACHMENT
                    | wgpu::TextureUsages::TEXTURE_BINDING
                    | wgpu::TextureUsages::COPY_DST,
                view_formats: &[],
            },
            // what an acquired swapchain image holds is not kept: each frame draws all of it
            wgpu::TextureUses::UNINITIALIZED,
        )
    })
}

/// The core as Horizon's renderer takes a headset (`horizon_client_render::xr`): the session is
/// made with the device, its eyes at the runtime's recommended size times `HORIZON_XR_SCALE`
/// (default 1).
impl horizon_client_render::xr::Headset for Xr {
    fn instance(&mut self, flags: wgpu::InstanceFlags) -> Result<wgpu::Instance> {
        self.wgpu_instance(flags)
    }

    fn adapter(&mut self, instance: &wgpu::Instance) -> Result<wgpu::Adapter> {
        Xr::adapter(self, instance)
    }

    fn device(
        &mut self,
        adapter: &wgpu::Adapter,
        desc: &wgpu::DeviceDescriptor<'_>,
    ) -> Result<(wgpu::Device, wgpu::Queue)> {
        let (device, queue) = Xr::device(self, adapter, desc)?;
        let scale = std::env::var("HORIZON_XR_SCALE")
            .ok()
            .and_then(|s| s.parse::<f32>().ok())
            .filter(|s| *s > 0.1 && *s <= 2.0)
            .unwrap_or(1.0);
        self.create_session(&device, scale)?;
        Ok((device, queue))
    }

    fn format(&self) -> Option<wgpu::TextureFormat> {
        Xr::format(self)
    }

    fn begin_frame(&mut self) -> Result<Option<horizon_client_render::xr::Frame>> {
        use horizon_client_render::xr as h;
        Ok(Xr::begin_frame(self)?.map(|frame| h::Frame {
            eyes: frame.eyes.map(|eye| h::Eye {
                position: eye.position,
                orientation: eye.orientation,
                fov: h::Fov {
                    left: eye.fov.left,
                    right: eye.fov.right,
                    up: eye.fov.up,
                    down: eye.fov.down,
                },
            }),
            size: frame.size,
        }))
    }

    fn eye_texture(&self, eye: usize) -> Option<wgpu::Texture> {
        Xr::eye_texture(self, eye).cloned()
    }

    fn end_frame(&mut self) -> Result<()> {
        Xr::end_frame(self)
    }

    fn exiting(&self) -> bool {
        self.status == Status::Exit
    }
}
