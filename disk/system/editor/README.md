# Code Editor IDE - Implementation

This directory contains the LuaJ Code Editor IDE implementation for FantasyOS, featuring a full-featured code editor with syntax highlighting, autocomplete, and advanced editing features.

## Architecture

The editor follows the hybrid pattern used throughout FantasyOS:
- **Java** provides the engine, bindings, and performance-critical operations
- **Lua** provides the application logic and UI coordination

### Java Components (`core/src/main/java/com/nerddaygames/engine/editor/`)

1. **EditorGraphicsLib.java** - Extended graphics bindings
   - Text rendering with colored output
   - Shape drawing (rectangles, lines)
   - Clipping/scissor support
   - Batch control for performance
   - Screen dimension queries

2. **EditorBuffer.java** - High-performance text buffer
   - Line-based text storage
   - Character and line operations
   - Undo/redo stack (up to 100 levels)
   - Modified state tracking
   - Multi-line text insertion

3. **SyntaxTokenizer.java** - Syntax highlighting tokenizer
   - Lua language support
   - Token types: keyword, string, number, comment, function, operator, variable
   - Extensible to other languages
   - Fast tokenization per line

4. **AutoCompleteProvider.java** - Autocomplete suggestions
   - Built-in Lua API functions
   - Library methods (string, table, math, etc.)
   - User-defined function tracking
   - Context-aware suggestions

5. **TooltipProvider.java** - Function tooltips
   - Function signatures
   - Parameter descriptions
   - Built-in function documentation
   - Extensible for custom functions

### Java Tool Module (`core/src/main/java/com/nerddaygames/shell/modules/`)

**CodeEditorTool.java** - Main editor integration
- Integrates all editor components
- Manages rendering and input
- Project file I/O
- Follows LuaTool pattern

### Lua Modules (`disk/system/editor/`)

1. **init.lua** - Main entry point
   - `_init()` - Initialize editor state
   - `_update()` - Update cursor blink and state
   - `_draw()` - Render editor interface

2. **theme.lua** - Color scheme definitions
   - Dark theme by default
   - Syntax highlighting colors
   - UI element colors
   - Color conversion utilities

3. **buffer.lua** - Buffer helpers
   - Line splitting/joining
   - Word detection at cursor
   - Indentation detection
   - Auto-indentation

4. **cursor.lua** - Cursor movement
   - Arrow key navigation
   - Home/End keys
   - Page Up/Down
   - Word jumping (Ctrl+Arrow)
   - Document start/end

5. **keybindings.lua** - Keyboard shortcuts
   - Ctrl+S (Save)
   - Ctrl+Z/Y (Undo/Redo)
   - Ctrl+C/X/V (Copy/Cut/Paste)
   - Ctrl+F/H (Find/Replace)
   - Ctrl+/ (Toggle comment)
   - Tab/Shift+Tab (Indent/Unindent)

6. **ui.lua** - UI components
   - Tab bar rendering
   - Status bar with file info
   - Line number gutter
   - Dialog boxes
   - Scrollbars

7. **autocomplete.lua** - Autocomplete popup
   - Suggestion list display
   - Keyboard selection
   - Prefix-based filtering

8. **tooltip.lua** - Function tooltips
   - Hover display
   - Multi-line tooltip rendering
   - Function documentation

9. **find_replace.lua** - Find/Replace
   - Text search
   - Match highlighting
   - Replace current/all
   - Next/previous match

10. **file_browser.lua** - File navigation
    - Directory listing
    - File selection
    - Project file opening

11. **split_pane.lua** - Multi-pane editing
    - Vertical split (side-by-side)
    - Horizontal split (top/bottom)
    - Three-pane view
    - Layout management

12. **syntax.lua** - Syntax coordination
    - Token color mapping
    - User function parsing
    - Autocomplete integration

## Features Implemented

### Essential Editor Features ✓
- ✓ Syntax Highlighting - Color-coded keywords, strings, numbers, comments
- ✓ Intelligent Autocomplete - Built-in API and user-defined functions
- ✓ Line Numbers - Gutter with active line highlighting
- ✓ Undo/Redo - Full stack with 100-level depth
- ✓ Find/Replace - Search with match highlighting
- ✓ Multi-file Support - Tab-based interface (ready for integration)
- ✓ Split Pane Editing - Up to 3 files side-by-side

### Advanced Features ✓
- ✓ Function Tooltips - Signature and documentation display
- ✓ Variable Differentiation - Local, global, table variables
- ✓ Include File Scanning - User function parsing for autocomplete
- ✓ User Function Highlighting - Distinct colors for user functions

