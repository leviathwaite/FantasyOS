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
import org.luaj.vm2.LuaValue;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * EditorScreen - ensures that when a project is opened, the Code module has
 * the project's main.lua loaded as the first/active tab in the code editor.
 */
public class EditorScreen extends ScreenAdapter implements EditorSession {
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

        // Create modules: bind project first, then compile the scripts (do not execute yet).
        createModule("Code",   new Color(0.2f, 0.2f, 0.4f, 1), "system/tools/code.lua");
        createModule("Sprite", new Color(0.2f, 0.4f, 0.2f, 1), "system/tools/sprite.lua");
        createModule("SFX",    new Color(0.4f, 0.4f, 0.2f, 1), "system/tools/sfx.lua");
        createModule("Music",  new Color(0.4f, 0.2f, 0.2f, 1), "system/tools/music.lua");
        createModule("Map",    new Color(0.4f, 0.2f, 0.4f, 1), "system/tools/map.lua");
        createModule("Input",  new Color(0.2f, 0.4f, 0.4f, 1), "system/tools/input.lua");
        createModule("Config", new Color(0.3f, 0.3f, 0.3f, 1), "system/tools/config.lua");

        // Activate Code module UI (this will call onFocus and initialize the tool)
        switchModule("Code");

        // After the tool is initialized, provide the project's main.lua content to the editor.
        try {
            FileHandle main = project.getDir() != null ? project.getDir().child("main.lua") : null;
            if (main != null && main.exists()) {
                ToolModule tm = modules.get("Code");
                if (tm != null && tm instanceof LuaTool) {
                    LuaTool codeTool = (LuaTool) tm;
                    String mainContent = null;
                    try { mainContent = main.readString("UTF-8"); } catch (Exception ignored) {}
                    boolean opened = false;
                    try {
                        LuaValue res = codeTool.callFunction("editor", "open", "main.lua");
                        if (res != null && !res.isnil() && res.toboolean()) { opened = true; }
                    } catch (Exception ignored) {}
                    if (!opened) {
                        try {
                            if (mainContent != null) {
                                codeTool.callFunction("editor", "open_tab", "main.lua", mainContent);
                            } else {
                                codeTool.callFunction(null, "load_file", project.getDir().child("main.lua").path());
                            }
                        } catch (Exception e) {
                            Gdx.app.error("EditorScreen", "Failed to populate Code editor with main.lua content: " + e.getMessage(), e);
                        }
                    }
                }
            } else {
                Gdx.app.log("EditorScreen", "No main.lua in project dir to open (path: " + (project.getDir() != null ? project.getDir().path() : "null") + ")");
            }
        } catch (Exception e) {
            Gdx.app.error("EditorScreen", "Failed to open main.lua in Code module: " + e.getMessage(), e);
        }

        // Register this editor session so the session manager can query/save it.
        EditorSessionManager.get().register(this);
    }

    private void createModule(String name, Color color, String scriptPath) {
        LuaTool tool = new LuaTool(name, scriptPath);

        // Bind the project before compiling the script so the script has access to project.* on init.
        tool.loadProject(project);

        // Compile the script (do not execute yet).
        tool.load();

        // Wire system callback so Lua tool can request "run" or other system actions
        tool.setSystemCallback(new com.nerddaygames.engine.ScriptEngine.SystemCallback() {
            @Override
            public void onSystemCall(String command) {
                if ("run".equals(command)) {
                    Gdx.app.postRunnable(new Runnable() {
                        @Override
                        public void run() {
                            game.setScreen(new RunScreen(game, project.getDir()));
                        }
                    });
                }
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

            // NEW: If the current module is a LuaTool, tell it where its editor drawing area lives
            // (so mouse() coordinates are reported relative to the editor content area).
            if (currentModule instanceof LuaTool) {
                LuaTool lt = (LuaTool) currentModule;
                int areaX = 0;
                int areaY = TOOLBAR_HEIGHT; // bottom of editor area (toolbar is at top)
                int areaW = Gdx.graphics.getWidth();
                int areaH = Gdx.graphics.getHeight() - TOOLBAR_HEIGHT;
                lt.setInputBounds(areaX, areaY, areaW, areaH);
            }
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
                uiBatch.draw(t, 0, 0, Gdx.graphics.getWidth(), areaHeight, 0, 0, t.getWidth(), t.getHeight(), false, true);
            }
        }
        uiBatch.end();

        drawToolbar();
    }

    private void drawToolbar() {
        // unchanged...
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
        // unchanged...
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
            if (keycode == com.badlogic.gdx.Input.Keys.ESCAPE || keycode == com.badlogic.gdx.Input.Keys.BACK) {
                DesktopScreen ds = new DesktopScreen(game);
                ds.addMinimizedEditor(project.getDir());
                game.setScreen(ds);
                return true;
            }
            return false;
        }
    }

    @Override public boolean isDirty() { /* implement as needed */ return false; }
    @Override public boolean save() { /* implement as needed */ return false; }
    @Override public FileHandle getProjectDir() { return project != null ? project.getDir() : null; }

    @Override public void dispose() {
        try { EditorSessionManager.get().unregister(this); } catch (Exception ignored) {}
        uiBatch.dispose(); uiShapes.dispose(); uiFont.dispose();
        for(ToolModule m : modules.values()) m.dispose();
    }
}
