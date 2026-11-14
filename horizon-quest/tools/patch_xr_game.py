"""The game's side of a headset (horizon-client): the player looks where the head does. build.sh runs it
after patch_xr_renderer.py:

    python3 -I patch_xr_game.py HORIZON_DIR

In a headset turning (the mouse, a stick, the arrow keys) turns the body (`xr::turn_body`), and every
frame the player's yaw is the body's plus the head's turn and their pitch the head's (`follow_headset`),
through the game's own turn (`Entity.turn`'s), so aiming, the held lead and a boat's limit follow.
"""
import sys
from pathlib import Path

game = Path(sys.argv[1]) / "crates/horizon-client/src"


def edit(path, old, new, count=1):
    text = path.read_text()
    found = text.count(old)
    if found != count:
        raise SystemExit(f"{path}: expected {count} of {old[:80]!r}, found {found}")
    path.write_text(text.replace(old, new))


edit(game / "game/perspective.rs", """    pub(super) fn turn_player(&mut self, xo: f64, yo: f64) {
        if self.studio.flying() {""", """    pub(super) fn turn_player(&mut self, xo: f64, yo: f64) {
        // A headset (QuestCraft): turning turns the body, the head looks around on its own.
        if horizon_client_render::xr::active() && !self.studio.flying() {
            horizon_client_render::xr::turn_body((xo * 0.15) as f32);
            return self.follow_headset();
        }
        self.turn_player_by(xo, yo)
    }
    /// A headset (QuestCraft): the player looks where the head does, the body's yaw (`turn_player`)
    /// plus the head's turn, and the head's pitch.
    pub(super) fn follow_headset(&mut self) {
        // the world the player is put in stays the way they face
        horizon_client_render::xr::align_body(self.yaw);
        let Some([head_yaw, head_pitch]) = horizon_client_render::xr::head() else {
            return;
        };
        let yaw = horizon_client_render::xr::body_yaw() + head_yaw;
        let turn = (yaw - self.yaw + 180.0).rem_euclid(360.0) - 180.0;
        let pitch = head_pitch.clamp(-90.0, 90.0) - self.pitch;
        self.turn_player_by(f64::from(turn / 0.15), f64::from(pitch / 0.15));
    }
    fn turn_player_by(&mut self, xo: f64, yo: f64) {
        if self.studio.flying() {""")

edit(game / "game.rs", """                    if self.title.is_none() {
                        horizon_client::spans::time("update_world", || self.update_world());""", """                    // A headset (QuestCraft): the player looks where the head does.
                    if horizon_client_render::xr::active() && self.title.is_none() {
                        self.follow_headset();
                    }
                    if self.title.is_none() {
                        horizon_client::spans::time("update_world", || self.update_world());""")
print("patched", game)
