package com.nerddaygames.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipInputStream;

/**
 * FileSystem - project & template filesystem helpers
 *
 * Responsibilities:
 *  - Provide a storageRoot (defaults to Gdx.files.local("disk/"))
 *  - Discover templates (user templates under disk/user/templates and packaged system templates)
 *  - Create new project folders from templates (copy content + ensure main.lua)
 *  - Export projects to an external folder
 *
 * Note: This class is intended to remove file and folder handling from UI classes such as DesktopScreen.
 */
public class FileSystem {
    private FileHandle storageRoot;

    public static class TemplateDescriptor {
        public final String name;
        public final FileHandle source; // may be internal or local handle
        public final boolean user;      // true if local user template (disk/user/templates)
        public final boolean immutable; // true if packaged/system template (not removable via UI)

        public TemplateDescriptor(String name, FileHandle source, boolean user, boolean immutable) {
            this.name = name;
            this.source = source;
            this.user = user;
            this.immutable = immutable;
        }
    }

    // Default constructor: local disk/ root
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

    // ---------------- Basic helpers ----------------

    private String cleanPath(String path) {
        if (path == null) return "";
        return path.replace("\\", "/").replaceAll("^/+", "");
    }

    /**
     * Resolve a path first as a child of storageRoot, otherwise fallback to internal assets,
     * otherwise return a child handle under storageRoot (for creating new files).
     */
    public FileHandle resolve(String path) {
        path = cleanPath(path);
        try {
            FileHandle userFile = storageRoot.child(path);
            try {
                if (userFile.file() != null && storageRoot.file() != null) {
                    if (userFile.file().getCanonicalPath().startsWith(storageRoot.file().getCanonicalPath())) {
                        if (userFile.exists()) return userFile;
                    }
                }
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}

        FileHandle internalFile = Gdx.files.internal(path);
        if (internalFile.exists()) return internalFile;

        return storageRoot.child(path);
    }

    // ---------------- Template discovery ----------------

    /**
     * Discover templates dynamically.
     * Scans:
     *  - disk/user/templates (user templates) first
     *  - packaged internal system/templates (system templates) — listed and marked immutable
     *
     * Uses libGDX's internal listing where possible; falls back to a safe filesystem / JAR scan only when needed.
     */
    public List<TemplateDescriptor> discoverTemplates() {
        List<TemplateDescriptor> results = new ArrayList<>();

        // 1) user templates (disk/user/templates)
        FileHandle localRoot = Gdx.files.local("disk/user/templates");
        try {
            if (localRoot != null && localRoot.exists() && localRoot.isDirectory()) {
                FileHandle[] locals = null;
                try { locals = localRoot.list(); } catch (Exception ignored) { locals = null; }
                if (locals != null && locals.length > 0) {
                    for (FileHandle f : locals) {
                        if (f.isDirectory()) results.add(new TemplateDescriptor(f.name(), f, true, false));
                    }
                }
            }
        } catch (Exception ignored) {}

        // 2) packaged/system templates - prefer libGDX internal listing
        try {
            FileHandle sys = Gdx.files.internal("system/templates");
            boolean foundAny = false;
            if (sys != null && sys.exists()) {
                FileHandle[] st = null;
                try { st = sys.list(); } catch (Exception ignored) { st = null; }
                if (st != null && st.length > 0) {
                    for (FileHandle entry : st) {
                        String tplName = entry.name();
                        FileHandle internalTpl = Gdx.files.internal("system/templates/" + tplName);
                        if (looksLikeTemplate(internalTpl)) {
                            results.add(new TemplateDescriptor(tplName, internalTpl, false, true));
                            foundAny = true;
                            Gdx.app.log("FileSystem", "Discovered packaged template (internal.list): " + tplName);
                        } else {
                            Gdx.app.log("FileSystem", "Skipped non-template in system/templates: " + tplName);
                        }
                    }
                }
            }

            // If internal.list() didn't find any packaged templates, attempt a conservative fallback:
            //  - Check whether the code source is a directory and contains system/templates/
            //  - If so, list directories from there (safe; uses normal java.io.File)
            if (!foundAny) {
                try {
                    java.net.URL codeSource = getClass().getProtectionDomain().getCodeSource().getLocation();
                    if (codeSource != null) {
                        java.io.File csFile = new java.io.File(codeSource.toURI());
                        if (csFile.isDirectory()) {
                            java.io.File possible = new java.io.File(csFile, "system/templates");
                            if (possible.exists() && possible.isDirectory()) {
                                java.io.File[] children = possible.listFiles();
                                if (children != null) {
                                    for (java.io.File f : children) {
                                        if (f.isDirectory()) {
                                            String tplName = f.getName();
                                            FileHandle internalTpl = Gdx.files.internal("system/templates/" + tplName);
                                            if (looksLikeTemplate(internalTpl)) {
                                                results.add(new TemplateDescriptor(tplName, internalTpl, false, true));
                                                Gdx.app.log("FileSystem", "Discovered packaged template (exploded code-source): " + tplName);
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // codeSource is not a directory (likely a JAR). We already attempted internal.list; scanning JAR is heavier and
                            // often unnecessary. If needed, keep a templates.zip in assets as a reliable fallback.
                            Gdx.app.log("FileSystem", "codeSource not a directory; relying on internal assets or templates.zip fallback.");
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            Gdx.app.log("FileSystem", "Failed to enumerate internal templates: " + e.getMessage());
        }

        return results;
    }

    private boolean looksLikeTemplate(FileHandle tplHandle) {
        if (tplHandle == null) return false;
        try {
            FileHandle[] children = null;
            try { children = tplHandle.list(); } catch (Exception ignored) { children = null; }
            if (children != null && children.length > 0) return true;
            if (tplHandle.child("main.lua").exists()) return true;
            if (tplHandle.child("starting_code.lua").exists()) return true;
            if (tplHandle.child("config.toon").exists()) return true;
            if (tplHandle.child("files").exists()) return true;
        } catch (Exception ignored) {}
        return false;
    }

    // ---------------- Create project from template ----------------

    /**
     * Create a new project folder under storageRoot and populate it from the template descriptor.
     * Returns the project folder FileHandle on success (or null on failure).
     */
    public FileHandle createNewProjectFromTemplate(TemplateDescriptor tpl) {
        if (tpl == null) return null;

        // Generate project directory name: Project_0001...
        String base = "Project";
        int i = 1;
        while (storageRoot.child(base + "_" + String.format("%04d", i)).exists()) i++;
        FileHandle projectDir = storageRoot.child(base + "_" + String.format("%04d", i));
        projectDir.mkdirs();

        boolean usedTemplate = false;

        // Attempt to copy from tpl.source (works for local or many internal handles)
        if (tpl.source != null && tpl.source.exists()) {
            try {
                copyDirectory(tpl.source, projectDir);
                usedTemplate = true;
            } catch (Exception e) {
                Gdx.app.log("FileSystem", "Direct template copy failed: " + e.getMessage());
            }
        }

        // If not copied and it's a packaged/system template, try copying packaged template into local then copy
        if (!usedTemplate && !tpl.user && tpl.name != null && !tpl.name.isEmpty()) {
            boolean copiedLocal = copyPackagedTemplateToLocal(tpl.name);
            if (copiedLocal) {
                FileHandle localTpl = Gdx.files.local("disk/user/templates/" + tpl.name);
                if (localTpl.exists()) {
                    copyDirectory(localTpl, projectDir);
                    usedTemplate = true;
                }
            }
        }

        // Ensure main.lua exists: prefer starting_code.lua -> main.lua -> default DEMO_MAIN
        FileHandle main = projectDir.child("main.lua");
        if (!main.exists()) {
            boolean copied = false;
            if (projectDir.child("starting_code.lua").exists()) {
                try { projectDir.child("starting_code.lua").copyTo(main); copied = true; } catch (Exception ignored) {}
            }
            if (!copied && projectDir.child("main.lua").exists()) copied = true;
            if (!copied && tpl.source != null) {
                try {
                    if (tpl.source.child("starting_code.lua").exists()) { tpl.source.child("starting_code.lua").copyTo(main); copied = true; }
                    else if (tpl.source.child("main.lua").exists()) { tpl.source.child("main.lua").copyTo(main); copied = true; }
                } catch (Exception ignored) {}
            }
            if (!main.exists()) {
                final String DEMO_MAIN =
                    "function _init()\n" +
                        "  msg = 'New Project'\n" +
                        "end\n\n" +
                        "function _update()\n" +
                        "end\n\n" +
                        "function _draw()\n" +
                        "  cls(0)\n" +
                        "  print(msg, 24, 80, 10)\n" +
                        "end\n";
                try { main.writeString(DEMO_MAIN, false, "UTF-8"); } catch (Exception ignored) {}
            }
        }

        return projectDir;
    }

    // ---------------- Copy helpers ----------------

    /**
     * Copy directory recursively from source to dest.
     * Works with both local FileHandles and internal assets (where list() may return null).
     */
    public void copyDirectory(FileHandle source, FileHandle dest) {
        if (source == null || dest == null) return;
        if (source.isDirectory()) {
            if (!dest.exists()) dest.mkdirs();
            FileHandle[] children = null;
            try { children = source.list(); } catch (Exception ignored) { children = null; }
            if (children != null) {
                for (FileHandle child : children) {
                    copyDirectory(child, dest.child(child.name()));
                }
                return;
            }
            String[] common = new String[]{"main.lua", "starting_code.lua", "config.toon", "files"};
            for (String n : common) {
                FileHandle c = source.child(n);
                if (c.exists()) {
                    if (c.isDirectory()) copyDirectory(c, dest.child(n));
                    else copyFile(c, dest.child(n));
                }
            }
            return;
        }

        copyFile(source, dest);
    }

    private void copyFile(FileHandle src, FileHandle dst) {
        if (src == null || dst == null) return;
        try {
            try {
                src.copyTo(dst);
                return;
            } catch (Throwable t) {
                // fallback to read/write bytes
            }
            byte[] bytes = src.readBytes();
            FileHandle parent = dst.parent();
            if (parent != null && !parent.exists()) parent.mkdirs();
            dst.writeBytes(bytes, false);
        } catch (Exception e) {
            Gdx.app.error("FileSystem", "copyFile failed: " + e.getMessage(), e);
        }
    }

    // ---------------- Packaged template helpers ----------------

    /**
     * Copy packaged template system/templates/<templateName> into disk/user/templates/<templateName>.
     * Returns true if something was copied.
     */
    public boolean copyPackagedTemplateToLocal(String templateName) {
        if (templateName == null || templateName.isEmpty()) return false;
        try {
            FileHandle localRoot = Gdx.files.local("disk/user/templates");
            if (!localRoot.exists()) localRoot.mkdirs();
            FileHandle dest = localRoot.child(templateName);
            if (!dest.exists()) dest.mkdirs();

            FileHandle src = Gdx.files.internal("system/templates/" + templateName);
            try {
                if (src != null && src.exists() && src.isDirectory()) {
                    copyDirectory(src, dest);
                    return true;
                }
            } catch (Exception ignored) {}

            FileHandle zipHandle = Gdx.files.internal("system/templates.zip");
            if (zipHandle != null && zipHandle.exists()) {
                try (InputStream is = new BufferedInputStream(zipHandle.read());
                     ZipInputStream zis = new ZipInputStream(is)) {
                    java.util.zip.ZipEntry entry;
                    String prefix = "system/templates/" + templateName + "/";
                    byte[] buffer = new byte[4096];
                    boolean any = false;
                    while ((entry = zis.getNextEntry()) != null) {
                        String name = entry.getName();
                        if (name == null || !name.startsWith(prefix)) { zis.closeEntry(); continue; }
                        String rel = name.substring(prefix.length());
                        if (rel.length() == 0) { zis.closeEntry(); continue; }
                        FileHandle out = dest.child(rel);
                        if (entry.isDirectory()) {
                            if (!out.exists()) out.mkdirs();
                        } else {
                            FileHandle parent = out.parent();
                            if (parent != null && !parent.exists()) parent.mkdirs();
                            try (OutputStream os = new BufferedOutputStream(out.write(false))) {
                                int r;
                                while ((r = zis.read(buffer)) != -1) os.write(buffer, 0, r);
                            }
                        }
                        any = true;
                        zis.closeEntry();
                    }
                    return any;
                } catch (Exception e) {
                    Gdx.app.error("FileSystem", "Failed to extract template from zip: " + e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            Gdx.app.error("FileSystem", "copyPackagedTemplateToLocal failed: " + e.getMessage(), e);
        }
        return false;
    }

    // Extract templates.zip entries under "system/templates/" into destDir
    public void extractTemplatesZip(FileHandle zipHandle, FileHandle destDir) {
        if (zipHandle == null || destDir == null) return;
        byte[] buffer = new byte[4096];
        try (InputStream is = new BufferedInputStream(zipHandle.read());
             ZipInputStream zis = new ZipInputStream(is)) {
            java.util.zip.ZipEntry entry;
            final String prefix = "system/templates/";
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name == null || !name.startsWith(prefix)) { zis.closeEntry(); continue; }
                String rel = name.substring(prefix.length());
                if (rel.length() == 0) { zis.closeEntry(); continue; }
                FileHandle out = destDir.child(rel);
                if (entry.isDirectory()) {
                    if (!out.exists()) out.mkdirs();
                } else {
                    FileHandle parent = out.parent();
                    if (parent != null && !parent.exists()) parent.mkdirs();
                    try (OutputStream os = new BufferedOutputStream(out.write(false))) {
                        int read;
                        while ((read = zis.read(buffer)) != -1) os.write(buffer, 0, read);
                        os.flush();
                    } catch (Exception ex) {
                        Gdx.app.error("FileSystem", "Failed to write extracted template file: " + out.path() + " : " + ex.getMessage(), ex);
                    }
                }
                zis.closeEntry();
            }
            Gdx.app.log("FileSystem", "extractTemplatesZip completed into " + destDir.path());
        } catch (Exception e) {
            Gdx.app.error("FileSystem", "extractTemplatesZip failed: " + e.getMessage(), e);
        }
    }

    // ---------------- Export helper ----------------

    public boolean exportProjectToDirectory(FileHandle project, java.io.File destinationDir) {
        if (project == null || destinationDir == null) return false;
        try {
            FileHandle out = Gdx.files.absolute(new java.io.File(destinationDir, project.name()).getAbsolutePath());
            copyDirectory(project, out);
            return true;
        } catch (Exception e) {
            Gdx.app.error("FileSystem", "exportProjectToDirectory failed: " + e.getMessage(), e);
            return false;
        }
    }

    // ---------------- Basic filesystem API methods (kept for compatibility) ----------------

    public List<String> list(String path) {
        FileHandle handle = resolve(path);
        List<String> results = new ArrayList<>();
        if (handle != null && handle.isDirectory()) {
            FileHandle[] children = null;
            try { children = handle.list(); } catch (Exception ignored) { children = null; }
            if (children != null) {
                for (FileHandle child : children) {
                    results.add(child.name() + (child.isDirectory() ? "/" : ""));
                }
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
        if (path != null && path.startsWith("/system")) return false;
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
}
