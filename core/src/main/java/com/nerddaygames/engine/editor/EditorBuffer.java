package com.nerddaygames.engine.editor;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * EditorBuffer - High-performance text buffer with undo/redo support
 * Manages text content as a list of lines with efficient operations
 */
public class EditorBuffer {
    private final Globals globals;
    private List<String> lines;
    private boolean modified;
    private Stack<BufferState> undoStack;
    private Stack<BufferState> redoStack;
    private static final int MAX_UNDO_DEPTH = 100;
    
    private static class BufferState {
        List<String> lines;
        boolean modified;
        
        BufferState(List<String> lines, boolean modified) {
            this.lines = new ArrayList<>(lines);
            this.modified = modified;
        }
    }
    
    public EditorBuffer(Globals globals) {
        this.globals = globals;
        this.lines = new ArrayList<>();
        this.lines.add("");
        this.modified = false;
        this.undoStack = new Stack<>();
        this.redoStack = new Stack<>();
    }
    
    public void register() {
        LuaTable bufferTable = new LuaTable();
        
        // Line access
        bufferTable.set("get_line", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                int n = arg.checkint() - 1; // Convert to 0-based
                if (n >= 0 && n < lines.size()) {
                    return LuaValue.valueOf(lines.get(n));
                }
                return LuaValue.NIL;
            }
        });
        
        bufferTable.set("set_line", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue nArg, LuaValue textArg) {
                int n = nArg.checkint() - 1; // Convert to 0-based
                String text = textArg.checkjstring();
                if (n >= 0 && n < lines.size()) {
                    lines.set(n, text);
                    modified = true;
                    return LuaValue.TRUE;
                }
                return LuaValue.FALSE;
            }
        });
        
        // Line manipulation
        bufferTable.set("insert_line", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue nArg, LuaValue textArg) {
                int n = nArg.checkint() - 1; // Convert to 0-based
                String text = textArg.checkjstring();
                if (n >= 0 && n <= lines.size()) {
                    lines.add(n, text);
                    modified = true;
                    return LuaValue.TRUE;
                }
                return LuaValue.FALSE;
            }
        });
        
        bufferTable.set("delete_line", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                int n = arg.checkint() - 1; // Convert to 0-based
                if (n >= 0 && n < lines.size() && lines.size() > 1) {
                    lines.remove(n);
                    modified = true;
                    return LuaValue.TRUE;
                }
                return LuaValue.FALSE;
            }
        });
        
        bufferTable.set("line_count", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return LuaValue.valueOf(lines.size());
            }
        });
        
        // Full content access
        bufferTable.set("get_content", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return LuaValue.valueOf(String.join("\n", lines));
            }
        });
        
        bufferTable.set("set_content", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                String content = arg.checkjstring();
                lines.clear();
                if (content.isEmpty()) {
                    lines.add("");
                } else {
                    String[] splitLines = content.split("\n", -1);
                    for (String line : splitLines) {
                        lines.add(line);
                    }
                }
                modified = true;
                return LuaValue.NONE;
            }
        });
        
        // Character operations
        bufferTable.set("insert_char", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int line = args.checkint(1) - 1;
                int col = args.checkint(2);
                String ch = args.checkjstring(3);
                
                if (line >= 0 && line < lines.size()) {
                    String current = lines.get(line);
                    if (col >= 0 && col <= current.length()) {
                        String newLine = current.substring(0, col) + ch + current.substring(col);
                        lines.set(line, newLine);
                        modified = true;
                        return LuaValue.TRUE;
                    }
                }
                return LuaValue.FALSE;
            }
        });
        
        bufferTable.set("insert_text", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int line = args.checkint(1) - 1;
                int col = args.checkint(2);
                String text = args.checkjstring(3);
                
                if (line >= 0 && line < lines.size()) {
                    String current = lines.get(line);
                    if (col >= 0 && col <= current.length()) {
                        // Handle multi-line insertion
                        if (text.contains("\n")) {
                            String[] textLines = text.split("\n", -1);
                            String beforeInsert = current.substring(0, col);
                            String afterInsert = current.substring(col);
                            
                            // First line
                            lines.set(line, beforeInsert + textLines[0]);
                            
                            // Middle lines
                            for (int i = 1; i < textLines.length - 1; i++) {
                                lines.add(line + i, textLines[i]);
                            }
                            
                            // Last line
                            if (textLines.length > 1) {
                                lines.add(line + textLines.length - 1, textLines[textLines.length - 1] + afterInsert);
                            }
                        } else {
                            String newLine = current.substring(0, col) + text + current.substring(col);
                            lines.set(line, newLine);
                        }
                        modified = true;
                        return LuaValue.TRUE;
                    }
                }
                return LuaValue.FALSE;
            }
        });
        
        bufferTable.set("delete_char", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int line = args.checkint(1) - 1;
                int col = args.checkint(2);
                int count = args.optint(3, 1);
                
                if (line >= 0 && line < lines.size()) {
                    String current = lines.get(line);
                    if (col >= 0 && col < current.length()) {
                        int endCol = Math.min(col + count, current.length());
                        String newLine = current.substring(0, col) + current.substring(endCol);
                        lines.set(line, newLine);
                        modified = true;
                        return LuaValue.TRUE;
                    }
                }
                return LuaValue.FALSE;
            }
        });
        
        bufferTable.set("delete_range", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int startLine = args.checkint(1) - 1;
                int startCol = args.checkint(2);
                int endLine = args.checkint(3) - 1;
                int endCol = args.checkint(4);
                
                if (startLine >= 0 && startLine < lines.size() && endLine >= 0 && endLine < lines.size()) {
                    if (startLine == endLine) {
                        // Single line deletion
                        String current = lines.get(startLine);
                        if (startCol >= 0 && startCol <= current.length() && endCol >= startCol && endCol <= current.length()) {
                            String newLine = current.substring(0, startCol) + current.substring(endCol);
                            lines.set(startLine, newLine);
                            modified = true;
                            return LuaValue.TRUE;
                        }
                    } else {
                        // Multi-line deletion
                        String firstLine = lines.get(startLine);
                        String lastLine = lines.get(endLine);
                        String newLine = firstLine.substring(0, startCol) + lastLine.substring(endCol);
                        
                        lines.set(startLine, newLine);
                        for (int i = endLine; i > startLine; i--) {
                            if (i < lines.size()) {
                                lines.remove(i);
                            }
                        }
                        modified = true;
                        return LuaValue.TRUE;
                    }
                }
                return LuaValue.FALSE;
            }
        });
        
        // Line operations
        bufferTable.set("split_line", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue lineArg, LuaValue colArg) {
                int line = lineArg.checkint() - 1;
                int col = colArg.checkint();
                
                if (line >= 0 && line < lines.size()) {
                    String current = lines.get(line);
                    if (col >= 0 && col <= current.length()) {
                        String before = current.substring(0, col);
                        String after = current.substring(col);
                        lines.set(line, before);
                        lines.add(line + 1, after);
                        modified = true;
                        return LuaValue.TRUE;
                    }
                }
                return LuaValue.FALSE;
            }
        });
        
        bufferTable.set("join_lines", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                int line = arg.checkint() - 1;
                
                if (line >= 0 && line < lines.size() - 1) {
                    String current = lines.get(line);
                    String next = lines.get(line + 1);
                    lines.set(line, current + next);
                    lines.remove(line + 1);
                    modified = true;
                    return LuaValue.TRUE;
                }
                return LuaValue.FALSE;
            }
        });
        
        // Range operations
        bufferTable.set("get_range", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int startLine = args.checkint(1) - 1;
                int startCol = args.checkint(2);
                int endLine = args.checkint(3) - 1;
                int endCol = args.checkint(4);
                
                if (startLine >= 0 && startLine < lines.size() && endLine >= 0 && endLine < lines.size()) {
                    if (startLine == endLine) {
                        String line = lines.get(startLine);
                        if (startCol >= 0 && startCol <= line.length() && endCol >= startCol && endCol <= line.length()) {
                            return LuaValue.valueOf(line.substring(startCol, endCol));
                        }
                    } else {
                        StringBuilder result = new StringBuilder();
                        String firstLine = lines.get(startLine);
                        result.append(firstLine.substring(startCol)).append("\n");
                        
                        for (int i = startLine + 1; i < endLine; i++) {
                            result.append(lines.get(i)).append("\n");
                        }
                        
                        String lastLine = lines.get(endLine);
                        result.append(lastLine.substring(0, endCol));
                        
                        return LuaValue.valueOf(result.toString());
                    }
                }
                return LuaValue.NIL;
            }
        });
        
        // Undo/Redo
        bufferTable.set("save_undo", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                undoStack.push(new BufferState(lines, modified));
                redoStack.clear();
                
                // Limit stack size
                if (undoStack.size() > MAX_UNDO_DEPTH) {
                    undoStack.remove(0);
                }
                return LuaValue.NONE;
            }
        });
        
        bufferTable.set("undo", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                if (!undoStack.isEmpty()) {
                    redoStack.push(new BufferState(lines, modified));
                    BufferState state = undoStack.pop();
                    lines = new ArrayList<>(state.lines);
                    modified = state.modified;
                    return LuaValue.TRUE;
                }
                return LuaValue.FALSE;
            }
        });
        
        bufferTable.set("redo", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                if (!redoStack.isEmpty()) {
                    undoStack.push(new BufferState(lines, modified));
                    BufferState state = redoStack.pop();
                    lines = new ArrayList<>(state.lines);
                    modified = state.modified;
                    return LuaValue.TRUE;
                }
                return LuaValue.FALSE;
            }
        });
        
        // Modified state
        bufferTable.set("is_modified", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return LuaValue.valueOf(modified);
            }
        });
        
        bufferTable.set("set_modified", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                modified = arg.checkboolean();
                return LuaValue.NONE;
            }
        });
        
        globals.set("buffer", bufferTable);
    }
}
