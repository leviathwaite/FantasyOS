-- REFERENCE BROWSER TOOL
-- Browse files from other projects for reference

local ReferenceBrowser = {}

-- State
local current_dir = "disk/"
local selected_file = nil
local preview_content = ""
local preview_lines = {}
local status_msg = "Browse projects for reference"
local status_timer = 3.0

-- Display
local split_position = 400  -- X position of the split between browser and preview
local line_height = 12
local preview_scroll = 0
local max_preview_lines = 50

function _init()
  -- Request Java to create file browser widget if available
  if type(sys) == "table" and type(sys.create_file_browser) == "function" then
    pcall(function()
      sys.create_file_browser(current_dir)
    end)
  end
  
  status_msg = "Reference Browser - Select a file to preview"
  status_timer = 3.0
end

function _update()
  -- Update status timer
  if status_timer > 0 then
    status_timer = status_timer - (1/60)
  end
  
  -- Keyboard shortcuts
  if type(key) == "function" then
    -- Copy selected file path
    if key("c") and (key("ctrl") or key("cmd")) then
      if selected_file then
        copy_to_clipboard(selected_file)
        status_msg = "Copied path: " .. selected_file
        status_timer = 2.0
      end
    end
    
    -- Scroll preview
    if key("up") then
      preview_scroll = math.max(0, preview_scroll - 1)
    elseif key("down") then
      preview_scroll = math.min(#preview_lines - max_preview_lines, preview_scroll + 1)
    elseif key("pageup") then
      preview_scroll = math.max(0, preview_scroll - max_preview_lines)
    elseif key("pagedown") then
      preview_scroll = math.min(#preview_lines - max_preview_lines, preview_scroll + max_preview_lines)
    end
  end
end

function _draw()
  cls(1)  -- Dark blue background
  
  -- Title bar
  print("Reference Browser", 10, 10, 10)
  print(current_dir, 200, 10, 7)
  
  -- Help text
  print("Click to select | Ctrl+C to copy path", 10, 25, 6)
  
  -- Vertical split line
  line(split_position, 40, split_position, 710, 5)
  
  -- Left panel: File browser
  print("Project Files", 10, 45, 11)
  print("(File browser widget renders here)", 10, 65, 6)
  
  -- Right panel: Preview
  print("Preview", split_position + 10, 45, 11)
  
  if selected_file then
    print("File: " .. selected_file, split_position + 10, 65, 7)
    
    -- Draw preview content
    local y = 85
    local start_line = preview_scroll + 1
    local end_line = math.min(#preview_lines, start_line + max_preview_lines - 1)
    
    for i = start_line, end_line do
      local line_text = preview_lines[i] or ""
      local color = 7  -- Default white
      
      -- Syntax highlighting
      if line_text:match("^%s*%-%-") then
        color = 13  -- Comment - pink
      elseif line_text:match("function") or line_text:match("local") or line_text:match("return") then
        color = 14  -- Keyword - orange
      elseif line_text:match("\"") or line_text:match("'") then
        color = 11  -- String - yellow
      end
      
      print(line_text, split_position + 10, y, color)
      y = y + line_height
    end
    
    -- Scroll indicator for preview
    if #preview_lines > max_preview_lines then
      local scroll_x = 1260
      local scroll_h = 600
      local scroll_y = 85
      local thumb_h = math.floor((max_preview_lines / #preview_lines) * scroll_h)
      local thumb_y = scroll_y + math.floor((preview_scroll / (#preview_lines - max_preview_lines)) * (scroll_h - thumb_h))
      
      rect(scroll_x, scroll_y, scroll_x + 5, scroll_y + scroll_h, 5)
      rectfill(scroll_x, thumb_y, scroll_x + 5, thumb_y + thumb_h, 10)
    end
  else
    print("No file selected", split_position + 10, 85, 6)
    print("Click a file on the left to preview it here", split_position + 10, 105, 6)
  end
  
  -- Status message
  if status_timer > 0 then
    local msg_y = 680
    rectfill(8, msg_y - 5, 10 + #status_msg * 8, msg_y + 15, 2)
    print(status_msg, 10, msg_y, 11)
  end
  
  -- Footer
  if selected_file then
    print("Lines: " .. #preview_lines .. "  Scroll: " .. preview_scroll, 10, 700, 6)
  end
end

-- Called by Java when a file is selected
function on_file_selected(file_path)
  selected_file = file_path
  preview_content = ""
  preview_lines = {}
  preview_scroll = 0
  
  -- Try to read file content
  if type(project) == "table" and type(project.read) == "function" then
    local ok, content = pcall(function() return project.read(file_path) end)
    if ok and content then
      preview_content = content
      -- Split into lines
      for line in (content .. "\n"):gmatch("([^\n]*)\n") do
        table.insert(preview_lines, line)
      end
      status_msg = "Loaded: " .. file_path
      status_timer = 2.0
      return
    end
  end
  
  -- Fallback: Try reading directly
  local fh = nil
  if type(Gdx) ~= "nil" and type(Gdx.files) ~= "nil" then
    fh = Gdx.files.local(file_path)
    if not fh or not fh.exists() then
      fh = Gdx.files.internal(file_path)
    end
  end
  
  if fh and fh.exists() and not fh.isDirectory() then
    local ok, content = pcall(function() return fh.readString("UTF-8") end)
    if ok and content then
      preview_content = content
      for line in (content .. "\n"):gmatch("([^\n]*)\n") do
        table.insert(preview_lines, line)
      end
      status_msg = "Loaded: " .. file_path
      status_timer = 2.0
      return
    end
  end
  
  status_msg = "Could not read file"
  status_timer = 2.0
end

function copy_to_clipboard(text)
  -- Try to copy to system clipboard
  if type(Gdx) ~= "nil" and type(Gdx.app) ~= "nil" and type(Gdx.app.getClipboard) == "function" then
    pcall(function()
      Gdx.app.getClipboard().setContents(text)
    end)
  end
end

-- Helper functions
function key(k)
  if type(_G.key) == "function" then
    return _G.key(k)
  end
  return false
end

-- Export globally so Java can call it
_G.on_file_selected = on_file_selected

return ReferenceBrowser
