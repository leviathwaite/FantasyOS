package com.nerddaygames.shell;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;
import com.nerddaygames.Main;
import com.nerddaygames.engine.FileSystem;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipInputStream;

/**
 * DesktopScreen
 *
 * - Desktop icons and windows
 * - New Project dialog shows templates discovered at runtime via FileSystem
 * - Creating a project uses FileSystem.createNewProjectFromTemplate(...) so projects are created under the app disk
 * - DesktopScreen focuses on UI and delegates filesystem tasks to FileSystem helper
 */
public class DesktopScreen extends ScreenAdapter {
    private final Main game;
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private BitmapFont font;

    // ASSETS
    private Texture spriteSheet;
    private Texture fallbackSheet; // For generated icons
    private TextureRegion iconFolder, iconFile, iconProject, iconTrash, iconComputer;

    // FILESYSTEM ROOTS
    private FileHandle diskRoot;
    private FileSystem fs; // helper for templates/projects

    // STATE
    private List<DesktopIcon> desktopIcons = new ArrayList<>();
    private List<DesktopWindow> windows = new ArrayList<>();
    private DesktopWindow activeWindow = null;

    // Minimized external "apps" (e.g. minimized Editor screens)
    private static class MinimizedApp {
        String type;
        String title;
        FileHandle file;
        TextureRegion icon;
        MinimizedApp(String type, String title, FileHandle file, TextureRegion icon) {
            this.type = type; this.title = title; this.file = file; this.icon = icon;
        }
    }
    private List<MinimizedApp> minimizedApps = new ArrayList<>();

    // DRAG AND DROP STATE
    private DesktopItem draggingItem = null;
    private Vector2 dragPos = new Vector2();
    private Vector2 dragOffset = new Vector2();

    // CONTEXT MENU
    private boolean showMenu = false;
    private float menuX, menuY;
    private FileHandle menuTarget = null;

    // DELETE CONFIRM
    private boolean showDeleteConfirm = false;
    private FileHandle pendingDeleteTarget = null;

    // EXIT CONFIRM
    private boolean showExitConfirm = false;

    // DOUBLE-CLICK TRACKING
    private DesktopIcon lastClickIcon = null;
    private long lastClickTime = 0L;
    private static final long DOUBLE_CLICK_THRESHOLD_MS = 400L;

    // DOCK/BAR
    private static final float DOCK_HEIGHT = 48f;
    private static final float DOCK_ICON_W = 72f;
    private static final float HEADER_H = 30f;

    // NEW PROJECT DIALOG STATE (templateList populated on-demand)
    private boolean showNewProjectDialog = false;
    private static class TemplateEntry {
        String name;
        FileHandle source; // may be internal or local
        boolean user;
        boolean immutable;
        TemplateEntry(String n, FileHandle s, boolean u, boolean imm){ name = n; source = s; user = u; immutable = imm; }
    }
    private final List<TemplateEntry> templateList = new ArrayList<>();
    private int selectedTemplateIndex = 0;
    private float templateScrollOffset = 0f;

    public DesktopScreen(Main game) {
        this.game = game;
        this.batch = new SpriteBatch();
        this.shapes = new ShapeRenderer();
        this.font = new BitmapFont();

        loadAssets();

        // decide disk root - keep local by default; for desktop you might prefer external
        if (Gdx.app.getType() == Application.ApplicationType.Desktop) {
            diskRoot = Gdx.files.local("disk/"); // keep local by default; change to external if desired
        } else {
            diskRoot = Gdx.files.local("disk/");
        }
        if (!diskRoot.exists()) diskRoot.mkdirs();

        // FileSystem helper uses the same storage root so projects/templates live under diskRoot
        fs = new FileSystem(diskRoot);

        // Best-effort ensure user folder exists (external helper may already handle)
        try {
            com.nerddaygames.shell.UserFolderRestorer.ensureUserFolderExists(false);
        } catch (Throwable t) {
            Gdx.app.log("DesktopScreen", "UserFolderRestorer.ensureUserFolderExists not available or failed: " + t.getMessage());
        }

        refreshDesktop();

        // Set input processor safely on the main thread using an InputMultiplexer.
        final DesktopInput desktopInput = new DesktopInput();
        final InputMultiplexer mux = new InputMultiplexer();
        mux.addProcessor(desktopInput);
        // If you have a Stage or other processors, add them here (e.g. mux.addProcessor(stage))
        Gdx.app.postRunnable(new Runnable() {
            @Override public void run() {
                Gdx.input.setInputProcessor(mux);
            }
        });
    }

    private void loadAssets() {
        TextureRegion[][] tmp = null;

        try {
            if (Gdx.files.internal("sprites.png").exists()) {
                spriteSheet = new Texture(Gdx.files.internal("sprites.png"));
                tmp = TextureRegion.split(spriteSheet, 8, 8);
            }
        } catch (Exception e) {
            Gdx.app.log("DesktopScreen", "Sprite load failed: " + e.getMessage());
        }

        if (fallbackSheet == null) {
            Pixmap p = new Pixmap(8, 8, Pixmap.Format.RGBA8888);
            p.setColor(Color.WHITE); p.fill();
            fallbackSheet = new Texture(p);
            p.dispose();
        }

        iconFile     = getSafeIcon(tmp, 0, 0);
        iconFolder   = getSafeIcon(tmp, 0, 1);
        iconProject  = getSafeIcon(tmp, 0, 2);
        iconTrash    = getSafeIcon(tmp, 0, 3);
        iconComputer = getSafeIcon(tmp, 0, 4);
    }

    private TextureRegion getSafeIcon(TextureRegion[][] split, int row, int col) {
        if (split != null && row < split.length && col < split[row].length) {
            return split[row][col];
        }
        return new TextureRegion(fallbackSheet);
    }

