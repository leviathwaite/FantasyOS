package com.nerddaygames.engine;

import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

import java.lang.reflect.Method;

/**
 * EditorLuaBindings - exposes font metrics and helpers to Lua editor scripts.
 *  - editor_font_metrics() -> { font_w, font_h, line_h }
 *  - set_editor_font_size(px) -> returns metrics table (calls VM method reflectively if available)
 *  - editor_text_width(str) -> pixel width (int)
 */
public class EditorLuaBindings {
    private final org.luaj.vm2.Globals globals;
    private final FantasyVM vm;

    public EditorLuaBindings(org.luaj.vm2.Globals globals, FantasyVM vm) {
        this.globals = globals;
        this.vm = vm;
    }

    public void register() {
        globals.set("editor_font_metrics", new ZeroArgFunction() {
            @Override public LuaValue call() { return metricsFromFont(vm != null ? vm.osFont : null); }
        });

        globals.set("set_editor_font_size", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                int px = arg.checkint();
                // Call setEditorFontSize reflectively if it exists to avoid compile-time dependency
                try {
                    if (vm != null) {
                        try {
                            Method m = vm.getClass().getMethod("setEditorFontSize", int.class);
                            m.invoke(vm, px);
                        } catch (NoSuchMethodException nsme) {
                            // try older or alternate name if you used a different method name
                            try {
                                Method m2 = vm.getClass().getMethod("set_editor_font_size", int.class);
                                m2.invoke(vm, px);
                            } catch (NoSuchMethodException ignored) {
                                // method not found; ignore
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // ignore invocation errors and fall back
                }
                return metricsFromFont(vm != null ? vm.osFont : null);
            }
        });

        globals.set("editor_text_width", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                String s = arg.checkjstring();
                int w = measureTextWidth(s);
                return LuaValue.valueOf(w);
            }
        });
    }

    private LuaValue metricsFromFont(BitmapFont font) {
        LuaTable t = new LuaTable();
        if (font == null) {
            t.set("font_w", LuaValue.valueOf(8));
            t.set("font_h", LuaValue.valueOf(16));
            t.set("line_h", LuaValue.valueOf(18));
            return t;
        }
        try {
            GlyphLayout gl = new GlyphLayout();
            gl.setText(font, "M"); // sample glyph for width estimate
            int fw = Math.max(1, (int) Math.ceil(gl.width));
            int lh = Math.max(1, (int) Math.ceil(font.getLineHeight()));
            t.set("font_w", LuaValue.valueOf(fw));
            t.set("font_h", LuaValue.valueOf(fw)); // single-cell width value for editor (compat)
            t.set("line_h", LuaValue.valueOf(lh));
            return t;
        } catch (Exception e) {
            t.set("font_w", LuaValue.valueOf(8));
            t.set("font_h", LuaValue.valueOf(16));
            t.set("line_h", LuaValue.valueOf(18));
            return t;
        }
    }

    private int measureTextWidth(String s) {
        try {
            if (s == null || s.length() == 0) return 0;
            if (vm == null || vm.osFont == null) {
                // fallback: approximate monospace width 8px
                return s.length() * 8;
            }
            GlyphLayout gl = new GlyphLayout(vm.osFont, s);
            return Math.max(0, (int) Math.ceil(gl.width));
        } catch (Exception e) {
            return (s == null) ? 0 : s.length() * 8;
        }
    }
}
