package com.nerddaygames.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerListener;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.controllers.ControllerMapping;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.viewport.Viewport;
import java.util.ArrayList;
import java.util.List;

public class InputManager implements ControllerListener, InputProcessor {

    public static final int BTN_LEFT=0, BTN_RIGHT=1, BTN_UP=2, BTN_DOWN=3;
    public static final int BTN_A=4, BTN_B=5, BTN_X=6, BTN_Y=7;

    // Virtual Buttons (Gamepad/Keys)
    private final boolean[] virtualBtnState = new boolean[8];
    private final boolean[] lastVirtualBtnState = new boolean[8];

    // Mouse State
    public int mouseX, mouseY;
    public boolean mouseDownLeft, mouseDownRight;
    private boolean lastMouseLeft; // To detect MouseUp

    // Event Data (Reset every frame)
    public int scrollAmount = 0;

    // Typing Buffer
    private final List<Character> charBuffer = new ArrayList<>();

    // Internal
    private final Vector2 tempVec = new Vector2();
    private Viewport viewport;
    private final IntIntMap keyMap = new IntIntMap();
    private Controller activeController;

    public InputManager() {
        resetDefaultMappings();
        Controllers.addListener(this);
        if (Controllers.getControllers().notEmpty()) {
            activeController = Controllers.getControllers().first();
        }

        // CRITICAL: Register this class to receive typing and scroll events
        Gdx.input.setInputProcessor(this);
    }

    public void setViewport(Viewport viewport) {
        this.viewport = viewport;
    }

    public void resetDefaultMappings() {
        keyMap.clear();
        keyMap.put(BTN_LEFT, Input.Keys.LEFT);
        keyMap.put(BTN_RIGHT, Input.Keys.RIGHT);
        keyMap.put(BTN_UP, Input.Keys.UP);
        keyMap.put(BTN_DOWN, Input.Keys.DOWN);
        keyMap.put(BTN_A, Input.Keys.Z);
        keyMap.put(BTN_B, Input.Keys.X);
        keyMap.put(BTN_X, Input.Keys.S);
        keyMap.put(BTN_Y, Input.Keys.A);
    }

    public void remap(int virtualBtn, int physicalKey) {
        keyMap.put(virtualBtn, physicalKey);
    }

    // CALLED ONCE PER FRAME BY FANTASYVM
    public void update() {
        // 1. Snapshot previous state
        System.arraycopy(virtualBtnState, 0, lastVirtualBtnState, 0, virtualBtnState.length);
        lastMouseLeft = mouseDownLeft;

        // Reset per-frame event data
        scrollAmount = 0;

        // 2. Poll Current Mouse State
        if (viewport != null) {
            tempVec.set(Gdx.input.getX(), Gdx.input.getY());
            viewport.unproject(tempVec);
            mouseX = (int) tempVec.x;
            mouseY = (int) tempVec.y;
        }
        mouseDownLeft = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        mouseDownRight = Gdx.input.isButtonPressed(Input.Buttons.RIGHT);

        // 3. Poll Current Buttons
        for (int i = 0; i < 8; i++) {
            boolean pressed = false;
            int key = keyMap.get(i, -1);
            if (key != -1 && Gdx.input.isKeyPressed(key)) pressed = true;
            if (!pressed && activeController != null) pressed = checkController(i);
            virtualBtnState[i] = pressed;
        }
    }

    // --- API FOR LUA ---

    public boolean btn(int id) {
        if (id < 0 || id > 7) return false;
        return virtualBtnState[id];
    }

    public boolean btnp(int id) {
        if (id < 0 || id > 7) return false;
        return virtualBtnState[id] && !lastVirtualBtnState[id];
    }

    // MOUSE LOGIC
    public boolean isMouseJustReleased() {
        // Was down last frame, is NOT down this frame
        return lastMouseLeft && !mouseDownLeft;
    }

    public boolean isKeyHeld(String keyName) {
        try { return Gdx.input.isKeyPressed(Input.Keys.valueOf(keyName.toUpperCase())); }
        catch (Exception e) { return false; }
    }

    public boolean isKeyJustPressed(String keyName) {
        try {
            int key = Input.Keys.valueOf(keyName.toUpperCase());
            if (key == Input.Keys.ESCAPE) return Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Input.Keys.BACK);
            return Gdx.input.isKeyJustPressed(key);
        } catch (Exception e) { return false; }
    }

    public String getNextChar() {
        if (charBuffer.isEmpty()) return null;
        return String.valueOf(charBuffer.remove(0));
    }

    // --- EVENTS (InputProcessor) ---

    @Override
    public boolean keyTyped(char character) {
        charBuffer.add(character);
        return true;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        scrollAmount = (int)amountY;
        return true;
    }

    // --- BOILERPLATE ---
    @Override public boolean keyDown(int k) { return false; }
    @Override public boolean keyUp(int k) { return false; }
    @Override public boolean touchDown(int x, int y, int p, int b) { return false; }
    @Override public boolean touchUp(int x, int y, int p, int b) { return false; }
    @Override public boolean touchCancelled(int x, int y, int p, int b) { return false; }
    @Override public boolean touchDragged(int x, int y, int p) { return false; }
    @Override public boolean mouseMoved(int x, int y) { return false; }

    private boolean checkController(int virtualID) {
        if (activeController == null) return false;
        ControllerMapping map = activeController.getMapping();
        switch (virtualID) {
            case BTN_LEFT:  return activeController.getAxis(map.axisLeftX) < -0.5f || activeController.getButton(map.buttonDpadLeft);
            case BTN_RIGHT: return activeController.getAxis(map.axisLeftX) > 0.5f  || activeController.getButton(map.buttonDpadRight);
            case BTN_UP:    return activeController.getAxis(map.axisLeftY) < -0.5f || activeController.getButton(map.buttonDpadUp);
            case BTN_DOWN:  return activeController.getAxis(map.axisLeftY) > 0.5f  || activeController.getButton(map.buttonDpadDown);
            case BTN_A:     return activeController.getButton(map.buttonA);
            case BTN_B:     return activeController.getButton(map.buttonB);
            case BTN_X:     return activeController.getButton(map.buttonX);
            case BTN_Y:     return activeController.getButton(map.buttonY);
        }
        return false;
    }

    @Override public void connected(Controller c) { activeController = c; }
    @Override public void disconnected(Controller c) { activeController = null; }
    @Override public boolean buttonDown(Controller c, int b) { return false; }
    @Override public boolean buttonUp(Controller c, int b) { return false; }
    @Override public boolean axisMoved(Controller c, int a, float v) { return false; }
}
