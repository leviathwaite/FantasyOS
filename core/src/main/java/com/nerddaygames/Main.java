package com.nerddaygames;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.nerddaygames.shell.DesktopScreen;

public class Main extends Game {
    public SpriteBatch batch;
    public BitmapFont font;
    public OrthographicCamera camera; // ADD THIS

    @Override
    public void create() {
        batch = new SpriteBatch();
        font = new BitmapFont();

        // Initialize Camera for ScissorStack
        camera = new OrthographicCamera();
        camera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        this.setScreen(new DesktopScreen(this));
    }
}
