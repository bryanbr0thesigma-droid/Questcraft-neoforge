//! Horizon on Meta Quest (QuestCraft): the game, started by Android's NativeActivity.
//!
//! Horizon's own game module (horizon-client's `game`, linked here as src/game.rs and src/game/)
//! runs unchanged but for its arguments and event loop, which on Android come from this entry
//! point (`game::ANDROID`, the one change horizon-client carries for it).
#![cfg(target_os = "android")]
mod game;

use horizon_client::{crash, logging};
use winit::platform::android::activity::AndroidApp;

#[global_allocator]
static ALLOCATOR: horizon_threads::Allocator = horizon_threads::Allocator;

/// The prepared resources' version (research-lock.json's): QuestCraft prepares them into
/// `<app storage>/resources/<version>`.
const VERSION: &str = "26.3";

#[unsafe(no_mangle)]
fn android_main(app: AndroidApp) {
    horizon_threads::configure_allocator();
    // The app's folder on shared storage (Android/data/<package>/files), where the resources and
    // the game folder can be reached over USB; the internal one when there is none.
    let Some(base) = app.external_data_path().or_else(|| app.internal_data_path()) else {
        return;
    };
    let home = base.join("horizon");
    let _ = std::fs::create_dir_all(&home);
    logging::set_game_home(home.clone());
    logging::init(&home, "Render thread");
    log::info!("Horizon {} on Meta Quest (QuestCraft)", crash::version());
    crash::install(home.clone(), "client");
    let resources = base.join("resources").join(VERSION);
    let args = vec![
        "--game-dir".to_owned(),
        home.display().to_string(),
        "--resources".to_owned(),
        resources.display().to_string(),
    ];
    let _ = game::ANDROID.set((app, args));
    if let Err(error) = game::run() {
        log::error!("Horizon: {error}");
        logging::flush();
        crash::report_error(&home, "client", &*error);
    }
    logging::flush();
}
