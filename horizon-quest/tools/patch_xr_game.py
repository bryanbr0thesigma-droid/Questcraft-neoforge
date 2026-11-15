"""The game's side of a headset (horizon-client): the player looks where the head does. build.sh runs it
after patch_xr_renderer.py:

    python3 -I patch_xr_game.py HORIZON_DIR

In a headset turning (the mouse, a stick, the arrow keys) turns the body (`xr::turn_body`), and every
frame the player's yaw is the body's plus the head's turn and their pitch the head's (`follow_headset`),
through the game's own turn (`Entity.turn`'s), so aiming, the held lead and a boat's limit follow.

The controllers are a gamepad merged into the game's, which counts it without the window's focus;
the headset's session losing its focus pauses; in a menu the laser is the mouse (`headset_input`).
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
# the controllers: a gamepad merged into the one the game reads
edit(game / "game/pad.rs", """            Some(state) => state,
            None => self.pad.read(),
        };""", """            Some(state) => state,
            // A headset's controllers (QuestCraft): a gamepad of their own, with any other
            None => {
                let hardware = self.pad.read();
                horizon_client_render::xr::pad().map_or(hardware, |pad| hardware.merge(&pad))
            }
        };""")
# The headset's focus is the window's, and in a menu its laser is the mouse (Vivecraft's way)
edit(game / "game/pad.rs", """    /// Once per loop iteration: read the pads and act on button changes.
    pub(super) fn poll_pads(&mut self, event_loop: &dyn EventLoopCtl) {
""", """    /// A headset (QuestCraft): the headset's session losing its focus (the system's menu over the
    /// game) is the window losing it, for pausing; in a menu the controller's laser is the mouse
    /// over the menu's panel, its trigger the left button. The window itself is left as it is
    /// (an immersive app's never gets the focus; the pad counts without it, `pad_has_window`).
    fn headset_input(&mut self, event_loop: &dyn EventLoopCtl) {
        use horizon_client_render::xr;
        use winit::{
            dpi::PhysicalPosition,
            event::{DeviceId, ElementState, WindowEvent},
        };
        let Some(id) = self.window.as_ref().map(|w| w.id()) else {
            return;
        };
        let origin = super::input_trace::InputOrigin::Game;
        // `pause_if_inactive` pauses a world half a second after the focus went
        match (xr::focused(), self.focus_lost) {
            (false, None) => self.focus_lost = Some(std::time::Instant::now()),
            (true, Some(_)) => self.focus_lost = None,
            _ => {}
        }
        // a screen, or a prompt over the world (a dialog, the pack prompt...): the pad's menus
        xr::set_menu(self.pad_in_menu());
        for event in xr::pointer_events() {
            let event = match event {
                xr::PointerEvent::Moved([x, y]) => WindowEvent::CursorMoved {
                    device_id: DeviceId::dummy(),
                    position: PhysicalPosition::new(x, y),
                },
                xr::PointerEvent::Button(pressed) => WindowEvent::MouseInput {
                    device_id: DeviceId::dummy(),
                    state: if pressed {
                        ElementState::Pressed
                    } else {
                        ElementState::Released
                    },
                    button: MouseButton::Left,
                },
            };
            self.handle_window_event(event_loop, id, event, origin);
        }
    }

    /// Once per loop iteration: read the pads and act on button changes.
    pub(super) fn poll_pads(&mut self, event_loop: &dyn EventLoopCtl) {
        if horizon_client_render::xr::active() {
            self.headset_input(event_loop);
        }
""")
# the pad counts in a headset without the window's focus
edit(game / "game/pad.rs", """    pub(super) fn pad_has_window(&self) -> bool {
        self.focused || self.settings.controller.play_in_background""", """    pub(super) fn pad_has_window(&self) -> bool {
        self.focused
            || self.settings.controller.play_in_background
            || horizon_client_render::xr::active()""")
# the headset paces the frames (its own rate, 72 or 90 a second): no menu limit of 60 on top
edit(game / "game/options.rs", """    pub(super) fn framerate_limit(&self) -> i32 {
""", """    pub(super) fn framerate_limit(&self) -> i32 {
        if horizon_client_render::xr::active() {
            return 260;
        }
""")
print("patched", game)
