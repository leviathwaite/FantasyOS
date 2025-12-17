package com.nerddaygames.engine.graphics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera; // <--- Import Camera
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;
import com.badlogic.gdx.utils.Array;

import org.luaj.vm2.LuaValue;

public class WindowManager {
    private final Array<AppWindow> windows = new Array<>();

    private AppWindow dragWindow;
    private AppWindow resizeWindow;
    private float dragOffsetX, dragOffsetY;

    private static final int HEADER_H = 24;
    private static final int RESIZE_HANDLE_SIZE = 15;
    private static final int MIN_W = 100; // 100
    private static final int MIN_H = 80; // 80

    public WindowManager() {
    }

    public void openWindow(String title, LuaValue luaTable, float x, float y, float w, float h) {
        AppWindow win = new AppWindow(title, x, y, w, h);

        win.luaTable = luaTable;

        if (luaTable != null && !luaTable.isnil()) {
            win.updateFunc = luaTable.get("_update");
            win.drawFunc = luaTable.get("_draw");
        }

        windows.add(win);
        focus(win);
    }

    public void update(float delta) {
        float mx = Gdx.input.getX();
        float my = Gdx.graphics.getHeight() - Gdx.input.getY();

        boolean click = Gdx.input.isButtonJustPressed(0);
        boolean down = Gdx.input.isButtonPressed(0);

        // 1. Drag & Resize Logic
        if (down) {
            if (dragWindow != null) {
                dragWindow.bounds.x = mx - dragOffsetX;
                dragWindow.bounds.y = my - dragOffsetY;
                return;
            }
            if (resizeWindow != null) {
                float newW = mx - resizeWindow.bounds.x;
                resizeWindow.bounds.width = Math.max(MIN_W, newW);

                float currentTop = resizeWindow.bounds.y + resizeWindow.bounds.height;
                float newH = currentTop - my;

                if(newH >= MIN_H) {
                    resizeWindow.bounds.height = newH;
                    resizeWindow.bounds.y = my;
                }
                return;
            }
        } else {
            dragWindow = null;
            resizeWindow = null;
        }

        // 2. Hit Testing
        boolean inputConsumed = false;

        for (int i = windows.size - 1; i >= 0; i--) {
            AppWindow win = windows.get(i);
            Rectangle b = win.bounds;

            boolean inHeader = (mx >= b.x && mx <= b.x + b.width &&
                my >= b.y + b.height - HEADER_H && my <= b.y + b.height);

            boolean inResize = (mx >= b.x + b.width - RESIZE_HANDLE_SIZE && mx <= b.x + b.width &&
                my >= b.y && my <= b.y + RESIZE_HANDLE_SIZE);

            boolean inWin = b.contains(mx, my);

            if (click && !inputConsumed) {
                if (inResize) {
                    resizeWindow = win;
                    focus(win);
                    inputConsumed = true;
                } else if (inHeader) {
                    dragWindow = win;
                    dragOffsetX = mx - b.x;
                    dragOffsetY = my - b.y;
                    focus(win);
                    inputConsumed = true;
                } else if (inWin) {
                    focus(win);
                    inputConsumed = true;
                }
            }

            // 3. Update Lua
            if (i == windows.size - 1 && !inputConsumed) {
                if (win.updateFunc != null && !win.updateFunc.isnil()) {
                    win.updateFunc.invoke(LuaValue.varargsOf(new LuaValue[]{
                        LuaValue.valueOf(b.x),
                        LuaValue.valueOf(b.y),
                        LuaValue.valueOf(b.width),
                        LuaValue.valueOf(b.height - HEADER_H)
                    }));
                }
            }

            if (inputConsumed) break;
        }
    }

    // UPDATED: Now requires 'Camera' argument
    public void draw(ShapeRenderer shape, SpriteBatch batch, Camera camera) {
        for (AppWindow win : windows) {
            Rectangle b = win.bounds;
            boolean active = (win == windows.get(windows.size - 1));

            if(batch.isDrawing()) batch.end();
            if(!shape.isDrawing()) shape.begin(ShapeRenderer.ShapeType.Filled);

            // Shadow
            shape.setColor(0, 0, 0, 0.5f);
            shape.rect(b.x + 4, b.y - 4, b.width, b.height);

            // Frame
            shape.setColor(0.7f, 0.7f, 0.7f, 1f);
            shape.rect(b.x, b.y, b.width, b.height);

            // Header
            shape.setColor(active ? new Color(0x29adffff) : Color.DARK_GRAY);
            shape.rect(b.x, b.y + b.height - HEADER_H, b.width, HEADER_H);

            // Canvas
            shape.setColor(Color.BLACK);
            shape.rect(b.x, b.y, b.width, b.height - HEADER_H);

            shape.end();

            // Title
            batch.begin();
            if(FantasyOS.font != null) {
                FantasyOS.font.setColor(Color.WHITE);
                FantasyOS.font.draw(batch, win.title, b.x + 5, b.y + b.height - 6);
            }
            batch.end();

            // Lua Draw with Clipping
            if (win.drawFunc != null && !win.drawFunc.isnil()) {
                Rectangle scissors = new Rectangle();
                Rectangle clipBounds = new Rectangle(b.x, b.y, b.width, b.height - HEADER_H);

                // FIXED: Using passed 'camera' object
                ScissorStack.calculateScissors(camera, batch.getTransformMatrix(), clipBounds, scissors);

                if (ScissorStack.pushScissors(scissors)) {
                    win.drawFunc.invoke(LuaValue.varargsOf(new LuaValue[]{
                        LuaValue.valueOf(b.x),
                        LuaValue.valueOf(b.y),
                        LuaValue.valueOf(b.width),
                        LuaValue.valueOf(b.height - HEADER_H)
                    }));

                    if(batch.isDrawing()) batch.end();
                    if(shape.isDrawing()) shape.end();

                    ScissorStack.popScissors();
                }
            }

            // Resize Handle
            shape.begin(ShapeRenderer.ShapeType.Filled);
            shape.setColor(Color.GRAY);
            shape.rect(b.x + b.width - 12, b.y + 2, 10, 2);
            shape.rect(b.x + b.width - 12, b.y + 6, 10, 2);
            shape.end();
        }
    }

    private void focus(AppWindow win) {
        windows.removeValue(win, true);
        windows.add(win);
    }
}
