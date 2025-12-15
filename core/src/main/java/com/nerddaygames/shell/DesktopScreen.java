package com.nerddaygames.shell;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap; // ADDED
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack; // Ensure this is imported
import com.nerddaygames.Main;

import java.util.ArrayList;
import java.util.List;

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
    private FileHandle systemRoot;

    // STATE
    private List<DesktopIcon> desktopIcons = new ArrayList<>();
    private List<DesktopWindow> windows = new ArrayList<>();
    private DesktopWindow activeWindow = null;

    // DRAG AND DROP STATE
    private DesktopItem draggingItem = null;
    private Vector2 dragPos = new Vector2();
    private Vector2 dragOffset = new Vector2();

    // CONTEXT MENU
    private boolean showMenu = false;
    private float menuX, menuY;
    private FileHandle menuTarget = null;

    public DesktopScreen(Main game) {
        this.game = game;
        this.batch = new SpriteBatch();
        this.shapes = new ShapeRenderer();
        this.font = new BitmapFont();

        loadAssets();

        this.diskRoot = Gdx.files.local("disk/");
        if (!diskRoot.exists()) diskRoot.mkdirs();

        if (Gdx.app.getType() == Application.ApplicationType.Android) {
            this.systemRoot = Gdx.files.external("/");
        } else {
            this.systemRoot = Gdx.files.absolute(System.getProperty("user.home"));
        }

        refreshDesktop();
        Gdx.input.setInputProcessor(new DesktopInput());
    }

    private void loadAssets() {
        TextureRegion[][] tmp = null;

        // 1. Try to load real sprites
        if (Gdx.files.internal("sprites.png").exists()) {
            try {
                spriteSheet = new Texture(Gdx.files.internal("sprites.png"));
                tmp = TextureRegion.split(spriteSheet, 8, 8);
            } catch (Exception e) {
                System.err.println("Sprite load failed: " + e.getMessage());
            }
        }

        // 2. Generate fallback texture if needed
        if (fallbackSheet == null) {
            Pixmap p = new Pixmap(8, 8, Pixmap.Format.RGBA8888);
            p.setColor(Color.WHITE); p.fill();
            fallbackSheet = new Texture(p);
            p.dispose();
        }

        // 3. Safely assign icons (Use fallback if index out of bounds)
        iconFile     = getSafeIcon(tmp, 0, 0, Color.GRAY);
        iconFolder   = getSafeIcon(tmp, 0, 1, Color.YELLOW);
        iconProject  = getSafeIcon(tmp, 0, 2, Color.ORANGE);
        iconTrash    = getSafeIcon(tmp, 0, 3, Color.RED);
        iconComputer = getSafeIcon(tmp, 0, 4, Color.CYAN);
    }

    // Helper to prevent ArrayIndexOutOfBoundsException
    private TextureRegion getSafeIcon(TextureRegion[][] split, int row, int col, Color tint) {
        if (split != null && row < split.length && col < split[row].length) {
            return split[row][col];
        }
        // Return colored square if sprite missing
        TextureRegion r = new TextureRegion(fallbackSheet);
        // We can't tint the region directly here without SpriteBatch setColor,
        // so we just return the white fallback.
        // In a polished app, you'd generate specific colored pixmaps.
        return r;
    }

    private void refreshDesktop() {
        desktopIcons.clear();
        desktopIcons.add(new DesktopIcon("My Computer", systemRoot, iconComputer, 20, Gdx.graphics.getHeight() - 80, true));
        desktopIcons.add(new DesktopIcon("Trash", diskRoot.child(".trash"), iconTrash, Gdx.graphics.getWidth() - 80, 20, true));

        FileHandle[] files = diskRoot.list();
        int x = 20; int y = Gdx.graphics.getHeight() - 180;

        for (FileHandle f : files) {
            if (f.name().startsWith(".")) continue;
            if (f.isDirectory()) {
                desktopIcons.add(new DesktopIcon(f.name(), f, iconProject, x, y, true));
                y -= 100;
                if (y < 100) { y = Gdx.graphics.getHeight() - 180; x += 90; }
            }
        }
    }

    private void createProject() {
        String base = "Project";
        int i = 1;
        while(diskRoot.child(base+"_"+String.format("%04d",i)).exists()) i++;
        FileHandle projectDir = diskRoot.child(base+"_"+String.format("%04d",i));
        projectDir.mkdirs();
        
        // Use blank template as default
        FileHandle templateDir = Gdx.files.internal("assets/system/templates/blank");
        if (templateDir.exists() && templateDir.isDirectory()) {
            // Copy template files to new project
            copyTemplateFiles(templateDir, projectDir);
        } else {
            // Fallback: create basic main.lua if template not found
            projectDir.child("main.lua").writeString(
                "function _init()\n" +
                "  log('New')\n" +
                "end\n\n" +
                "function _draw()\n" +
                "  cls(1)\n" +
                "  print('Hi',50,50,7)\n" +
                "end", false);
        }
        
        refreshDesktop();
    }
    
    private void copyTemplateFiles(FileHandle source, FileHandle dest) {
        if (source.isDirectory()) {
            if (!dest.exists()) {
                dest.mkdirs();
            }
            FileHandle[] children = source.list();
            for (FileHandle child : children) {
                FileHandle destChild = dest.child(child.name());
                copyTemplateFiles(child, destChild);
            }
        } else {
            try {
                source.copyTo(dest);
            } catch (Exception e) {
                Gdx.app.error("DesktopScreen", "Failed to copy template file: " + e.getMessage());
            }
        }
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.2f, 0.3f, 0.4f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        batch.begin();
        for (DesktopIcon icon : desktopIcons) icon.render(batch, font);
        batch.end();

        for (DesktopWindow win : windows) win.render(shapes, batch, font);

        if (draggingItem != null) {
            batch.begin();
            batch.setColor(1, 1, 1, 0.7f);
            if (draggingItem.icon != null) batch.draw(draggingItem.icon, dragPos.x, dragPos.y, 48, 48);
            batch.setColor(1, 1, 1, 1);
            batch.end();
        }

        if (showMenu) drawMenu();
    }

    private void drawMenu() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0, 0, 0, 0.8f);
        shapes.rect(menuX, menuY - 80, 160, 80);
        shapes.end();

        batch.begin();
        float tx = menuX + 10; float ty = menuY - 20;
        if (menuTarget == null) {
            font.draw(batch, "New Project", tx, ty);
            font.draw(batch, "Refresh", tx, ty - 25);
        } else {
            font.draw(batch, "Open", tx, ty);
            font.draw(batch, "Delete", tx, ty - 25);
            font.draw(batch, "Copy", tx, ty - 50);
        }
        batch.end();
    }

    private class DesktopInput extends InputAdapter {
        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            float y = Gdx.graphics.getHeight() - screenY;
            float x = screenX;

            if (showMenu) { handleMenuClick(x, y); return true; }

            for (int i = windows.size() - 1; i >= 0; i--) {
                DesktopWindow win = windows.get(i);
                if (win.touchDown(x, y)) {
                    windows.remove(i); windows.add(win);
                    activeWindow = win;
                    return true;
                }
            }

            for (DesktopIcon icon : desktopIcons) {
                if (icon.bounds.contains(x, y)) {
                    if (button == Input.Buttons.RIGHT) openMenu(x, y, icon.file);
                    else {
                        if (icon.isDir && icon.file.child("main.lua").exists()) game.setScreen(new EditorScreen(game, icon.file));
                        else if (icon.isDir) openWindow(icon.file);
                    }
                    return true;
                }
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

    private void openWindow(FileHandle folder) { windows.add(new DesktopWindow(folder, 400, 300)); }
    private void openMenu(float x, float y, FileHandle target) { showMenu = true; menuX = x; menuY = y; menuTarget = target; }

    private void handleMenuClick(float x, float y) {
        showMenu = false;
        float ly = menuY - y;
        if (menuTarget == null) {
            if (ly > 0 && ly < 25) createProject();
            if (ly > 25 && ly < 50) refreshDesktop();
        } else {
            if (ly > 0 && ly < 25) {
                if (menuTarget.child("main.lua").exists()) game.setScreen(new EditorScreen(game, menuTarget));
                else openWindow(menuTarget);
            }
            if (ly > 25 && ly < 50) { menuTarget.deleteDirectory(); refreshDesktop(); }
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

    private class DesktopIcon {
        String label; FileHandle file; TextureRegion icon; Rectangle bounds; boolean isDir;
        DesktopIcon(String name, FileHandle f, TextureRegion ico, float x, float y, boolean isDir) {
            this.label = name; this.file = f; this.icon = ico; this.isDir = isDir;
            this.bounds = new Rectangle(x, y, 64, 80);
        }
        void render(SpriteBatch sb, BitmapFont fnt) {
            if (icon != null) {
                // If it's the fallback texture, tint it based on type logic inside render if needed
                // For now just draw white
                sb.draw(icon, bounds.x, bounds.y + 16, 64, 64);
            }
            fnt.draw(sb, label.length() > 10 ? label.substring(0,8)+".." : label, bounds.x, bounds.y + 10);
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

        DesktopWindow(FileHandle folder, float w, float h) {
            this.currentFolder = folder;
            this.bounds = new Rectangle(Gdx.graphics.getWidth()/2f - w/2, Gdx.graphics.getHeight()/2f - h/2, w, h);
            refresh();
        }

        void refresh() {
            items.clear();
            if (currentFolder.list() == null) return;
            FileHandle[] files = currentFolder.list();
            float x = 10, y = -10;
            for (FileHandle f : files) {
                if (f.name().startsWith(".")) continue;
                items.add(new DesktopItem(f, x, y));
                x += 60;
                if (x > bounds.width - 60) { x = 10; y -= 70; }
            }
        }

        void setPosition(float x, float y) { bounds.setPosition(x, y); }

        void render(ShapeRenderer sr, SpriteBatch sb, BitmapFont fnt) {
            sr.begin(ShapeRenderer.ShapeType.Filled);
            sr.setColor(0.9f, 0.9f, 0.9f, 1); sr.rect(bounds.x, bounds.y, bounds.width, bounds.height);
            sr.setColor(0.1f, 0.1f, 0.4f, 1); sr.rect(bounds.x, bounds.y + bounds.height - 30, bounds.width, 30);
            sr.setColor(0.8f, 0.2f, 0.2f, 1); sr.rect(bounds.x + bounds.width - 25, bounds.y + bounds.height - 25, 20, 20);
            sr.end();

            sb.begin();
            fnt.setColor(Color.WHITE); fnt.draw(sb, currentFolder.name(), bounds.x + 10, bounds.y + bounds.height - 8);

            Rectangle scissors = new Rectangle();
            Rectangle clipBounds = new Rectangle(bounds.x, bounds.y, bounds.width, bounds.height - 30);

            // NOTE: Ensure game.camera is initialized in Main.java!
            if (game.camera != null) {
                ScissorStack.calculateScissors(game.camera, sb.getTransformMatrix(), clipBounds, scissors);
                if (ScissorStack.pushScissors(scissors)) {
                    float startY = bounds.y + bounds.height - 80 + scrollY;
                    for (DesktopItem item : items) {
                        float wx = bounds.x + item.bounds.x; float wy = startY + item.bounds.y;
                        if (wy < bounds.y - 50 || wy > bounds.y + bounds.height) continue;
                        if (item.icon != null) sb.draw(item.icon, wx, wy, 48, 48);
                        fnt.setColor(Color.BLACK);
                        String n = item.file.name(); if(n.length()>8) n=n.substring(0,8);
                        fnt.draw(sb, n, wx, wy - 5);
                    }
                    sb.flush();
                    ScissorStack.popScissors();
                }
            }
            sb.end();
        }

        boolean touchDown(float x, float y) {
            if (!bounds.contains(x, y)) return false;
            if (y > bounds.y + bounds.height - 30) {
                if (x > bounds.x + bounds.width - 30) { windows.remove(this); return true; }
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

    @Override public void dispose() {
        batch.dispose(); shapes.dispose(); font.dispose();
        if (spriteSheet != null) spriteSheet.dispose();
        if (fallbackSheet != null) fallbackSheet.dispose();
    }
}
