package com.nerddaygames.shell.modules;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.nerddaygames.engine.FantasyVM;
import com.nerddaygames.engine.Profile;
import com.nerddaygames.engine.ScriptEngine;
import com.nerddaygames.shell.Project;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;

/**
 * LuaTool - runs small Lua-based tools using a dedicated FantasyVM instance.
 *
 * Fixes:
 *  - ensureStarterFiles now prefers the project's writable directory (project.getDir())
 *    for creating main.lua and the marker. This avoids repeatedly attempting to write to
 *    read-only/internal asset handles and prevents the repeated initialization loop.
 *  - ensureStarterFiles returns a boolean indicating whether it wrote files so callers can act.
 *  - loadProject only attempts starter creation if a writable project directory is available.
 *
 * Usage:
 *  - Call LuaTool.ensureStarterFiles(project, false) from your "New Project" flow so starter files
 *    are created exactly once at creation time. loadProject will invoke ensureStarterFiles as a
 *    safe fallback if the project directory is writable.
 */
public class LuaTool implements ToolModule {
    private final String name;
    private final FantasyVM toolVM;
    private final ScreenViewport viewport;
    private Project currentProject;
    private ScriptEngine.SystemCallback systemCallback;

    // Demo content to write into new projects (created once)
    private static final String DEMO_MAIN = ""
        + "-- Hello World tutorial\n"
        + "-- This runs when you press the Run button in the editor.\n\n"
        + "-- _init is called once at startup\n"
        + "function _init()\n"
        + "  msg = \"Hello, FantasyOS!\"\n"
        + "  prompt = \"Press any key to change the message.\"\n"
        + "  last_char = nil\n"
        + "end\n\n"
        + "-- _update runs every frame; use it for input/state\n"
        + "function _update()\n"
        + "  local c = char() -- next typed character (if any)\n"
        + "  if c then\n"
        + "    last_char = c\n"
        + "    msg = \"You typed: \" .. tostring(c)\n"
        + "  end\n"
        + "end\n\n"
        + "-- _draw runs every frame; use it for rendering\n"
        + "function _draw()\n"
        + "  cls(0) -- clear screen with color 0 (black)\n"
        + "  print(msg, 24, 80, 10)\n"
        + "  print(prompt, 24, 60, 7)\n"
        + "  if last_char then\n"
        + "    print(\"Last char code: \" .. string.byte(last_char), 24, 40, 9)\n"
        + "  end\n"
        + "end\n";

    // Project marker filename (prevents repeated template creation)
    private static final String PROJECT_MARKER = ".project_initialized";

