package com.nerddaygames.engine.shell;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.nerddaygames.engine.Profile;

public class Project {
    public final FileHandle rootDir;
    public final ProjectConfig config;

    public Project(FileHandle rootDir) {
        this.rootDir = rootDir;

        // Load project.json or create a default one if new
        FileHandle configFile = rootDir.child("project.json");
        Json json = new Json();

        if (configFile.exists()) {
            config = json.fromJson(ProjectConfig.class, configFile);
        } else {
            config = new ProjectConfig();
            saveConfig();
        }
    }

    public void saveConfig() {
        Json json = new Json();
        // Pretty print for human readability
        rootDir.child("project.json").writeString(json.prettyPrint(config), false);
    }

    public FileHandle getAsset(String name) {
        return rootDir.child(name);
    }

    // Serializable Data Class
    public static class ProjectConfig {
        public String name = "Untitled Project";
        public String author = "User";
        public Profile targetProfile = Profile.createNerdOS();
    }
}
