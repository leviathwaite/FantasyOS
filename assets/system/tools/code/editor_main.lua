-- editor_main.lua
local config = require("system/tools/code/config")
local io_bind = require("system/tools/code/io")
local buffer = require("system/tools/code/buffer")
local syntax = require("system/tools/code/syntax")
local controls = require("system/tools/code/controls")
local input = require("system/tools/code/input")
local renderer = require("system/tools/code/renderer")

local CodeEditor = {}

-- Expose shared modules/state to submodules
local state = {
  config = config,
  buffer = buffer,
  syntax = syntax,
  controls = controls,
  io = io_bind,
  input = input,
  renderer = renderer
}

-- Initialize submodules that require the shared state
buffer.init(state)
syntax.init(state)
controls.init(state)
input.init(state)
renderer.init(state)

-- Public API
function CodeEditor.open(path)
  -- Use io.read to get contents, then open tab via buffer
  local ok, content = pcall(function() return io_bind.read(path) end)
  if ok and content then
    buffer.open_tab(path, content)
  else
    buffer.open_tab(path or "main.lua", "-- New file\n")
  end
  return true
end

function CodeEditor.open_tab(path, content) buffer.open_tab(path, content) end
function CodeEditor.load_file(path) return io_bind.read(path) end
function CodeEditor.save_file(path, content) return io_bind.save(path, content) end
function CodeEditor.import_dialog() return io_bind.import_dialog() end
function CodeEditor.run_project_file(path) return io_bind.run(path) end
function CodeEditor.set_font_size(px) return io_bind.set_editor_font_size(px) end
function CodeEditor.toast(msg, secs) io_bind.toast(msg, secs) end

-- Runtime callbacks expected by host: _update and _draw
function CodeEditor._update(win_x, win_y, win_w, win_h)
  -- delegate to input module which coordinates keyboard / mouse and buffer changes
  input.update(win_x, win_y, win_w, win_h)
end

function CodeEditor._draw(win_x, win_y, win_w, win_h)
  renderer.draw(win_x, win_y, win_w, win_h)
end

-- Provide legacy global bindings so EditorScreen's fallback calls work
_G.editor = CodeEditor
_G.load_file = function(p) return CodeEditor.load_file(p) end
_G.save_file = function(p, c) return CodeEditor.save_file(p, c) end
_G.import_file_dialog = function() return CodeEditor.import_dialog() end
_G.run_project = function(p) return CodeEditor.run_project_file(p) end
_G.set_editor_font_size = function(px) return CodeEditor.set_font_size(px) end
_G.toast = function(m, s) return CodeEditor.toast(m, s) end

return CodeEditor
