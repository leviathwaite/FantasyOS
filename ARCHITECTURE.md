# FantasyOS System Architecture

This document describes the enhanced FantasyOS architecture with tool overrides, templates, and configuration management.

## Directory Structure

```
FantasyOS/
├── assets/system/          # Read-only system files
│   ├── tools/             # Default editor tools
│   │   ├── code.lua       # Code editor
│   │   ├── sprite.lua     # Sprite editor
│   │   ├── config.lua     # Configuration editor
│   │   └── reference.lua  # Reference file browser
│   ├── templates/         # Project templates
│   │   ├── blank/        # Minimal template
│   │   ├── platformer/   # Platformer game template
│   │   └── shooter/      # Shooter game template
│   └── converters/       # Format converters
│       └── toon_to_json.lua  # TOON to JSON converter
│
├── disk/                  # User writable area
│   ├── user/             # User customizations
│   │   ├── tools/        # Tool overrides
│   │   ├── templates/    # Custom templates
│   │   └── wallpaper.lua # Desktop live wallpaper
│   └── Project_XXXX/     # User projects
│       ├── main.lua
│       ├── config.toon   # Human-editable config
│       └── config.json   # Auto-generated from TOON
```

## Tool Fallback System

When loading a tool, FantasyOS searches in this order:

1. **User Override**: `disk/user/tools/{tool_name}.lua`
2. **System Default**: `assets/system/tools/{tool_name}.lua`

This allows users to customize any tool while maintaining safe defaults.

### Available Tools

- **code.lua** - Full-featured code editor with tabs and syntax highlighting
- **sprite.lua** - Sprite/graphics editor
- **sfx.lua** - Sound effects editor
- **music.lua** - Music tracker
- **map.lua** - Tile map editor
- **input.lua** - Input configuration
- **config.lua** - Project configuration editor (TOON format)
- **reference.lua** - Reference file browser for code reuse

## Configuration System

Projects use TOON format for human-readable configuration:

### TOON Format Example (config.toon)

```toon
# Project Configuration

name: My Game
description: A fantasy game

resolution:
  width: 240
  height: 136

palette: pico8

memory:
  size: 65536
  banks: 8

input:
  enabled: true
```

### Features

- **Human-readable**: No quotes, brackets, or commas needed
- **Comments**: Use `#` for comments
- **Indentation-based**: Like Python/YAML
- **Auto-conversion**: Saves as both `.toon` (editable) and `.json` (machine-readable)

### Using Configs in Code

```lua
-- Config is auto-loaded as JSON
local config = require("config")
print(config.name)  -- "My Game"
print(config.resolution.width)  -- 240
```

## Template System

### Creating New Projects

When you create a new project, FantasyOS copies files from a template:

1. Default template is `assets/system/templates/blank/`
2. Templates include:
   - `main.lua` - Entry point
   - `config.toon` - Configuration
   - Other assets as needed

### Built-in Templates

- **blank** - Minimal starting point
- **platformer** - Physics-based platformer with gravity/jumping
- **shooter** - Top-down shooter with enemies/scoring

### Creating Custom Templates

1. Create directory in `disk/user/templates/{template_name}/`
2. Add your template files
3. Templates automatically available for new projects

## Live Wallpaper

Place an animated background script in `disk/user/wallpaper.lua`:

```lua
function _init()
  -- Initialize animation
end

function _update()
  -- Update animation state
end

function _draw()
  -- Draw animated background
  cls(1)
  -- Your drawing code here
end
```

The wallpaper runs in a sandboxed VM with:
- No file system access
- Full sprite/drawing API
- 60 FPS updates

## Reference File Browser

The Reference tool (`reference.lua`) allows you to:

1. Browse files from any project
2. Preview file contents with syntax highlighting
3. Copy file paths with Ctrl+C
4. Reuse code across projects

### Usage

1. Open Reference tab in editor
2. Navigate project tree on left
3. Click file to preview on right
4. Use Ctrl+C to copy path
5. Reference the code in your project

## Customization Examples

### Override Code Editor

1. Copy `/assets/system/tools/code.lua` to `/disk/user/tools/code.lua`
2. Modify as needed (colors, shortcuts, features)
3. Restart FantasyOS - your version loads automatically

### Add Custom Template

1. Create `/disk/user/templates/rpg/`
2. Add `main.lua` with RPG starter code
3. Add `config.toon` with RPG-specific settings
4. Template available when creating projects

## API Version Compatibility

Tools can specify API version for compatibility checking:

```lua
local MyTool = {
  _api_version = "1.0"
}

function _init()
  -- Tool initialization
end

return MyTool
```

Future FantasyOS versions will check this to warn about incompatibilities.

## Security Notes

- User tools run in same VM as system tools (full access)
- Wallpapers run in sandboxed VM (limited access)
- Config files are data-only (no code execution)
- Projects can only access their own directory

## Development Workflow

### Typical Project Lifecycle

1. **Create** - Desktop → Right-click → New Project
2. **Configure** - Config tab → Edit config.toon → Ctrl+S
3. **Code** - Code tab → Edit main.lua → Ctrl+S
4. **Reference** - Reference tab → Browse other projects
5. **Run** - Click Run button or press Ctrl+R
6. **Test** - Game runs in isolated environment
7. **Iterate** - Return to editor, make changes, repeat

### Multi-Project Workflow

1. Keep reference browser open to view library code
2. Copy reusable functions to your project
3. Use templates for consistent project structure
4. Share custom tools across all projects

## Future Enhancements

See the implementation roadmap for planned features:

- Phase 3: Enhanced file browser with copy/paste
- Phase 4: More wallpaper customization options
- Phase 5: Resizable windows for multi-project editing

## Troubleshooting

### Tool Not Loading

- Check `/disk/user/tools/` for name conflicts
- Verify Lua syntax (errors prevent loading)
- Check console logs for error messages

### Config Not Saving

- Ensure `config.lua` tool is active
- Check file permissions on project directory
- Verify TOON syntax (invalid format prevents save)

### Template Not Appearing

- Ensure template directory has `main.lua`
- Check template is in correct directory
- Restart FantasyOS to refresh template list
