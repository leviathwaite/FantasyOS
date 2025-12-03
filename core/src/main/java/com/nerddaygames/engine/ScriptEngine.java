package com.nerddaygames.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
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

    public ScriptEngine(FantasyVM vm) {
        this.vm = vm;
        initLua();
    }

    private void initLua() {
        globals = JsePlatform.standardGlobals();
        globals.load(new org.luaj.vm2.lib.DebugLib());
        globals.finder = new ResourceFinder() {
            @Override public InputStream findResource(String f) {
                if(!f.endsWith(".lua")) f+=".lua";
                if(Gdx.files.internal(f).exists()) {
                    try { return new ByteArrayInputStream(LuaSyntaxCandy.process(Gdx.files.internal(f).readString()).getBytes()); }
                    catch(Exception e){}
                } return null;
            }
        };

        LuaValue hook = new ZeroArgFunction() {
            @Override public LuaValue call() {
                if (System.currentTimeMillis() - startTime > MAX_EXECUTION_TIME_MS) throw new LuaError("CPU LIMIT EXCEEDED");
                return LuaValue.NONE;
            }
        };
        globals.get("debug").get("sethook").call(hook, LuaValue.valueOf(""), LuaValue.valueOf(INSTRUCTION_CHECK_INTERVAL));

        globals.set("log", new OneArgFunction() { @Override public LuaValue call(LuaValue m) { System.out.println("[LUA] "+m.tojstring()); return LuaValue.NONE; }});

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
        try {
            String sugared = LuaSyntaxCandy.process(script);
            globals.load(sugared, "boot.lua").call();
        } catch (LuaError e) { throw e; }
        catch (Exception e) { throw new LuaError(e); }
    }
}
