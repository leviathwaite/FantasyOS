package com.nerddaygames.engine.graphics;

import com.badlogic.gdx.math.Rectangle;
import org.luaj.vm2.LuaValue;

public class AppWindow {
    public String title;
    public Rectangle bounds = new Rectangle();

    // Lua Hooks
    public LuaValue luaTable;   // The 'self' or module table
    public LuaValue updateFunc; // _update(x, y, w, h)
    public LuaValue drawFunc;   // _draw(x, y, w, h)

    // State
    public boolean minimized = false;

    // Constructor
    public AppWindow(String title, float x, float y, float w, float h) {
        this.title = title;
        this.bounds.set(x, y, w, h);
    }
}
