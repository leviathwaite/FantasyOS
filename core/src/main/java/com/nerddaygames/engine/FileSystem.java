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
        if (handle.isDirectory()) {
            for (FileHandle child : handle.list()) {
                results.add(child.name() + (child.isDirectory() ? "/" : ""));
            }
        }
        return results;
    }

    public String read(String path) {
        FileHandle handle = resolve(path);
        if (handle.exists() && !handle.isDirectory()) return handle.readString();
        return null;
    }

    public boolean write(String path, String content) {
        if (path.startsWith("/system")) return false; // Protected
        try {
            FileHandle handle = storageRoot.child(cleanPath(path));
            handle.writeString(content, false);
            return true;
        } catch (Exception e) { return false; }
    }

    public boolean exists(String path) {
        return resolve(path).exists();
    }

    private FileHandle resolve(String path) {
        path = cleanPath(path);
        FileHandle userFile = storageRoot.child(path);
        if (userFile.exists()) return userFile;
        FileHandle internalFile = Gdx.files.internal(path);
        if (internalFile.exists()) return internalFile;
        return userFile;
    }

    private String cleanPath(String path) {
        return path.replace("\\", "/").replaceAll("^/+", "");
    }
}
