package com.nerddaygames.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.ResourceFinder;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

public class ScriptEngine {
    public Globals globals;
    private FantasyVM vm;
    private static final int INSTRUCTION_CHECK_INTERVAL = 1000;
    private static final long MAX_EXECUTION_TIME_MS = 200;
    private long startTime;
    private boolean enableTimeout;

    public interface SystemCallback {
        void onSystemCall(String command);
    }
    private SystemCallback systemCallback;

    public ScriptEngine(FantasyVM vm) {
        this(vm, true);
    }

    public ScriptEngine(FantasyVM vm, boolean enableTimeout) {
        this.vm = vm;
        this.enableTimeout = enableTimeout;
        initLua();
    }

    public void setSystemCallback(SystemCallback callback) {
        this.systemCallback = callback;
    }

    private void initLua() {
        globals = JsePlatform.standardGlobals();
        try { globals.load(new org.luaj.vm2.lib.DebugLib()); } catch (Exception ignored) {}

        globals.finder = new ResourceFinder() {
            @Override public InputStream findResource(String f) {
                String filename = f.endsWith(".lua") ? f : (f + ".lua");
                try {
                    if (vm.fs != null && vm.fs.exists(filename)) {
                        String content = vm.fs.read(filename);
                        if (content != null) {
                            String processed = LuaSyntaxCandy.process(content);
                            return new ByteArrayInputStream(processed.getBytes("UTF-8"));
                        }
                    }
                } catch(Exception e){ e.printStackTrace(); }
                return null;
            }
        };

        // require() override
        globals.set("dofile", new OneArgFunction() {
            @Override public LuaValue call(LuaValue filename) {
                String path = filename.checkjstring();
                if (!path.endsWith(".lua")) path += ".lua";
                try {
                    if (vm.fs.exists(path)) {
                        String content = vm.fs.read(path);
                        if (content != null) return globals.load(LuaSyntaxCandy.process(content), path).call();
                    }
                    throw new LuaError("File not found: " + path);
                } catch (Exception e) { throw new LuaError("Error in dofile: " + e.getMessage()); }
            }
        });

        if (enableTimeout) {
            LuaValue hook = new ZeroArgFunction() {
                @Override public LuaValue call() {
                    if (System.currentTimeMillis() - startTime > MAX_EXECUTION_TIME_MS) throw new LuaError("CPU LIMIT EXCEEDED");
                    return LuaValue.NONE;
                }
            };
            try { globals.get("debug").get("sethook").call(hook, LuaValue.valueOf(""), LuaValue.valueOf(INSTRUCTION_CHECK_INTERVAL)); } catch (Exception ignored) {}
        }

        globals.set("log", new OneArgFunction() {
            @Override public LuaValue call(LuaValue m) { System.out.println("[LUA] "+m.tojstring()); return LuaValue.NONE; }
        });

        // --- GRAPHICS (Direct calls to FantasyVM) ---

        globals.set("cls", new OneArgFunction() {
            @Override public LuaValue call(LuaValue c) { vm.cls(c.checkint()); return LuaValue.NONE; }
        });

        globals.set("print", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String s = args.checkjstring(1);
                int x = args.optint(2, 0);
                int y = args.optint(3, 0);
                int colorIndex = args.optint(4, 7);

                try {
                    vm.beginBatch();
                    BitmapFont font = vm.getCurrentFont();
                    if (font != null && vm.batch != null) {
                        // Set color from palette if available
                        Color c = (vm.palette != null && colorIndex >= 0 && colorIndex < vm.palette.size())
                            ? vm.palette.get(colorIndex) : Color.WHITE;
                        font.setColor(c);
                        
                        // Fix Y-coordinate for text rendering
                        // Camera uses Y=0 at bottom (setToOrtho(false)), but Lua expects Y=0 at top
                        // Transform: flip Y and add font height for proper baseline positioning
                        float fontHeight = font.getLineHeight();
                        float drawY = (vm.getCurrentTarget() == vm.getGameBuffer() ? vm.profile.gameHeight : vm.profile.height) - y - fontHeight;
                        
                        font.draw(vm.batch, s, x, drawY);
                    } else {
                        // Fallback only if no font
                        System.out.println(s);
                    }
                } catch (Exception e) {
                    System.out.println(s);
                }
                return LuaValue.NONE;
            }
        });

        globals.set("rect", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                vm.rect(args.checkint(1), args.checkint(2), args.checkint(3), args.checkint(4), args.checkint(5));
                return LuaValue.NONE;
            }
        });

        globals.set("line", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                vm.line(args.checkint(1), args.checkint(2), args.checkint(3), args.checkint(4), args.checkint(5));
                return LuaValue.NONE;
            }
        });

        globals.set("circ", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                vm.circle(args.checkint(1), args.checkint(2), args.checkint(3), args.checkint(4), args.optboolean(5, false));
                return LuaValue.NONE;
            }
        });

        globals.set("spr", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                int id = args.checkint(1);
                TextureRegion[] sheet = vm.getActiveSprites();
                if (sheet != null && id >= 0 && id < sheet.length) {
                    vm.beginBatch();
                    vm.batch.setColor(Color.WHITE);
                    vm.batch.draw(sheet[id], args.checkint(2), args.checkint(3));
                }
                return LuaValue.NONE;
            }
        });

        // --- SYSTEM / FS / INPUT ---

        LuaValue sys = LuaValue.tableOf();
        sys.set("exit", new ZeroArgFunction() { @Override public LuaValue call() { Gdx.app.exit(); return LuaValue.NONE; } });
        globals.set("sys", sys);

        globals.set("btn", new OneArgFunction() { @Override public LuaValue call(LuaValue id) { return LuaValue.valueOf(vm.input.btn(id.checkint())); } });
        globals.set("btnp", new OneArgFunction() { @Override public LuaValue call(LuaValue id) { return LuaValue.valueOf(vm.input.btnp(id.checkint())); } });
        globals.set("key", new OneArgFunction() { @Override public LuaValue call(LuaValue n) { return LuaValue.valueOf(vm.input.isKeyHeld(n.checkjstring())); } });
        globals.set("keyp", new OneArgFunction() { @Override public LuaValue call(LuaValue n) { return LuaValue.valueOf(vm.input.isKeyJustPressed(n.checkjstring())); } });

        globals.set("char", new ZeroArgFunction() {
            @Override public LuaValue call() {
                String c = vm.input.getNextChar();
                return (c != null) ? LuaValue.valueOf(c) : LuaValue.NIL;
            }
        });

        globals.set("mouse", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue t = LuaValue.tableOf();
                t.set("x", LuaValue.valueOf(vm.input.mouseX));
                t.set("y", LuaValue.valueOf(vm.input.mouseY));
                t.set("left", LuaValue.valueOf(vm.input.mouseDownLeft));
                t.set("right", LuaValue.valueOf(vm.input.mouseDownRight));
                t.set("click", LuaValue.valueOf(vm.input.isMouseJustReleased()));
                t.set("scroll", LuaValue.valueOf(vm.input.scrollAmount));
                return t;
            }
        });

        // Clipboard
        globals.set("clipboard", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                if (args.narg() > 0) { Gdx.app.getClipboard().setContents(args.checkjstring(1)); return LuaValue.NONE; }
                String content = Gdx.app.getClipboard().getContents();
                return (content != null) ? LuaValue.valueOf(content) : LuaValue.NIL;
            }
        });

        // FS
        LuaValue fs = LuaValue.tableOf();
        fs.set("list", new OneArgFunction() {
            @Override public LuaValue call(LuaValue path) {
                java.util.List<String> files = vm.fs.list(path.optjstring(""));
                LuaValue list = LuaValue.tableOf();
                for (int i = 0; i < files.size(); i++) list.set(i + 1, LuaValue.valueOf(files.get(i)));
                return list;
            }
        });
        fs.set("read", new OneArgFunction() {
            @Override public LuaValue call(LuaValue path) {
                String c = vm.fs.read(path.checkjstring());
                return (c != null) ? LuaValue.valueOf(c) : LuaValue.NIL;
            }
        });
        fs.set("write", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                boolean ok = vm.fs.write(args.checkjstring(1), args.checkjstring(2));
                return LuaValue.valueOf(ok);
            }
        });
        fs.set("exists", new OneArgFunction() {
            @Override public LuaValue call(LuaValue path) { return LuaValue.valueOf(vm.fs.exists(path.checkjstring())); }
        });
        globals.set("fs", fs);
    }

    public void executeFunction(String functionName) {
        if (globals == null) return;
        LuaValue func = globals.get(functionName);
        if (func.isfunction()) {
            startTime = System.currentTimeMillis();
            func.call();
        }
    }

    public void runScript(String script, String scriptName) {
        if (globals == null) return;
        try {
            String sugared = LuaSyntaxCandy.process(script);
            globals.load(sugared, scriptName).call();
        } catch (LuaError e) { throw e; }
        catch (Exception e) { throw new LuaError(e); }
    }

    public void dispose() {
        if (globals == null) return;
        try {
            LuaValue debug = globals.get("debug");
            if (!debug.isnil()) debug.get("sethook").call(LuaValue.NIL);
        } catch (Exception ignored) {}
        globals = null;
    }
}
