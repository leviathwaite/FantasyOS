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
        // Catch Android back button to prevent app exit
        Gdx.input.setCatchKey(Input.Keys.BACK, true);

        // createModule("Code", "icons/icon_code_editor.png", "system/tools/code_module/code.lua"); // Example path usage
        createModule("Code", "icons/icon_code_editor.png", "system/tools/code_module/code.lua");
        switchModule("Code");
    }

    private void createModule(String name, String iconPath, String scriptPath) {
        LuaTool tool = new LuaTool(name, scriptPath);
        
        // CRITICAL: Set project directory BEFORE load() so _init can read files
        tool.setProjectDir(projectDir);
        
        // Now load and initialize the script
        tool.load();
        
        // Fix Input Offset: Toolbar is 56px tall
        tool.setInputBounds(0, TOOLBAR_HEIGHT, Gdx.graphics.getWidth(), Gdx.graphics.getHeight() - TOOLBAR_HEIGHT);
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
        Rectangle rect = new Rectangle(10 + (index * (TAB_SIZE + 10)), (TOOLBAR_HEIGHT - TAB_SIZE)/2f, TAB_SIZE, TAB_SIZE);
        tabs.put(name, rect);
        
        // Store icon in a map or add to ToolModule interface? 
        // For simplicity, let's store it in a parallel map in EditorScreen since ToolModule interface is minimal.
        // Better yet, extend a bit or use a Map<String, Texture> moduleIcons
        if (icon != null) {
            moduleIcons.put(name, icon);
        }
    }
    
    // Store icons
    private Map<String, Texture> moduleIcons = new LinkedHashMap<>();

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
            // FBO is Y-Up (bottom-left 0,0), Camera is Y-Down (top-left 0,0)
            // To render upright, we map FBO(0,1) [Top-Left] to Screen(Top-Left).
            // Standard FBO texture coords: (0,0) is Bottom-Left.
            // draw(tex, x, y, w, h, u, v, u2, v2)
            // We want Top of FBO (v=1) at Top of Screen (y).
            // We want Bottom of FBO (v=0) at Bottom of Screen (y+h).
            // So u=0, v=1, u2=1, v2=0.
            uiBatch.draw(currentModule.getTexture(), 0, TOOLBAR_HEIGHT, Gdx.graphics.getWidth(), Gdx.graphics.getHeight()-TOOLBAR_HEIGHT, 0, 1, 1, 0);
        }
        
        // Draw Toolbar background
        // uiShapes is better for rects but we are in Batch. 
        // Let's end batch to draw shapes then batch again for icons? Or just use white px?
        uiBatch.end();

        uiShapes.setProjectionMatrix(uiViewport.getCamera().combined);
        uiShapes.begin(ShapeRenderer.ShapeType.Filled);
        uiShapes.setColor(0.15f, 0.15f, 0.15f, 1);
        uiShapes.rect(0, 0, Gdx.graphics.getWidth(), TOOLBAR_HEIGHT);
        
        // Draw tab highlights
        for(Map.Entry<String, Rectangle> e : tabs.entrySet()) {
            boolean active = currentModule == modules.get(e.getKey());
            if (active) {
                uiShapes.setColor(0.3f, 0.3f, 0.5f, 1);
                uiShapes.rect(e.getValue().x - 2, e.getValue().y - 2, e.getValue().width + 4, e.getValue().height + 4);
            }
        }
        uiShapes.end();
        
        uiBatch.begin();
        // Draw Module Icons and Titles
        for(Map.Entry<String, Rectangle> e : tabs.entrySet()) {
            String modName = e.getKey();
            ToolModule mod = modules.get(modName);
            Rectangle r = e.getValue();
            
            // Draw Icon
            Texture icon = moduleIcons.get(modName);
            if (icon != null) {
                uiBatch.draw(icon, r.x, r.y, r.width, r.height);
            }
            
            // Draw Title (if active or if space permits? For now just draw it below or beside?)
            // User requested: "Add the tab for the code editor and the name of the current file should be written on it"
            // The current rect is just an icon square (48x48). We might need to make tabs wider or draw text next to it.
            // Let's draw text to the right of the icon if active.
            if (mod != null) {
                String title = mod.getTitle();
                if (title != null && !title.isEmpty()) {
                    uiFont.setColor(Color.WHITE);
                    // Center vertically in toolbar
                    float th = uiFont.getLineHeight();
                    uiFont.draw(uiBatch, title, r.x + r.width + 10, r.y + (r.height + th)/2 - 4);
                }
            }
        }
        uiBatch.end();
    }

    @Override
    public void resize(int w, int h) {
        uiViewport.update(w, h, false);
        ((OrthographicCamera)uiViewport.getCamera()).setToOrtho(true, w, h);
        if (currentModule != null) {
            currentModule.resize(w, h - TOOLBAR_HEIGHT);
            // Ensure inputs are updated too
            if (currentModule instanceof LuaTool) {
                ((LuaTool) currentModule).setInputBounds(0, TOOLBAR_HEIGHT, w, h - TOOLBAR_HEIGHT);
            }
        }
    }
}
