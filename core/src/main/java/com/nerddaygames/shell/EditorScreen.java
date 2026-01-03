package com.nerddaygames.shell;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
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
    private final int TAB_BAR_HEIGHT = 40; // New tab bar under toolbar
    private final int TAB_HEIGHT = 28;
    private final int TAB_PADDING = 12;
    private final int TAB_MIN_WIDTH = 80;
    private final int TAB_GAP = 8;
    private final int TAB_ICON_SIZE = 48;
    private final int TAB_ICON_MARGIN = 10;

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

        createModule("Code", "icons/icon_code_editor.png", "system/tools/code_module/code.lua");
        switchModule("Code");

        // initial layout of tabs
        layoutTabs(Gdx.graphics.getWidth());
    }

    private void createModule(String name, String iconPath, String scriptPath) {
        LuaTool tool = new LuaTool(name, scriptPath);

        // CRITICAL: Set project directory BEFORE load() so _init can read files
        tool.setProjectDir(projectDir);

        // Now load and initialize the script
        tool.load();

        // Fix Input Offset: Toolbar + Tab bar heights
        tool.setInputBounds(0, TOOLBAR_HEIGHT + TAB_BAR_HEIGHT, Gdx.graphics.getWidth(), Gdx.graphics.getHeight() - TOOLBAR_HEIGHT - TAB_BAR_HEIGHT);
        tool.setInputGutterTop(0); // If needed

        // Handle Run Command
        tool.setSystemCallback(new com.nerddaygames.engine.ScriptEngine.SystemCallback() {
            @Override
            public void onSystemCall(String cmd) {
                if ("run".equals(cmd)) {
                    // Save all?
                    for(ToolModule m : modules.values()) m.onBlur(); // Force save if implemented

                    Gdx.app.postRunnable(() -> {
                        game.setScreen(new RunScreen(game, projectDir));
                    });
                }
            }
        });

        modules.put(name, tool);

        // Load icon texture
        Texture icon = null;
        try {
            FileHandle fh = Gdx.files.internal(iconPath);
            if (fh.exists()) {
                icon = new Texture(fh);
                icon.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            } else {
                System.err.println("Icon not found: " + iconPath);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        int index = tabs.size();
        // Reserve a placeholder rect; real widths are computed in layoutTabs()
        Rectangle rect = new Rectangle(10 + (index * (TAB_MIN_WIDTH + TAB_GAP)), TOOLBAR_HEIGHT + (TAB_BAR_HEIGHT - TAB_HEIGHT)/2f, TAB_MIN_WIDTH, TAB_HEIGHT);
        tabs.put(name, rect);

        if (icon != null) {
            moduleIcons.put(name, icon);
        }

        // Recompute tab layout to adapt to new module title
        layoutTabs(Gdx.graphics.getWidth());
    }

    // Store icons
    private Map<String, Texture> moduleIcons = new LinkedHashMap<>();

    private void switchModule(String name) {
        if (currentModule != null) currentModule.onBlur();
        currentModule = modules.get(name);
        if (currentModule != null) {
            currentModule.onFocus();
            currentModule.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight() - TOOLBAR_HEIGHT - TAB_BAR_HEIGHT);
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

    // Recompute tab rectangles based on module titles and available width
    private void layoutTabs(int totalWidth) {
        int x = 10; // start left padding
        for (Map.Entry<String, Rectangle> e : tabs.entrySet()) {
            String name = e.getKey();
            ToolModule mod = modules.get(name);
            String title = (mod != null) ? mod.getTitle() : name;
            int estWidth = Math.max(TAB_MIN_WIDTH, (int)(title.length() * 8) + TAB_PADDING * 2);
            Rectangle r = e.getValue();
            r.x = x;
            r.y = TOOLBAR_HEIGHT + (TAB_BAR_HEIGHT - TAB_HEIGHT)/2f;
            r.width = estWidth;
            r.height = TAB_HEIGHT;
            x += estWidth + TAB_GAP;
        }
        // Add space for + button (we won't store it in tabs map)
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
            // Render module FBO shifted down by toolbar + tab bar
            uiBatch.draw(currentModule.getTexture(), 0, TOOLBAR_HEIGHT + TAB_BAR_HEIGHT, Gdx.graphics.getWidth(), Gdx.graphics.getHeight() - TOOLBAR_HEIGHT - TAB_BAR_HEIGHT, 0, 1, 1, 0);
        }

        // Draw Toolbar background
        // uiShapes is better for rects but we are in Batch.
        // Let's end batch to draw shapes then batch again for icons? Or just use white px?
        uiBatch.end();

        uiShapes.setProjectionMatrix(uiViewport.getCamera().combined);
        uiShapes.begin(ShapeRenderer.ShapeType.Filled);
        // Toolbar
        uiShapes.setColor(0.15f, 0.15f, 0.15f, 1);
        uiShapes.rect(0, 0, Gdx.graphics.getWidth(), TOOLBAR_HEIGHT);
        // Tab bar under toolbar
        uiShapes.setColor(0.93f, 0.93f, 0.93f, 1f);
        uiShapes.rect(0, TOOLBAR_HEIGHT, Gdx.graphics.getWidth(), TAB_BAR_HEIGHT);

        // Draw tab highlights/backgrounds
        for(Map.Entry<String, Rectangle> e : tabs.entrySet()) {
            boolean active = currentModule == modules.get(e.getKey());
            Rectangle r = e.getValue();
            if (active) {
                uiShapes.setColor(0.98f, 0.98f, 0.98f, 1f);
            } else {
                uiShapes.setColor(0.85f, 0.85f, 0.85f, 1f);
            }
            uiShapes.rect(r.x - 2, r.y - 2, r.width + 4, r.height + 4);
        }
        uiShapes.end();

        uiBatch.begin();
        // Draw Module Icons in toolbar and Titles in tab bar
        int iconX = TAB_ICON_MARGIN;
        for(Map.Entry<String, Rectangle> e : tabs.entrySet()) {
            String modName = e.getKey();
            ToolModule mod = modules.get(modName);
            Rectangle r = e.getValue();

            // Draw Icon in top toolbar (left area)
            Texture icon = moduleIcons.get(modName);
            if (icon != null) {
                uiBatch.draw(icon, iconX, (TOOLBAR_HEIGHT - TAB_ICON_SIZE)/2f, TAB_ICON_SIZE, TAB_ICON_SIZE);
                iconX += TAB_ICON_SIZE + TAB_ICON_MARGIN;
            }

            // Draw Title inside tab rect
            if (mod != null) {
                String title = mod.getTitle();
                if (title != null && !title.isEmpty()) {
                    uiFont.setColor(activeColor(mod == currentModule));
                    float th = uiFont.getLineHeight();
                    uiFont.draw(uiBatch, title, r.x + 10, r.y + (r.height + th)/2 - 4);
                }
            }
        }

        // Draw + button after tabs
        int plusX = 10;
        for (Map.Entry<String, Rectangle> e : tabs.entrySet()) plusX = Math.max(plusX, (int)(e.getValue().x + e.getValue().width + TAB_GAP));
        uiFont.setColor(Color.DARK_GRAY);
        uiFont.draw(uiBatch, "+", plusX + 8, TOOLBAR_HEIGHT + (TAB_BAR_HEIGHT + uiFont.getLineHeight())/2 - 4);

        // Draw Save/Run Icons (Right Aligned in Toolbar)
         if (true) {
            int icon_size = 24;
            int icon_y = (TOOLBAR_HEIGHT - icon_size)/2;
            int right_margin = 10;

            // Run Button
            Texture runIcon = moduleIcons.get("Run");
            Texture saveIcon = moduleIcons.get("Save");
            // Fallback to known asset names if we loaded them
            if (moduleIcons.get("Code") != null) {
                // Use known right-side icons from assets if loaded earlier
                runIcon = Gdx.files.internal("icons/icon_play.png").exists() ? new Texture(Gdx.files.internal("icons/icon_play.png")) : runIcon;
                saveIcon = Gdx.files.internal("icons/icon_save.png").exists() ? new Texture(Gdx.files.internal("icons/icon_save.png")) : saveIcon;
            }
            if (runIcon != null) uiBatch.draw(runIcon, Gdx.graphics.getWidth() - icon_size - right_margin, icon_y, icon_size, icon_size);
            if (saveIcon != null) uiBatch.draw(saveIcon, Gdx.graphics.getWidth() - (icon_size * 2) - (right_margin * 2), icon_y, icon_size, icon_size);
        }
        uiBatch.end();
    }

    private Color activeColor(boolean active) {
        return active ? Color.BLACK : Color.DARK_GRAY;
    }

    @Override
    public void resize(int w, int h) {
        uiViewport.update(w, h, false);
        ((OrthographicCamera)uiViewport.getCamera()).setToOrtho(true, w, h);
        if (currentModule != null) {
            currentModule.resize(w, h - TOOLBAR_HEIGHT - TAB_BAR_HEIGHT);
            // Ensure inputs are updated too
            if (currentModule instanceof LuaTool) {
                ((LuaTool) currentModule).setInputBounds(0, TOOLBAR_HEIGHT + TAB_BAR_HEIGHT, w, h - TOOLBAR_HEIGHT - TAB_BAR_HEIGHT);
            }
        }
        // recompute tab layout on resize
        layoutTabs(w);
    }
}
