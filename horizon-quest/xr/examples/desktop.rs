//! Horizon in a headset on a desktop: the game with the OpenXR core installed as its headset, as
//! horizon-quest starts it on the Quest. With Monado's simulated headset this tries the whole VR
//! path without one. Takes the game's own arguments.
//!   cargo run -p horizon-xr --example desktop -- --resources PATH --game-dir DIR --world NAME
fn main() {
    horizon_client_render::xr::install_with(|| {
        horizon_xr::Xr::start("Horizon")
            .map(|xr| Box::new(xr) as Box<dyn horizon_client_render::xr::Headset>)
    });
    horizon_client::entry::main();
}