    private void refreshDesktop() {
        desktopIcons.clear();
        desktopIcons.add(new DesktopIcon("Trash", diskRoot.child(".trash"), iconTrash, Gdx.graphics.getWidth() - 80, Gdx.graphics.getHeight() - 80, true));

        FileHandle[] files = diskRoot.list();
        int x = 20; int y = Gdx.graphics.getHeight() - 180;

        if (files != null) {
            for (FileHandle f : files) {
                if (f.name().startsWith(".")) continue;
                if (f.isDirectory()) {
                    desktopIcons.add(new DesktopIcon(f.name(), f, iconProject, x, y, true));
                    y -= 100;
                    if (y < 100) { y = Gdx.graphics.getHeight() - 180; x += 90; }
                }
            }
        }
    }

    // Create a blank project quickly (keeps older behaviour)
    private void createProject() { createProjectFromTemplate(null, "blank"); }

    // Use FileSystem to create project from template descriptor or explicit source
    private void createProjectFromTemplate(FileHandle templateSource, String templateName) {
        FileSystem.TemplateDescriptor tplDesc = null;
        if (templateSource != null) {
            boolean isUser = false;
            try {
                FileHandle localRoot = Gdx.files.local("disk/user/templates");
                if (templateSource.path().startsWith(localRoot.path())) isUser = true;
            } catch (Exception ignored) {}
            tplDesc = new FileSystem.TemplateDescriptor(templateName != null ? templateName : "template", templateSource, isUser, false);
        } else if (templateName != null && !"blank".equalsIgnoreCase(templateName)) {
            List<FileSystem.TemplateDescriptor> discovered = fs.discoverTemplates();
            for (FileSystem.TemplateDescriptor d : discovered) {
                if (d.name.equalsIgnoreCase(templateName)) { tplDesc = d; break; }
            }
        }

        if (tplDesc == null && "blank".equalsIgnoreCase(templateName)) {
            FileHandle localRoot = Gdx.files.local("disk/user/templates");
            if (!localRoot.exists()) localRoot.mkdirs();
            FileHandle blank = localRoot.child("blank");
            if (!blank.exists()) blank.mkdirs();
            FileHandle starter = blank.child("starting_code.lua");
            if (!starter.exists()) {
                final String START =
                    "-- Blank Project Template\n" +
                        "function _init()\n" +
                        "  -- blank starter\n" +
                        "end\n\n" +
                        "function _update() end\nfunction _draw() cls(0) end\n";
                try { starter.writeString(START, false, "UTF-8"); } catch (Exception e) { Gdx.app.error("DesktopScreen", "Failed writing blank starter: "+e.getMessage(), e); }
            }
            tplDesc = new FileSystem.TemplateDescriptor("blank", blank, true, false);
        }

        FileHandle projectDir = null;
        if (tplDesc != null) {
            projectDir = fs.createNewProjectFromTemplate(tplDesc);
        } else {
            projectDir = fs.createNewProjectFromTemplate(new FileSystem.TemplateDescriptor("blank", Gdx.files.local("disk/user/templates/blank"), true, false));
        }

        if (projectDir != null) {
            refreshDesktop();
            try {
                game.setScreen(new EditorScreen(game, projectDir));
            } catch (Exception e) {
                Gdx.app.error("DesktopScreen", "Failed to open new project in editor: " + e.getMessage(), e);
            }
        } else {
            Gdx.app.log("DesktopScreen", "Project creation failed for template: " + templateName);
        }
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.2f, 0.3f, 0.4f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        batch.begin();
        for (DesktopIcon icon : desktopIcons) icon.render(batch, font);
        batch.end();

        for (DesktopWindow win : windows) {
            if (!win.minimized && !win.closed) win.render(shapes, batch, font);
        }

        if (draggingItem != null) {
            batch.begin();
            batch.setColor(1, 1, 1, 0.7f);
            if (draggingItem.icon != null) batch.draw(draggingItem.icon, dragPos.x, dragPos.y, 48, 48);
            batch.setColor(1, 1, 1, 1);
            batch.end();
        }

        drawDock();

        if (showNewProjectDialog) drawNewProjectDialog();
        if (showDeleteConfirm) drawDeleteConfirm();
        if (showExitConfirm) drawExitConfirm();

        windows.removeIf(win -> win.closed);

        if (showMenu && !showNewProjectDialog && !showDeleteConfirm && !showExitConfirm) drawMenu();
    }

    private void drawMenu() {
        List<String> options = new ArrayList<>();
        if (menuTarget == null) {
            options.add("New Project");
            options.add("Refresh");
        } else {
            boolean isProject = (menuTarget != null && menuTarget.child("main.lua") != null && menuTarget.child("main.lua").exists());
            options.add(isProject ? "View Files" : "Open");
            if (isProject) options.add("Export");
            if (!isUserFolder(menuTarget)) options.add("Delete");
            options.add("Copy");
        }

        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0, 0, 0, 0.8f);
        float height = options.size() * 25f + 20f;
        shapes.rect(menuX, menuY - height + 10, 160, height);
        shapes.end();

