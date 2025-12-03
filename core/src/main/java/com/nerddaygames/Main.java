package com.nerddaygames;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.nerddaygames.engine.FantasyVM;
import com.nerddaygames.engine.Profile;

public class Main extends ApplicationAdapter {

    private FantasyVM vm;
    private SpriteBatch hostBatch;
    private Viewport hostViewport;

    @Override
    public void create() {
        vm = new FantasyVM(Profile.createNerdOS());
        hostBatch = new SpriteBatch();
        // Host Viewport matches OS Resolution (1920x1080)
        hostViewport = new FitViewport(vm.profile.width, vm.profile.height);
        vm.setViewport(hostViewport);

        // Boot Desktop
        try {
            String osScript = Gdx.files.internal("system/desktop.lua").readString();
            vm.scriptEngine.runScript(osScript);
            vm.scriptEngine.executeFunction("_init");
        } catch (Exception e) {
            vm.hasCrashed = true;
            vm.crashMessage = e.getMessage();
        }
    }

    @Override
    public void render() {
        vm.update(Gdx.graphics.getDeltaTime());
        vm.render();

        // Draw the OS Buffer to the Physical Window
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        hostViewport.apply();
        hostBatch.setProjectionMatrix(hostViewport.getCamera().combined);
        hostBatch.begin();

        // Draw OS Texture. Flip Y because it's from a FrameBuffer.
        hostBatch.draw(vm.getOsTexture(),
            0, 0,
            vm.profile.width, vm.profile.height,
            0, 0, vm.profile.width, vm.profile.height,
            false, true);

        hostBatch.end();
    }

    @Override
    public void resize(int width, int height) {
        hostViewport.update(width, height, true);
        vm.setViewport(hostViewport);
    }

    @Override
    public void dispose() {
        if (vm != null) vm.dispose();
        if (hostBatch != null) hostBatch.dispose();
    }
}
