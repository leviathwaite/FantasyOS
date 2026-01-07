package com.nerddaygames.shell.modules;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.nerddaygames.engine.ScriptEngine;
import com.nerddaygames.engine.editor.AutoCompleteProvider;
import com.nerddaygames.engine.editor.EditorBuffer;
import com.nerddaygames.engine.editor.EditorGraphicsLib;
import com.nerddaygames.engine.editor.SyntaxTokenizer;
import com.nerddaygames.engine.editor.TooltipProvider;
import com.nerddaygames.shell.Project;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.nio.charset.StandardCharsets;

/**
 * CodeEditorTool - Advanced code editor with syntax highlighting, autocomplete, and more
 * Integrates EditorGraphicsLib, EditorBuffer, SyntaxTokenizer, AutoCompleteProvider, and TooltipProvider
 */
public class CodeEditorTool implements ToolModule {
    private final String name;
    private final String scriptPath;
    private Globals globals;
    private Project project;
    private ScriptEngine.SystemCallback systemCallback;
    private InputProcessor inputProcessor;
    private FileHandle projectDir;
    
    // Rendering
    private FrameBuffer fbo;
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private BitmapFont font;
    private Texture generatedTexture;
    private int fboWidth = 1280;
    private int fboHeight = 720;
    
    // Editor components
    private EditorGraphicsLib graphicsLib;
    private EditorBuffer editorBuffer;
    private SyntaxTokenizer syntaxTokenizer;
    private AutoCompleteProvider autoCompleteProvider;
    private TooltipProvider tooltipProvider;
    
    // State
    private boolean initialized = false;
    private LuaValue initChunk;
    private String initError = null;
    
    public CodeEditorTool(String name, String scriptPath) {
        this.name = name;
        this.scriptPath = scriptPath;
        
        // Initialize graphics
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        font = new BitmapFont(false);
        
        // Initialize Lua
        globals = JsePlatform.standardGlobals();
        
        // Setup package path
        try {
            LuaValue pkg = globals.get("package");
            String currentPath = pkg.get("path").tojstring();
            String newPath = currentPath + ";?.lua;disk/system/editor/?.lua";
            pkg.set("path", LuaValue.valueOf(newPath));
        } catch (Exception e) {
            System.err.println("Failed to setup package.path: " + e.getMessage());
        }
        
        // Initialize editor components
        editorBuffer = new EditorBuffer(globals);
        syntaxTokenizer = new SyntaxTokenizer(globals);
        autoCompleteProvider = new AutoCompleteProvider(globals);
        tooltipProvider = new TooltipProvider(globals);
        
        // Create input processor (reuse pattern from LuaTool)
        this.inputProcessor = new LuaToolInputAdapter();
    }
    
    @Override
    public void load() {
        if (initialized) return;
        
        try {
            // Register editor components
            editorBuffer.register();
            syntaxTokenizer.register();
            autoCompleteProvider.register();
            tooltipProvider.register();
            
            // Initialize graphics lib (will be done in first render with proper size)
            
            // Load script
            FileHandle fh = Gdx.files.internal(scriptPath);
            if (!fh.exists()) {
                initError = "Script not found: " + scriptPath;
                System.err.println("CodeEditorTool: " + initError);
                return;
            }
            
            String script = fh.readString(StandardCharsets.UTF_8.name());
            initChunk = globals.load(script, scriptPath);
            initChunk.call();
            
            // Bind project APIs
            bindProjectAPIs();
            
            // Call _init if present
            LuaValue init = globals.get("_init");
            if (init != null && !init.isnil()) {
                init.call();
            }
            
            initialized = true;
            initError = null;
            System.out.println("[CodeEditorTool] Loaded and initialized " + name);
        } catch (Exception e) {
            initialized = false;
            initError = e.getMessage();
            if (e instanceof org.luaj.vm2.LuaError) {
                initError = "Lua Error: " + e.getMessage();
            }
            e.printStackTrace();
            System.err.println("[CodeEditorTool] LOAD ERROR: " + initError);
        }
    }
    
