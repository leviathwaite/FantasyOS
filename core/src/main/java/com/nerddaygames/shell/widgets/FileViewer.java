package com.nerddaygames.shell.widgets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * FileViewer - Read-only file browser widget
 * Displays a tree view of files and directories
 */
public class FileViewer {
    private FileHandle rootDir;
    private List<FileEntry> entries = new ArrayList<>();
    private int selectedIndex = -1;
    private int scrollOffset = 0;
    private Rectangle bounds;
    private final int lineHeight = 20;
    private final int indent = 15;
    
    public static class FileEntry {
        public FileHandle file;
        public int depth;
        public boolean expanded;
        public boolean isDirectory;
        
        public FileEntry(FileHandle file, int depth) {
            this.file = file;
            this.depth = depth;
            this.isDirectory = file.isDirectory();
            this.expanded = false;
        }
    }
    
    public FileViewer(FileHandle rootDir, Rectangle bounds) {
        this.rootDir = rootDir;
        this.bounds = bounds;
        refresh();
    }
    
    public void setRootDir(FileHandle rootDir) {
        this.rootDir = rootDir;
        refresh();
    }
    
    public void refresh() {
        entries.clear();
        selectedIndex = -1;
        scrollOffset = 0;
        
        if (rootDir != null && rootDir.exists()) {
            addEntry(rootDir, 0);
        }
    }
    
    private void addEntry(FileHandle file, int depth) {
        FileEntry entry = new FileEntry(file, depth);
        entries.add(entry);
        
        // Auto-expand first level
        if (depth == 0 && file.isDirectory()) {
            entry.expanded = true;
            FileHandle[] children = file.list();
            if (children != null) {
                for (FileHandle child : children) {
                    if (!child.name().startsWith(".")) {  // Skip hidden files
                        addEntry(child, depth + 1);
                    }
                }
            }
        }
    }
    
    public void toggleExpand(int index) {
        if (index < 0 || index >= entries.size()) return;
        
        FileEntry entry = entries.get(index);
        if (!entry.isDirectory) return;
        
        entry.expanded = !entry.expanded;
        
        if (entry.expanded) {
            // Add children
            FileHandle[] children = entry.file.list();
            if (children != null) {
                List<FileEntry> newEntries = new ArrayList<>();
                for (FileHandle child : children) {
                    if (!child.name().startsWith(".")) {
                        newEntries.add(new FileEntry(child, entry.depth + 1));
                    }
                }
                entries.addAll(index + 1, newEntries);
            }
        } else {
            // Remove children
            int removeCount = 0;
            for (int i = index + 1; i < entries.size(); i++) {
                if (entries.get(i).depth <= entry.depth) break;
                removeCount++;
            }
            for (int i = 0; i < removeCount; i++) {
                entries.remove(index + 1);
            }
        }
    }
    
    public void render(ShapeRenderer shapes, SpriteBatch batch, BitmapFont font) {
        // Background
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.1f, 0.1f, 0.15f, 1f);
        shapes.rect(bounds.x, bounds.y, bounds.width, bounds.height);
        shapes.end();
        
        // Border
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(0.3f, 0.3f, 0.4f, 1f);
        shapes.rect(bounds.x, bounds.y, bounds.width, bounds.height);
        shapes.end();
        
        // Entries
        batch.begin();
        float y = bounds.y + bounds.height - 25;
        int visibleLines = (int)(bounds.height / lineHeight) - 1;
        int startIndex = scrollOffset;
        int endIndex = Math.min(entries.size(), startIndex + visibleLines);
        
        for (int i = startIndex; i < endIndex; i++) {
            FileEntry entry = entries.get(i);
            float x = bounds.x + 10 + (entry.depth * indent);
            
            // Selection highlight
            if (i == selectedIndex) {
                shapes.begin(ShapeRenderer.ShapeType.Filled);
                shapes.setColor(0.2f, 0.3f, 0.5f, 1f);
                shapes.rect(bounds.x, y - 2, bounds.width, lineHeight);
                shapes.end();
                batch.begin();
            }
            
            // Icon
            String icon = entry.isDirectory ? (entry.expanded ? "▼" : "▶") : "●";
            font.setColor(entry.isDirectory ? Color.YELLOW : Color.WHITE);
            font.draw(batch, icon, x, y + 12);
            
            // Name
            font.setColor(entry.isDirectory ? Color.CYAN : Color.LIGHT_GRAY);
            font.draw(batch, entry.file.name(), x + 15, y + 12);
            
            y -= lineHeight;
        }
        batch.end();
        
        // Scroll indicator
        if (entries.size() > visibleLines) {
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(0.4f, 0.4f, 0.5f, 1f);
            
            float scrollbarHeight = bounds.height - 10;
            float thumbHeight = (visibleLines / (float)entries.size()) * scrollbarHeight;
            float thumbY = bounds.y + 5 + ((scrollOffset / (float)(entries.size() - visibleLines)) * (scrollbarHeight - thumbHeight));
            
            shapes.rect(bounds.x + bounds.width - 10, thumbY, 5, thumbHeight);
            shapes.end();
        }
    }
    
    public boolean handleClick(float x, float y) {
        if (!bounds.contains(x, y)) return false;
        
        int visibleLines = (int)(bounds.height / lineHeight) - 1;
        float relY = (bounds.y + bounds.height - y);
        int clickedLine = (int)(relY / lineHeight);
        int index = scrollOffset + clickedLine;
        
        if (index >= 0 && index < entries.size()) {
            if (index == selectedIndex) {
                // Double-click: toggle expand
                toggleExpand(index);
            } else {
                selectedIndex = index;
            }
            return true;
        }
        
        return false;
    }
    
    public void scroll(int delta) {
        int visibleLines = (int)(bounds.height / lineHeight) - 1;
        int maxScroll = Math.max(0, entries.size() - visibleLines);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + delta));
    }
    
    public FileHandle getSelectedFile() {
        if (selectedIndex >= 0 && selectedIndex < entries.size()) {
            return entries.get(selectedIndex).file;
        }
        return null;
    }
    
    public String getSelectedFilePath() {
        FileHandle file = getSelectedFile();
        return file != null ? file.path() : null;
    }
    
    public String getSelectedFileContent() {
        FileHandle file = getSelectedFile();
        if (file != null && !file.isDirectory()) {
            try {
                return file.readString("UTF-8");
            } catch (Exception e) {
                return "Error reading file: " + e.getMessage();
            }
        }
        return null;
    }
}
