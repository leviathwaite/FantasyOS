-- Thin renderer abstraction. Keep platform-specific drawing behind a small adapter.
local Renderer = {}
Renderer.__index = Renderer

function Renderer.new(settings)
  local self = setmetatable({}, Renderer)
  self.settings = settings or {}
  self.line_height = 12
  return self
end

function Renderer:render(file, cursor, syntax)
  local lines = {}
  for line in file.text:gmatch("([^\n]*)\n?") do
    table.insert(lines, line)
  end
  return {
    lines = lines,
    cursor_pos = cursor and cursor:get_position()
  }
end

return Renderer
