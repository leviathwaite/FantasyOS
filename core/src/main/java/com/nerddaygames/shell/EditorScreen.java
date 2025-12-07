package com.nerddaygames.shell;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
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
    private final Project project;
    private SpriteBatch uiBatch;
    private ShapeRenderer uiShapes;
    private BitmapFont uiFont;
    private Viewport uiViewport;
    private Map<String, ToolModule> modules = new LinkedHashMap<>();
    private Map<String, Rectangle> tabs = new LinkedHashMap<>();
    private Map<String, Color> tabColors = new LinkedHashMap<>();
    private ToolModule currentModule;
    private EditorInput editorInput;
    private final int TAB_SIZE = 48;
    private final int TOOLBAR_HEIGHT = 56;

    public EditorScreen(Main game, FileHandle projectDir) {
        this.game = game;
        this.project = new Project(projectDir);
        uiBatch = new SpriteBatch();
        uiShapes = new ShapeRenderer();
        uiFont = new BitmapFont();
        uiViewport = new ScreenViewport();
        editorInput = new EditorInput();

        createModule("Code",   new Color(0.2f, 0.2f, 0.4f, 1), "system/tools/code.lua");
        createModule("Sprite", new Color(0.2f, 0.4f, 0.2f, 1), "system/tools/sprite.lua");
        createModule("SFX",    new Color(0.4f, 0.4f, 0.2f, 1), "system/tools/sfx.lua");
        createModule("Music",  new Color(0.4f, 0.2f, 0.2f, 1), "system/tools/music.lua");
        createModule("Map",    new Color(0.4f, 0.2f, 0.4f, 1), "system/tools/map.lua");
        createModule("Input",  new Color(0.2f, 0.4f, 0.4f, 1), "system/tools/input.lua");
        createModule("Config", new Color(0.3f, 0.3f, 0.3f, 1), "system/tools/config.lua");

        switchModule("Code");
    }

    private void createModule(String name, Color color, String scriptPath) {
        LuaTool tool = new LuaTool(name, scriptPath);
        tool.loadProject(project);

        // Pass the "Run" capability to the tool
        tool.setSystemCallback((command) -> {
            if ("run".equals(command)) {
                Gdx.app.postRunnable(() -> game.setScreen(new RunScreen(game, project.getDir())));
            }
        });

        modules.put(name, tool);
        int index = tabs.size();
        tabs.put(name, new Rectangle(10 + (index * (TAB_SIZE + 5)), 0, TAB_SIZE, TAB_SIZE));
        tabColors.put(name, color);
    }

    private void switchModule(String name) {
        if (currentModule != null) currentModule.onBlur();
        currentModule = modules.get(name);
        if (currentModule != null) {
            currentModule.onFocus();
            currentModule.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight() - TOOLBAR_HEIGHT);
            InputMultiplexer plexer = new InputMultiplexer();
            plexer.addProcessor(editorInput);
            if (currentModule.getInputProcessor() != null) plexer.addProcessor(currentModule.getInputProcessor());
            Gdx.input.setInputProcessor(plexer);
        }
    }

    @Override
    public void render(float delta) {
        if (currentModule != null) {
            currentModule.update(delta);
            currentModule.render();
        }
        Gdx.gl.glClearColor(0.05f, 0.05f, 0.05f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        uiViewport.apply();
        uiBatch.setProjectionMatrix(uiViewport.getCamera().combined);
        uiShapes.setProjectionMatrix(uiViewport.getCamera().combined);

        uiBatch.begin();
        if (currentModule instanceof LuaTool) {
            Texture t = ((LuaTool) currentModule).getTexture();
            float areaHeight = Gdx.graphics.getHeight() - TOOLBAR_HEIGHT;
            if (t != null) {
                // FIXED: Set FlipY to TRUE (false, true)
                // This corrects the upside-down FrameBuffer issue
                uiBatch.draw(t, 0, 0, Gdx.graphics.getWidth(), areaHeight, 0, 0, t.getWidth(), t.getHeight(), false, true);
            }
        }
        uiBatch.end();

        drawToolbar();
    }

    private void drawToolbar() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        uiShapes.begin(ShapeRenderer.ShapeType.Filled);
        float toolbarY = Gdx.graphics.getHeight() - TOOLBAR_HEIGHT;
        uiShapes.setColor(0.15f, 0.15f, 0.15f, 1);
        uiShapes.rect(0, toolbarY, Gdx.graphics.getWidth(), TOOLBAR_HEIGHT);

        for (String name : modules.keySet()) {
            Rectangle r = tabs.get(name);
            r.y = Gdx.graphics.getHeight() - TAB_SIZE - 4;
            boolean active = (currentModule != null && currentModule.getName().equals(name));
            if (active) {
                uiShapes.setColor(1, 1, 1, 0.2f);
                uiShapes.rect(r.x - 2, r.y - 2, r.width + 4, r.height + 4);
            }
            uiShapes.setColor(tabColors.get(name));
            uiShapes.rect(r.x, r.y, r.width, r.height);
            if (active) {
                uiShapes.setColor(Color.WHITE);
                uiShapes.rect(r.x, r.y, r.width, 2);
            }
        }
        uiShapes.end();

        uiBatch.begin();
        for (String name : modules.keySet()) {
            Rectangle r = tabs.get(name);
            uiFont.setColor(Color.WHITE);
            uiFont.draw(uiBatch, name.substring(0,1), r.x + 18, r.y + 32);
        }
        uiBatch.end();
    }

    @Override public void resize(int width, int height) {
        uiViewport.update(width, height, true);
        if (currentModule != null) currentModule.resize(width, height - TOOLBAR_HEIGHT);
    }

    private class EditorInput extends InputAdapter {
        @Override public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            float y = Gdx.graphics.getHeight() - screenY;
            for (Map.Entry<String, Rectangle> entry : tabs.entrySet()) {
                if (entry.getValue().contains(screenX, y)) {
                    switchModule(entry.getKey());
                    return true;
                }
            }
            return false;
        }
        @Override public boolean keyDown(int keycode) {
            if (keycode == com.badlogic.gdx.Input.Keys.ESCAPE) {
                game.setScreen(new DesktopScreen(game));
                return true;
            }
            return false;
        }
    }
    @Override public void dispose() {
        uiBatch.dispose(); uiShapes.dispose(); uiFont.dispose();
        for(ToolModule m : modules.values()) m.dispose();
    }
}
