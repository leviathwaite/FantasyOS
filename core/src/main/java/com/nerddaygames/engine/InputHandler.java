package com.nerddaygames.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;

public class InputHandler {

    // Check if a virtual button is held down
    public boolean btn(int id) {
        switch (id) {
            case 0: return Gdx.input.isKeyPressed(Input.Keys.LEFT);
            case 1: return Gdx.input.isKeyPressed(Input.Keys.RIGHT);
            case 2: return Gdx.input.isKeyPressed(Input.Keys.UP);
            case 3: return Gdx.input.isKeyPressed(Input.Keys.DOWN);
            case 4: return Gdx.input.isKeyPressed(Input.Keys.Z) || Gdx.input.isKeyPressed(Input.Keys.C);
            case 5: return Gdx.input.isKeyPressed(Input.Keys.X) || Gdx.input.isKeyPressed(Input.Keys.V);
            default: return false;
        }
    }

    // Future: Add btnp() (Button Pressed just this frame) logic here
}
