# FantasyOS  
### A Multi-Platform Game Editor + LuaJ Runtime Built on LibGDX

FantasyOS is a modular, extensible **game creation environment** built on **LibGDX** for performance and portability.  
The system behaves like a lightweight desktop OS, where all tools and game projects are written in **LuaJ**, letting users view, edit, or replace tools and create games entirely in Lua.

---

## 🚀 Features

### **Desktop-Style Interface**
- Start-menu style lower bar  
- “My Computer” folder containing tool modules  
- Double-click to explore, open, or edit Lua-based tool files  
- Multiple open projects, each with its own task icon  
- Minimize projects to the lower bar and switch between them

### **Lua-Driven Tools**
All tools are implemented in **LuaJ** and fully editable by the user. Tools include:
- Code Editor  
- Sprite / Pixel Editor  
- SFX Tool  
- Music Tracker  
- Map / Layer Editor  
- Configuration Tool  
- “+” tab to add/import custom modules

### **Project Workflow**
- Create new projects (starter template, future templates planned)  
- Edit project assets/code via multi-tab EditorScreen  
- Drag assets (INCLUDING CODE) from one project to another  
- Add per-project or global tools  
- Hot-reload Lua tools and scripts

### **Configurable Fantasy Console Rules**
Use the Configuration Tool to set:
- Resolution  
- Color palette  
- Memory/ROM/sprite limits  
- Map sizes  
- CPU/tick budget  
- Or load presets to emulate fantasy consoles (PICO-8, TIC-80, GB, etc.)

---

## 🧩 Architecture Overview

### **LibGDX Layer (Java)**
Handles:
- Rendering + UI  
- DesktopScreen  
- EditorScreen  
- File Explorer  
- Project manager  
- LuaJ bridging (API bindings, sandbox, asset mapping)

### **LuaJ Layer (Lua)**
Handles:
- Tool logic (all editors, utilities)  
- Project game logic  
- Template scripts  
- Custom modules  
- Runtime execution  
- Live editing + reload

The user can edit **everything** in Lua, including internal tools.

---

## 📂 Proposed Folder Structure

/core
  /java
    DesktopScreen.java
    EditorScreen.java
    FileExplorer.java
    LuaBridge.java
  /lua
    /system
      boot.lua
      desktop.lua
      util.lua
    /tools
      code_editor.lua
      sprite_editor.lua
      map_editor.lua
      sfx_tool.lua
      music_tool.lua
      config_tool.lua
      tool_manifest.json
    /templates
      /starter
        main.lua
        config.lua
      /fantasy_console
        pico8.json
        tic80.json

/projects
(user projects)

---

## 🖥️ User Workflow

### **1. Desktop Screen**
- Tool modules stored under **My Computer**
- Double-click a module to open a file explorer view
- View/edit the Lua implementation of any tool

### **2. Create or Open a Project**
- New project uses a starter Lua template  
- Opens in **EditorScreen** with multiple tabs  
- Tabs can host any tool (Code, Sprite, Map, etc.)  
- Add more tools using the “+” tab

### **3. Multi-Project Editing**
- Minimize projects to the lower bar  
- Open multiple projects simultaneously  
- Drag & drop assets and code between projects

---

## 📜 Roadmap

### **Core**
- [ ] Complete desktop UI  
- [ ] Full file explorer  
- [ ] Dynamic tool loading via manifest  
- [ ] Multiple project support  
- [ ] Clipboard/drag-drop asset sharing

### **Tools**
- [ ] Improve code editor  
- [ ] Sprite editor layers  
- [ ] SFX + music enhancements  
- [ ] Tilemap editing  
- [ ] Animation tool (planned)

### **Runtime**
- [ ] Lua API for tool development  
- [ ] Project export system  
- [ ] Template selector  
- [ ] Retro console presets

---
