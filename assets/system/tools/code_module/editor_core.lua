local Buffer = require("system/tools/code_module/buffer")
local Core = {}

Core.tabs = {}
Core.current_tab = 1
Core.current_file = "main.lua"

function Core.open_tab(path, content)
  local b = Buffer.new(path, content)
  table.insert(Core.tabs, b)
  Core.current_tab = #Core.tabs
  if path then Core.current_file = path end
  return b
end

function Core.close_tab(idx)
  table.remove(Core.tabs, idx)
  if Core.current_tab > #Core.tabs then
    Core.current_tab = math.max(1, #Core.tabs)
  end
end

function Core.get_current()
  -- FAILSAFE: If no tabs exist, create one immediately
  if #Core.tabs == 0 then
    local initial_text = "-- New File\n"
    -- Try to load main.lua if project exists
    if type(project) == "table" and project.read then
        pcall(function() initial_text = project.read("main.lua") or initial_text end)
    end
    Core.open_tab("main.lua", initial_text)
  end

  if Core.current_tab < 1 then Core.current_tab = 1 end
  return Core.tabs[Core.current_tab]
end

return Core
