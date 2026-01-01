-- Cursor and selection logic: everything about indices, moves, and selections.
local Cursor = {}
Cursor.__index = Cursor

function Cursor.new()
  local self = setmetatable({}, Cursor)
  self.line = 1
  self.col = 1
  self.sel_start = nil
  self.sel_end = nil
  return self
end

function Cursor:get_position()
  return { line = self.line, col = self.col }
end

function Cursor:set_position(line, col)
  self.line = math.max(1, line or self.line)
  self.col = math.max(1, col or self.col)
end

function Cursor:move_left()
  if self.col > 1 then
    self.col = self.col - 1
  else
    self.line = math.max(1, self.line - 1)
  end
end

function Cursor:move_right()
  self.col = self.col + 1
end

function Cursor:start_selection()
  self.sel_start = { line = self.line, col = self.col }
  self.sel_end = nil
end

function Cursor:update_selection_to_current()
  self.sel_end = { line = self.line, col = self.col }
end

function Cursor:clear_selection()
  self.sel_start = nil
  self.sel_end = nil
end

return Cursor
