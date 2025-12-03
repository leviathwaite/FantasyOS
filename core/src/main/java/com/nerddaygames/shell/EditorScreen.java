package com.nerddaygames.shell;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.nerddaygames.shell.modules.LuaTool;
import com.nerddaygames.shell.modules.ToolModule;

import java.util.LinkedHashMap;
import java.util.Map;

public class EditorScreen extends InputAdapter implements Screen {

    // Core Dependencies
    private final Project project;

    // UI Rendering
    private SpriteBatch uiBatch;
    private ShapeRenderer uiShapes;
    private BitmapFont uiFont;
    private Viewport uiViewport; // For the Editor Chrome (Tabs, etc)

    // Module Management
    private Map<String, ToolModule> modules;
    private ToolModule currentModule;
    private Map<String, Rectangle> tabButtons; // Hitboxes for tabs

    // Layout Constants
    private static final int TAB_HEIGHT = 30;
    private static final int TAB_WIDTH = 80;

    public EditorScreen(FileHandle projectDir) {
        // 1. Load the Project
        this.project = new Project(projectDir);

        // 2. Setup UI Rendering
        uiBatch = new SpriteBatch();
        uiShapes = new ShapeRenderer();
        uiFont = new BitmapFont(); // Default font for Editor UI
        uiViewport = new ScreenViewport(); // 1:1 pixel mapping for UI

        // 3. Initialize Modules
        modules = new LinkedHashMap<>();
        tabButtons = new LinkedHashMap<>();

        // Add the Code Editor (pointing to the Lua script we made)
        addModule(new LuaTool("Code", "system/tools/code.lua"));

        // Placeholder modules (using same script for now to prevent crashes)
        addModule(new LuaTool("Sprite", "system/tools/code.lua"));
        addModule(new LuaTool("Map", "system/tools/code.lua"));

        // 4. Set Input Processor to handle tab clicks
        Gdx.input.setInputProcessor(this);

        // 5. Open Default Module
        switchModule("Code");
    }

    private void addModule(ToolModule m) {
        modules.put(m.getName(), m);
        m.loadProject(project);

        // Create a hitbox for the tab
        int index = tabButtons.size();
        tabButtons.put(m.getName(), new Rectangle(index * TAB_WIDTH, Gdx.graphics.getHeight() - TAB_HEIGHT, TAB_WIDTH, TAB_HEIGHT));
    }

    private void switchModule(String name) {
        if (currentModule != null) currentModule.onBlur();
        currentModule = modules.get(name);
        if (currentModule != null) currentModule.onFocus();
    }

    @Override
    public void render(float delta) {
        // --- 1. UPDATE ACTIVE TOOL ---
        if (currentModule != null) {
            currentModule.update(delta);
            currentModule.render(); // This renders to the Tool's internal FrameBuffer
        }

        // --- 2. RENDER EDITOR CHROME ---
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.1f, 1);
        // Don't clear color buffer here, or we wipe what the tool rendered!
        // We only clear if the tool isn't fullscreen.
        // Actually, normally the Tool Texture is drawn *by* the EditorScreen.
        // Let's draw the Tool Texture now.

        Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT); // Clear depth only

        uiViewport.apply();
        uiBatch.setProjectionMatrix(uiViewport.getCamera().combined);
        uiShapes.setProjectionMatrix(uiViewport.getCamera().combined);

        // Draw Tool Viewport (The "Window" of the tool)
        uiBatch.begin();
        if (currentModule != null && currentModule instanceof LuaTool) {
            Texture toolTex = ((LuaTool) currentModule).getTexture();
            // Draw tool texture filling the screen below the tab bar
            // Flip Y because FrameBuffer textures are upside down
            uiBatch.draw(toolTex,
                0, 0,
                Gdx.graphics.getWidth(), Gdx.graphics.getHeight() - TAB_HEIGHT,
                0, 0,
                toolTex.getWidth(), toolTex.getHeight(),
                false, true); // Flip Y
        }
        uiBatch.end();

        // Draw Tabs Background
        Gdx.gl.glEnable(GL20.GL_BLEND);
        uiShapes.begin(ShapeRenderer.ShapeType.Filled);

        // Top Bar Background
        uiShapes.setColor(0.2f, 0.2f, 0.2f, 1);
        uiShapes.rect(0, Gdx.graphics.getHeight() - TAB_HEIGHT, Gdx.graphics.getWidth(), TAB_HEIGHT);

        // Draw Individual Tabs
        int index = 0;
        for (String name : modules.keySet()) {
            boolean isActive = currentModule != null && currentModule.getName().equals(name);

            float x = index * TAB_WIDTH;
            float y = Gdx.graphics.getHeight() - TAB_HEIGHT;

            // Tab Color
            if (isActive) uiShapes.setColor(0.4f, 0.4f, 0.4f, 1);
            else uiShapes.setColor(0.25f, 0.25f, 0.25f, 1);

            uiShapes.rect(x, y, TAB_WIDTH - 2, TAB_HEIGHT);

            // Update hitbox (handle resize)
            tabButtons.get(name).set(x, y, TAB_WIDTH, TAB_HEIGHT);

            index++;
        }
        uiShapes.end();

        // Draw Tab Text
        uiBatch.begin();
        index = 0;
        for (String name : modules.keySet()) {
            float x = index * TAB_WIDTH + 10;
            float y = Gdx.graphics.getHeight() - 8;
            uiFont.setColor(Color.WHITE);
            uiFont.draw(uiBatch, name, x, y);
            index++;
        }
        // Draw Project Name
        uiFont.setColor(Color.YELLOW);
        uiFont.draw(uiBatch, "Project: " + project.config.name, Gdx.graphics.getWidth() - 200, Gdx.graphics.getHeight() - 8);
        uiBatch.end();
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        // Convert screen Y (top-down) to libgdx Y (bottom-up)
        float y = Gdx.graphics.getHeight() - screenY;

        // Check Tab Clicks
        for (Map.Entry<String, Rectangle> entry : tabButtons.entrySet()) {
            // Because hitboxes are stored in bottom-up coordinates
            // But Gdx.graphics.getHeight() changes on resize,
            // we rely on render() updating the hitboxes.
            if (entry.getValue().contains(screenX, y)) {
                switchModule(entry.getKey());
                return true;
            }
        }
        return false;
    }

    @Override
    public void resize(int width, int height) {
        uiViewport.update(width, height, true);

        // Notify modules of resize so their internal VMs adjust
        // We subtract TAB_HEIGHT to give them the correct working area
        for (ToolModule m : modules.values()) {
            m.resize(width, height - TAB_HEIGHT);
        }
    }

    @Override public void show() { }
    @Override public void pause() { }
    @Override public void resume() { }
    @Override public void hide() { }
    @Override public void dispose() {
        uiBatch.dispose();
        uiShapes.dispose();
        uiFont.dispose();
        for (ToolModule m : modules.values()) {
            m.dispose();
        }
    }
}
