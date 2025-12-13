package com.nerddaygames.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import java.util.ArrayList;
import java.util.List;

/**
 * FileSystem - simple project/file abstraction.
 * Added constructor that accepts a FileHandle for a custom storage root (used by FantasyVM.setProjectDir).
 */
public class FileSystem {
    private FileHandle storageRoot;

    // Default constructor: local disk/disk/ root
    public FileSystem() {
        this.storageRoot = Gdx.files.local("disk/");
        if (!storageRoot.exists()) storageRoot.mkdirs();
    }

    // New constructor: explicit storage root (project directory)
    public FileSystem(FileHandle root) {
        if (root != null) this.storageRoot = root;
        else this.storageRoot = Gdx.files.local("disk/");
        if (!storageRoot.exists()) storageRoot.mkdirs();
    }

    public List<String> list(String path) {
        FileHandle handle = resolve(path);
        List<String> results = new ArrayList<>();
        if (handle != null && handle.isDirectory()) {
            for (FileHandle child : handle.list()) {
                results.add(child.name() + (child.isDirectory() ? "/" : ""));
            }
        }
        return results;
    }

    public String read(String path) {
        if (isPathInvalid(path)) return null;
        FileHandle handle = resolve(path);
        if (handle != null && handle.exists() && !handle.isDirectory()) return handle.readString();
        return null;
    }

    public boolean write(String path, String content) {
        if (path.startsWith("/system")) return false;
        if (isPathInvalid(path)) return false;
        if (content == null) return false;
        if (content.length() > 10 * 1024 * 1024) return false;
        try {
            FileHandle handle = storageRoot.child(cleanPath(path));
            FileHandle parent = handle.parent();
            if (parent != null && !parent.exists()) parent.mkdirs();
            handle.writeString(content, false, "UTF-8");
            return true;
        } catch (Exception e) { return false; }
    }

    public boolean exists(String path) {
        if (isPathInvalid(path)) return false;
        FileHandle handle = resolve(path);
        return handle != null && handle.exists();
    }

    private boolean isPathInvalid(String path) {
        return path == null || path.contains("..") || path.contains("~");
    }

    public FileHandle resolve(String path) {
        path = cleanPath(path);
        try {
            FileHandle userFile = storageRoot.child(path);
            // ensure canonical path still within storageRoot
            try {
                if (userFile.file().getCanonicalPath().startsWith(storageRoot.file().getCanonicalPath())) {
                    if (userFile.exists()) return userFile;
                }
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}

        // fallback to internal assets
        FileHandle internalFile = Gdx.files.internal(path);
        if (internalFile.exists()) return internalFile;

        // if not found, return a handle inside storage root (for create/write)
        return storageRoot.child(path);
    }

    private String cleanPath(String path) {
        if (path == null) return "";
        return path.replace("\\", "/").replaceAll("^/+", "");
    }
}
