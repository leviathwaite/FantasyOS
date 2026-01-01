local UndoRedo = {}
UndoRedo.__index = UndoRedo

function UndoRedo.new()
  local u = setmetatable({}, UndoRedo)
  u.stack = {}
  u.position = 0
  return u
end

function UndoRedo:push(state)
  while #self.stack > self.position do table.remove(self.stack) end
  table.insert(self.stack, state)
  self.position = #self.stack
  -- Limit stack size to 100
  if #self.stack > 100 then
    table.remove(self.stack, 1)
    self.position = self.position - 1
  end
end

function UndoRedo:undo()
  if self.position > 1 then
    self.position = self.position - 1
    return self.stack[self.position]
  end
  return nil
end

function UndoRedo:redo()
  if self.position < #self.stack then
    self.position = self.position + 1
    return self.stack[self.position]
  end
  return nil
end

return UndoRedo
