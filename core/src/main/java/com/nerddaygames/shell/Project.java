package com.nerddaygames.shell;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;

public class Project {
    private FileHandle projectDir;
    public ProjectConfig config;

    public Project(FileHandle dir) {
        this.projectDir = dir;
        loadConfig();
    }

    private void loadConfig() {
        FileHandle configFile = projectDir.child("project.json");
        if (configFile.exists()) {
            try {
                config = new Json().fromJson(ProjectConfig.class, configFile);
            } catch (Exception e) {
                // Fallback if json is corrupt
                config = new ProjectConfig();
                config.name = "Unknown Project";
            }
        } else {
            // Default config if missing
            config = new ProjectConfig();
            config.name = projectDir.name();
        }
    }

    /**
     * Helper to get a file inside the project folder.
     */
    public FileHandle getAsset(String path) {
        return projectDir.child(path);
    }

    /**
     * Returns the root directory of the project.
     * Required by RunScreen.
     */
    public FileHandle getDir() {
        return projectDir;
    }

    // --- CONFIG MODEL ---
    public static class ProjectConfig {
        public String name = "New Project";
        public String author = "User";
        public String version = "1.0";
        public int width = 240;  // Optional: Allow per-project resolution
        public int height = 136;
    }
}
