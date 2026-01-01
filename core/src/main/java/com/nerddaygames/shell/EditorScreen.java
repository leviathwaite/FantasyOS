package com.nerddaygames.shell;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.nerddaygames.Main;
import com.nerddaygames.shell.modules.LuaTool;
import com.nerddaygames.shell.modules.ToolModule;
import java.util.LinkedHashMap;
import java.util.Map;

public class EditorScreen extends ScreenAdapter {
    private final Main game;
    private final FileHandle projectDir; // Store project directory for minimizing
    private SpriteBatch uiBatch;
    private ShapeRenderer uiShapes;
    private BitmapFont uiFont;
    private Viewport uiViewport;
    private Map<String, ToolModule> modules = new LinkedHashMap<>();
    private Map<String, Rectangle> tabs = new LinkedHashMap<>();
    private ToolModule currentModule;
    private final int TOOLBAR_HEIGHT = 56;
    private final int TAB_SIZE = 48;

    public EditorScreen(Main game, FileHandle projectDir) {
        this.game = game;
        this.projectDir = projectDir; // Store for later use
        uiBatch = new SpriteBatch();
        uiShapes = new ShapeRenderer();
        uiFont = new BitmapFont(true); // Flipped for Y-Down
        uiViewport = new ScreenViewport();
        ((OrthographicCamera)uiViewport.getCamera()).setToOrtho(true);

        // Catch Android back button to prevent app exit
        Gdx.input.setCatchKey(Input.Keys.BACK, true);

        createModule("Code", new Color(0.2f, 0.2f, 0.4f, 1), "system/tools/code_module/code.lua");
        switchModule("Code");
    }

    private void createModule(String name, Color color, String scriptPath) {
        LuaTool tool = new LuaTool(name, scriptPath);
        tool.load();
        modules.put(name, tool);
        int index = tabs.size();
        tabs.put(name, new Rectangle(10 + (index * (TAB_SIZE + 5)), (TOOLBAR_HEIGHT - TAB_SIZE)/2f, TAB_SIZE, TAB_SIZE));
    }

    private void switchModule(String name) {
        if (currentModule != null) currentModule.onBlur();
        currentModule = modules.get(name);
        if (currentModule != null) {
            currentModule.onFocus();
            currentModule.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight() - TOOLBAR_HEIGHT);
            Gdx.input.setInputProcessor(new InputMultiplexer(new InputAdapter(){
                public boolean touchDown(int x, int y, int p, int b) {
                    for(Map.Entry<String, Rectangle> e : tabs.entrySet()) {
                        if(e.getValue().contains(x, y)) { switchModule(e.getKey()); return true; }
                    }
                    return false;
                }
            }, currentModule.getInputProcessor()));
        }
    }

    @Override
    public void render(float delta) {
        // Handle Escape/Back key to exit to desktop
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Input.Keys.BACK)) {
            // Save current state
            if (currentModule != null) {
                currentModule.onBlur();
            }
            // Return to desktop with minimized project
            DesktopScreen desktop = new DesktopScreen(game);
            if (projectDir != null) {
                desktop.addMinimizedEditor(projectDir);
            }
            game.setScreen(desktop);
            return;
        }
        
        Gdx.gl.glClearColor(0.05f, 0.05f, 0.05f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        if (currentModule != null) {
            currentModule.update(delta);
            currentModule.render();
        }

        uiViewport.apply();
        uiBatch.setProjectionMatrix(uiViewport.getCamera().combined);
        uiBatch.begin();
        if (currentModule != null && currentModule.getTexture() != null) {
            // flipY is FALSE because both the FBO Camera and Screen Camera are Y-Down
            uiBatch.draw(currentModule.getTexture(), 0, TOOLBAR_HEIGHT, Gdx.graphics.getWidth(), Gdx.graphics.getHeight()-TOOLBAR_HEIGHT, 0, 0, 1, 1);
        }
        uiBatch.end();

        uiShapes.setProjectionMatrix(uiViewport.getCamera().combined);
        uiShapes.begin(ShapeRenderer.ShapeType.Filled);
        uiShapes.setColor(0.15f, 0.15f, 0.15f, 1);
        uiShapes.rect(0, 0, Gdx.graphics.getWidth(), TOOLBAR_HEIGHT);
        uiShapes.end();
    }

    @Override
    public void resize(int w, int h) {
        uiViewport.update(w, h, false);
        ((OrthographicCamera)uiViewport.getCamera()).setToOrtho(true, w, h);
        if (currentModule != null) currentModule.resize(w, h - TOOLBAR_HEIGHT);
    }
}
