package com.nerddaygames.shell;

import com.badlogic.gdx.Gdx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * EditorSessionManager - singleton that tracks active editor sessions (EditorScreen instances).
 *
 * Responsibilities:
 *  - Allow editor screens to register/unregister themselves.
 *  - Provide helpers to detect unsaved work and to request saves for all sessions.
 *  - Provide a saveAllAndExit convenience used by the Desktop exit flow.
 */
public class EditorSessionManager {
    private static final EditorSessionManager INSTANCE = new EditorSessionManager();

    public static EditorSessionManager get() { return INSTANCE; }

    private final List<EditorSession> sessions = Collections.synchronizedList(new ArrayList<>());

    private EditorSessionManager() { }

    public void register(EditorSession s) {
        if (s == null) return;
        if (!sessions.contains(s)) sessions.add(s);
        Gdx.app.log("EditorSessionManager", "Registered editor session for: " + (s.getProjectDir() != null ? s.getProjectDir().path() : "null"));
    }

    public void unregister(EditorSession s) {
        if (s == null) return;
        sessions.remove(s);
        Gdx.app.log("EditorSessionManager", "Unregistered editor session for: " + (s.getProjectDir() != null ? s.getProjectDir().path() : "null"));
    }

    /**
     * Return true if any registered session reports unsaved changes.
     */
    public boolean hasUnsavedSessions() {
        synchronized (sessions) {
            for (EditorSession s : sessions) {
                try {
                    if (s != null && s.isDirty()) return true;
                } catch (Exception e) {
                    Gdx.app.error("EditorSessionManager", "isDirty() threw: " + e.getMessage(), e);
                }
            }
        }
        return false;
    }

    /**
     * Attempt to save all sessions. Returns number of successful saves.
     */
    public int saveAll() {
        int ok = 0;
        synchronized (sessions) {
            for (EditorSession s : sessions) {
                try {
                    if (s != null && s.save()) ok++;
                } catch (Exception e) {
                    Gdx.app.error("EditorSessionManager", "save() threw: " + e.getMessage(), e);
                }
            }
        }
        Gdx.app.log("EditorSessionManager", "saveAll completed: " + ok + " saved.");
        return ok;
    }

    /**
     * Save all sessions (best-effort) and then exit the application.
     * This method performs saves synchronously; if your saves are async,
     * implement a proper callback or completion mechanism and call Gdx.app.exit()
     * when ready.
     */
    public void saveAllAndExit() {
        Gdx.app.log("EditorSessionManager", "saveAllAndExit requested");
        saveAll();
        Gdx.app.exit();
    }
}
