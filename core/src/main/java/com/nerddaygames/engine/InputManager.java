package com.nerddaygames.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.badlogic.gdx.math.Vector2;
import java.util.LinkedList;
import java.util.Queue;

public class InputManager implements InputProcessor {
    private Viewport viewport;

    // Text Input Queue (Thread-safe)
    private final Queue<Character> typeQueue = new LinkedList<>();

    // Mouse State Cache
    public int mouseX, mouseY;
    public int scrollAmount;
    public boolean mouseDownLeft, mouseDownRight;
    private boolean prevMouseLeft, prevMouseRight;

    // --- FANTASY CONSOLE MAPPING ---
    // 0: Left, 1: Right, 2: Up, 3: Down, 4: Z, 5: X, 6: Enter
    private int[] buttonMap = {
        Input.Keys.LEFT,
        Input.Keys.RIGHT,
        Input.Keys.UP,
        Input.Keys.DOWN,
        Input.Keys.Z,     // Button A
        Input.Keys.X,     // Button B
        Input.Keys.ENTER, // Start
        Input.Keys.ESCAPE // Button 7
    };

    public InputManager() {
        Gdx.input.setInputProcessor(this);
    }

    public void setViewport(Viewport viewport) {
        this.viewport = viewport;
    }

    public void update() {
        scrollAmount = 0; // Reset scroll delta per frame

        // Track mouse button state
        prevMouseLeft = mouseDownLeft;
        prevMouseRight = mouseDownRight;
        mouseDownLeft = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        mouseDownRight = Gdx.input.isButtonPressed(Input.Buttons.RIGHT);

        // Calculate Mouse Position mapped to VM Viewport
        if (viewport != null) {
            Vector2 vec = new Vector2(Gdx.input.getX(), Gdx.input.getY());
            viewport.unproject(vec);
            mouseX = (int) vec.x;
            mouseY = (int) vec.y;
        } else {
            mouseX = Gdx.input.getX();
            mouseY = Gdx.graphics.getHeight() - Gdx.input.getY();
        }
    }

    // --- API for Lua ---

    // Hybrid Check:
    // IDs 0-7 use Fantasy Map (Z/X/Arrows)
    // IDs > 7 use Raw LibGDX Keycodes (for Editor tools)
    public boolean btn(int id) {
        if (id >= 0 && id < buttonMap.length) {
            return Gdx.input.isKeyPressed(buttonMap[id]);
        }
        return Gdx.input.isKeyPressed(id);
    }

    public boolean btnp(int id) {
        if (id >= 0 && id < buttonMap.length) {
            return Gdx.input.isKeyJustPressed(buttonMap[id]);
        }
        return Gdx.input.isKeyJustPressed(id);
    }

    // Key name helpers (for code editor tools)
    public boolean isKeyHeld(String keyName) {
        try {
            int keyCode = Input.Keys.valueOf(keyName.toUpperCase());
            return Gdx.input.isKeyPressed(keyCode);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isKeyJustPressed(String keyName) {
        try {
            int keyCode = Input.Keys.valueOf(keyName.toUpperCase());
            return Gdx.input.isKeyJustPressed(keyCode);
        } catch (Exception e) {
            return false;
        }
    }

    // Character input for text editor
    public String getNextChar() {
        Character c = popTypedChar();
        return (c != null) ? String.valueOf(c) : null;
    }

    // Remap button
    public void remap(int buttonIndex, int newKeyCode) {
        if (buttonIndex >= 0 && buttonIndex < buttonMap.length) {
            buttonMap[buttonIndex] = newKeyCode;
        }
    }

    // Mouse Helpers
    public boolean isMouseDownLeft() { return Gdx.input.isButtonPressed(Input.Buttons.LEFT); }
    public boolean isMouseDownRight() { return Gdx.input.isButtonPressed(Input.Buttons.RIGHT); }
    public boolean isMouseJustClicked() { return Gdx.input.isButtonJustPressed(Input.Buttons.LEFT); }
    public boolean isMouseJustReleased() { return prevMouseLeft && !mouseDownLeft; }

    // Modifier Helpers
    public boolean isCtrlDown() {
        return Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
    }

    public boolean isShiftDown() {
        return Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
    }

    public Character popTypedChar() {
        synchronized (typeQueue) {
            return typeQueue.poll();
        }
    }

    // --- InputProcessor Implementation ---

    @Override
    public boolean keyTyped(char character) {
        // Filter control characters to prevent "Boxes" in text
        // Ignore Backspace, Tab, Enter, Delete (Handled by Key Codes in Lua)
        if (character == '\b' || character == '\t' || character == '\r' || character == '\n' || character == 127) {
            return false;
        }
        // Only accept printable characters
        if (character >= 32) {
            synchronized (typeQueue) {
                typeQueue.add(character);
            }
        }
        return true;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        scrollAmount = (int) amountY;
        return true;
    }

    // Unused methods required by interface
    @Override public boolean keyDown(int keycode) { return false; }
    @Override public boolean keyUp(int keycode) { return false; }
    @Override public boolean touchDown(int x, int y, int p, int b) { return false; }
    @Override public boolean touchUp(int x, int y, int p, int b) { return false; }
    @Override public boolean touchCancelled(int x, int y, int p, int b) { return false; } // LibGDX 1.13+ compat
    @Override public boolean touchDragged(int x, int y, int p) { return false; }
    @Override public boolean mouseMoved(int x, int y) { return false; }
}
