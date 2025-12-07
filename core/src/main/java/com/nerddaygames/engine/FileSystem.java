package com.nerddaygames.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import java.util.ArrayList;
import java.util.List;

public class FileSystem {
    private final FileHandle storageRoot;

    public FileSystem() {
        this.storageRoot = Gdx.files.local("disk/");
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

        if (content.length() > 10 * 1024 * 1024) return false;

        try {
            FileHandle handle = storageRoot.child(cleanPath(path));
            handle.writeString(content, false);
            return true;
        } catch (Exception e) { return false; }
    }

    public boolean exists(String path) {
        if (isPathInvalid(path)) return false;
        FileHandle handle = resolve(path);
        return handle != null && handle.exists();
    }

    private boolean isPathInvalid(String path) {
        return path.contains("..") || path.contains("~");
    }

    public FileHandle resolve(String path) {
        path = cleanPath(path);
        FileHandle userFile = storageRoot.child(path);
        try {
            if (!userFile.file().getCanonicalPath().startsWith(storageRoot.file().getCanonicalPath())) {
                return null;
            }
        } catch(Exception e) { return null; }
        if (userFile.exists()) return userFile;
        FileHandle internalFile = Gdx.files.internal(path);
        if (internalFile.exists()) return internalFile;
        return userFile;
    }

    private String cleanPath(String path) {
        return path.replace("\\", "/").replaceAll("^/+", "");
    }
}
