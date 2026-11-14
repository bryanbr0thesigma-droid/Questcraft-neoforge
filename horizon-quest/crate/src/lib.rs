//! Horizon on Meta Quest (QuestCraft): the game, started by Android's NativeActivity.
//!
//! Horizon's own game (horizon-client's entry) runs unchanged but for its arguments and event
//! loop, which on Android come from this entry point (`horizon_client::game::ANDROID`, the change
//! patches/ makes for it).
#![cfg(target_os = "android")]

use std::path::Path;
use winit::platform::android::activity::AndroidApp;

#[global_allocator]
static ALLOCATOR: horizon_threads::Allocator = horizon_threads::ALLOCATOR;

/// The prepared resources' version (research-lock.json's).
const VERSION: &str = "26.3";

/// The game's arguments from QuestCraft's launcher, one per line, in the app's internal files
/// (HorizonActivity writes it before the library starts): the instance's game folder, the
/// prepared resources and the player's name.
const LAUNCH_ARGS: &str = "horizon-args.txt";

#[unsafe(no_mangle)]
fn android_main(app: AndroidApp) {
    let args = app
        .internal_data_path()
        .and_then(|files| std::fs::read_to_string(files.join(LAUNCH_ARGS)).ok())
        .map(|text| text.lines().map(str::to_owned).filter(|a| !a.is_empty()).collect())
        .unwrap_or_else(|| standalone_args(&app));
    let _ = horizon_client::game::ANDROID.set((app, args));
    // Logging, the crash handler and the game, as the desktop executable starts them
    horizon_client::entry::main();
}

/// Started on its own (Horizon-quest.apk): the app's folder on shared storage
/// (Android/data/<package>/files), where the resources and the game folder can be reached over
/// USB; the internal one when there is none.
fn standalone_args(app: &AndroidApp) -> Vec<String> {
    let Some(base) = app.external_data_path().or_else(|| app.internal_data_path()) else {
        return Vec::new();
    };
    let path = |p: &Path| p.display().to_string();
    vec![
        "--game-dir".to_owned(),
        path(&base.join("horizon")),
        "--home".to_owned(),
        path(&base.join("horizon")),
        "--resources".to_owned(),
        path(&base.join("resources").join(VERSION)),
    ]
}
