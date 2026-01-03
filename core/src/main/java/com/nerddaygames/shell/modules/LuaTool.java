package com.nerddaygames.shell.modules;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.nerddaygames.engine.ScriptEngine;
import com.nerddaygames.shell.Project;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * LuaTool - editor integration with comprehensive debugging
 */
public class LuaTool implements ToolModule {
    private final String name;
    private final String scriptPath;
    private Globals globals;
    private Project project;
    private ScriptEngine.SystemCallback systemCallback;
    private ToolInputProcessor inputProcessor;
    private FileHandle projectDir;

    // Rendering
    private FrameBuffer fbo;
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private BitmapFont font;
    private GlyphLayout glyph = new GlyphLayout();
    private Texture generatedTexture;
    private int fboWidth = 1280;
    private int fboHeight = 720;

    // State
    private boolean initialized = false;
    private LuaValue initChunk;
    
    // Toasts
    private static class Toast {
        String text; long expiresAtMs;
        Toast(String t, long e) { text = t; expiresAtMs = e; }
    }
    private final List<Toast> toasts = new ArrayList<>();
    
    // Input state
    private final Object keyLock = new Object();
    private final Set<Integer> keysDown = new HashSet<>();
    private final Set<Integer> keysPressed = new HashSet<>();
    private final Queue<Character> charQueue = new ArrayDeque<>();
    private final Map<Integer, Long> keyDownTime = new HashMap<>();
    private final Map<Integer, Long> lastRepeatTime = new HashMap<>();
    private float scrollDelta = 0f;
    
    // Configurable input area
    private int inputBoundsX = 0;
    private int inputBoundsTopY = 0;
    private int inputBoundsW = 0;
    private int inputBoundsH = 0;
    private int inputGutterLeft = 0;
    private int inputGutterTop = 0;
    private int caretOffsetX = 0;

    private static final long KEY_REPEAT_INITIAL_MS = 400;
    private static final long KEY_REPEAT_INTERVAL_MS = 50;

    public LuaTool(String name, String scriptPath) {
        this.name = name;
        this.scriptPath = scriptPath;
        this.inputProcessor = new ToolInputProcessor();
        
        // Initialize graphics
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        font = new BitmapFont(false); // Flip Y=false (Standard Y-Up)
        
        // Initialize Lua
        globals = JsePlatform.standardGlobals();
        
        // Setup package path to include assets folder (for require)
        try {
            LuaValue pkg = globals.get("package");
            String currentPath = pkg.get("path").tojstring();
            // Ensure we check current dir and assets subdir
            String newPath = currentPath + ";?.lua;assets/?.lua;assets/system/tools/?.lua";
            pkg.set("path", LuaValue.valueOf(newPath));
        } catch (Exception e) {
            System.err.println("Failed to setup package.path: " + e.getMessage());
        }
        
        // Bindings
        bindDrawingFunctions();
        bindClipboard();
        bindConsoleLog();
        bindInputBindings();
        bindKeyConstants();
        bindRunFunction();
        bindFontHelpers();
        bindToastFunctions();
    }
    
    @Override
    public void load() {
        if (initialized) return;
        try {
            FileHandle fh = Gdx.files.internal(scriptPath);
            if (!fh.exists()) {
                initError = "Script not found: " + scriptPath;
                System.err.println("LuaTool: " + initError);
                return;
            }
            String script = fh.readString(StandardCharsets.UTF_8.name());
            initChunk = globals.load(script, scriptPath);
            // Run the chunk to define functions (like _init, _draw, _update)
            initChunk.call();
            
            // Bind project APIs BEFORE calling _init so project.read works
            bindProjectAPIs();
            System.out.println("[LuaTool] Bound project to LuaTool for projectDir=" + (projectDir != null ? projectDir.path() : "null"));
            
            // Call _init if present
            LuaValue init = globals.get("_init");
            if (init != null && !init.isnil()) init.call();
            
            initialized = true;
            initError = null;
            System.out.println("[LuaTool] Loaded and initialized " + name);
        } catch (Exception e) {
            initialized = false;
            initError = e.getMessage(); // Capture primitive message
            // Try to get LuaError details
            if (e instanceof org.luaj.vm2.LuaError) {
                initError = "Lua Error: " + e.getMessage();
            }
            e.printStackTrace();
            System.err.println("[LuaTool] LOAD ERROR: " + initError);
        }
    }
    
    private void ensureFbo(int w, int h) {
        if (fbo == null || fbo.getWidth() != w || fbo.getHeight() != h) {
            if (fbo != null) fbo.dispose();
            try {
                fbo = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
                // Important: FBO textures are Y-up by default logic in LibGDX
                generatedTexture = fbo.getColorBufferTexture();
                generatedTexture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
                this.fboWidth = w;
                this.fboHeight = h;
            } catch (Exception e) {
                Gdx.app.error("LuaTool", "Failed to create FBO", e);
            }
        }
    }