        batch.begin();
        float tx = menuX + 10; float ty = menuY - 20;
        for (int i = 0; i < options.size(); i++) {
            font.draw(batch, options.get(i), tx, ty - (i * 25f));
        }
        batch.end();
    }

    private void drawDeleteConfirm() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.6f);
        shapes.rect(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        shapes.end();

        float w = 420, h = 160;
        float cx = (Gdx.graphics.getWidth() - w) / 2f;
        float cy = (Gdx.graphics.getHeight() - h) / 2f;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.95f, 0.95f, 0.95f, 1f);
        shapes.rect(cx, cy, w, h);
        shapes.setColor(0.2f, 0.2f, 0.2f, 1f);
        shapes.rect(cx, cy + h - 36, w, 36);
        shapes.end();

        batch.begin();
        font.setColor(Color.BLACK);
        font.draw(batch, "Confirm Delete", cx + 12, cy + h - 12);
        font.setColor(Color.DARK_GRAY);
        String body = (pendingDeleteTarget != null) ? ("Delete '" + pendingDeleteTarget.name() + "' and all contents?") : "Delete selected item?";
        font.draw(batch, body, cx + 12, cy + h - 50);
        batch.end();

        float btnW = 120, btnH = 36;
        float yesX = cx + w - btnW - 16;
        float noX = cx + w - (2 * btnW) - 24;
        float btnY = cy + 18;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.8f, 0.2f, 0.2f, 1f);
        shapes.rect(yesX, btnY, btnW, btnH);
        shapes.setColor(0.6f, 0.6f, 0.6f, 1f);
        shapes.rect(noX, btnY, btnW, btnH);
        shapes.end();

        batch.begin();
        font.setColor(Color.WHITE);
        font.draw(batch, "Yes, Delete", yesX + 12, btnY + 24);
        font.draw(batch, "Cancel", noX + 36, btnY + 24);
        batch.end();
    }

    private void drawExitConfirm() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.6f);
        shapes.rect(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        shapes.end();

        float w = 520, h = 180;
        float cx = (Gdx.graphics.getWidth() - w) / 2f;
        float cy = (Gdx.graphics.getHeight() - h) / 2f;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.95f, 0.95f, 0.95f, 1f);
        shapes.rect(cx, cy, w, h);
        shapes.setColor(0.2f, 0.2f, 0.2f, 1f);
        shapes.rect(cx, cy + h - 36, w, 36);
        shapes.end();

        batch.begin();
        font.setColor(Color.BLACK);
        font.draw(batch, "Exit FantasyOS", cx + 12, cy + h - 12);
        font.setColor(Color.DARK_GRAY);
        font.draw(batch, "There may be unsaved work. Save before exiting?", cx + 12, cy + h - 50);
        batch.end();

        float btnW = 160, btnH = 36;
        float saveX = cx + 12;
        float exitX = cx + 12 + btnW + 12;
        float cancelX = cx + w - btnW - 12;
        float btnY = cy + 18;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.2f, 0.6f, 0.2f, 1f);
        shapes.rect(saveX, btnY, btnW, btnH);
        shapes.setColor(0.8f, 0.2f, 0.2f, 1f);
        shapes.rect(exitX, btnY, btnW, btnH);
        shapes.setColor(0.6f, 0.6f, 0.6f, 1f);
        shapes.rect(cancelX, btnY, btnW, btnH);
        shapes.end();

        batch.begin();
        font.setColor(Color.WHITE);
        font.draw(batch, "Save & Exit", saveX + 28, btnY + 24);
        font.draw(batch, "Exit Without Saving", exitX + 12, btnY + 24);
        font.draw(batch, "Cancel", cancelX + 60, btnY + 24);
        batch.end();
    }

    private void drawDock() {
        float screenW = Gdx.graphics.getWidth();
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.6f);
        shapes.rect(0, 0, screenW, DOCK_HEIGHT);
        shapes.end();

        float x = 8;

        for (MinimizedApp app : minimizedApps) {
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(0.15f, 0.15f, 0.15f, 1f);
            shapes.rect(x - 4, 6, DOCK_ICON_W, DOCK_HEIGHT - 12);
            shapes.end();

            batch.begin();
            if (app.icon != null) batch.draw(app.icon, x, 10, 32, 32);
            font.setColor(Color.WHITE);
            font.draw(batch, app.title.length() > 10 ? app.title.substring(0, 8) + ".." : app.title, x + 36, 30);
            batch.end();

            x += DOCK_ICON_W + 8;
        }

        for (DesktopWindow win : windows) {
            if (!win.minimized) continue;

            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(0.15f, 0.15f, 0.15f, 1f);
            shapes.rect(x - 4, 6, DOCK_ICON_W, DOCK_HEIGHT - 12);
            shapes.end();

            batch.begin();
            TextureRegion ico = (win.icon != null) ? win.icon : iconProject;
            if (ico != null) batch.draw(ico, x, 10, 32, 32);
            font.setColor(Color.WHITE);
            font.draw(batch, win.title.length() > 10 ? win.title.substring(0, 8) + ".." : win.title, x + 36, 30);
            batch.end();

            x += DOCK_ICON_W + 8;
        }

        if (minimizedApps.isEmpty() && windows.stream().noneMatch(win -> win.minimized)) {
            batch.begin();
            font.setColor(Color.LIGHT_GRAY);
            font.draw(batch, "No minimized apps", 8, 30);
            batch.end();
        }
    }

    // ---------------- helpers and small interfaces ----------------

    private boolean isUserFolder(FileHandle f) {
        if (f == null || diskRoot == null) return false;
        try {
            FileHandle user = diskRoot.child("user");
            if (user.exists()) {
                try {
                    if (f.file().getCanonicalPath().equals(user.file().getCanonicalPath())) return true;
                } catch (Exception ignored) {}
                if (f.path().equals(user.path())) return true;
            }
            if ("user".equalsIgnoreCase(f.name())) return true;
        } catch (Exception ignored) {}
        return false;
    }

    public void addMinimizedEditor(FileHandle projectDir) {
        if (projectDir == null) return;
        MinimizedApp app = new MinimizedApp("editor", projectDir.name(), projectDir, iconProject);
        minimizedApps.add(app);
    }

    private class DesktopIcon {
        String label; FileHandle file; TextureRegion icon; Rectangle bounds; boolean isDir;
        DesktopIcon(String name, FileHandle f, TextureRegion ico, float x, float y, boolean isDir) {
            this.label = name; this.file = f; this.icon = ico; this.isDir = isDir;
            this.bounds = new Rectangle(x, y, 64, 80);
        }
        void render(SpriteBatch sb, BitmapFont fnt) {
            if (icon != null) sb.draw(icon, bounds.x, bounds.y + 16, 64, 64);
            fnt.setColor(Color.WHITE);
            fnt.draw(sb, label.length() > 10 ? label.substring(0,8) + ".." : label, bounds.x, bounds.y + 10);
        }
    }

    private class DesktopItem {
        FileHandle file; Rectangle bounds; TextureRegion icon;
        DesktopItem(FileHandle f, float x, float y) {
            this.file = f; this.bounds = new Rectangle(x, y, 48, 48);
            if (f.isDirectory()) this.icon = iconFolder; else this.icon = iconFile;
        }
    }

    private class DesktopWindow {
        FileHandle currentFolder; Rectangle bounds; List<DesktopItem> items = new ArrayList<>();
        boolean isDragging = false; float dragOffX, dragOffY; float scrollY = 0;
        public boolean minimized = false;
        public boolean fullscreen = false;
        public boolean closed = false;
        public String title = "Window";
        public TextureRegion icon = null;
        private Rectangle prevBounds = new Rectangle();

        DesktopWindow(FileHandle folder, float w, float h) {
            this.currentFolder = folder;
            this.title = folder != null ? folder.name() : "Untitled";
            this.bounds = new Rectangle(Gdx.graphics.getWidth()/2f - w/2, Gdx.graphics.getHeight()/2f - h/2, w, h);
            refresh();
        }

        void refresh() {
            items.clear();
            if (currentFolder == null || !currentFolder.exists() || !currentFolder.isDirectory()) {
                Gdx.app.log("DesktopWindow", "refresh: folder invalid: " + (currentFolder != null ? currentFolder.path() : "null"));
                return;
            }
            FileHandle[] files = currentFolder.list();
            float x = 10, y = -10;
            if (files == null) return;
            for (FileHandle f : files) {
                if (f.name().startsWith(".")) continue;
                items.add(new DesktopItem(f, x, y));
                x += 60;
                if (x > bounds.width - 60) { x = 10; y -= 70; }
            }
        }

        void setPosition(float x, float y) { bounds.setPosition(x, y); }

        void render(ShapeRenderer sr, SpriteBatch sb, BitmapFont fnt) {
            if (minimized || closed) return;

            sr.begin(ShapeRenderer.ShapeType.Filled);
            sr.setColor(0.9f, 0.9f, 0.9f, 1); sr.rect(bounds.x, bounds.y, bounds.width, bounds.height);
            sr.setColor(0.1f, 0.1f, 0.4f, 1); sr.rect(bounds.x, bounds.y + bounds.height - HEADER_H, bounds.width, HEADER_H);
            sr.setColor(0.8f, 0.2f, 0.2f, 1); sr.rect(bounds.x + bounds.width - 26, bounds.y + bounds.height - 26, 20, 20);
            sr.setColor(0.2f, 0.8f, 0.2f, 1); sr.rect(bounds.x + bounds.width - 52, bounds.y + bounds.height - 26, 20, 20);
            sr.setColor(0.8f, 0.8f, 0.2f, 1); sr.rect(bounds.x + bounds.width - 78, bounds.y + bounds.height - 26, 20, 20);
            sr.end();

            sb.begin();
            fnt.setColor(Color.WHITE); fnt.draw(sb, title, bounds.x + 10, bounds.y + bounds.height - 8);
            sb.end();

            Rectangle scissors = new Rectangle();
            Rectangle clipBounds = new Rectangle(bounds.x, bounds.y, bounds.width, bounds.height - HEADER_H);
            if (game.camera != null) {
                ScissorStack.calculateScissors(game.camera, sb.getTransformMatrix(), clipBounds, scissors);
                if (ScissorStack.pushScissors(scissors)) {
                    sb.begin();
                    float startY = bounds.y + bounds.height - 80 + scrollY;
                    for (DesktopItem item : items) {
                        float wx = bounds.x + item.bounds.x; float wy = startY + item.bounds.y;
                        if (wy < bounds.y - 50 || wy > bounds.y + bounds.height) continue;
                        if (item.icon != null) sb.draw(item.icon, wx, wy, 48, 48);
                        fnt.setColor(Color.BLACK);
                        String n = item.file.name(); if (n.length() > 8) n = n.substring(0,8);
                        fnt.draw(sb, n, wx, wy - 5);
                    }
                    sb.end();
                    ScissorStack.popScissors();
                }
            }
        }

        boolean touchDown(float x, float y) {
            if (closed) return false;
            if (!bounds.contains(x, y)) return false;

            if (y > bounds.y + bounds.height - HEADER_H) {
                float closeX0 = bounds.x + bounds.width - 26, closeX1 = bounds.x + bounds.width - 6;
                float fsX0 = bounds.x + bounds.width - 52, fsX1 = bounds.x + bounds.width - 32;
                float minX0 = bounds.x + bounds.width - 78, minX1 = bounds.x + bounds.width - 58;

                if (x >= closeX0 && x <= closeX1) { this.closed = true; return true; }
                if (x >= fsX0 && x <= fsX1) {
                    if (!fullscreen) { prevBounds.set(bounds); bounds.set(0, DOCK_HEIGHT, Gdx.graphics.getWidth(), Gdx.graphics.getHeight() - DOCK_HEIGHT); fullscreen = true; isDragging = false; }
                    else { bounds.set(prevBounds); fullscreen = false; }
                    return true;
                }
                if (x >= minX0 && x <= minX1) { this.minimized = true; return true; }

                isDragging = true; dragOffX = x - bounds.x; dragOffY = y - bounds.y; return true;
            }

            float startY = bounds.y + bounds.height - 80 + scrollY;
            for (DesktopItem item : items) {
                float wx = bounds.x + item.bounds.x; float wy = startY + item.bounds.y;
                if (x >= wx && x <= wx + 48 && y >= wy && y <= wy + 48) {
                    draggingItem = item; dragPos.set(x - 24, y - 24); dragOffset.set(24, 24); return true;
                }
            }
            return true;
        }
    }

    /**
     * Input handler for desktop interactions.
     * Kept as an inner class so it can call the surrounding helpers directly.
     */
    private class DesktopInput extends InputAdapter {
        @Override
        public boolean keyDown(int keycode) {
            if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.BACK) {
                showExitConfirm = true;
                return true;
            }
            return false;
        }

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            // handle modal dialogs (new project first)
            if (showNewProjectDialog) { handleNewProjectDialogClick(screenX, screenY, button); return true; }
            if (showDeleteConfirm) { handleDeleteConfirmClick(screenX, screenY); return true; }
            if (showExitConfirm) { handleExitConfirmClick(screenX, screenY); return true; }

            float y = Gdx.graphics.getHeight() - screenY;
            float x = screenX;

            // Dock click (restore minimized windows & apps)
            if (y < DOCK_HEIGHT) {
                float slotX = 8;
                for (int i = 0; i < minimizedApps.size(); i++) {
                    MinimizedApp app = minimizedApps.get(i);
                    Rectangle iconRect = new Rectangle(slotX - 4, 6, DOCK_ICON_W, DOCK_HEIGHT - 12);
                    if (iconRect.contains(x, y)) {
                        if ("editor".equals(app.type)) {
                            try {
                                game.setScreen(new EditorScreen(game, app.file));
                                minimizedApps.remove(i);
                            } catch (Exception e) {
                                Gdx.app.error("DesktopScreen", "Failed to restore minimized editor: " + e.getMessage(), e);
                            }
                            return true;
                        } else {
                            minimizedApps.remove(i);
                            return true;
                        }
                    }
                    slotX += DOCK_ICON_W + 8;
                }

                for (DesktopWindow win : windows) {
                    if (!win.minimized) continue;
                    Rectangle iconRect = new Rectangle(slotX - 4, 6, DOCK_ICON_W, DOCK_HEIGHT - 12);
                    if (iconRect.contains(x, y)) {
                        restoreWindow(win);
                        return true;
                    }
                    slotX += DOCK_ICON_W + 8;
                }
            }

            if (showMenu) { handleMenuClick(x, y); return true; }

            // Window hit tests
            for (int i = windows.size() - 1; i >= 0; i--) {
                DesktopWindow win = windows.get(i);
                if (win.closed) continue;
                if (win.touchDown(x, y)) {
                    if (!win.minimized) { windows.remove(i); windows.add(win); activeWindow = win; }
                    windows.removeIf(w -> w.closed);
                    return true;
                }
            }

            // Desktop icons
            try {
                for (DesktopIcon icon : desktopIcons) {
                    if (icon.bounds.contains(x, y)) {
                        if (button == Input.Buttons.RIGHT) {
                            openMenu(x, y, icon.file);
                        } else if (button == Input.Buttons.LEFT) {
                            long now = System.currentTimeMillis();
                            boolean sameIcon = (icon == lastClickIcon);
                            if (sameIcon && (now - lastClickTime) <= DOUBLE_CLICK_THRESHOLD_MS) {
                                lastClickIcon = null;
                                lastClickTime = 0L;
                                boolean isProject = (icon.file != null && icon.file.child("main.lua") != null && icon.file.child("main.lua").exists());
                                if (icon.isDir && isProject) {
                                    try {
                                        game.setScreen(new EditorScreen(game, icon.file));
                                    } catch (Exception e) {
                                        Gdx.app.error("DesktopScreen", "Failed to open project in editor: " + e.getMessage(), e);
                                    }
                                } else if (icon.isDir) {
                                    try { if (icon.file != null) openWindow(icon.file); } catch (Exception e) { Gdx.app.error("DesktopScreen", "Failed to open folder window: " + e.getMessage(), e); }
                                }
                            } else {
                                lastClickIcon = icon;
                                lastClickTime = now;
                            }
                        }
                        return true;
                    }
                }
            } catch (Exception e) {
                Gdx.app.error("DesktopScreen", "Unhandled exception while handling desktop icon click: " + e.getMessage(), e);
                return true;
            }

            if (button == Input.Buttons.RIGHT) openMenu(x, y, null);
            return false;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            float y = Gdx.graphics.getHeight() - screenY;
            float x = screenX;
            if (draggingItem != null) { dragPos.set(x - dragOffset.x, y - dragOffset.y); return true; }
            if (activeWindow != null && activeWindow.isDragging) { activeWindow.setPosition(x - activeWindow.dragOffX, y - activeWindow.dragOffY); return true; }
            return false;
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            if (draggingItem != null) handleDrop();
            draggingItem = null;
            if (activeWindow != null) activeWindow.isDragging = false;
            return false;
        }
    }

    private void handleExitConfirmClick(int screenX, int screenY) {
        float w = 520, h = 180;
        float cx = (Gdx.graphics.getWidth() - w) / 2f;
        float cy = (Gdx.graphics.getHeight() - h) / 2f;
        float btnW = 160, btnH = 36;
        float saveX = cx + 12;
        float exitX = cx + 12 + btnW + 12;
        float cancelX = cx + w - btnW - 12;
        float btnY = cy + 18;
        float y = Gdx.graphics.getHeight() - screenY;
        float x = screenX;

        Rectangle saveRect = new Rectangle(saveX, btnY, btnW, btnH);
        Rectangle exitRect = new Rectangle(exitX, btnY, btnW, btnH);
        Rectangle cancelRect = new Rectangle(cancelX, btnY, btnW, btnH);

        if (saveRect.contains(x, y)) {
            try {
                EditorSessionManager.get().saveAllAndExit();
            } catch (Exception e) {
                Gdx.app.error("DesktopScreen", "saveAllAndExit failed: " + e.getMessage(), e);
                Gdx.app.exit();
            }
        } else if (exitRect.contains(x, y)) {
            Gdx.app.exit();
        } else if (cancelRect.contains(x, y)) {
            showExitConfirm = false;
        } else {
            showExitConfirm = false;
        }
    }

    private void handleDeleteConfirmClick(int screenX, int screenY) {
        float w = 420, h = 160;
        float cx = (Gdx.graphics.getWidth() - w) / 2f;
        float cy = (Gdx.graphics.getHeight() - h) / 2f;
        float btnW = 120, btnH = 36;
        float yesX = cx + w - btnW - 16;
        float noX = cx + w - (2 * btnW) - 24;
        float btnY = cy + 18;
        float y = Gdx.graphics.getHeight() - screenY;
        float x = screenX;

        Rectangle yesRect = new Rectangle(yesX, btnY, btnW, btnH);
        Rectangle noRect = new Rectangle(noX, btnY, btnW, btnH);

        if (yesRect.contains(x, y)) {
            try {
                if (pendingDeleteTarget != null) {
                    if (isUserFolder(pendingDeleteTarget)) {
                        Gdx.app.log("DesktopScreen", "Deletion prevented for protected user folder: " + pendingDeleteTarget.path());
                    } else {
                        boolean deleted = false;
                        try { deleted = pendingDeleteTarget.deleteDirectory(); } catch (Exception e) { try { pendingDeleteTarget.delete(); deleted = true; } catch (Exception ex) {} }
                        if (!deleted) Gdx.app.error("DesktopScreen", "Failed to delete: " + pendingDeleteTarget.path());
                    }
                }
            } catch (Exception e) {
                Gdx.app.error("DesktopScreen", "Delete failed: " + e.getMessage(), e);
            } finally {
                pendingDeleteTarget = null;
                showDeleteConfirm = false;
                refreshDesktop();
            }
        } else if (noRect.contains(x, y)) {
            pendingDeleteTarget = null;
            showDeleteConfirm = false;
        } else {
            pendingDeleteTarget = null;
            showDeleteConfirm = false;
        }
    }

    private void openWindow(FileHandle folder) {
        if (folder == null) {
            Gdx.app.log("DesktopScreen", "openWindow: folder is null, ignoring");
            return;
        }
        windows.add(new DesktopWindow(folder, 400, 300));
    }

    private void openMenu(float x, float y, FileHandle target) { showMenu = true; menuX = x; menuY = y; menuTarget = target; }

    private void handleMenuClick(float x, float y) {
        List<String> options = new ArrayList<>();
        if (menuTarget == null) {
            options.add("New Project");
            options.add("Refresh");
        } else {
            boolean isProject = (menuTarget != null && menuTarget.child("main.lua") != null && menuTarget.child("main.lua").exists());
            options.add(isProject ? "View Files" : "Open");
            if (isProject) options.add("Export");
            if (!isUserFolder(menuTarget)) options.add("Delete");
            options.add("Copy");
        }

        float ly = menuY - y;
        int index = (int)(ly / 25f);
        if (index < 0 || index >= options.size()) { showMenu = false; return; }

        String choice = options.get(index);
        showMenu = false;

        if (menuTarget == null) {
            if ("New Project".equals(choice)) openNewProjectDialog();
            if ("Refresh".equals(choice)) refreshDesktop();
            return;
        }

        switch (choice) {
            case "View Files":
            case "Open":
                if (menuTarget != null && menuTarget.isDirectory()) {
                    FileHandle filesSub = menuTarget.child("files");
                    if (filesSub.exists() && filesSub.isDirectory()) openWindow(filesSub);
                    else openWindow(menuTarget);
                }
                break;
            case "Export":
                if (menuTarget != null && menuTarget.isDirectory()) {
                    exportProject(menuTarget);
                }
                break;
            case "Delete":
                if (isUserFolder(menuTarget)) {
                    Gdx.app.log("DesktopScreen", "Attempted delete on protected user folder: " + menuTarget.path());
                } else {
                    pendingDeleteTarget = menuTarget;
                    showDeleteConfirm = true;
                }
                break;
            case "Copy":
                // placeholder
                break;
            default:
                break;
        }
    }

    // Export implementation: desktop uses JFileChooser; Android should use a bridge (not shown here)
    private void exportProject(FileHandle projectDir) {
        if (projectDir == null || !projectDir.exists()) return;
        if (Gdx.app.getType() == Application.ApplicationType.Desktop) {
            exportProjectDesktop(projectDir);
        } else if (Gdx.app.getType() == Application.ApplicationType.Android) {
            // Android bridge pattern: Main or launcher should implement an export interface to handle SAF
            if (game instanceof ExportBridgeProvider) {
                ((ExportBridgeProvider) game).requestExportProject(projectDir);
            } else {
                Gdx.app.log("DesktopScreen", "Android export bridge not available.");
            }
        } else {
            // fallback: export to external/local folder
            exportProjectDesktop(projectDir);
        }
    }

    // Desktop export uses JFileChooser to pick destination directory and then FileSystem.exportProjectToDirectory
    private void exportProjectDesktop(FileHandle projectDir) {
        try {
            javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
            chooser.setDialogTitle("Export Project - Select Destination Folder");
            chooser.setFileSelectionMode(javax.swing.JFileChooser.DIRECTORIES_ONLY);
            int res = chooser.showSaveDialog(null);
            if (res == javax.swing.JFileChooser.APPROVE_OPTION) {
                java.io.File dest = chooser.getSelectedFile();
                java.io.File outDir = new java.io.File(dest, projectDir.name());
                if (outDir.exists()) {
                    outDir = new java.io.File(dest, projectDir.name() + "_" + System.currentTimeMillis());
                }
                boolean ok = fs.exportProjectToDirectory(projectDir, outDir);
                if (ok) {
                    Gdx.app.log("DesktopScreen", "Project exported to: " + outDir.getAbsolutePath());
                    try { java.awt.Desktop.getDesktop().open(outDir); } catch (Exception ignored) {}
                } else {
                    Gdx.app.log("DesktopScreen", "Project export failed.");
                }
            }
        } catch (Exception e) {
            Gdx.app.error("DesktopScreen", "Desktop export failed: " + e.getMessage(), e);
        }
    }

    private void handleDrop() {
        for (int i = windows.size() - 1; i >= 0; i--) {
            DesktopWindow win = windows.get(i);
            if (win.bounds.contains(dragPos)) {
                try { draggingItem.file.copyTo(win.currentFolder); win.refresh(); } catch (Exception e) {}
                return;
            }
        }
        for (DesktopIcon icon : desktopIcons) {
            if (icon.isDir && icon.bounds.contains(dragPos)) {
                try { draggingItem.file.copyTo(icon.file); } catch (Exception e) {}
                return;
            }
        }
    }

    private void restoreWindow(DesktopWindow w) {
        w.minimized = false;
        focusWindow(w);
    }

    private void focusWindow(DesktopWindow w) {
        windows.remove(w);
        windows.add(w);
        activeWindow = w;
    }

    // ---------------- New Project dialog (on-demand scanning) ----------------

    // Replace the existing openNewProjectDialog() in DesktopScreen with this version
    private void openNewProjectDialog() {
        templateList.clear();

        // 1) Discover via FileSystem (user templates first, packaged next)
        List<com.nerddaygames.engine.FileSystem.TemplateDescriptor> discovered = fs.discoverTemplates();
        for (com.nerddaygames.engine.FileSystem.TemplateDescriptor d : discovered) {
            templateList.add(new TemplateEntry(d.name, d.source, d.user, d.immutable));
        }

        // Log discovered templates for debugging
        Gdx.app.log("DesktopScreen", "discoverTemplates returned " + discovered.size() + " entries");
        for (com.nerddaygames.engine.FileSystem.TemplateDescriptor d : discovered) {
            Gdx.app.log("DesktopScreen", "  - " + d.name + " user=" + d.user + " immutable=" + d.immutable + " source=" + (d.source != null ? d.source.path() : "null"));
        }

        // Guarantee at least a blank template exists locally
        boolean hasBlank = false;
        for (TemplateEntry e : templateList) if ("blank".equalsIgnoreCase(e.name)) { hasBlank = true; break; }
        if (!hasBlank) {
            FileHandle localRoot = Gdx.files.local("disk/user/templates");
            if (!localRoot.exists()) localRoot.mkdirs();
            FileHandle blank = localRoot.child("blank");
            if (!blank.exists()) blank.mkdirs();
            FileHandle starter = blank.child("starting_code.lua");
            if (!starter.exists()) {
                final String START =
                    "-- Blank Project Template\n" +
                        "function _init()\n" +
                        "  -- blank starter\n" +
                        "end\n\n" +
                        "function _update() end\nfunction _draw() cls(0) end\n";
                try { starter.writeString(START, false, "UTF-8"); } catch (Exception e) { Gdx.app.error("DesktopScreen", "Failed writing blank starter: "+e.getMessage(), e); }
            }
            templateList.add(0, new TemplateEntry("blank", blank, true, false));
        }

        // If only blank is present, do extra probing & logging to find packaged templates
        if (templateList.size() == 1 && "blank".equalsIgnoreCase(templateList.get(0).name)) {
            Gdx.app.log("DesktopScreen", "Only blank found - probing internal assets for packaged templates...");

            try {
                FileHandle sys = Gdx.files.internal("system/templates");
                Gdx.app.log("DesktopScreen", "internal(system/templates).exists=" + (sys != null && sys.exists()));
                FileHandle[] children = null;
                try { children = (sys != null) ? sys.list() : null; } catch (Exception ex) { children = null; }
                if (children != null) {
                    Gdx.app.log("DesktopScreen", "internal(system/templates).list() returned " + children.length + " entries:");
                    for (FileHandle c : children) Gdx.app.log("DesktopScreen", "  -> " + c.name() + " isDirectory=" + c.isDirectory());
                } else {
                    Gdx.app.log("DesktopScreen", "internal(system/templates).list() returned null or threw.");
                }

                // Check templates.zip fallback
                FileHandle zipHandle = Gdx.files.internal("system/templates.zip");
                Gdx.app.log("DesktopScreen", "internal(system/templates.zip).exists=" + (zipHandle != null && zipHandle.exists()));

                // Probe common template names explicitly (helps when internal.list() fails)
                String[] commonTemplates = new String[] {"platformer", "shooter", "starter", "basic"};
                for (String tplName : commonTemplates) {
                    FileHandle probe = Gdx.files.internal("system/templates/" + tplName);
                    boolean looksLike = false;
                    try {
                        FileHandle[] maybe = probe.list();
                        if (maybe != null && maybe.length > 0) looksLike = true;
                    } catch (Exception ignored) {}
                    if (!looksLike) {
                        if (probe.child("main.lua").exists() || probe.child("starting_code.lua").exists() || probe.child("config.toon").exists()) looksLike = true;
                    }
                    Gdx.app.log("DesktopScreen", "probe '" + tplName + "': exists=" + (probe != null && probe.exists()) + " looksLike=" + looksLike + " path=" + (probe != null ? probe.path() : "null"));
                    if (looksLike) {
                        // only add if not already present
                        boolean present = false;
                        for (TemplateEntry te : templateList) if (te.name.equalsIgnoreCase(tplName)) { present = true; break; }
                        if (!present) {
                            templateList.add(new TemplateEntry(tplName, probe, false, true));
                            Gdx.app.log("DesktopScreen", "Added packaged template from probe: " + tplName);
                        }
                    }
                }

                // If still just blank, attempt a classpath/JAR scan (for completeness; FileSystem already does this)
                if (templateList.size() == 1) {
                    try {
                        String prefix = "system/templates/";
                        java.net.URL dirURL = getClass().getClassLoader().getResource(prefix);
                        if (dirURL != null) Gdx.app.log("DesktopScreen", "getResource(system/templates/) -> " + dirURL.toString());
                        else Gdx.app.log("DesktopScreen", "getResource(system/templates/) returned null");
                    } catch (Exception ex) {
                        Gdx.app.log("DesktopScreen", "Classpath probe failed: " + ex.getMessage());
                    }
                }
            } catch (Exception e) {
                Gdx.app.log("DesktopScreen", "Probing internal templates failed: " + e.getMessage());
            }
        }

        // Ensure blank listed first
        for (int i = 0; i < templateList.size(); i++) {
            if ("blank".equalsIgnoreCase(templateList.get(i).name)) {
                TemplateEntry b = templateList.remove(i);
                templateList.add(0, b);
                break;
            }
        }

        selectedTemplateIndex = 0;
        templateScrollOffset = 0f;
        showNewProjectDialog = true;

        // Debug: dump final templateList
        Gdx.app.log("DesktopScreen", "Final templateList has " + templateList.size() + " entries:");
        for (TemplateEntry e : templateList) Gdx.app.log("DesktopScreen", "  * " + e.name + " user=" + e.user + " immutable=" + e.immutable + " source=" + (e.source != null ? e.source.path() : "null"));
    }

    private void drawNewProjectDialog() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        float w = 620f, h = 360f;
        float cx = (Gdx.graphics.getWidth() - w) / 2f;
        float cy = (Gdx.graphics.getHeight() - h) / 2f;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.6f);
        shapes.rect(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        shapes.setColor(0.96f, 0.96f, 0.96f, 1f);
        shapes.rect(cx, cy, w, h);
        shapes.setColor(0.18f, 0.18f, 0.18f, 1f);
        shapes.rect(cx, cy + h - 44, w, 44);
        shapes.end();

        batch.begin();
        font.setColor(Color.WHITE);
        font.draw(batch, "Create New Project", cx + 12, cy + h - 12);
        batch.end();

        batch.begin();
        font.setColor(Color.DARK_GRAY);
        font.draw(batch, "Choose a template to use as the starting project. System templates are read-only; use Import to make editable.", cx + 12, cy + h - 60);
        batch.end();

        float listX = cx + 12;
        float listY = cy + h - 110;
        float entryHeight = 28f;
        int maxVisible = 10;
        int total = templateList.size();
        int start = 0;
        int end = Math.min(total, maxVisible);

        for (int i = start; i < end; i++) {
            TemplateEntry e = templateList.get(i);
            float y = listY - ((i - start) * entryHeight);

            if (i == selectedTemplateIndex) {
                shapes.begin(ShapeRenderer.ShapeType.Filled);
                shapes.setColor(0.12f, 0.16f, 0.36f, 1f);
                shapes.rect(listX - 6, y - 20, w - 260, entryHeight + 8);
                shapes.end();
            }

            batch.begin();
            if (i == selectedTemplateIndex) font.setColor(Color.WHITE); else font.setColor(Color.DARK_GRAY);
            // Avoid emoji — use plain text so BitmapFont can render it
            String label = e.name + (e.user ? " (user)" : " (system)");
            if (e.immutable) label += " (locked)";
            font.draw(batch, label, listX, y);
            batch.end();
        }

        float btnW = 140f, btnH = 40f;
        float createX = cx + 12;
        float importX = createX + btnW + 12;
        float cancelX = cx + w - btnW - 12;
        float btnY = cy + 12;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.18f, 0.6f, 0.18f, 1f);
        shapes.rect(createX, btnY, btnW, btnH);
        shapes.setColor(0.36f, 0.58f, 0.9f, 1f);
        shapes.rect(importX, btnY, btnW, btnH);
        shapes.setColor(0.56f, 0.56f, 0.56f, 1f);
        shapes.rect(cancelX, btnY, btnW, btnH);
        shapes.end();

        batch.begin();
        font.setColor(Color.WHITE);
        font.draw(batch, "Create", createX + 44, btnY + 26);
        font.draw(batch, "Import", importX + 48, btnY + 26);
        font.draw(batch, "Cancel", cancelX + 48, btnY + 26);
        batch.end();
    }

    private boolean handleNewProjectDialogClick(int screenX, int screenY, int button) {
        if (!showNewProjectDialog) return false;
        float w = 620f, h = 360f;
        float cx = (Gdx.graphics.getWidth() - w) / 2f;
        float cy = (Gdx.graphics.getHeight() - h) / 2f;
        float y = Gdx.graphics.getHeight() - screenY;
        float x = screenX;

        if (!(x >= cx && x <= cx + w && y >= cy && y <= cy + h)) {
            showNewProjectDialog = false;
            return true;
        }

        float listX = cx + 12;
        float listY = cy + h - 110;
        float entryHeight = 28f;
        int maxVisible = 10;
        int end = Math.min(templateList.size(), maxVisible);

        for (int i = 0; i < end; i++) {
            int idx = i;
            float entryYTop = listY - (i * entryHeight) + 8;
            float entryYBottom = entryYTop - entryHeight;
            if (x >= listX - 6 && x <= cx + w - 260 && y <= entryYTop && y >= entryYBottom) {
                selectedTemplateIndex = idx;
                return true;
            }
        }

        float btnW = 140f, btnH = 40f;
        float createX = cx + 12;
        float importX = createX + btnW + 12;
        float cancelX = cx + w - btnW - 12;
        float btnY = cy + 12;

        if (x >= createX && x <= createX + btnW && y >= btnY && y <= btnY + btnH) {
            TemplateEntry sel = templateList.get(Math.max(0, Math.min(selectedTemplateIndex, templateList.size() - 1)));
            createProjectFromTemplate(sel.source, sel.name);
            showNewProjectDialog = false;
            return true;
        }

        if (x >= importX && x <= importX + btnW && y >= btnY && y <= btnY + btnH) {
            TemplateEntry sel = templateList.get(Math.max(0, Math.min(selectedTemplateIndex, templateList.size() - 1)));
            if (!sel.user && sel.source != null) {
                boolean ok = fs.copyPackagedTemplateToLocal(sel.name);
                if (ok) {
                    FileHandle local = Gdx.files.local("disk/user/templates/" + sel.name);
                    for (int i = 0; i < templateList.size(); i++) {
                        if (templateList.get(i).name.equals(sel.name)) {
                            templateList.set(i, new TemplateEntry(sel.name, local, true, false));
                            break;
                        }
                    }
                } else {
                    Gdx.app.log("DesktopScreen", "Import failed for template: " + sel.name);
                }
            }
            return true;
        }

        if (x >= cancelX && x <= cancelX + btnW && y >= btnY && y <= btnY + btnH) {
            showNewProjectDialog = false;
            return true;
        }

        return true;
    }

    @Override public void dispose() {
        batch.dispose(); shapes.dispose(); font.dispose();
        if (spriteSheet != null) spriteSheet.dispose();
        if (fallbackSheet != null) fallbackSheet.dispose();
    }

    // Interface used by DesktopScreen to request Android export (optional)
    public interface ExportBridgeProvider {
        void requestExportProject(FileHandle projectDir);
    }
}
