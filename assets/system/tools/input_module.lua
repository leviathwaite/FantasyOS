-- input_module.lua
-- Editor UI module (tab) for configuring InputMapper mappings.
-- Depends on input_mapper.lua (InputMapper). Use this file as an Editor tab.

local InputMapper = require("input_mapper")

local InputModuleUI = {}

-- Minimal UI integration for your editor: expects print(), rect(), mouse(), and project object.
-- UI provides: draw(winX, winY, w, h), update(winX,winY,w,h), open(project), close()

local uiState = {
  project = nil,
  width = 600,
  height = 480,
  scroll = 0
}

function InputModuleUI.open(project)
  uiState.project = project
  InputMapper.init(project)
end

function InputModuleUI.close()
  -- nothing for now
end

-- simple button hit test helper
local function in_rect(mx,my,x,y,w,h) return mx >= x and mx <= x+w and my >= y and my <= y+h end

function InputModuleUI.update(winX, winY, winW, winH)
  -- call mapper listening update
  InputMapper.update_listen()

  -- handle clicks for bind/clear/save/load (caller must provide mouse() and old_mouse state)
  local m = nil
  if type(mouse) == "function" then m = mouse() end
  if not m then return end

  if m.click then
    -- determine which bind button clicked
    local y = winY + winH - 40
    for i = 0, 7 do
      local bx = winX + winW - 120
      local by = y - (i * 20)
      if in_rect(m.x, m.y, bx, by - 12, 48, 18) then -- Bind
        InputMapper.start_listen(i, "any")
        return
      end
      if in_rect(m.x, m.y, bx + 56, by - 12, 48, 18) then -- Clear
        InputMapper.set_mapping(i, {})
        return
      end
    end

    -- Save/Load buttons at bottom
    local saveX = winX + 8
    local saveY = winY + 8
    if in_rect(m.x, m.y, saveX, saveY, 64, 20) then
      InputMapper.save(uiState.project)
      return
    end
    if in_rect(m.x, m.y, saveX + 72, saveY, 64, 20) then
      InputMapper.load(uiState.project)
      return
    end
  end
end

function InputModuleUI.draw(winX, winY, winW, winH)
  if type(print) ~= "function" then return end
  print("Input Mapper (PICO 0..7)", winX + 8, winY + winH - 18, 7)
  local y = winY + winH - 40
  for i = 0, 7 do
    local list = InputMapper.get_mapping(i) or {}
    local ds = {}
    for _,t in ipairs(list) do
      if t.type == "key" then table.insert(ds, tostring(t.code))
      elseif t.type == "mouse" then table.insert(ds, "mouse."..t.button)
      elseif t.type == "touch" then table.insert(ds, "touch."..(t.zone or "rect"))
      elseif t.type == "controller" then table.insert(ds, ("pad#%s.btn%d"):format(t.controllerIndex or (t.controllerName or "?"), t.button or -1))
      else table.insert(ds, t.type) end
    end
    local desc = #ds > 0 and table.concat(ds, ", ") or "<none>"
    print(string.format("Pico %d: %s", i, desc), winX + 12, y, 7)
    -- draw Bind / Clear labels
    if type(rect) == "function" then rect(winX + winW - 120, y - 12, 48, 18, 1) end
    print("[Bind]", winX + winW - 100, y, 10)
    if type(rect) == "function" then rect(winX + winW - 64, y - 12, 48, 18, 1) end
    print("[Clear]", winX + winW - 48, y, 9)
    y = y - 20
  end

  -- Save / Load buttons
  if type(rect) == "function" then rect(winX + 8, winY + 8, 64, 20, 1); rect(winX + 80, winY + 8, 64, 20, 1) end
  print("[Save]", winX + 12, winY + 24, 7)
  print("[Load]", winX + 84, winY + 24, 7)

  if InputMapper.listening then
    print("Listening for input for Pico " .. tostring(InputMapper.listening.pico) .. "...", winX + 8, winY + 48, 11)
  end
end

return InputModuleUI
