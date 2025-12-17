-- Responsible for open/save/listing project files.
local FileManager = {}
FileManager.__index = FileManager

function FileManager.new(settings)
  local self = setmetatable({}, FileManager)
  self.settings = settings or {}
  return self
end

function FileManager:open(path)
  local f = { path = path, text = '', dirty = false }
  -- TODO: load actual contents here (port from monolith)
  return f
end

function FileManager:save(file)
  if not file or not file.path then return false end
  file.dirty = false
  return true
end

return FileManager
