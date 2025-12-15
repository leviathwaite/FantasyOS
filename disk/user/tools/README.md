# User Tool Overrides

Place custom tool scripts in this directory to override the default system tools.

## How It Works

When FantasyOS loads a tool, it searches in this order:

1. **User Override**: `disk/user/tools/{tool_name}.lua` (this directory)
2. **System Default**: `assets/system/tools/{tool_name}.lua` (built-in)

If a file exists in this directory with the same name as a system tool, your version will be used instead.

## Available Tools

You can override any of these tools:

- `code.lua` - Code editor
- `sprite.lua` - Sprite editor
- `sfx.lua` - Sound effects editor
- `music.lua` - Music editor
- `map.lua` - Map editor
- `input.lua` - Input configuration
- `config.lua` - Project configuration

## Example

To create a custom code editor:

1. Copy `/assets/system/tools/code.lua` to this directory
2. Modify it as needed
3. Restart FantasyOS - your version will be loaded automatically

## Resetting to Default

To reset a tool to its default version:

1. Delete (or rename) your custom version from this directory
2. Restart FantasyOS

## API Version

Tools can specify an API version to ensure compatibility:

```lua
local MyTool = {
  _api_version = "1.0"
}
```

Future versions of FantasyOS will check this to warn about compatibility issues.
