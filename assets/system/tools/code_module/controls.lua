-- controls.lua
local M = {}

M.controls = {
  height = 40,
  buttons = {
    {id = "save", label = "Save", x = 10, w = 80},
    {id = "run",  label = "Run",  x = 100, w = 80}
  }
}

function M.init(state) end
function M.get_controls() return M.controls end

return M
