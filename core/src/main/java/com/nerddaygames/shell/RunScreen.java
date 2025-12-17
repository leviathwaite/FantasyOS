package com.nerddaygames.shell;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.nerddaygames.Main;
import com.nerddaygames.engine.FantasyVM;
import com.nerddaygames.engine.Profile;

public class RunScreen extends ScreenAdapter {
    private Main game;
    private FantasyVM vm;
    private SpriteBatch batch;
    private FitViewport viewport;
    private FileHandle projectDir;

    public RunScreen(Main game, FileHandle projectDir) {
        this.game = game;
        this.projectDir = projectDir;
        this.batch = new SpriteBatch();

        Profile p = Profile.createNerdOS();

        // Game has CPU timeout enabled (flag = 1)
        this.vm = new FantasyVM(p, 1);

        // Set project directory on VM so editor run/save use the project folder
        if (projectDir != null) {
            vm.setProjectDir(projectDir);
        }

        // FitViewport maintains aspect ratio (retro feel)
        this.viewport = new FitViewport(p.gameWidth, p.gameHeight);
        vm.setViewport(viewport);

        FileHandle mainLua = projectDir.child("main.lua");
        if (mainLua.exists()) {
            try {
                vm.scriptEngine.runScript(mainLua.readString(), mainLua.path());
                if (vm.scriptEngine.globals.get("_init").isfunction()) {
                    vm.scriptEngine.globals.get("_init").call();
                }
            } catch (Exception e) { System.err.println("Runtime Error: " + e.getMessage()); }
        }
    }

    @Override
    public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            game.setScreen(new EditorScreen(game, projectDir));
            return;
        }
        vm.update(delta);
        vm.render();

        viewport.apply();
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        Texture t = vm.getScreenTexture();
        batch.draw(t, 0, 0, viewport.getWorldWidth(), viewport.getWorldHeight(), 0, 0, t.getWidth(), t.getHeight(), false, true);
        batch.end();
    }

    @Override public void resize(int w, int h) { viewport.update(w, h, true); }
    @Override public void dispose() { vm.dispose(); batch.dispose(); }
}