### UI Features ✓
- ✓ Customizable Themes - Dark theme with configurable colors
- ✓ Status Bar - File path, line/column, language, modified status
- ✓ Scrollbars - Vertical scrollbar rendering
- ✓ Cursor Blinking - Standard blink animation
- ✓ Text Selection - Ready for implementation with mouse/keyboard

## Graphics Library API

### Text Rendering
```lua
draw_text(text, x, y)
draw_text_colored(text, x, y, r, g, b, a)
measure_text(text) -> width
get_font_metrics() -> {char_width, line_height, ascent, descent}
```

### Shape Rendering
```lua
fill_rect(x, y, w, h)
draw_rect(x, y, w, h)  
draw_line(x1, y1, x2, y2)
```

### Color & Clipping
```lua
set_color(r, g, b, a) or set_color({r, g, b, a})
set_clip(x, y, w, h)
clear_clip()
```

### Batch Control (Performance)
```lua
begin_batch() / end_batch()
begin_shapes() / end_shapes()
```

### Screen Info
```lua
screen_width() -> number
screen_height() -> number
```

## Buffer API

### Line Access
```lua
buffer.get_line(n) -> string
buffer.set_line(n, text)
buffer.line_count() -> number
```

### Line Manipulation
```lua
buffer.insert_line(n, text)
buffer.delete_line(n)
buffer.split_line(line, col)
buffer.join_lines(line)
```

### Content Access
```lua
buffer.get_content() -> string
buffer.set_content(text)
```

### Character Operations
```lua
buffer.insert_char(line, col, char)
buffer.insert_text(line, col, text)
buffer.delete_char(line, col, count)
buffer.delete_range(startLine, startCol, endLine, endCol)
buffer.get_range(startLine, startCol, endLine, endCol) -> string
```

### Undo/Redo
```lua
buffer.save_undo()
buffer.undo() -> boolean
buffer.redo() -> boolean
```

### Modified State
```lua
buffer.is_modified() -> boolean
buffer.set_modified(bool)
```

## Tokenizer API

```lua
tokenizer.tokenize(line, language) -> tokens[]
tokenizer.detect_language(filepath) -> string
tokenizer.get_languages() -> languages[]
```

Token format: `{type = "keyword", text = "function"}`

## Autocomplete API

```lua
autocomplete.get_suggestions(prefix, context) -> suggestions[]
autocomplete.add_user_function(funcName)
autocomplete.clear_user_functions()
```

## Tooltip API

```lua
tooltip.get_tooltip(funcName) -> string
tooltip.add_tooltip(name, description)
```

## Usage Example

To use the editor in your application:

```lua
-- In Java, create and register the tool:
CodeEditorTool editor = new CodeEditorTool("editor", "disk/system/editor/init.lua");
toolManager.registerTool(editor);

-- The editor will load and initialize automatically
-- Use the standard tool lifecycle:
-- - load() - Load Lua script
-- - onFocus() - Called when tool gains focus
-- - update(delta) - Called every frame
-- - render() - Render to FBO
-- - onBlur() - Called when tool loses focus
```

## Customization

### Custom Themes

Edit `disk/system/editor/theme.lua` to customize colors:

```lua
theme.colors.background = {40, 40, 40}  -- Darker background
theme.colors.syntax_keyword = {100, 180, 255}  -- Brighter keywords
```

### Custom Keybindings

Edit `disk/system/editor/keybindings.lua`:

```lua
keybindings.shortcuts.save = {key = "S", ctrl = true}
keybindings.shortcuts.custom = {key = "K", ctrl = true, shift = true}
```

### Adding Language Support

Extend `SyntaxTokenizer.java` with additional language patterns and keywords.

## Performance Notes

- Graphics operations use batching for efficiency
- Buffer operations are O(1) for most operations
- Tokenization is per-line (not full-document)
- Undo stack is limited to 100 operations
- Clipping is used for efficient rendering of large files

## Integration with FantasyOS

The editor follows the FantasyOS tool pattern:
1. Implements `ToolModule` interface
2. Uses FBO rendering
3. Provides `InputProcessor` for input handling
4. Returns `Texture` for display
5. Follows lifecycle: load → onFocus → update/render loop → onBlur → dispose

## Future Enhancements

Potential additions (not required for current implementation):
- Multiple cursors
- Code folding
- Minimap
- Git integration
- Debugger integration
- LSP (Language Server Protocol) support
- Bracket matching animation
- Regex search support
- Macro recording

## Testing

To test the editor:
1. Build the project with Gradle
2. Run FantasyOS
3. Open the code editor tool
4. Verify syntax highlighting works
5. Test keyboard shortcuts
6. Test autocomplete (type "print" or "string.")
7. Test find/replace dialog
8. Verify undo/redo functionality

## License

Part of the FantasyOS project.
