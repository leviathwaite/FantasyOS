package com.nerddaygames.shell.modules;

import com.badlogic.gdx.Gdx;
import com.nerddaygames.engine.FantasyVM;
import com.nerddaygames.engine.Profile;
import com.nerddaygames.shell.Project;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;

public class LuaTool implements ToolModule {
    private String name;
    private String toolScriptPath;

    // The VM running this tool (e.g. the Code Editor's VM)
    private FantasyVM toolVM;

    // The user's project currently being edited
    private Project currentProject;

    public LuaTool(String name, String toolScriptPath) {
        this.name = name;
        this.toolScriptPath = toolScriptPath;

        // 1. Create a High-Res Profile for the Tool
        // Tools usually run at higher resolution than the games they make
        Profile p = new Profile();
        p.width = 480;  // Double the game resolution for better UI
        p.height = 270;

        // 2. Boot the VM
        toolVM = new FantasyVM(p);

        // 3. Inject the "Project" API (Allows tool to read/write project files)
        injectProjectAPI();

        // 4. Load the Tool Script
        try {
            if (Gdx.files.internal(toolScriptPath).exists()) {
                String script = Gdx.files.internal(toolScriptPath).readString();
                toolVM.scriptEngine.runScript(script);

                // Call _init if exists
                if (toolVM.scriptEngine.globals.get("_init").isfunction()) {
                    toolVM.scriptEngine.globals.get("_init").call();
                }
            } else {
                System.err.println("Tool script missing: " + toolScriptPath);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void injectProjectAPI() {
        LuaValue projectLib = LuaValue.tableOf();

        // project.read("main.lua")
        projectLib.set("read", new OneArgFunction() {
            @Override public LuaValue call(LuaValue path) {
                if (currentProject == null) return LuaValue.NIL;

                // Read from the actual project folder
                com.badlogic.gdx.files.FileHandle f = currentProject.getAsset(path.checkjstring());
                if (f.exists()) return LuaValue.valueOf(f.readString());
                return LuaValue.NIL;
            }
        });

        // project.write("main.lua", content)
        projectLib.set("write", new VarArgFunction() {
            @Override public LuaValue invoke(Varargs args) {
                if (currentProject == null) return LuaValue.FALSE;

                String path = args.checkjstring(1);
                String content = args.checkjstring(2);
                try {
                    currentProject.getAsset(path).writeString(content, false);
                    return LuaValue.TRUE;
                } catch (Exception e) {
                    return LuaValue.FALSE;
                }
            }
        });

        // Inject into global scope
        toolVM.scriptEngine.globals.set("project", projectLib);
    }

    @Override
    public void loadProject(Project project) {
        this.currentProject = project;
        // Notify Lua tool
        LuaValue func = toolVM.scriptEngine.globals.get("_on_project_loaded");
        if (func.isfunction()) func.call();
    }

    @Override
    public void update(float delta) {
        toolVM.update(delta);
    }

    @Override
    public void render() {
        toolVM.render();
    }

    // We expose the texture so EditorScreen can draw it
    public com.badlogic.gdx.graphics.Texture getTexture() {
        return toolVM.getScreenTexture();
    }

    @Override public String getName() { return name; }
    @Override public void resize(int w, int h) { toolVM.setViewport(null); /* Handle resize if needed */ }
    @Override public void dispose() { toolVM.dispose(); }
    @Override public void onFocus() { }
    @Override public void onBlur() { }
}