    private void ensureFbo(int w, int h) {
        if (fbo == null || fbo.getWidth() != w || fbo.getHeight() != h) {
            if (fbo != null) fbo.dispose();
            try {
                fbo = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
                generatedTexture = fbo.getColorBufferTexture();
                generatedTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
                this.fboWidth = w;
                this.fboHeight = h;
                
                // Initialize graphics lib with correct dimensions
                if (graphicsLib != null) {
                    graphicsLib.cleanup();
                }
                graphicsLib = new EditorGraphicsLib(globals, batch, shapes, font, w, h);
                graphicsLib.register();
            } catch (Exception e) {
                Gdx.app.error("CodeEditorTool", "Failed to create FBO", e);
            }
        }
    }
    
    private void bindProjectAPIs() {
        globals.set("project", org.luaj.vm2.LuaTable.tableOf());
        
        globals.get("project").set("read", new org.luaj.vm2.lib.OneArgFunction() {
            @Override
            public LuaValue call(LuaValue pathLv) {
                try {
                    String path = pathLv.checkjstring();
                    if (projectDir == null) return LuaValue.NIL;
                    FileHandle fh = projectDir.child(path);
                    if (fh.exists() && !fh.isDirectory()) {
                        String s = fh.readString(StandardCharsets.UTF_8.name());
                        return LuaValue.valueOf(s);
                    }
                } catch (Exception e) {
                    Gdx.app.error("CodeEditorTool", "project.read error: " + e.getMessage(), e);
                }
                return LuaValue.NIL;
            }
        });
        
        globals.get("project").set("write", new org.luaj.vm2.lib.TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue pathLv, LuaValue contentLv) {
                try {
                    String path = pathLv.checkjstring();
                    String content = contentLv.isnil() ? "" : contentLv.checkjstring();
                    if (projectDir == null) {
                        System.err.println("[CodeEditorTool] project.write FAILED: projectDir is null!");
                        return LuaValue.FALSE;
                    }
                    FileHandle fh = projectDir.child(path);
                    FileHandle parent = fh.parent();
                    if (parent != null && !parent.exists()) parent.mkdirs();
                    fh.writeString(content, false, "UTF-8");
                    System.out.println("[CodeEditorTool] project.write: SUCCESS - wrote " + content.length() + " chars to " + fh.path());
                    return LuaValue.TRUE;
                } catch (Exception e) {
                    System.err.println("[CodeEditorTool] project.write ERROR: " + e.getMessage());
                    Gdx.app.error("CodeEditorTool", "project.write error: " + e.getMessage(), e);
                    return LuaValue.FALSE;
                }
            }
        });
        
        globals.get("project").set("exists", new org.luaj.vm2.lib.OneArgFunction() {
            @Override
            public LuaValue call(LuaValue pathLv) {
                try {
                    String path = pathLv.checkjstring();
                    if (projectDir == null) return LuaValue.FALSE;
                    FileHandle fh = projectDir.child(path);
                    return fh.exists() ? LuaValue.TRUE : LuaValue.FALSE;
                } catch (Exception e) {
                    return LuaValue.FALSE;
                }
            }
        });
        
        globals.get("project").set("list", new org.luaj.vm2.lib.OneArgFunction() {
            @Override
            public LuaValue call(LuaValue pathLv) {
                try {
                    String path = pathLv.isnil() ? "" : pathLv.checkjstring();
                    if (projectDir == null) return LuaValue.NIL;
                    FileHandle fh = path == null || path.length() == 0 ? projectDir : projectDir.child(path);
                    if (!fh.exists() || !fh.isDirectory()) return LuaValue.NIL;
                    FileHandle[] children = fh.list();
                    if (children == null) return LuaValue.NIL;
                    org.luaj.vm2.LuaTable t = new org.luaj.vm2.LuaTable();
                    int idx = 1;
                    for (FileHandle c : children) {
                        t.set(idx++, LuaValue.valueOf(c.name() + (c.isDirectory() ? "/" : "")));
                    }
                    return t;
                } catch (Exception e) {
                    Gdx.app.error("CodeEditorTool", "project.list error: " + e.getMessage(), e);
                    return LuaValue.NIL;
                }
            }
        });
        
        try {
            String p = (projectDir != null) ? projectDir.path() : "";
            globals.get("project").set("path", LuaValue.valueOf(p));
        } catch (Exception ignored) {}
    }
    
    @Override
    public void loadProject(Project proj) {
        this.project = proj;
        if (proj != null) {
            this.projectDir = proj.getDir();
        }
        bindProjectAPIs();
    }
    
    @Override
    public void resize(int w, int h) {
        System.out.println("[CodeEditorTool] resize() called: " + w + "x" + h);
        ensureFbo(Math.max(16, w), Math.max(16, h));
        try {
            LuaValue f = globals.get("onResize");
            if (f != null && !f.isnil()) f.call(LuaValue.valueOf(w), LuaValue.valueOf(h));
        } catch (Exception ignored) {}
    }
    
    @Override
    public void onFocus() {
        if (!initialized) {
            load();
        } else {
            try {
                LuaValue f = globals.get("onFocus");
                if (f != null && !f.isnil()) f.call();
            } catch (Exception ignored) {}
        }
    }
    
    @Override
    public void onBlur() {
        try {
            LuaValue f = globals.get("onBlur");
            if (f != null && !f.isnil()) f.call();
        } catch (Exception ignored) {}
    }
    
    @Override
    public void setSystemCallback(ScriptEngine.SystemCallback cb) {
        this.systemCallback = cb;
    }
    
    @Override
    public InputProcessor getInputProcessor() {
        return inputProcessor;
    }
    
    @Override
    public Texture getTexture() {
        return generatedTexture;
    }
    
    @Override
    public void update(float delta) {
        if (!initialized) return;
        try {
            LuaValue f = globals.get("_update");
            if (f != null && !f.isnil()) f.call();
        } catch (Exception e) {
            System.err.println("[CodeEditorTool] Exception in _update(): " + e.getMessage());
        }
    }
    
    @Override
    public void render() {
        if (!initialized) {
            System.out.println("[CodeEditorTool] render() - NOT INITIALIZED");
            return;
        }
        
        // Ensure FBO exists
        if (fbo == null) {
            ensureFbo(fboWidth, fboHeight);
        }
        
        // Render to FBO
        fbo.begin();
        
        // Clear to background
        Gdx.gl.glClearColor(0.12f, 0.12f, 0.12f, 1f);
        Gdx.gl.glClear(Gdx.gl.GL_COLOR_BUFFER_BIT);
        
        // Call _draw()
        try {
            LuaValue f = globals.get("_draw");
            if (f != null && !f.isnil()) {
                f.call();
            }
        } catch (Exception e) {
            System.err.println("[CodeEditorTool] render() - Exception in _draw(): " + e.getMessage());
            e.printStackTrace();
        }
        
        // Cleanup any active batches/shapes
        if (graphicsLib != null) {
            graphicsLib.cleanup();
        }
        
        fbo.end();
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public String getTitle() {
        if (globals != null) {
            try {
                LuaValue v = globals.get("_TITLE");
                if (v != null && !v.isnil()) return v.tojstring();
            } catch (Exception ignored) {}
        }
        return name;
    }
    
    @Override
    public void dispose() {
        try {
            if (fbo != null) {
                fbo.dispose();
                fbo = null;
            }
            if (batch != null) {
                batch.dispose();
                batch = null;
            }
            if (shapes != null) {
                shapes.dispose();
                shapes = null;
            }
            if (font != null) {
                font.dispose();
                font = null;
            }
        } catch (Exception ignored) {}
    }
    
    // Simple input processor adapter
    private class LuaToolInputAdapter implements InputProcessor {
        @Override
        public boolean keyDown(int keycode) {
            return false;
        }
        
        @Override
        public boolean keyUp(int keycode) {
            return false;
        }
        
        @Override
        public boolean keyTyped(char character) {
            return false;
        }
        
        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            return false;
        }
        
        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            return false;
        }
        
        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            return false;
        }
        
        @Override
        public boolean mouseMoved(int screenX, int screenY) {
            return false;
        }
        
        @Override
        public boolean scrolled(float amountX, float amountY) {
            return false;
        }
        
        @Override
        public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
            return false;
        }
    }
}
