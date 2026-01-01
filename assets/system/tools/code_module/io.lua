local IO = {}

function IO.is_func(v) return type(v) == "function" end

function IO.btn_safe(i)
  if not IO.is_func(btn) then return false end
  local ok, res = pcall(function() return btn(i) end)
  return ok and res or false
end

function IO.btnp_safe(i)
  if not IO.is_func(btnp) then return false end
  local ok, res = pcall(function() return btnp(i) end)
  return ok and res or false
end

function IO.kbchar()
  if IO.is_func(char) then local ok, r = pcall(char); if ok then return r end end
  if IO.is_func(keyboard_char) then local ok, r = pcall(keyboard_char); if ok then return r end end
  return nil
end

function IO.get_mouse()
  if IO.is_func(mouse) then local ok, r = pcall(mouse); if ok then return r end end
  return nil
end

function IO.save_wrapper(path, content)
  if type(project) == "table" and type(project.write) == "function" then
    local ok, res = pcall(function() return project.write(path, content) end)
    if ok then return res end
  end
  if IO.is_func(save_file) then
    local ok, res = pcall(function() return save_file(path, content) end)
    if ok then return res end
  end
  return false
end

function IO.toast(msg)
    if IO.is_func(toast) then pcall(function() toast(msg) end) end
end

return IO
