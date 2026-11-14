package com.godot.game;

import android.os.Bundle;
import android.util.Log;

/**
 * Horizon in VR: the same as HorizonActivity, but the headset starts it as an immersive app (the manifest's VR
 * category), so the game's OpenXR session (horizon-quest's XR core) shows it in the headset instead of a panel. The
 * launcher starts it for an instance in VR (its command line has "--vr").
 */
public class HorizonVrActivity extends HorizonActivity {
    /**
     * The eyes' images at this share of the size the headset recommends (the XR core's HORIZON_XR_SCALE): Horizon
     * draws the whole frame once per eye, and at the full 1680x1760 of a Quest 3 that is six times the pixels of its
     * panel, twice.
     */
    private static final String EYE_SCALE = "0.75";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            android.system.Os.setenv("HORIZON_XR_SCALE", EYE_SCALE, true);
        } catch (Exception e) {
            Log.e("QuestCraftHorizon", "Could not set the eyes' size", e);
        }
        super.onCreate(savedInstanceState);
    }
}
