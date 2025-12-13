package com.nerddaygames.engine.graphics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ZeroArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.Varargs;

public class FantasyOS {
    // Singleton References (Set these from your Main/DesktopScreen)
    public static ShapeRenderer shape;
    public static SpriteBatch batch;
    public static BitmapFont font;

    // Palette (Standard Fantasy 16)
    public static final Color[] PALETTE = {
        Color.BLACK, new Color(0x1d2b53ff), new Color(0x7e2553ff), new Color(0x008751ff),
        new Color(0xab5236ff), new Color(0x5f574fff), new Color(0xc2c3c7ff), new Color(0xfff1e8ff),
        new Color(0xff004dff), new Color(0xffa300ff), new Color(0xffec27ff), new Color(0x00e436ff),
        new Color(0x29adffff), new Color(0x83769cff), new Color(0xff77a8ff), new Color(0xffccaaff)
    };

    // Register API into Lua Globals
    public static void registerAPI(LuaValue globals) {

        // rect(x, y, w, h, colorIndex)
        globals.set("rect", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                if(shape == null) return LuaValue.NIL;
                float x = (float)args.checkdouble(1);
                float y = (float)args.checkdouble(2);
                float w = (float)args.checkdouble(3);
                float h = (float)args.checkdouble(4);
                int c = args.checkint(5);

                // Safety check: ensure batch is paused (since we are drawing shapes)
                if(batch.isDrawing()) batch.end();
                if(!shape.isDrawing()) shape.begin(ShapeRenderer.ShapeType.Filled);

                shape.setColor(PALETTE[Math.max(0, Math.min(15, c))]);
                shape.rect(x, y, w, h);
                return LuaValue.NIL;
            }
        });

        // print(text, x, y, colorIndex)
        globals.set("print", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                if(batch == null) return LuaValue.NIL;
                String text = args.checkjstring(1);
                float x = (float)args.checkdouble(2);
                float y = (float)args.checkdouble(3);
                int c = args.checkint(4);

                // Switch to SpriteBatch
                if(shape.isDrawing()) shape.end();
                if(!batch.isDrawing()) batch.begin();

                font.setColor(PALETTE[Math.max(0, Math.min(15, c))]);
                font.draw(batch, text, x, y + 12); // Adjust baseline
                return LuaValue.NIL;
            }
        });

        // mouse() -> {x, y, click}
        globals.set("mouse", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                LuaTable t = new LuaTable();
                t.set("x", Gdx.input.getX());
                t.set("y", Gdx.graphics.getHeight() - Gdx.input.getY()); // Y-Up
                t.set("click", LuaValue.valueOf(Gdx.input.isButtonJustPressed(0)));
                t.set("left", LuaValue.valueOf(Gdx.input.isButtonPressed(0)));
                t.set("scroll", LuaValue.valueOf(0)); // TODO: InputProcessor for scroll
                return t;
            }
        });

        // key(keycode)
        globals.set("key", new org.luaj.vm2.lib.OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                // Map LibGDX keys (simplified)
                String keyName = arg.checkjstring().toLowerCase();
                // Real implementation needs map of "ctrl" -> Input.Keys.CONTROL_LEFT
                return LuaValue.FALSE;
            }
        });

        // char() -> returns typed character
        globals.set("char", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                // Requires implementing InputProcessor.keyTyped to buffer chars
                return LuaValue.NIL;
            }
        });
    }
}
