# User Project Templates

Place custom project templates in this directory to use when creating new projects.

## Structure

Each template should be a directory containing:

- `main.lua` - Entry point for the project
- `config.toon` - Project configuration (optional)
- Other assets as needed (sprites, sounds, etc.)

## Example

Create a platformer template:

```
disk/user/templates/platformer/
  ├── main.lua
  ├── config.toon
  ├── sprites.png
  └── README.md
```

## Using Templates

Templates in this directory will be available when creating new projects in FantasyOS.

The system will copy all files from the template directory to the new project directory.
