-- CONFIG EDITOR TOOL
-- Edit project configuration in TOON format

local ConfigEditor = {}

-- State
local config_content = ""
local config_exists = false
local lines = {}
local cursor_line = 1
local scroll_offset = 0
local status_msg = ""
local status_timer = 0

-- Display settings
local line_height = 12
local visible_lines = 25
local left_margin = 10

-- Load TOON to JSON converter
local ToonConverter = nil
pcall(function()
  local converter_path = "system/converters/toon_to_json.lua"
  if type(project) == "table" and type(project.read) == "function" then
    local script = project.read(converter_path)
    if script then
      ToonConverter = load(script)()
    end
  end
end)

function _init()
  load_config()
end

function load_config()
  config_exists = false
  config_content = ""
  lines = {}
  
  -- Try to load config.toon
  if type(project) == "table" and type(project.read) == "function" then
    local ok, content = pcall(function() return project.read("config.toon") end)
    if ok and content and content ~= "" then
      config_exists = true
      config_content = content
      -- Split into lines
      for line in (content .. "\n"):gmatch("([^\n]*)\n") do
        table.insert(lines, line)
      end
      status_msg = "Loaded config.toon"
      status_timer = 2.0
      return
    end
  end
  
  -- Create default config if none exists
  lines = {
    "# Project Configuration",
    "",
    "name: My Project",
    "description: A FantasyOS project",
    "",
    "resolution:",
    "  width: 240",
    "  height: 136",
    "",
    "palette: pico8",
    "",
    "memory:",
    "  size: 65536",
    "  banks: 8",
    "",
    "input:",
    "  enabled: true",
    "",
    "audio:",
    "  enabled: true"
  }
  status_msg = "Created default config"
  status_timer = 2.0
end

function save_config()
  -- Join lines
  config_content = table.concat(lines, "\n")
  
  -- Save TOON file
  if type(project) == "table" and type(project.write) == "function" then
    local ok = pcall(function()
      project.write("config.toon", config_content)
    end)
    if ok then
      status_msg = "Saved config.toon"
      status_timer = 2.0
      
      -- Convert to JSON if converter available
      if ToonConverter then
        pcall(function()
          ToonConverter.convertFile("config.toon", "config.json")
          status_msg = "Saved config.toon and config.json"
        end)
      end
      return true
    end
  end
  
  status_msg = "Failed to save config"
  status_timer = 2.0
  return false
end

function _update()
  -- Update status timer
  if status_timer > 0 then
    status_timer = status_timer - (1/60)
  end
  
  -- Keyboard input
  if type(key) == "function" then
    -- Navigation
    if key("up") then
      cursor_line = math.max(1, cursor_line - 1)
      if cursor_line < scroll_offset + 1 then
        scroll_offset = cursor_line - 1
      end
    elseif key("down") then
      cursor_line = math.min(#lines, cursor_line + 1)
      if cursor_line > scroll_offset + visible_lines then
        scroll_offset = cursor_line - visible_lines
      end
    elseif key("pageup") then
      cursor_line = math.max(1, cursor_line - visible_lines)
      scroll_offset = math.max(0, scroll_offset - visible_lines)
    elseif key("pagedown") then
      cursor_line = math.min(#lines, cursor_line + visible_lines)
      scroll_offset = math.min(#lines - visible_lines, scroll_offset + visible_lines)
    end
    
    -- Actions
    if key("s") and (key("ctrl") or key("cmd")) then
      save_config()
    elseif key("r") and (key("ctrl") or key("cmd")) then
      load_config()
    end
  end
  
  -- Fallback to btn/btnp
  if type(btnp) == "function" then
    if btnp(2) then  -- Up
      cursor_line = math.max(1, cursor_line - 1)
    elseif btnp(3) then  -- Down
      cursor_line = math.min(#lines, cursor_line + 1)
    end
  end
end

function _draw()
  cls(1)  -- Dark blue background
  
  -- Title
  print("Configuration Editor", left_margin, 10, 10)
  print("config.toon", left_margin + 150, 10, 7)
  
  -- Help text
  print("Ctrl+S: Save  Ctrl+R: Reload", left_margin, 25, 6)
  
  -- Draw lines
  local y = 45
  local start_line = scroll_offset + 1
  local end_line = math.min(#lines, start_line + visible_lines - 1)
  
  for i = start_line, end_line do
    local line = lines[i]
    local color = 7  -- Default white
    
    -- Color syntax
    if line:match("^#") then
      color = 13  -- Comment - pink
    elseif line:match("^%s*[%w_]+:") then
      color = 11  -- Key - yellow
    elseif line:match("^%s+[%w_]+:") then
      color = 14  -- Nested key - orange
    end
    
    -- Highlight cursor line
    if i == cursor_line then
      rectfill(left_margin - 5, y - 2, 1270, y + line_height - 2, 2)
      print(">", left_margin - 8, y, 10)
    end
    
    -- Draw line
    print(line, left_margin, y, color)
    y = y + line_height
  end
  
  -- Scroll indicator
  if #lines > visible_lines then
    local scroll_h = 200
    local scroll_y = 45
    local thumb_h = math.floor((visible_lines / #lines) * scroll_h)
    local thumb_y = scroll_y + math.floor((scroll_offset / (#lines - visible_lines)) * (scroll_h - thumb_h))
    
    rect(1260, scroll_y, 1265, scroll_y + scroll_h, 5)
    rectfill(1260, thumb_y, 1265, thumb_y + thumb_h, 10)
  end
  
  -- Status message
  if status_timer > 0 then
    local msg_y = 680
    rectfill(left_margin - 5, msg_y - 5, left_margin + #status_msg * 8 + 5, msg_y + 15, 2)
    print(status_msg, left_margin, msg_y, 11)
  end
  
  -- Footer
  print("Lines: " .. #lines .. "  Cursor: " .. cursor_line, left_margin, 700, 6)
end

-- Helper functions
function key(k)
  if type(_G.key) == "function" then
    return _G.key(k)
  end
  return false
end

function btnp(i)
  if type(_G.btnp) == "function" then
    return _G.btnp(i)
  end
  return false
end

return ConfigEditor
