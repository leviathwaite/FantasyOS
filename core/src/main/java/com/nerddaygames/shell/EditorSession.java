package com.nerddaygames.shell;

import com.badlogic.gdx.files.FileHandle;

/**
 * EditorSession - small interface that an editor screen implements so the
 * EditorSessionManager can query dirty state and request saves.
 */
public interface EditorSession {
    /**
     * Return true if the session has unsaved changes.
     */
    boolean isDirty();

    /**
     * Request the session to save. Returns true if save was successful (or
     * at least initiated).
     */
    boolean save();

    /**
     * The project directory associated with this session (may be null).
     */
    FileHandle getProjectDir();
}
