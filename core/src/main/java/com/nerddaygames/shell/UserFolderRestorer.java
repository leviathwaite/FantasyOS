package com.nerddaygames.shell;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

/**
 * UserFolderRestorer
 *
 * Copies a packaged template from internal assets ("system/default_user")
 * into the writable user folder ("disk/user") if the user folder is missing
 * or if overwrite==true. Safe, recursive copy that preserves any existing
 * user files unless overwrite is requested.
 *
 * Usage:
 *   UserFolderRestorer.ensureUserFolderExists(); // default, non-overwriting
 *   UserFolderRestorer.ensureUserFolderExists(true); // force overwrite from template
 */
public final class UserFolderRestorer {
    private static final String TEMPLATE_INTERNAL_PATH = "system/default_user";
    private static final String USER_LOCAL_PATH = "disk/user";

    private UserFolderRestorer() {}

    public static void ensureUserFolderExists() {
        ensureUserFolderExists(false);
    }

    /**
     * Ensure the local user folder exists. If missing, copy the packaged template.
     * @param overwrite if true, files in the destination will be overwritten by template files
     */
    public static void ensureUserFolderExists(boolean overwrite) {
        try {
            FileHandle userDir = Gdx.files.local(USER_LOCAL_PATH);
            if (userDir == null) return;

            // Create parent if missing
            if (!userDir.exists()) {
                userDir.mkdirs();
            }

            FileHandle template = Gdx.files.internal(TEMPLATE_INTERNAL_PATH);
            if (template.exists() && template.isDirectory()) {
                copyRecursive(template, userDir, overwrite);
                Gdx.app.log("UserFolderRestorer", "Restored user folder from internal template: " + TEMPLATE_INTERNAL_PATH);
            } else {
                // Template not packaged: ensure minimal structure so the app won't break
                FileHandle tools = userDir.child("tools");
                FileHandle projects = userDir.child("projects");
                if (!tools.exists()) tools.mkdirs();
                if (!projects.exists()) projects.mkdirs();
                if (!userDir.child("README.txt").exists()) {
                    userDir.child("README.txt").writeString("Restored user folder.", false, "UTF-8");
                }
                Gdx.app.log("UserFolderRestorer", "Created minimal user folder structure at " + userDir.path());
            }
        } catch (Exception e) {
            Gdx.app.error("UserFolderRestorer", "Failed to ensure user folder exists: " + e.getMessage(), e);
        }
    }

    /**
     * Recursive copy from src (internal) to dst (local).
     * - If src is a directory, ensure dst exists and copy children.
     * - If src is a file, copy if dst missing or overwrite==true.
     */
    private static void copyRecursive(FileHandle src, FileHandle dst, boolean overwrite) {
        try {
            if (src.isDirectory()) {
                if (!dst.exists()) dst.mkdirs();
                FileHandle[] children = src.list();
                if (children == null) return;
                for (FileHandle child : children) {
                    copyRecursive(child, dst.child(child.name()), overwrite);
                }
            } else {
                // src is a file
                if (!dst.exists() || overwrite) {
                    // Use FileHandle.copyTo to copy from internal to local
                    try {
                        src.copyTo(dst);
                    } catch (Exception e) {
                        // Some backends may not support copyTo for internal->local; fall back to stream copy
                        try {
                            dst.writeBytes(src.readBytes(), false);
                        } catch (Exception ex) {
                            Gdx.app.error("UserFolderRestorer", "Failed to copy " + src.path() + " -> " + dst.path() + ": " + ex.getMessage(), ex);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Gdx.app.error("UserFolderRestorer", "Error copying " + src.path() + " -> " + dst.path() + ": " + e.getMessage(), e);
        }
    }
}
