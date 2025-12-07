package com.nerddaygames.shell.modules;

import com.nerddaygames.shell.Project;
import com.badlogic.gdx.InputProcessor;

public interface ToolModule {
    String getName();

    // Lifecycle methods
    void loadProject(Project project);
    void update(float delta);
    void render();
    void resize(int width, int height);
    void dispose();

    // Input focus handling
    void onFocus();
    void onBlur();

    // NEW: Allow tool to receive raw keyboard events
    InputProcessor getInputProcessor();
}
