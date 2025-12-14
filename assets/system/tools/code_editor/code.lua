-- Facade for backwards compatibility. Delegates to editor_core in the same folder.
local EditorCore = require('system.tools.code_editor.editor_core')

local M = {}
M.__index = M

function M.new(params)
  return EditorCore.new(params)
end

function M.open(path)
  local e = EditorCore.new()
  return e:open(path)
end

return M
