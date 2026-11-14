//! Tries the XR core against whatever OpenXR runtime there is (Monado's simulated headset on a
//! desktop): the runtime, wgpu's device on its GPU, the session, and frames with the left eye red
//! and the right green.
//!   cargo run -p horizon-xr --example probe
use horizon_xr::{Status, Xr};

/// The crate's log lines on standard error.
struct Stderr;
impl log::Log for Stderr {
    fn enabled(&self, _: &log::Metadata) -> bool {
        true
    }
    fn log(&self, record: &log::Record) {
        if record.target().starts_with("horizon_xr") {
            eprintln!("[{}] {}", record.level(), record.args());
        }
    }
    fn flush(&self) {}
}

fn main() {
    log::set_logger(&Stderr).unwrap();
    log::set_max_level(log::LevelFilter::Info);
    let mut xr = Xr::start("Horizon XR probe").unwrap_or_else(|e| panic!("{e}"));
    let instance = xr.wgpu_instance(wgpu::InstanceFlags::empty()).unwrap();
    let adapter = xr.adapter(&instance).unwrap();
    println!("adapter: {:?}", adapter.get_info().name);
    let desc = wgpu::DeviceDescriptor {
        label: Some("probe"),
        required_limits: adapter.limits(),
        ..Default::default()
    };
    let (device, queue) = xr.device(&adapter, &desc).unwrap();
    xr.create_session(&device, 0.5).unwrap();
    // PROBE_FRAMES: how many frames to draw (default 120)
    let frames = std::env::var("PROBE_FRAMES").ok().and_then(|f| f.parse().ok()).unwrap_or(120);
    let mut drawn = 0;
    loop {
        match xr.begin_frame().unwrap() {
            Some(frame) => {
                if drawn == 0 {
                    println!("first frame {:?}: {:?}", frame.size, frame.eyes);
                }
                let mut encoder = device.create_command_encoder(&Default::default());
                for (eye, color) in [(0, wgpu::Color::RED), (1, wgpu::Color::GREEN)] {
                    let view = xr.eye_texture(eye).unwrap().create_view(&Default::default());
                    encoder.begin_render_pass(&wgpu::RenderPassDescriptor {
                        label: Some("probe eye"),
                        color_attachments: &[Some(wgpu::RenderPassColorAttachment {
                            view: &view,
                            depth_slice: None,
                            resolve_target: None,
                            ops: wgpu::Operations {
                                load: wgpu::LoadOp::Clear(color),
                                store: wgpu::StoreOp::Store,
                            },
                        })],
                        ..Default::default()
                    });
                }
                queue.submit([encoder.finish()]);
                xr.end_frame().unwrap();
                drawn += 1;
                if drawn == frames {
                    break;
                }
            }
            None => {
                if xr.poll().unwrap() == Status::Exit {
                    break;
                }
                std::thread::sleep(std::time::Duration::from_millis(10));
            }
        }
    }
    println!("drew {drawn} frames");
}