    // Sprite cache
    private final Map<String, Texture> spriteCache = new HashMap<>();

    // Draw queue
    private static abstract class DrawCmd {}
    private static final class CmdText extends DrawCmd {
        final String text; final float x, y; final int colorIndex;
        CmdText(String t, float x, float y, int c){ this.text=t; this.x=x; this.y=y; this.colorIndex=c;}
    }
    private static final class CmdRect extends DrawCmd {
        final float x, y, w, h; final int colorIndex;
        CmdRect(float x, float y, float w, float h, int c){ this.x=x; this.y=y; this.w=w; this.h=h; this.colorIndex=c;}
    }
    private static final class CmdClear extends DrawCmd {
        final int colorIndex;
        CmdClear(int c){ this.colorIndex = c; }
    }
    private static final class CmdSprite extends DrawCmd {
        final String path; final float x, y, w, h;
        CmdSprite(String p, float x, float y, float w, float h){ this.path=p; this.x=x; this.y=y; this.w=w; this.h=h; }
    }
    private final List<DrawCmd> cmdQueue = new ArrayList<>();

    // ... Toasts and input fields ...

    private void bindDrawingFunctions() {
        globals.set("print", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                try {
                    String text = args.arg(1).tojstring();
                    double ox = args.optdouble(2, 4.0);
                    double oy = args.optdouble(3, fboHeight - 8.0);
                    int color = args.optint(4, 7);
                    synchronized (cmdQueue) { cmdQueue.add(new CmdText(text, (float)ox, (float)oy, color)); }
                } catch (Exception ignored) {}
                return LuaValue.NIL;
            }
        });

        globals.set("rect", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                try {
                    double x = args.optdouble(1, 0.0);
                    double y = args.optdouble(2, 0.0);
                    double w = args.optdouble(3, 0.0);
                    double h = args.optdouble(4, 0.0);
                    int color = args.optint(5, 7);
                    synchronized (cmdQueue) { cmdQueue.add(new CmdRect((float)x, (float)y, (float)w, (float)h, color)); }
                } catch (Exception ignored) {}
                return LuaValue.NIL;
            }
        });

        globals.set("spr", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                try {
                    String path = args.checkjstring(1);
                    double x = args.optdouble(2, 0.0);
                    double y = args.optdouble(3, 0.0);
                    double w = args.isnil(4) ? -1 : args.todouble(4); // -1 means use texture width
                    double h = args.isnil(5) ? -1 : args.todouble(5);
                    synchronized (cmdQueue) { cmdQueue.add(new CmdSprite(path, (float)x, (float)y, (float)w, (float)h)); }
                } catch (Exception ignored) {}
                return LuaValue.NIL;
            }
        });

        globals.set("cls", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                int color = arg.isnil() ? 0 : arg.toint();
                synchronized (cmdQueue) { cmdQueue.add(new CmdClear(color)); }
                return LuaValue.NIL;
            }
        });
        
        globals.set("mouse", new ZeroArgFunction() {
            @Override public LuaValue call() {
                org.luaj.vm2.LuaTable t = new org.luaj.vm2.LuaTable();
                int sx = Gdx.input.getX();
                int sy_top = Gdx.input.getY();
                int areaX = inputBoundsX;
                int areaTopY = inputBoundsTopY;
                int areaW = inputBoundsW;
                int areaH = inputBoundsH;
                int gutter = inputGutterLeft;
                int gutterTop = inputGutterTop;

                if (areaW <= 0 || areaH <= 0) {
                    t.set("x", LuaValue.valueOf(sx));
                    t.set("y", LuaValue.valueOf(sy_top));
                } else {
                    float localX = sx - areaX - gutter;
                    float localYFromTop = sy_top - areaTopY - gutterTop;
                    if (localX < 0) localX = 0;
                    if (localX > areaW) localX = areaW;
                    if (localYFromTop < 0) localYFromTop = 0;
                    if (localYFromTop > areaH) localYFromTop = areaH;
                    
                    float tx = (areaW > 0) ? (localX * ((float) fboWidth / (float) areaW)) : localX;
                    // Invert Y for Lua (Y-Up), so 0 is bottom, Height is top.
                    // localYFromTop is 0 at Top.
                    float scaledYFromTop = (areaH > 0) ? (localYFromTop * ((float) fboHeight / (float) areaH)) : localYFromTop;
                    float ty = fboHeight - scaledYFromTop;
                    
                    t.set("x", LuaValue.valueOf(Math.round(tx)));
                    t.set("y", LuaValue.valueOf(Math.round(ty)));
                }
                boolean click = Gdx.input.isButtonJustPressed(Input.Buttons.LEFT);
                boolean left = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
                boolean right = Gdx.input.isButtonPressed(Input.Buttons.RIGHT);
                t.set("click", LuaValue.valueOf(click));
                t.set("left", LuaValue.valueOf(left));
                t.set("right", LuaValue.valueOf(right));
                t.set("scroll", LuaValue.valueOf((int) scrollDelta));
                scrollDelta = 0f;
                return t;
            }
        });
    }

    private void bindClipboard() {
        globals.set("clipboard", org.luaj.vm2.LuaTable.tableOf());
        globals.get("clipboard").set("set", new OneArgFunction() {
            @Override public LuaValue call(LuaValue content) {
                try {
                    String s = content.checkjstring();
                    Gdx.app.getClipboard().setContents(s);
                    return LuaValue.TRUE;
                } catch (Exception e) { return LuaValue.FALSE; }
            }
        });
        globals.get("clipboard").set("get", new ZeroArgFunction() {
            @Override public LuaValue call() {
                try {
                    String s = Gdx.app.getClipboard().getContents();
                    return (s == null) ? LuaValue.NIL : LuaValue.valueOf(s);
                } catch (Exception e) { return LuaValue.NIL; }
            }
        });
    }

    private void bindConsoleLog() {
        // Console logging that goes directly to System.out
        globals.set("log", new OneArgFunction() {
            @Override public LuaValue call(LuaValue msg) {
                System.out.println("[Lua] " + msg.tojstring());
                return LuaValue.NIL;
            }
        });
    }

    private void bindInputBindings() {
        globals.set("char", new ZeroArgFunction() {
            @Override public LuaValue call() {
                synchronized (keyLock) {
                    Character c = charQueue.poll();
                    return (c == null) ? LuaValue.NIL : LuaValue.valueOf(c.toString());
                }
            }
        });

        globals.set("key", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                int code = arg.toint();
                try { return LuaValue.valueOf(Gdx.input.isKeyPressed(code)); } catch (Exception e) { return LuaValue.FALSE; }
            }
        });

        globals.set("keyp", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                int code = arg.toint();
                synchronized (keyLock) {
                    boolean had = keysPressed.remove(code);
                    return LuaValue.valueOf(had);
                }
            }
        });

        globals.set("btn", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                int id = arg.toint();
                switch (id) {
                    case 0: return LuaValue.valueOf(Gdx.input.isKeyPressed(Input.Keys.LEFT));
                    case 1: return LuaValue.valueOf(Gdx.input.isKeyPressed(Input.Keys.RIGHT));
                    case 2: return LuaValue.valueOf(Gdx.input.isKeyPressed(Input.Keys.UP));
                    case 3: return LuaValue.valueOf(Gdx.input.isKeyPressed(Input.Keys.DOWN));
                    case 4: return LuaValue.valueOf(Gdx.input.isKeyPressed(Input.Keys.Z));
                    case 5: return LuaValue.valueOf(Gdx.input.isKeyPressed(Input.Keys.X));
                    default: return LuaValue.valueOf(Gdx.input.isKeyPressed(id));
                }
            }
        });
        globals.set("btnp", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                int id = arg.toint();
                switch (id) {
                    case 0: return LuaValue.valueOf(Gdx.input.isKeyJustPressed(Input.Keys.LEFT));
                    case 1: return LuaValue.valueOf(Gdx.input.isKeyJustPressed(Input.Keys.RIGHT));
                    case 2: return LuaValue.valueOf(Gdx.input.isKeyJustPressed(Input.Keys.UP));
                    case 3: return LuaValue.valueOf(Gdx.input.isKeyJustPressed(Input.Keys.DOWN));
                    case 4: return LuaValue.valueOf(Gdx.input.isKeyJustPressed(Input.Keys.Z));
                    case 5: return LuaValue.valueOf(Gdx.input.isKeyJustPressed(Input.Keys.X));
                    default: return LuaValue.valueOf(Gdx.input.isKeyJustPressed(id));
                }
            }
        });
    }

    private void bindKeyConstants() {
        globals.set("KEY_UP", LuaValue.valueOf(Input.Keys.UP));
        globals.set("KEY_DOWN", LuaValue.valueOf(Input.Keys.DOWN));
        globals.set("KEY_LEFT", LuaValue.valueOf(Input.Keys.LEFT));
        globals.set("KEY_RIGHT", LuaValue.valueOf(Input.Keys.RIGHT));
        globals.set("KEY_ENTER", LuaValue.valueOf(Input.Keys.ENTER));
        globals.set("KEY_BACK", LuaValue.valueOf(Input.Keys.BACKSPACE));
        globals.set("KEY_TAB", LuaValue.valueOf(Input.Keys.TAB));
        globals.set("KEY_DEL", LuaValue.valueOf(Input.Keys.FORWARD_DEL));
        globals.set("KEY_SPACE", LuaValue.valueOf(Input.Keys.SPACE));
        globals.set("KEY_A", LuaValue.valueOf(Input.Keys.A));
        globals.set("KEY_S", LuaValue.valueOf(Input.Keys.S));
        globals.set("KEY_F", LuaValue.valueOf(Input.Keys.F));
        globals.set("KEY_R", LuaValue.valueOf(Input.Keys.R));
        globals.set("KEY_C", LuaValue.valueOf(Input.Keys.C));
        globals.set("KEY_V", LuaValue.valueOf(Input.Keys.V));
        globals.set("KEY_X", LuaValue.valueOf(Input.Keys.X));
        globals.set("KEY_Y", LuaValue.valueOf(Input.Keys.Y));
        globals.set("KEY_Z", LuaValue.valueOf(Input.Keys.Z));
    }

    private void bindRunFunction() {
        globals.set("run_project", new OneArgFunction() {
            @Override public LuaValue call(LuaValue pathLv) {
                try {
                    if (systemCallback != null) systemCallback.onSystemCall("run");
                    return LuaValue.TRUE;
                } catch (Exception e) { return LuaValue.FALSE; }
            }
        });
    }

    private void bindFontHelpers() {
        globals.set("text_width", new OneArgFunction() {
            @Override public LuaValue call(LuaValue s) {
                try {
                    String txt = s.checkjstring();
                    synchronized (glyph) {
                        glyph.setText(font, txt);
                        return LuaValue.valueOf(Math.round(glyph.width));
                    }
                } catch (Exception e) { return LuaValue.valueOf(0); }
            }
        });

        globals.set("text_width_sub", new TwoArgFunction() {
            @Override public LuaValue call(LuaValue s, LuaValue n) {
                try {
                    String txt = s.checkjstring();
                    int count = Math.max(0, Math.min(txt.length(), n.checkint()));
                    String sub = txt.substring(0, count);
                    synchronized (glyph) {
                        glyph.setText(font, sub);
                        return LuaValue.valueOf(Math.round(glyph.width));
                    }
                } catch (Exception e) { return LuaValue.valueOf(0); }
            }
        });

        globals.set("font_height", new ZeroArgFunction() {
            @Override public LuaValue call() {
                float h = font.getCapHeight() > 0 ? font.getCapHeight() : font.getLineHeight();
                return LuaValue.valueOf(Math.round(h));
            }
        });
        
        // Font Scaling (Legacy support)
        globals.set("set_editor_font_size", new OneArgFunction() {
            @Override public LuaValue call(LuaValue size) {
                float px = (float) size.optdouble(16.0);
                float base = 16.0f; // Approx default BitmapFont size
                float scale = Math.max(0.5f, Math.min(4.0f, px / base));
                font.getData().setScale(scale);
                
                // Return metrics
                org.luaj.vm2.LuaTable t = new org.luaj.vm2.LuaTable();
                glyph.setText(font, "M");
                t.set("font_w", LuaValue.valueOf(glyph.width));
                t.set("font_h", LuaValue.valueOf(font.getLineHeight()));
                t.set("line_h", LuaValue.valueOf(font.getLineHeight() + 4));
                return t;
            }
        });

        globals.set("editor_font_metrics", new ZeroArgFunction() {
            @Override public LuaValue call() {
                org.luaj.vm2.LuaTable t = new org.luaj.vm2.LuaTable();
                glyph.setText(font, "M");
                t.set("font_w", LuaValue.valueOf(glyph.width));
                t.set("font_h", LuaValue.valueOf(font.getLineHeight()));
                t.set("line_h", LuaValue.valueOf(font.getLineHeight() + 4));
                return t;
            }
        });
    }

    private void bindToastFunctions() {
        globals.set("toast", new OneArgFunction() {
            @Override public LuaValue call(LuaValue msg) {
                try { enqueueToast(msg.checkjstring(), 1.8f); } catch (Exception ignored) {}
                return LuaValue.NIL;
            }
        });
        globals.set("toast_with_time", new TwoArgFunction() {
            @Override public LuaValue call(LuaValue msg, LuaValue secs) {
                try { enqueueToast(msg.checkjstring(), (float) secs.optdouble(1.8)); } catch (Exception ignored) {}
                return LuaValue.NIL;
            }
        });
        
        // Import stub
        globals.set("import_file_dialog", new ZeroArgFunction() {
             @Override public LuaValue call() {
                 // Return nil for now, or implement via SystemCallback if we had a file picker
                 enqueueToast("Import not implemented", 1.0f);
                 return LuaValue.NIL;
             }
        });
    }

    private void enqueueToast(String message, float seconds) {
        long expires = System.currentTimeMillis() + (long) (seconds * 1000L);
        synchronized (toasts) { toasts.add(new Toast(message, expires)); }
        Gdx.app.log("Toast", message);
    }

    @Override
    public void loadProject(Project proj) {
        this.project = proj;
        if (proj != null) {
            this.projectDir = proj.getDir();
        }
        bindProjectAPIs();
    }
    
    private void bindProjectAPIs() {
        globals.set("project", org.luaj.vm2.LuaTable.tableOf());

        globals.get("project").set("read", new OneArgFunction() {
            @Override public LuaValue call(LuaValue pathLv) {
                try {
                    String path = pathLv.checkjstring();
                    if (projectDir == null) return LuaValue.NIL;
                    FileHandle fh = projectDir.child(path);
                    if (fh.exists() && !fh.isDirectory()) {
                        String s = fh.readString(StandardCharsets.UTF_8.name());
                        return LuaValue.valueOf(s);
                    }
                } catch (Exception e) { Gdx.app.error("LuaTool", "project.read error: " + e.getMessage(), e); }
                return LuaValue.NIL;
            }
        });

        globals.get("project").set("write", new TwoArgFunction() {
            @Override public LuaValue call(LuaValue pathLv, LuaValue contentLv) {
                try {
                    String path = pathLv.checkjstring();
                    String content = contentLv.isnil() ? "" : contentLv.checkjstring();
                    if (projectDir == null) {
                        System.err.println("[LuaTool] project.write FAILED: projectDir is null!");
                        return LuaValue.FALSE;
                    }
                    FileHandle fh = projectDir.child(path);
                    System.out.println("[LuaTool] project.write: Saving to " + fh.path());
                    FileHandle parent = fh.parent();
                    if (parent != null && !parent.exists()) parent.mkdirs();
                    fh.writeString(content, false, "UTF-8");
                    System.out.println("[LuaTool] project.write: SUCCESS - wrote " + content.length() + " chars to " + fh.path());
                    enqueueToast("Saved " + path, 1.2f);
                    return LuaValue.TRUE;
                } catch (Exception e) { 
                    System.err.println("[LuaTool] project.write ERROR: " + e.getMessage());
                    Gdx.app.error("LuaTool", "project.write error: " + e.getMessage(), e); 
                    return LuaValue.FALSE; 
                }
            }
        });

        globals.get("project").set("exists", new OneArgFunction() {
            @Override public LuaValue call(LuaValue pathLv) {
                try {
                    String path = pathLv.checkjstring();
                    if (projectDir == null) return LuaValue.FALSE;
                    FileHandle fh = projectDir.child(path);
                    return fh.exists() ? LuaValue.TRUE : LuaValue.FALSE;
                } catch (Exception e) { return LuaValue.FALSE; }
            }
        });

        globals.get("project").set("list", new OneArgFunction() {
            @Override public LuaValue call(LuaValue pathLv) {
                try {
                    String path = pathLv.isnil() ? "" : pathLv.checkjstring();
                    if (projectDir == null) return LuaValue.NIL;
                    FileHandle fh = path == null || path.length() == 0 ? projectDir : projectDir.child(path);
                    if (!fh.exists() || !fh.isDirectory()) return LuaValue.NIL;
                    FileHandle[] children = fh.list();
                    if (children == null) return LuaValue.NIL;
                    org.luaj.vm2.LuaTable t = new org.luaj.vm2.LuaTable();
                    int idx = 1;
                    for (FileHandle c : children) { t.set(idx++, LuaValue.valueOf(c.name() + (c.isDirectory() ? "/" : ""))); }
                    return t;
                } catch (Exception e) { Gdx.app.error("LuaTool", "project.list error: " + e.getMessage(), e); return LuaValue.NIL; }
            }
        });

        try {
            String p = (projectDir != null) ? projectDir.path() : "";
            globals.get("project").set("path", LuaValue.valueOf(p));
        } catch (Exception ignored) {}

        globals.set("load_file", new OneArgFunction() {
            @Override public LuaValue call(LuaValue pathLv) {
                LuaValue res = globals.get("project").get("read").call(pathLv);
                return (res == null) ? LuaValue.NIL : res;
            }
        });
        globals.set("save_file", new TwoArgFunction() {
            @Override public LuaValue call(LuaValue pathLv, LuaValue contentLv) {
                LuaValue res = globals.get("project").get("write").call(pathLv, contentLv);
                return (res == null) ? LuaValue.FALSE : res;
            }
        });

        Gdx.app.log("LuaTool", "Bound project to LuaTool for projectDir=" + (projectDir != null ? projectDir.path() : "null"));
    }

    public void setInputBounds(int x, int topY, int w, int h) {
        this.inputBoundsX = x;
        this.inputBoundsTopY = topY;
        this.inputBoundsW = w;
        this.inputBoundsH = h;
        ensureFbo(Math.max(16, fboWidth), Math.max(16, fboHeight));
    }

    public void setInputGutterLeft(int gutterPx) { this.inputGutterLeft = Math.max(0, gutterPx); }
    public int getInputGutterLeft() { return inputGutterLeft; }

    public void setInputGutterTop(int gutterPx) { this.inputGutterTop = Math.max(0, gutterPx); }
    public int getInputGutterTop() { return inputGutterTop; }

    public void setCaretXOffset(int offsetPx) {
        this.caretOffsetX = offsetPx;
        globals.set("caret_offset_x", LuaValue.valueOf(this.caretOffsetX));
    }
    public int getCaretXOffset() { return caretOffsetX; }

    @Override
    public void resize(int w, int h) {
        System.out.println("[LuaTool] resize() called: " + w + "x" + h);
        ensureFbo(Math.max(16, w), Math.max(16, h));
        try {
            LuaValue f = globals.get("onResize");
            if (f != null && !f.isnil()) f.call(LuaValue.valueOf(w), LuaValue.valueOf(h));
        } catch (Exception ignored) {}
    }

    @Override
    public void onFocus() {
        System.out.println("[LuaTool] onFocus() called, initialized=" + initialized);

        if (!initialized) {
            try {
                if (initChunk != null) {
                    System.out.println("[LuaTool] Executing initChunk...");
                    initChunk.call();
                    
                    // Call _init if defined
                    try {
                        LuaValue initFunc = globals.get("_init");
                        if (initFunc != null && !initFunc.isnil()) {
                            initFunc.call();
                            System.out.println("[LuaTool] _init() called");
                        }
                    } catch (Exception e) {
                        System.err.println("[LuaTool] Error calling _init: " + e.getMessage());
                    }

                    initialized = true;
                    Gdx.app.log("LuaTool", "Initialized tool onFocus: " + scriptPath);
                } else {
                    System.err.println("[LuaTool] ERROR: initChunk is null!");
                }
            } catch (Exception e) {
                Gdx.app.error("LuaTool", "Initialization failed in onFocus: " + e.getMessage(), e);
                System.err.println("[LuaTool] EXCEPTION in onFocus: " + e.getMessage());
                e.printStackTrace();
            }
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

    @Override public void setSystemCallback(ScriptEngine.SystemCallback cb) { this.systemCallback = cb; }
    
    public void setProjectDir(FileHandle dir) { 
        this.projectDir = dir;
        System.out.println("[LuaTool] setProjectDir: " + (dir != null ? dir.path() : "null"));
        // Only rebind if globals exists (i.e., after load() was called)
        if (globals != null) {
            bindProjectAPIs();
        }
    }
    
    @Override public InputProcessor getInputProcessor() { return inputProcessor; }
    @Override public Texture getTexture() { return generatedTexture; }

    @Override
    public void update(float delta) {
        if (!initialized) return;
        processKeyRepeats();
        try {
            LuaValue f = globals.get("_update");
            if (f != null && !f.isnil()) f.call();
        } catch (Exception e) {
            System.err.println("[LuaTool] Exception in _update(): " + e.getMessage());
        }
    }

    private void processKeyRepeats() {
        long now = System.currentTimeMillis();
        synchronized (keyLock) {
            for (Integer keycode : new ArrayList<>(keysDown)) {
                Long downAt = keyDownTime.get(keycode);
                Long last = lastRepeatTime.getOrDefault(keycode, 0L);
                if (downAt == null) continue;
                long held = now - downAt;
                long sinceLast = now - last;
                if (held >= KEY_REPEAT_INITIAL_MS && sinceLast >= KEY_REPEAT_INTERVAL_MS) {
                    if (keycode == Input.Keys.BACKSPACE || keycode == Input.Keys.FORWARD_DEL ||
                        keycode == Input.Keys.LEFT || keycode == Input.Keys.RIGHT ||
                        keycode == Input.Keys.UP || keycode == Input.Keys.DOWN) {
                        keysPressed.add(keycode);
                        lastRepeatTime.put(keycode, now);
                    } else {
                        Character mapped = mapKeycodeToChar(keycode,
                            Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT));
                        if (mapped != null) {
                            charQueue.add(mapped);
                            lastRepeatTime.put(keycode, now);
                        }
                    }
                }
            }
        }
    }

    private String initError = null;

    @Override
    public void render() {
        if (!initialized) {
            // Render error if present
            if (initError != null) {
                if (batch != null && font != null) {
                    batch.begin();
                    font.setColor(Color.RED);
                    font.draw(batch, "Lua Init Error:\n" + initError, 10, Gdx.graphics.getHeight() - 20);
                    batch.end();
                } else {
                    System.err.println("[LuaTool] render() - NOT INITIALIZED (Error: " + initError + ")");
                }
            } else {
                System.out.println("[LuaTool] render() - NOT INITIALIZED");
            }
            return;
        }

        // Call _draw() to populate command queue
        try {
            LuaValue f = globals.get("_draw");
            if (f != null && !f.isnil()) {
                f.call();
            }
        } catch (Exception e) {
            System.err.println("[LuaTool] render() - Exception in _draw(): " + e.getMessage());
            e.printStackTrace();
        }

        // Ensure FBO exists
        if (fbo == null) {
            ensureFbo(fboWidth, fboHeight);
        }

        // Render to FBO
        fbo.begin();

        // Default clear color (Black)
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(Gdx.gl.GL_COLOR_BUFFER_BIT);

        // Draw shapes
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        synchronized (cmdQueue) {
            for (DrawCmd c : cmdQueue) {
                if (c instanceof CmdRect) {
                    CmdRect r = (CmdRect) c;
                    Color col = paletteColor(r.colorIndex);
                    shapes.setColor(col);
                    shapes.rect(r.x, r.y, r.w, r.h);
                }
            }
        }
        shapes.end();

        // Draw text and sprites
        batch.begin();
        synchronized (cmdQueue) {
            for (DrawCmd c : cmdQueue) {
                if (c instanceof CmdText) {
                    CmdText t = (CmdText) c;
                    Color col = paletteColor(t.colorIndex);
                    font.setColor(col);
                    font.draw(batch, t.text, t.x, t.y);
                } else if (c instanceof CmdSprite) {
                    CmdSprite s = (CmdSprite) c;
                    Texture tex = spriteCache.get(s.path);
                    if (tex == null) {
                        try {
                            FileHandle fh = Gdx.files.internal(s.path);
                            if (fh.exists()) {
                                tex = new Texture(fh);
                                tex.setFilter(TextureFilter.Linear, TextureFilter.Linear);
                                spriteCache.put(s.path, tex);
                            }
                        } catch (Exception e) { /* Ignore load fail */ }
                    }
                    if (tex != null) {
                        float w = (s.w < 0) ? tex.getWidth() : s.w;
                        float h = (s.h < 0) ? tex.getHeight() : s.h;
                        // Draw with Y-Up logic for FBO
                        batch.draw(tex, s.x, s.y, w, h, 0, 0, 1, 1);
                    }
                }
            }
            cmdQueue.clear();
        }

        // Draw toasts
        synchronized (toasts) {
            long now = System.currentTimeMillis();
            // Position: Centered X, 1/3 up from bottom Y
            float startY = fboHeight / 3f; 
            float y = startY;
            
            List<Toast> remove = new ArrayList<>();
            for (Toast t : toasts) {
                if (t.expiresAtMs <= now) { remove.add(t); continue; }
                
                glyph.setText(font, t.text);
                float tw = glyph.width;
                float th = font.getLineHeight();
                float x = (fboWidth - tw) / 2f;
                
                // Draw background box
                batch.end();
                Gdx.gl.glEnable(Gdx.gl.GL_BLEND);
                Gdx.gl.glBlendFunc(Gdx.gl.GL_SRC_ALPHA, Gdx.gl.GL_ONE_MINUS_SRC_ALPHA);
                shapes.begin(ShapeRenderer.ShapeType.Filled);
                shapes.setColor(0f, 0f, 0f, 0.7f);
                float padding = 8f;
                shapes.rect(x - padding, y - th - padding, tw + padding*2, th + padding*2);
                shapes.end();
                Gdx.gl.glDisable(Gdx.gl.GL_BLEND); // Batch begin might reset this but good practice
                batch.begin();
                
                font.setColor(Color.WHITE);
                font.draw(batch, t.text, x, y);
                y -= (th + 20f);
            }
            toasts.removeAll(remove);
        }
        batch.end();

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
            if (fbo != null) { fbo.dispose(); fbo = null; }
            if (batch != null) { batch.dispose(); batch = null; }
            if (shapes != null) { shapes.dispose(); shapes = null; }
            if (font != null) { font.dispose(); font = null; }
        } catch (Exception ignored) {}
    }

    private Color paletteColor(int idx) {
        switch (idx) {
            case 0: return new Color(0f,0f,0f,1f);
            case 1: return new Color(0.06f,0.06f,0.06f,1f);
            case 2: return new Color(0.1f,0.6f,0.1f,1f);
            case 7: return new Color(0.9f,0.9f,0.9f,1f);
            case 8: return new Color(1f,0f,0f,1f);  // RED
            case 9: return new Color(1f,0.5f,0f,1f);
            case 10: return new Color(1f,0.8f,0f,1f);
            case 11: return new Color(1f,0.6f,0.6f,1f);
            case 12: return new Color(0.2f,0.6f,1f,1f);
            case 13: return new Color(0.4f,0.4f,0.4f,1f);
            default: return new Color(0.9f,0.9f,0.9f,1f);
        }
    }

    private Character mapKeycodeToChar(int keycode, boolean shift) {
        if (keycode >= Input.Keys.A && keycode <= Input.Keys.Z) {
            char base = (char) ('a' + (keycode - Input.Keys.A));
            return shift ? Character.toUpperCase(base) : base;
        }
        if (keycode >= Input.Keys.NUM_0 && keycode <= Input.Keys.NUM_9) {
            char c = (char) ('0' + (keycode - Input.Keys.NUM_0));
            return c;
        }
        if (keycode == Input.Keys.SPACE) return ' ';
        switch (keycode) {
            case Input.Keys.COMMA: return shift ? '<' : ',';
            case Input.Keys.PERIOD: return shift ? '>' : '.';
            case Input.Keys.SLASH: return shift ? '?' : '/';
            case Input.Keys.SEMICOLON: return shift ? ':' : ';';
            case Input.Keys.APOSTROPHE: return shift ? '"' : '\'';
            case Input.Keys.MINUS: return shift ? '_' : '-';
            case Input.Keys.EQUALS: return shift ? '+' : '=';
            case Input.Keys.LEFT_BRACKET: return shift ? '{' : '[';
            case Input.Keys.RIGHT_BRACKET: return shift ? '}' : ']';
            case Input.Keys.BACKSLASH: return shift ? '|' : '\\';
            case Input.Keys.GRAVE: return shift ? '~' : '`';
            default: return null;
        }
    }

    private class ToolInputProcessor implements InputProcessor {
        @Override public boolean keyDown(int keycode) {
            synchronized (keyLock) {
                keysDown.add(keycode);
                keysPressed.add(keycode);
                long now = System.currentTimeMillis();
                keyDownTime.put(keycode, now);
                lastRepeatTime.put(keycode, now);
                if (keycode == Input.Keys.BACKSPACE) {
                    keysPressed.add(keycode);
                } else if (keycode == Input.Keys.ENTER) {
                    charQueue.add('\n');
                } else if (keycode == Input.Keys.TAB) {
                    charQueue.add('\t');
                }
            }
            return false;
        }
        @Override public boolean keyUp(int keycode) {
            synchronized (keyLock) {
                keysDown.remove(keycode);
                keyDownTime.remove(keycode);
                lastRepeatTime.remove(keycode);
            }
            return false;
        }
        @Override public boolean keyTyped(char character) {
            if (character >= 32 && character != 127) {
                synchronized (keyLock) { charQueue.add(character); }
            }
            return false;
        }
        @Override public boolean touchDown(int screenX, int screenY, int pointer, int button) { return false; }
        @Override public boolean touchUp(int screenX, int screenY, int pointer, int button) { return false; }
        @Override public boolean touchDragged(int screenX, int screenY, int pointer) { return false; }
        @Override public boolean mouseMoved(int screenX, int screenY) { return false; }
        @Override public boolean scrolled(float amountX, float amountY) { scrollDelta += amountY != 0f ? amountY : amountX; return false; }
        @Override public boolean touchCancelled(int screenX, int screenY, int pointer, int button) { return false; }
    }

    public LuaValue callFunction(String tableName, String functionName, Object... args) {
        try {
            LuaValue table = (tableName != null) ? globals.get(tableName) : globals;
            if (table == null || table.isnil()) table = globals;
            LuaValue func = table.get(functionName);
            if (func == null || func.isnil()) return LuaValue.NIL;

            LuaValue[] luArgs = new LuaValue[args.length];
            for (int i = 0; i < args.length; i++) {
                Object a = args[i];
                if (a == null) luArgs[i] = LuaValue.NIL;
                else if (a instanceof String) luArgs[i] = LuaValue.valueOf((String) a);
                else if (a instanceof Boolean) luArgs[i] = LuaValue.valueOf((Boolean) a);
                else if (a instanceof Number) luArgs[i] = LuaValue.valueOf(((Number) a).doubleValue());
                else luArgs[i] = LuaValue.valueOf(a.toString());
            }
            Varargs res = func.invoke(LuaValue.varargsOf(luArgs));
            return res.arg1();
        } catch (Exception e) {
            Gdx.app.error("LuaTool", "callFunction error: " + e.getMessage(), e);
            return LuaValue.NIL;
        }
    }

    public LuaValue callFunction(String functionName, Object... args) {
        return callFunction(null, functionName, args);
    }
}
