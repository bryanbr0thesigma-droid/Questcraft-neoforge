#!/usr/bin/env python3
"""Edits to the decompiled game, OptiFine and Vivecraft sources (mcp940/src/minecraft) that QuestCraft needs, made at
build time so no OptiFine or Minecraft code is kept here. Each edit checks that its text is there, so a changed source fails
loudly instead of building something else. Usage: fixups.py <mcp940/src/minecraft>"""
import os
import sys

src = sys.argv[1]


def edit(path, old, new):
    full = os.path.join(src, path)
    text = open(full, encoding='utf-8').read()
    if old in text:
        open(full, 'w', encoding='utf-8').write(text.replace(old, new))
    elif new and new not in text:  # neither the text to change nor the result of an earlier run
        sys.exit('fixups: %s has changed, cannot apply: %r' % (path, old[:60]))


# OptiFine's GL debug output uses LWJGL 2's ARBDebugOutputCallback, which the headset's LWJGL 2 layer (on LWJGL 3)
# does not have: its debug display becomes a plain one
edit('net/optifine/GlDebugHandler.java',
     'import org.lwjgl.opengl.ARBDebugOutputCallback;\n', '')
edit('net/optifine/GlDebugHandler.java',
     'import org.lwjgl.opengl.ARBDebugOutputCallback.Handler;\n', '')
edit('net/optifine/GlDebugHandler.java',
     'public class GlDebugHandler implements Handler\n', 'public class GlDebugHandler\n')
edit('net/optifine/GlDebugHandler.java',
     '''        boolean flag = GLContext.getCapabilities().GL_ARB_debug_output;
        ContextAttribs contextattribs = (new ContextAttribs()).withDebug(true);
        Display.create((new PixelFormat()).withDepthBits(24), contextattribs);
        ARBDebugOutput.glDebugMessageCallbackARB(new ARBDebugOutputCallback(new GlDebugHandler()));
        ARBDebugOutput.glDebugMessageControlARB(4352, 4352, 4352, (IntBuffer)null, true);
        GL11.glEnable(33346);
''', '''        Display.create((new PixelFormat()).withDepthBits(24)); // QuestCraft: no GL debug output
''')
# Vivecraft's own defaults as QuestCraft's Vivecraft has them (pojlib's vivecraft-client-config.json): no desktop
# mirror (there is no window on the headset), no FSAA (a full-screen filter per eye), the HUD on the wrist
edit('org/vivecraft/settings/VRSettings.java',
     '    public boolean useFsaa = true;   // default to off\n',
     '    public boolean useFsaa = false;   // QuestCraft: off\n')
edit('org/vivecraft/settings/VRSettings.java',
     '    public int displayMirrorMode = MIRROR_ON_CROPPED;\n',
     '    public int displayMirrorMode = MIRROR_OFF; // QuestCraft: no window to mirror to\n')
edit('org/vivecraft/settings/VRSettings.java',
     '    public int vrHudLockMode = HUD_LOCK_HAND;\n',
     '    public int vrHudLockMode = HUD_LOCK_WRIST; // QuestCraft\n')
print('fixups applied')
