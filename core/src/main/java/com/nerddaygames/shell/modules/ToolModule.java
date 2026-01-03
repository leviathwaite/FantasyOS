package com.nerddaygames.shell.modules;

import com.nerddaygames.shell.Project;
import com.nerddaygames.engine.ScriptEngine;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Texture;

/**
 * ToolModule - minimal interface for editor modules (Lua or Java-backed).
 * EditorScreen uses these lifecycle / render/update hooks.
 */
public interface ToolModule {
    String getName();
    
    // Returns the display title for the tab (e.g. "main.lua*")
    default String getTitle() { return getName(); }

    // Optional: load/initialize the module (compile scripts, allocate resources)
    void load();

    // Bind a project (EditorScreen passes the opened project)
    void loadProject(Project project);

    // Update + render lifecycle
    void update(float delta);
    void render();

    // Window/viewport resize
    void resize(int width, int height);

    // Cleanup
    void dispose();

    // Focus lifecycle
    void onFocus();
    void onBlur();

    // Optional: provide an InputProcessor to receive input events
    InputProcessor getInputProcessor();

    // Optional: return a texture that the tool renders into (may be null)
    Texture getTexture();

    /**
     * Allow the host to receive system calls from the tool (run, etc.).
     * Implementations may accept null if not used.
     */
    void setSystemCallback(ScriptEngine.SystemCallback cb);
}
