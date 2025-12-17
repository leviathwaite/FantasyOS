-- High-level orchestrator: coordinates submodules and exposes methods used by the UI.
local FileManager = require('system.tools.code_editor.file_manager')
local Renderer = require('system.tools.code_editor.renderer')
local Cursor = require('system.tools.code_editor.cursor')
local Input = require('system.tools.code_editor.input_handler')
local UndoRedo = require('system.tools.code_editor.undo_redo')
local Syntax = require('system.tools.code_editor.syntax_highlighter')
local Search = require('system.tools.code_editor.search_replace')
local Settings = require('system.tools.code_editor.settings')

local EditorCore = {}
EditorCore.__index = EditorCore

function EditorCore.new(params)
  local self = setmetatable({}, EditorCore)
  self.settings = Settings.load(params)
  self.files = FileManager.new(self.settings)
  self.cursor = Cursor.new()
  self.renderer = Renderer.new(self.settings)
  self.input = Input.new(self)
  self.undo = UndoRedo.new()
  self.syntax = Syntax.new(self.settings)
  self.search = Search.new()
  self.current = nil
  return self
end

function EditorCore:open(path)
  local file = self.files:open(path)
  self.current = file
  if file and file.text then
    self.syntax:tokenize(file.text)
  end
  return file
end

function EditorCore:save()
  if not self.current then return false end
  return self.files:save(self.current)
end

function EditorCore:update(dt)
  -- update cursors or other timed behavior if needed
end

function EditorCore:render()
  if not self.current then return end
  self.renderer:render(self.current, self.cursor, self.syntax)
end

function EditorCore:handle_input(event)
  return self.input:handle(event)
end

return EditorCore
