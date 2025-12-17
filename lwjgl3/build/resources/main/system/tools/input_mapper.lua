-- input_mapper.lua
-- Core runtime mapping engine: maps raw inputs -> "fantasy console" buttons (0..7).
-- Responsibilities:
--  - maintain mapping table
--  - evaluate triggers each frame (InputMapper.update)
--  - provide InputMapper.btn / InputMapper.btnp API
--  - persist/load mapping as input_mapper_config.lua inside project
--  - install_globals() to expose legacy btn/btnp if desired

local InputMapper = {}

-- Default mapping example (can be overwritten per-project)
local DEFAULT_MAP = {
  [0] = { { type="key", code="KEY_LEFT" } },
  [1] = { { type="key", code="KEY_RIGHT" } },
  [2] = { { type="key", code="KEY_UP" } },
  [3] = { { type="key", code="KEY_DOWN" } },
  [4] = { { type="key", code="KEY_Z" } },
  [5] = { { type="key", code="KEY_X" } },
  [6] = { { type="mouse", button="left" } },
  [7] = { { type="none" } }
}

-- runtime state
InputMapper.map = {}
InputMapper.current = {}
InputMapper.previous = {}
InputMapper.project = nil
InputMapper.listening = nil

-- small helpers
local function is_fn(f) return type(f) == "function" end
local function safe_pcall(f, ...)
  if not is_fn(f) then return nil end
  local ok, res = pcall(f, ...)
  if not ok then return nil end
  return res
end

local function resolve_code(code)
  if type(code) == "number" then return code end
  if type(code) == "string" then
    local g = _G[code]
    if type(g) == "number" then return g end
    local n = tonumber(code)
    if n then return n end
  end
  return nil
end

-- raw wrappers (use engine-provided bindings when available)
local function raw_key(code) return safe_pcall(key, code) end
local function raw_keyp(code) return safe_pcall(keyp, code) end
local function raw_mouse() return safe_pcall(mouse) end
local function raw_touch() return safe_pcall(touch) end
local function raw_controller_count() return safe_pcall(controller_count) end
local function raw_controller_name(i) return safe_pcall(controller_name, i) end
local function raw_controller_button(i, b) return safe_pcall(controller_button, i, b) end
local function raw_controller_buttonp(i, b) return safe_pcall(controller_buttonp, i, b) end
local function raw_set_editor_font_size(px) return safe_pcall(set_editor_font_size, px) end

