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

    // Rendering
    private FrameBuffer fbo;
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private BitmapFont font;
    private GlyphLayout glyph = new GlyphLayout();
    private Texture generatedTexture;
    private int fboWidth = 1280;
    private int fboHeight = 720;

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
    private final List<DrawCmd> cmdQueue = new ArrayList<>();

    // Toasts
    private static final class Toast {
        final String text;
        final long expiresAtMs;
        Toast(String t, long e){ text=t; expiresAtMs=e; }
    }
    private final List<Toast> toasts = new ArrayList<>();

    // Compiled chunk (lazy init)
    private LuaFunction initChunk = null;
    private boolean initialized = false;

    // Input state
    private final Object keyLock = new Object();
    private final Set<Integer> keysDown = new HashSet<>();
    private final Set<Integer> keysPressed = new HashSet<>();
    private final Queue<Character> charQueue = new ArrayDeque<>();
    private volatile float scrollDelta = 0f;

    // Key repeat
    private final Map<Integer, Long> keyDownTime = new HashMap<>();
    private final Map<Integer, Long> lastRepeatTime = new HashMap<>();
    private long KEY_REPEAT_INITIAL_MS = 400L;
    private long KEY_REPEAT_INTERVAL_MS = 60L;

    // Input mapping bounds and gutter
    private int inputBoundsX = 0, inputBoundsTopY = 0, inputBoundsW = 0, inputBoundsH = 0;
    private int inputGutterLeft = 0;
    private int inputGutterTop = 0;
    private int caretOffsetX = 0;

    public LuaTool(String name, String scriptPath) {
        this.name = name;
        this.scriptPath = scriptPath;
        this.globals = JsePlatform.standardGlobals();
        this.batch = new SpriteBatch();
        this.shapes = new ShapeRenderer();
        this.font = new BitmapFont();
        this.inputProcessor = new ToolInputProcessor();

        System.out.println("[LuaTool] Constructor called for: " + name);
    }

    @Override public String getName() { return name; }

    @Override
    public void load() {
        System.out.println("[LuaTool] load() called for: " + name + " with script: " + scriptPath);

        try {
            FileHandle handle = Gdx.files.internal(scriptPath);
            if (handle != null && handle.exists()) {
                System.out.println("[LuaTool] Script file found: " + scriptPath);
                String script = handle.readString(StandardCharsets.UTF_8.name());
                System.out.println("[LuaTool] Script loaded, length: " + script.length() + " chars");

                LuaValue chunk = globals.load(script, scriptPath);
                initChunk = chunk.checkfunction();

                Gdx.app.log("LuaTool", "Compiled (no-exec) script: " + scriptPath);
                System.out.println("[LuaTool] Script compiled successfully");
            } else {
                Gdx.app.log("LuaTool", "Script not found: " + scriptPath);
                System.err.println("[LuaTool] ERROR: Script file not found: " + scriptPath);
            }
        } catch (Exception e) {
            Gdx.app.error("LuaTool", "Failed to compile script: " + scriptPath + " : " + e.getMessage(), e);
            System.err.println("[LuaTool] EXCEPTION during load: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("[LuaTool] Binding Lua functions...");
        bindDrawingFunctions();
        bindConsoleLog();
        bindInputBindings();
        bindKeyConstants();
        bindRunFunction();
        bindFontHelpers();
        bindToastFunctions();

        globals.set("set_gutter_left", new OneArgFunction() {
            @Override public LuaValue call(LuaValue px) {
                try { setInputGutterLeft(px.checkint()); } catch (Exception ignored) {}
                return LuaValue.NIL;
            }
        });

        globals.set("caret_offset_x", LuaValue.valueOf(caretOffsetX));

        System.out.println("[LuaTool] load() complete for: " + name);
    }

    private void ensureFbo(int w, int h) {
        if (w <= 0 || h <= 0) return;
        if (fbo != null && w == fboWidth && h == fboHeight) return;
        try { if (fbo != null) fbo.dispose(); } catch (Exception ignored) {}
        fboWidth = w; fboHeight = h;
        fbo = new FrameBuffer(Pixmap.Format.RGBA8888, fboWidth, fboHeight, false);
        generatedTexture = fbo.getColorBufferTexture();
        generatedTexture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        Gdx.app.log("LuaTool", "Created FBO " + fboWidth + "x" + fboHeight + " for " + name);
    }

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
                    t.set("y", LuaValue.valueOf(Gdx.graphics.getHeight() - sy_top));
                } else {
                    float localX = sx - areaX - gutter;
                    float localYFromTop = sy_top - areaTopY - gutterTop;
                    if (localX < 0) localX = 0;
                    if (localX > areaW) localX = areaW;
                    if (localYFromTop < 0) localYFromTop = 0;
                    if (localYFromTop > areaH) localYFromTop = areaH;
                    float localYFromBottom = areaH - localYFromTop;
                    float tx = (areaW > 0) ? (localX * ((float) fboWidth / (float) areaW)) : localX;
                    float ty = (areaH > 0) ? (localYFromBottom * ((float) fboHeight / (float) areaH)) : localYFromBottom;
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
    }

    private void enqueueToast(String message, float seconds) {
        long expires = System.currentTimeMillis() + (long) (seconds * 1000L);
        synchronized (toasts) { toasts.add(new Toast(message, expires)); }
        Gdx.app.log("Toast", message);
    }

    @Override
    public void loadProject(Project proj) {
        this.project = proj;
        final FileHandle projectDir = (proj != null) ? proj.getDir() : null;

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
                    if (projectDir == null) return LuaValue.FALSE;
                    FileHandle fh = projectDir.child(path);
                    FileHandle parent = fh.parent();
                    if (parent != null && !parent.exists()) parent.mkdirs();
                    fh.writeString(content, false, "UTF-8");
                    enqueueToast("Saved " + path, 1.2f);
                    return LuaValue.TRUE;
                } catch (Exception e) { Gdx.app.error("LuaTool", "project.write error: " + e.getMessage(), e); return LuaValue.FALSE; }
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
                    initialized = true;
                    Gdx.app.log("LuaTool", "Initialized tool onFocus: " + scriptPath);
                    System.out.println("[LuaTool] initChunk executed successfully");
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

    @Override
    public void render() {
        if (!initialized) {
            System.out.println("[LuaTool] render() - NOT INITIALIZED");
            return;
        }

        // Call _draw() to populate command queue
        try {
            LuaValue f = globals.get("_draw");
            if (f != null && !f.isnil()) {
                f.call();
            } else {
                System.err.println("[LuaTool] render() - _draw is null or nil!");
            }
        } catch (Exception e) {
            System.err.println("[LuaTool] render() - Exception in _draw(): " + e.getMessage());
            e.printStackTrace();
        }

        // Ensure FBO exists
        if (fbo == null) {
            System.out.println("[LuaTool] render() - FBO is null, creating...");
            ensureFbo(fboWidth, fboHeight);
        }

        // Render to FBO
        fbo.begin();

        // DEBUGGING: Red clear color to verify FBO works
        Gdx.gl.glClearColor(1f, 0f, 0f, 1f);
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

        // Draw text
        batch.begin();
        synchronized (cmdQueue) {
            for (DrawCmd c : cmdQueue) {
                if (c instanceof CmdText) {
                    CmdText t = (CmdText) c;
                    Color col = paletteColor(t.colorIndex);
                    font.setColor(col);
                    font.draw(batch, t.text, t.x, t.y);
                }
            }
            cmdQueue.clear();
        }

        // Draw toasts
        synchronized (toasts) {
            long now = System.currentTimeMillis();
            float y = fboHeight - 20f;
            List<Toast> remove = new ArrayList<>();
            for (Toast t : toasts) {
                if (t.expiresAtMs <= now) { remove.add(t); continue; }
                font.setColor(Color.WHITE);
                font.draw(batch, t.text, 8, y);
                y -= (font.getLineHeight() + 4);
            }
            toasts.removeAll(remove);
        }
        batch.end();

        fbo.end();
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
