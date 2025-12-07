package com.nerddaygames.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
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
        // Load standard libraries (includes os, math, string, table, io, package, etc.)
        globals = JsePlatform.standardGlobals();
        globals.load(new org.luaj.vm2.lib.DebugLib());

        // Custom resource finder for dofile()
        globals.finder = new ResourceFinder() {
            @Override public InputStream findResource(String f) {
                // Handle both with and without .lua extension
                String filename = f.endsWith(".lua") ? f : (f + ".lua");

                // Check FileSystem (Disk -> Internal)
                if (vm.fs.exists(filename)) {
                    try {
                        String content = vm.fs.read(filename);
                        if (content != null) {
                            String processed = LuaSyntaxCandy.process(content);
                            return new ByteArrayInputStream(processed.getBytes("UTF-8"));
                        }
                    }
                    catch(Exception e){
                        System.err.println("Error loading " + filename + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                }
                return null;
            }
        };

        // Override require() to use our custom loader
        LuaValue customLoader = new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String moduleName = args.checkjstring(1);

                // 1. Check package.loaded
                LuaValue loaded = globals.get("package").get("loaded");
                LuaValue cached = loaded.get(moduleName);
                if (!cached.isnil()) return cached;

                String filename = moduleName.replace('.', '/');
                if (!filename.endsWith(".lua")) filename += ".lua";

                try {
                    // Check FileSystem (Disk -> Internal)
                    if (vm.fs.exists(filename)) {
                        String content = vm.fs.read(filename);
                        if (content != null) {
                            String processed = LuaSyntaxCandy.process(content);
                            LuaValue chunk = globals.load(processed, filename);
                            LuaValue result = chunk.call();

                            // If module returned nothing, default to true (like standard Lua)
                            // But usually modules return a table.
                            if (result.isnil()) result = LuaValue.TRUE;

                            loaded.set(moduleName, result);
                            return result;
                        }
                    }
                } catch (Exception e) {
                    // Propagate error so we don't get silent failures
                    throw new LuaError("Error requiring " + moduleName + ": " + e.getMessage());
                }

                // Return string to indicate "module not found" to Lua's require system
                return LuaValue.valueOf("\n\tno file '" + filename + "' (checked disk/internal)");
            }
        };

        // Insert our custom loader at the front of package.loaders
        LuaValue package_ = globals.get("package");
        LuaValue loaders = package_.get("loaders");
        if (loaders.isnil()) loaders = package_.get("searchers"); // Lua 5.2+ uses "searchers"

        // Create new loaders table with our loader first
        LuaValue newLoaders = LuaValue.tableOf();
        newLoaders.set(1, customLoader);
        if (!loaders.isnil()) {
            for (int i = 1; i <= loaders.length(); i++) {
                newLoaders.set(i + 1, loaders.get(i));
            }
        }
        package_.set("loaders", newLoaders);
        package_.set("searchers", newLoaders); // Set both for compatibility

        // Override dofile() to work with our asset system
        globals.set("dofile", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue filename) {
                String path = filename.checkjstring();
                if (!path.endsWith(".lua")) path += ".lua";

                try {
                    // Check FileSystem (Disk -> Internal)
                    if (vm.fs.exists(path)) {
                        String content = vm.fs.read(path);
                        if (content != null) {
                            String processed = LuaSyntaxCandy.process(content);
                            return globals.load(processed, path).call();
                        }
                    }
                    throw new LuaError("File not found: " + path);
                } catch (Exception e) {
                    throw new LuaError("Error in dofile(" + path + "): " + e.getMessage());
                }
            }
        });

        if (enableTimeout) {
            LuaValue hook = new ZeroArgFunction() {
                @Override public LuaValue call() {
                    if (System.currentTimeMillis() - startTime > MAX_EXECUTION_TIME_MS) throw new LuaError("CPU LIMIT EXCEEDED");
                    return LuaValue.NONE;
                }
            };
            globals.get("debug").get("sethook").call(hook, LuaValue.valueOf(""), LuaValue.valueOf(INSTRUCTION_CHECK_INTERVAL));
        }

        globals.set("log", new OneArgFunction() { @Override public LuaValue call(LuaValue m) { System.out.println("[LUA] "+m.tojstring()); return LuaValue.NONE; }});

        // Ensure os library functions are available
        LuaValue os = globals.get("os");
        if (os.isnil()) {
            os = LuaValue.tableOf();
            globals.set("os", os);
        }
        if (os.get("time").isnil()) {
            os.set("time", new ZeroArgFunction() {
                @Override public LuaValue call() {
                    return LuaValue.valueOf(System.currentTimeMillis() / 1000);
                }
            });
        }
        if (os.get("date").isnil()) {
            os.set("date", new OneArgFunction() {
                @Override public LuaValue call(LuaValue format) {
                    String fmt = format.optjstring("%c");
                    long time = System.currentTimeMillis();
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(fmt.replace("%H", "HH").replace("%M", "mm"));
                    return LuaValue.valueOf(sdf.format(new java.util.Date(time)));
                }
            });
        }

        // SYSTEM API
        LuaValue sys = LuaValue.tableOf();
        sys.set("target", new OneArgFunction() { @Override public LuaValue call(LuaValue t) { vm.setTarget(t.checkjstring()); return LuaValue.NONE; }});
        sys.set("draw_game", new VarArgFunction() {
            @Override public LuaValue invoke(Varargs args) {
                vm.beginBatch(); vm.batch.setColor(1,1,1,1);
                vm.batch.draw(vm.getGameTexture(), args.checkint(1), args.checkint(2), args.checkint(3), args.checkint(4), 0, 0, vm.profile.gameWidth, vm.profile.gameHeight, false, true);
                return LuaValue.NONE;
            }
        });
        sys.set("exit", new ZeroArgFunction() { @Override public LuaValue call() { Gdx.app.exit(); return LuaValue.NONE; }});
        globals.set("sys", sys);

        // GRAPHICS
        globals.set("print", new VarArgFunction() {
            @Override public LuaValue invoke(Varargs args) {
                vm.beginBatch();
                vm.getCurrentFont().setColor(vm.palette.get(vm.ram.peek(0x5F00+(args.optint(4, 7)%32))));
                vm.getCurrentFont().draw(vm.batch, args.checkjstring(1), args.checkint(2), args.checkint(3));
                return LuaValue.NONE;
            }
        });
        globals.set("cls", new OneArgFunction() { @Override public LuaValue call(LuaValue c) { vm.cls(c.checkint()); return LuaValue.NONE; }});
        globals.set("rect", new VarArgFunction() { @Override public LuaValue invoke(Varargs args) { vm.rect(args.checkint(1), args.checkint(2), args.checkint(3), args.checkint(4), args.checkint(5)); return LuaValue.NONE; }});
        globals.set("line", new VarArgFunction() { @Override public LuaValue invoke(Varargs args) { vm.line(args.checkint(1), args.checkint(2), args.checkint(3), args.checkint(4), args.checkint(5)); return LuaValue.NONE; }});
        globals.set("circ", new VarArgFunction() { @Override public LuaValue invoke(Varargs args) { vm.circle(args.checkint(1), args.checkint(2), args.checkint(3), args.checkint(4), args.optboolean(5, false)); return LuaValue.NONE; }});
        globals.set("spr", new VarArgFunction() {
            @Override public LuaValue invoke(Varargs args) {
                int id = args.checkint(1);
                TextureRegion[] currentSheet = vm.getActiveSprites();
                if (currentSheet == null || id < 0 || id >= currentSheet.length) return LuaValue.NONE;
                vm.beginBatch();
                TextureRegion r = currentSheet[id];
                boolean fx = args.optboolean(4, false); boolean fy = args.optboolean(5, false);
                boolean sx = r.isFlipX(); boolean sy = r.isFlipY();
                if (sx != fx) r.flip(true, false); if (sy != fy) r.flip(false, true);
                vm.batch.setColor(1, 1, 1, 1);
                vm.batch.draw(r, args.checkint(2), args.checkint(3));
                if (r.isFlipX() != sx) r.flip(true, false); if (r.isFlipY() != sy) r.flip(false, true);
                return LuaValue.NONE;
            }
        });

        globals.set("sspr", new VarArgFunction() {
            @Override public LuaValue invoke(Varargs args) {
                int sx = args.checkint(1);
                int sy = args.checkint(2);
                int sw = args.checkint(3);
                int sh = args.checkint(4);
                int dx = args.checkint(5);
                int dy = args.checkint(6);
                int dw = args.checkint(7);
                int dh = args.checkint(8);

                TextureRegion[] currentSheet = vm.getActiveSprites();
                if (currentSheet == null || currentSheet.length == 0) return LuaValue.NONE;

                vm.beginBatch();
                vm.batch.setColor(1, 1, 1, 1);
                // Draw stretched region from sprite sheet texture
                vm.batch.draw(currentSheet[0].getTexture(), dx, dy, dw, dh, sx, sy, sx + sw, sy + sh, false, false);
                return LuaValue.NONE;
            }
        });

        globals.set("sget", new VarArgFunction() {
            @Override public LuaValue invoke(Varargs args) {
                return LuaValue.valueOf(vm.sget(args.checkint(1), args.checkint(2)));
            }
        });

        globals.set("sset", new VarArgFunction() {
            @Override public LuaValue invoke(Varargs args) {
                vm.sset(args.checkint(1), args.checkint(2), args.checkint(3));
                vm.refreshSpriteTexture();
                return LuaValue.NONE;
            }
        });

        globals.set("save_sprites", new OneArgFunction() {
            @Override public LuaValue call(LuaValue path) {
                return LuaValue.valueOf(vm.saveSpriteSheet(path.checkjstring()));
            }
        });

        globals.set("sprite_sheet_ok", new ZeroArgFunction() {
            @Override public LuaValue call() {
                return LuaValue.valueOf(vm.isSpriteSheetLoaded());
            }
        });

        globals.set("display_width", new ZeroArgFunction() {
            @Override public LuaValue call() {
                return LuaValue.valueOf(Gdx.graphics.getWidth());
            }
        });

        globals.set("display_height", new ZeroArgFunction() {
            @Override public LuaValue call() {
                return LuaValue.valueOf(Gdx.graphics.getHeight());
            }
        });

        globals.set("font_width", new ZeroArgFunction() {
            @Override public LuaValue call() {
                BitmapFont font = vm.getCurrentFont();
                // Get width of a standard character (using 'M' which is typically widest in monospace)
                com.badlogic.gdx.graphics.g2d.GlyphLayout layout = new com.badlogic.gdx.graphics.g2d.GlyphLayout();
                layout.setText(font, "M");
                return LuaValue.valueOf((int)layout.width);
            }
        });

        globals.set("font_height", new ZeroArgFunction() {
            @Override public LuaValue call() {
                BitmapFont font = vm.getCurrentFont();
                return LuaValue.valueOf((int)font.getLineHeight());
            }
        });

        globals.set("sheet", new OneArgFunction() { @Override public LuaValue call(LuaValue id) { int idx = id.checkint(); if (idx >= 0 && idx < vm.spriteSheets.size()) vm.activeSheetIndex = idx; return LuaValue.NONE; }});
        globals.set("map", new VarArgFunction() { @Override public LuaValue invoke(Varargs args) { vm.map(args.checkint(1), args.checkint(2), args.checkint(3), args.checkint(4), args.checkint(5), args.checkint(6)); return LuaValue.NONE; }});
        globals.set("mget", new VarArgFunction() { @Override public LuaValue invoke(Varargs args) { return LuaValue.valueOf(vm.mget(args.checkint(1), args.checkint(2))); }});
        globals.set("mset", new VarArgFunction() { @Override public LuaValue invoke(Varargs args) { vm.mset(args.checkint(1), args.checkint(2), args.checkint(3)); return LuaValue.NONE; }});

        // MEMORY
        globals.set("peek", new OneArgFunction() { @Override public LuaValue call(LuaValue addr) { return LuaValue.valueOf(vm.ram.peek(addr.checkint())); }});
        globals.set("poke", new VarArgFunction() { @Override public LuaValue invoke(Varargs args) { vm.ram.poke(args.checkint(1), args.checkint(2)); return LuaValue.NONE; }});
        globals.set("peek2", new OneArgFunction() { @Override public LuaValue call(LuaValue addr) { return LuaValue.valueOf(vm.ram.peek2(addr.checkint())); }});
        globals.set("poke2", new VarArgFunction() { @Override public LuaValue invoke(Varargs args) { vm.ram.poke2(args.checkint(1), args.checkint(2)); return LuaValue.NONE; }});
        globals.set("memcpy", new VarArgFunction() { @Override public LuaValue invoke(Varargs args) { vm.ram.memcpy(args.checkint(1), args.checkint(2), args.checkint(3)); return LuaValue.NONE; }});
        globals.set("memset", new VarArgFunction() { @Override public LuaValue invoke(Varargs args) { vm.ram.memset(args.checkint(1), args.checkint(2), args.checkint(3)); return LuaValue.NONE; }});
        globals.set("bank", new OneArgFunction() { @Override public LuaValue call(LuaValue id) { vm.ram.setBank(id.checkint()); return LuaValue.NONE; }});

        // INPUT
        globals.set("btn", new OneArgFunction() { @Override public LuaValue call(LuaValue id) { return LuaValue.valueOf(vm.input.btn(id.checkint())); }});
        globals.set("btnp", new OneArgFunction() { @Override public LuaValue call(LuaValue id) { return LuaValue.valueOf(vm.input.btnp(id.checkint())); }});
        globals.set("key", new OneArgFunction() { @Override public LuaValue call(LuaValue n) { return LuaValue.valueOf(vm.input.isKeyHeld(n.checkjstring())); }});
        globals.set("keyp", new OneArgFunction() { @Override public LuaValue call(LuaValue n) { return LuaValue.valueOf(vm.input.isKeyJustPressed(n.checkjstring())); }});
        globals.set("char", new ZeroArgFunction() { @Override public LuaValue call() { String c = vm.input.getNextChar(); return (c != null) ? LuaValue.valueOf(c) : LuaValue.NIL; }});

        globals.set("mouse", new VarArgFunction() {
            @Override public LuaValue invoke(Varargs args) {
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

        globals.set("remap", new VarArgFunction() { @Override public LuaValue invoke(Varargs args) { try { vm.input.remap(args.checkint(1), Input.Keys.valueOf(args.checkjstring(2).toUpperCase())); } catch (Exception e) {} return LuaValue.NONE; }});

        // CLIPBOARD (get/set)
        globals.set("clipboard", new VarArgFunction() {
            @Override public LuaValue invoke(Varargs args) {
                if (args.narg() > 0) {
                    // Set clipboard
                    Gdx.app.getClipboard().setContents(args.checkjstring(1));
                    return LuaValue.NONE;
                } else {
                    // Get clipboard
                    String content = Gdx.app.getClipboard().getContents();
                    return (content != null) ? LuaValue.valueOf(content) : LuaValue.NIL;
                }
            }
        });

        // FS
        LuaValue fs = LuaValue.tableOf();
        fs.set("list", new OneArgFunction() { @Override public LuaValue call(LuaValue path) { java.util.List<String> files = vm.fs.list(path.optjstring("")); LuaValue list = LuaValue.tableOf(); for (int i = 0; i < files.size(); i++) list.set(i + 1, LuaValue.valueOf(files.get(i))); return list; }});
        fs.set("read", new OneArgFunction() { @Override public LuaValue call(LuaValue path) { String c = vm.fs.read(path.checkjstring()); return (c != null) ? LuaValue.valueOf(c) : LuaValue.NIL; }});
        fs.set("write", new VarArgFunction() { @Override public LuaValue invoke(Varargs args) { return LuaValue.valueOf(vm.fs.write(args.checkjstring(1), args.checkjstring(2))); }});
        fs.set("exists", new OneArgFunction() { @Override public LuaValue call(LuaValue path) { return LuaValue.valueOf(vm.fs.exists(path.checkjstring())); }});
        globals.set("fs", fs);
    }

    public void executeFunction(String functionName) {
        LuaValue func = globals.get(functionName);
        if (func.isfunction()) {
            startTime = System.currentTimeMillis();
            func.call();
        }
    }

    public void runScript(String script) {
        runScript(script, "boot.lua");
    }

    public void runScript(String script, String scriptName) {
        try {
            String sugared = LuaSyntaxCandy.process(script);
            globals.load(sugared, scriptName).call();
        } catch (LuaError e) { throw e; }
        catch (Exception e) { throw new LuaError(e); }
    }
}