    public LuaTool(String name, String toolScriptPath) {
        this.name = name;
        Profile p = new Profile();
        // High-res defaults for editor tools
        p.width = 1280;
        p.height = 720;

        // 0 = disable timeout for editor tools
        toolVM = new FantasyVM(p, 0);
        viewport = new ScreenViewport();
        toolVM.setViewport(viewport);

        injectProjectAPI();

        try {
            FileHandle fh = Gdx.files.internal(toolScriptPath);
            if (fh.exists()) {
                String script = fh.readString("UTF-8");
                if (toolVM.scriptEngine != null) {
                    toolVM.scriptEngine.runScript(script, toolScriptPath);
                    if (toolVM.scriptEngine.globals.get("_init").isfunction()) {
                        toolVM.scriptEngine.globals.get("_init").call();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Create starter files (main.lua + marker) inside the project's writable directory.
     *
     * This method intentionally uses project.getDir() (writable) rather than project.getAsset()
     * for writes, because getAsset() may reference read-only internal assets.
     *
     * @param project   the project to initialize
     * @param overwrite if true, will overwrite main.lua even if it exists
     * @return true if any file was written (main.lua or marker), false otherwise
     */
    public static boolean ensureStarterFiles(Project project, boolean overwrite) {
        if (project == null) return false;

        try {
            FileHandle projectRoot = project.getDir();
            if (projectRoot == null) {
                // No writable project directory available; cannot create starter files
                Gdx.app.log("LuaTool", "ensureStarterFiles: project.getDir() returned null; skipping starter creation.");
                return false;
            }

            // Ensure directory exists
            if (!projectRoot.exists()) projectRoot.mkdirs();

            FileHandle markerHandle = projectRoot.child(PROJECT_MARKER);
            if (markerHandle.exists() && !overwrite) {
                // already initialized
                return false;
            }

            FileHandle mainHandle = projectRoot.child("main.lua");

            boolean wroteSomething = false;

            if (!mainHandle.exists() || overwrite) {
                try {
                    FileHandle parent = mainHandle.parent();
                    if (parent != null && !parent.exists()) parent.mkdirs();
                    mainHandle.writeString(DEMO_MAIN, false, "UTF-8");
                    wroteSomething = true;
                    Gdx.app.log("LuaTool", "ensureStarterFiles: wrote main.lua to project.");
                } catch (Exception e) {
                    Gdx.app.error("LuaTool", "Failed to write main.lua: " + e.getMessage(), e);
                }
            }

            // Create marker to avoid future re-creation
            try {
                if (!markerHandle.exists()) {
                    FileHandle parent = markerHandle.parent();
                    if (parent != null && !parent.exists()) parent.mkdirs();
                    markerHandle.writeString("initialized", false, "UTF-8");
                    wroteSomething = true;
                    Gdx.app.log("LuaTool", "ensureStarterFiles: wrote " + PROJECT_MARKER);
                }
            } catch (Exception e) {
                Gdx.app.error("LuaTool", "Failed to write marker: " + e.getMessage(), e);
            }

            return wroteSomething;
        } catch (Exception e) {
            Gdx.app.error("LuaTool", "ensureStarterFiles exception: " + e.getMessage(), e);
            return false;
        }
    }

    // Wire the Run button from Lua -> Java -> Screen
    public void setSystemCallback(ScriptEngine.SystemCallback cb) {
        this.systemCallback = cb;
        if (toolVM != null && toolVM.scriptEngine != null) toolVM.scriptEngine.setSystemCallback(cb);
    }

    private void injectProjectAPI() {
        if (toolVM == null || toolVM.scriptEngine == null) return;

        LuaValue projectLib = LuaValue.tableOf();
        projectLib.set("read", new OneArgFunction() {
            @Override public LuaValue call(LuaValue path) {
                if (currentProject == null) return LuaValue.NIL;
                try {
                    // Try project.getAsset first (read), fallback to project.getDir()
                    FileHandle f = currentProject.getAsset(path.checkjstring());
                    if (f != null && f.exists()) {
                        byte[] bytes = f.readBytes();
                        return LuaValue.valueOf(new String(bytes, "UTF-8"));
                    }
                    FileHandle root = currentProject.getDir();
                    if (root != null) {
                        FileHandle fh = root.child(path.checkjstring());
                        if (fh.exists()) return LuaValue.valueOf(fh.readString("UTF-8"));
                    }
                } catch(Exception e){ /* ignore */ }
                return LuaValue.NIL;
            }
        });
        projectLib.set("write", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                if (currentProject == null) return LuaValue.FALSE;
                try {
                    String path = args.checkjstring(1);
                    String content = args.checkjstring(2);
                    FileHandle root = currentProject.getDir();
                    FileHandle f = null;
                    if (root != null) {
                        f = root.child(path);
                    }
                    if (f == null) {
                        // fallback to getAsset (may be read-only) — but prefer root
                        f = currentProject.getAsset(path);
                    }
                    if (f == null) return LuaValue.FALSE;
                    FileHandle parent = f.parent();
                    if (parent != null && !parent.exists()) parent.mkdirs();
                    f.writeString(content, false, "UTF-8");
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

        // Ensure the VM works with project files (set project dir early)
        try {
            if (project != null) {
                FileHandle projectRoot = project.getDir();
                if (projectRoot != null) {
                    if (!projectRoot.exists()) projectRoot.mkdirs();
                    try {
                        toolVM.setProjectDir(projectRoot);
                    } catch (Throwable t) {
                        Gdx.app.error("LuaTool", "setProjectDir failed: " + t.getMessage(), t);
                    }
                } else {
                    Gdx.app.log("LuaTool", "project.getDir() returned null; skipping setProjectDir.");
                }
            }
        } catch (Exception ignored) {}

        // Only attempt to create starter files if we have a writable project directory.
        try {
            if (project != null && project.getDir() != null) {
                ensureStarterFiles(project, false);
            } else {
                Gdx.app.log("LuaTool", "loadProject: project dir unavailable; skipping starter files creation.");
            }
        } catch (Exception e) {
            Gdx.app.error("LuaTool", "loadProject: ensureStarterFiles failed: " + e.getMessage(), e);
        }

        // Call Lua hook if present to notify the tool of the loaded project
        try {
            LuaValue func = toolVM.scriptEngine.globals.get("_on_project_loaded");
            if (func.isfunction()) func.call();
        } catch (Exception ignored) {}
    }

    @Override public void update(float delta) { if (toolVM != null) toolVM.update(delta); }
    @Override public void render() { if (toolVM != null) toolVM.render(); }

    @Override public void resize(int w, int h) {
        if (toolVM != null) {
            toolVM.resize(w, h);
            viewport.update(w, h, true);
        }
    }

    public com.badlogic.gdx.graphics.Texture getTexture() {
        return (toolVM != null) ? toolVM.getScreenTexture() : null;
    }

    @Override public String getName() { return name; }
    @Override public void dispose() { if (toolVM != null) toolVM.dispose(); }
    @Override public void onFocus() { }
    @Override public void onBlur() { }
    @Override public InputProcessor getInputProcessor() { return (toolVM != null) ? toolVM.input : null; }
}
