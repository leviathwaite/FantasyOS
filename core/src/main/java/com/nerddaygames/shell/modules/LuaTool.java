package com.nerddaygames.shell.modules;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.nerddaygames.engine.FantasyVM;
import com.nerddaygames.engine.Profile;
import com.nerddaygames.engine.ScriptEngine;
import com.nerddaygames.shell.Project;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;

public class LuaTool implements ToolModule {
    private String name;
    private FantasyVM toolVM;
    private ScreenViewport viewport;
    private Project currentProject;
    private ScriptEngine.SystemCallback systemCallback;

    public LuaTool(String name, String toolScriptPath) {
        this.name = name;
        Profile p = new Profile();
        // High-res defaults for editor tools
        p.width = 1280;
        p.height = 720;

        toolVM = new FantasyVM(p, 0); // 0 = disable timeout for editor tools
        viewport = new ScreenViewport();
        toolVM.setViewport(viewport);

        injectProjectAPI();

        try {
            if (Gdx.files.internal(toolScriptPath).exists()) {
                String script = Gdx.files.internal(toolScriptPath).readString();
                toolVM.scriptEngine.runScript(script, toolScriptPath);
                if (toolVM.scriptEngine.globals.get("_init").isfunction()) {
                    toolVM.scriptEngine.globals.get("_init").call();
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    // Wire the Run button from Lua -> Java -> Screen
    public void setSystemCallback(ScriptEngine.SystemCallback cb) {
        this.systemCallback = cb;
        if (toolVM != null) toolVM.scriptEngine.setSystemCallback(cb);
    }

    private void injectProjectAPI() {
        LuaValue projectLib = LuaValue.tableOf();
        projectLib.set("read", new OneArgFunction() {
            @Override public LuaValue call(LuaValue path) {
                if (currentProject == null) return LuaValue.NIL;
                try {
                    com.badlogic.gdx.files.FileHandle f = currentProject.getAsset(path.checkjstring());
                    if (f.exists()) return LuaValue.valueOf(f.readBytes());
                } catch(Exception e){}
                return LuaValue.NIL;
            }
        });
        projectLib.set("write", new VarArgFunction() {
            @Override public LuaValue invoke(Varargs args) {
                if (currentProject == null) return LuaValue.FALSE;
                try {
                    currentProject.getAsset(args.checkjstring(1)).writeString(args.checkjstring(2), false);
                    return LuaValue.TRUE;
                } catch (Exception e) { return LuaValue.FALSE; }
            }
        });
        toolVM.scriptEngine.globals.set("project", projectLib);

        // Add sys.run() to trigger project execution
        LuaValue sys = toolVM.scriptEngine.globals.get("sys");
        if (sys.isnil()) {
            sys = LuaValue.tableOf();
            toolVM.scriptEngine.globals.set("sys", sys);
        }
        sys.set("run", new org.luaj.vm2.lib.ZeroArgFunction() {
            @Override public LuaValue call() {
                if (systemCallback != null) {
                    systemCallback.onSystemCall("run");
                }
                return LuaValue.NONE;
            }
        });
    }

    @Override public void loadProject(Project project) {
        this.currentProject = project;
        LuaValue func = toolVM.scriptEngine.globals.get("_on_project_loaded");
        if (func.isfunction()) func.call();
    }

    @Override public void update(float delta) { toolVM.update(delta); }
    @Override public void render() { toolVM.render(); }

    @Override public void resize(int w, int h) {
        toolVM.resize(w, h);
        viewport.update(w, h, true);
    }

    public com.badlogic.gdx.graphics.Texture getTexture() { return toolVM.getScreenTexture(); }
    @Override public String getName() { return name; }
    @Override public void dispose() { toolVM.dispose(); }
    @Override public void onFocus() { }
    @Override public void onBlur() { }
    @Override public InputProcessor getInputProcessor() { return toolVM.input; }
}
