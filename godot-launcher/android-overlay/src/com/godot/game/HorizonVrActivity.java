package com.godot.game;

/**
 * Horizon in VR: the same as HorizonActivity, but the headset starts it as an immersive app (the manifest's VR
 * category), so the game's OpenXR session (horizon-quest's XR core) shows it in the headset instead of a panel. The
 * launcher starts it for an instance in VR (its command line has "--vr").
 */
public class HorizonVrActivity extends HorizonActivity {
}
