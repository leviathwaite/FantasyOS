-- io.lua
-- Safe bindings to project/host operations.
local M = {}

local function is_func(v) return type(v) == "function" end

function M.read(path)
  if type(project) == "table" and type(project.read) == "function" then
    local ok, res = pcall(function() return project.read(path) end)
    if ok then return res end
  end
  if is_func(load_file) then
    local ok, res = pcall(function() return load_file(path) end)
    if ok then return res end
  end
  return nil
end

function M.save(path, content)
  if type(project) == "table" and type(project.write) == "function" then
    local ok, res = pcall(function() return project.write(path, content) end)
    if ok then return res end
  end
  if is_func(save_file) then
    local ok, res = pcall(function() return save_file(path, content) end)
    if ok then return res end
  end
  return false
end

function M.run(path)
  if is_func(run_project) then
    local ok, res = pcall(function() return run_project(path) end)
    if ok then return res end
  end
  return false
end

function M.import_dialog()
  if is_func(import_file_dialog) then
    local ok, res = pcall(function() return import_file_dialog() end)
    if ok then return res end
  end
  return nil
end

function M.set_editor_font_size(px)
  if is_func(set_editor_font_size) then
    local ok, res = pcall(function() return set_editor_font_size(px) end)
    if ok then return res end
  end
  return nil
end

function M.toast(msg, secs)
  if is_func(toast) then pcall(function() toast(msg) end) end
  if is_func(toast_with_time) and secs then pcall(function() toast_with_time(msg, secs) end) end
end

return M
