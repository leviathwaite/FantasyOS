-- Basic undo/redo stack.
local UndoRedo = {}
UndoRedo.__index = UndoRedo

function UndoRedo.new()
  local self = setmetatable({}, UndoRedo)
  self.past = {}
  self.future = {}
  return self
end

function UndoRedo:record(cmd)
  table.insert(self.past, cmd)
  self.future = {}
end

function UndoRedo:undo()
  local cmd = table.remove(self.past)
  if not cmd then return false end
  if cmd.undo then cmd.undo() end
  table.insert(self.future, cmd)
  return true
end

function UndoRedo:redo()
  local cmd = table.remove(self.future)
  if not cmd then return false end
  if cmd.do then cmd.do() end
  table.insert(self.past, cmd)
  return true
end

return UndoRedo