-- persist/load helpers
local function serialize_value(v)
  local t = type(v)
  if t == "number" or t == "boolean" then return tostring(v) end
  if t == "string" then return string.format("%q", v) end
  if t == "table" then
    -- treat as array-like or map
    local is_array = true
    local maxn = 0
    for k,_ in pairs(v) do
      if type(k) ~= "number" then is_array = false end
      if type(k) == "number" and k > maxn then maxn = k end
    end
    if is_array then
      local parts = {}
      for i=1,maxn do parts[#parts+1] = serialize_value(v[i]) end
      return "{" .. table.concat(parts, ",") .. "}"
    else
      local parts = {}
      for k,val in pairs(v) do
        local key = (type(k) == "string") and ("[["..k.."]]") or ("["..tostring(k).."]")
        parts[#parts+1] = key .. " = " .. serialize_value(val)
      end
      return "{ " .. table.concat(parts, ", ") .. " }"
    end
  end
  return "nil"
end

local function serialize_map(map)
  return "return " .. serialize_value(map)
end

local function safe_save(path, contents)
  if InputMapper.project and type(InputMapper.project.getAsset) == "function" then
    local ok, fh = pcall(function() return InputMapper.project.getAsset(path) end)
    if ok and fh and type(fh.writeString) == "function" then
      pcall(function() fh.writeString(contents, false) end)
      return true
    end
  end
  if is_fn(save_file) then
    local ok, res = pcall(function() return save_file(path, contents) end)
    return ok and res
  end
  return false
end

local function safe_load(path)
  if InputMapper.project and type(InputMapper.project.getAsset) == "function" then
    local ok, fh = pcall(function() return InputMapper.project.getAsset(path) end)
    if ok and fh and type(fh.exists) == "function" and fh.exists() then
      local ok2, content = pcall(function() return fh.readString() end)
      if ok2 then return content end
    end
  end
  if is_fn(load_file) then
    local ok, res = pcall(function() return load_file(path) end)
    if ok then return res end
  end
  return nil
end

-- normalize map (ensure indices 0..7)
local function normalize_map(m)
  local out = {}
  for i=0,7 do
    local v = m and m[i]
    if not v then out[i] = {} else out[i] = v end
  end
  return out
end

-- Core API

function InputMapper.init(project)
  InputMapper.project = project or InputMapper.project
  InputMapper.map = normalize_map(DEFAULT_MAP)
  local content = safe_load("input_mapper_config.lua")
  if content then
    local chunk, err = load(content)
    if chunk then
      local ok, tbl = pcall(chunk)
      if ok and type(tbl) == "table" then
        InputMapper.map = normalize_map(tbl)
      end
    end
  end
  for i=0,7 do InputMapper.current[i] = false; InputMapper.previous[i] = false end
end

function InputMapper.save(project)
  InputMapper.project = project or InputMapper.project
  local src = serialize_map(InputMapper.map)
  return safe_save("input_mapper_config.lua", src)
end

function InputMapper.set_mapping(index, triggers)
  index = tonumber(index) or 0
  if index < 0 or index > 7 then return false end
  InputMapper.map[index] = triggers or {}
  return true
end

function InputMapper.get_mapping(index)
  return InputMapper.map[index] or {}
end

-- Trigger evaluation
function InputMapper._eval_trigger(t)
  if not t or type(t) ~= "table" then return false end
  if t.type == "none" then return false end
  if t.type == "key" then
    local code = resolve_code(t.code)
    if not code then return false end
    return raw_key(code) == true
  elseif t.type == "mouse" then
    local m = raw_mouse()
    if not m then return false end
    if t.button == "left" then return (m.left == true) or (m.click == true) or (m.left == 1) end
    if t.button == "right" then return (m.right == true) or (m.right == 1) end
    return false
  elseif t.type == "touch" then
    local touches = raw_touch()
    if not touches then return false end
    if t.zone == "left" then
      for _,p in ipairs(touches) do if p.x and p.x < (t.screen_w or 0)/2 then return true end end
    elseif t.zone == "right" then
      for _,p in ipairs(touches) do if p.x and p.x >= (t.screen_w or 0)/2 then return true end end
    elseif t.zone == "rect" and t.rect then
      for _,p in ipairs(touches) do
        if p.x and p.y and p.x >= t.rect.x and p.x <= t.rect.x + t.rect.w and p.y >= t.rect.y and p.y <= t.rect.y + t.rect.h then
          return true
        end
      end
    end
    return false
  elseif t.type == "controller" then
    local cnt = raw_controller_count() or 0
    if cnt < 1 then return false end
    local btn = t.button
    if t.controllerIndex then
      return raw_controller_button(t.controllerIndex, btn) == true
    elseif t.controllerName then
      for i=1,cnt do
        local nm = raw_controller_name(i)
        if nm and nm == t.controllerName then
          return raw_controller_button(i, btn) == true
        end
      end
      return false
    else
      for i=1,cnt do
        if raw_controller_button(i, btn) then return true end
      end
      return false
    end
  end
  return false
end

function InputMapper.update()
  for i=0,7 do InputMapper.previous[i] = InputMapper.current[i] or false end
  for i=0,7 do
    local active = false
    for _, t in ipairs(InputMapper.map[i] or {}) do
      if InputMapper._eval_trigger(t) then active = true; break end
    end
    InputMapper.current[i] = active
  end
end

function InputMapper.btn(i)
  i = tonumber(i) or 0
  if i < 0 or i > 7 then return false end
  return InputMapper.current[i] == true
end

function InputMapper.btnp(i)
  i = tonumber(i) or 0
  if i < 0 or i > 7 then return false end
  return (InputMapper.current[i] == true) and (not InputMapper.previous[i])
end

function InputMapper.install_globals()
  _G["btn"] = function(i) return InputMapper.btn(i) end
  _G["btnp"] = function(i) return InputMapper.btnp(i) end
end

-- Listening for binds (editor UI)
function InputMapper.start_listen(picoIndex, mode)
  InputMapper.listening = { pico = picoIndex, mode = mode or "any", started = os.time() }
end

local function detect_input_for_listen(mode)
  -- keyboard
  if (mode == "any" or mode == "key") and is_fn(keyp) then
    for k,v in pairs(_G) do
      if type(k) == "string" and k:match("^KEY_") and type(v) == "number" then
        if raw_keyp(v) then return { type="key", code=k } end
      end
    end
  end
  -- mouse
  if (mode == "any" or mode == "mouse") then
    local m = raw_mouse()
    if m and (m.click or m.left) then return { type="mouse", button="left" } end
    if m and (m.right) then return { type="mouse", button="right" } end
  end
  -- touch
  if (mode == "any" or mode == "touch") then
    local touches = raw_touch()
    if touches and #touches > 0 then
      local t = touches[1]
      return { type="touch", zone="rect", rect = { x = t.x - 20, y = t.y - 20, w = 40, h = 40 } }
    end
  end
  -- controller
  if (mode == "any" or mode == "controller") then
    local cnt = raw_controller_count() or 0
    for ci = 1, (cnt or 0) do
      for b = 0, 31 do
        if raw_controller_buttonp and raw_controller_buttonp(ci, b) then
          local nm = raw_controller_name(ci)
          return { type="controller", controllerIndex = ci, controllerName = nm, button = b }
        elseif raw_controller_button and raw_controller_button(ci, b) then
          local nm = raw_controller_name(ci)
          return { type="controller", controllerIndex = ci, controllerName = nm, button = b }
        end
      end
    end
  end
  return nil
end

function InputMapper.update_listen()
  if not InputMapper.listening then return nil end
  local trig = detect_input_for_listen(InputMapper.listening.mode)
  if trig then
    local pico = InputMapper.listening.pico
    InputMapper.map[pico] = InputMapper.map[pico] or {}
    -- dedupe
    local exists = false
    for _,v in ipairs(InputMapper.map[pico]) do
      if v.type == trig.type and (v.code == trig.code or v.button == trig.button) then exists = true; break end
    end
    if not exists then table.insert(InputMapper.map[pico], trig) end
    InputMapper.listening = nil
    return trig
  end
  return nil
end

return InputMapper
