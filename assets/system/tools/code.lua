-- code.lua
-- NerdOS Professional Code Editor with tabs, import, save, run, toasts.
-- Adjusted defaults to avoid overlapping text: larger default font_h/line_h and
-- dynamic line height when runtime metrics are available.

local CodeEditor = {}

-- ============================================================================
-- CONFIGURATION & RUNTIME METRICS
-- ============================================================================
local config = {
  win_min_width = 1900,
  win_min_height = 1000,
  font_w = 8,
  font_h = 18,     -- bumped up from 16
  line_h = 22,     -- bumped up from 18 to reduce overlap
  help_h = 60,
  tab_width = 2,
  scroll_speed = 3,
  colors = {
    bg = 0, text = 7, keyword = 12, func = 14, num = 9, str = 11,
    comment = 13, cursor = 10, gutter_bg = 1, gutter_fg = 6,
    help_bg = 1, help_title = 10, help_text = 7, help_example = 11,
    selection = 2, current_line = 1, bracket = 10, error = 8
  },
  font_size = 20
}

-- Pull runtime font metrics from Java if present
if type(editor_font_metrics) == "function" then
  local ok, m = pcall(editor_font_metrics)
  if ok and m then
    config.font_w = m.font_w or config.font_w
    config.font_h = m.font_h or config.font_h
    -- Recompute line height based on actual font height to avoid overlap
    local lh = m.line_h or config.line_h or (config.font_h + 6)
    lh = math.max(lh, math.floor((config.font_h or 16) * 1.35) + 2)
    config.line_h = lh
  end
else
  -- If no metrics, still ensure line height is safely larger than font height
  config.line_h = math.max(config.line_h, math.floor((config.font_h or 16) * 1.35) + 2)
end

-- The rest of the editor code remains unchanged
-- ============================================================================
-- UTF-8 HELPERS
-- ============================================================================
-- UTF-8 character boundary pattern (matches single UTF-8 codepoint)
local UTF8_CHAR_PATTERN = "([%z\1-\127\194-\244][\128-\191]*)"

local function utf8_char_count(s)
  if not s or s == "" then return 0 end
  local count = 0
  for _ in string.gmatch(s, UTF8_CHAR_PATTERN) do count = count + 1 end
  return count
end

-- Convert visual character index to byte offset in string
local function visual_to_byte_offset(s, visual_idx)
  if not s or s == "" or visual_idx <= 0 then return 0 end
  local count = 0
  local byte_pos = 0
  for char in string.gmatch(s, UTF8_CHAR_PATTERN) do
    if count >= visual_idx then break end
    byte_pos = byte_pos + #char
    count = count + 1
  end
  return byte_pos
end

local function compute_font_metrics_from_size(px)
  px = px or config.font_size or 16
  local fh = math.max(8, math.floor(px * 0.95))
  local fw = math.max(4, math.floor(px * 0.58 + 0.5))
  local lh = math.floor(fh * 1.35) + 2
  lh = math.max(lh, config.line_h or 0)
  return { font_w = fw, font_h = fh, line_h = lh }
end

-- ============================================================================
-- SAFE BINDINGS WRAPPERS
-- ============================================================================
local function is_func(v) return type(v) == "function" end

local function btn_safe(i)
  if not is_func(btn) then return false end
  local ok, res = pcall(function() return btn(i) end)
  if not ok then return false end
  return res and true or false
end

local function btnp_safe(i)
  if not is_func(btnp) then return false end
  local ok, res = pcall(function() return btnp(i) end)
  if not ok then return false end
  return res and true or false
end

local function key_safe(v)
  if is_func(key) then
    local ok, res = pcall(function() return key(v) end)
    if ok then return res and true or false end
  end
  if is_func(keyp) then
    local ok2, res2 = pcall(function() return keyp(v) end)
    if ok2 then return res2 and true or false end
  end
  return false
end

local function kbchar()
  if is_func(char) then
    local ok, res = pcall(function() return char() end)
    if ok then return res end
  end
  if is_func(keyboard_char) then
    local ok, res = pcall(function() return keyboard_char() end)
    if ok then return res end
  end
  return nil
end

local function get_mouse()
  if is_func(mouse) then
    local ok, res = pcall(function() return mouse() end)
    if ok then return res end
  end
  return nil
end

local function call_save(path, content)
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

local function call_read(path)
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

local function call_run(path)
  if is_func(run_project) then
    local ok, res = pcall(function() return run_project(path) end)
    if ok then return res end
  end
  return false
end

local function call_import_dialog()
  if is_func(import_file_dialog) then
    local ok, res = pcall(function() return import_file_dialog() end)
    if ok then return res end
  end
  return nil
end

local function call_set_editor_font_size(px)
  if is_func(set_editor_font_size) then
    local ok, res = pcall(function() return set_editor_font_size(px) end)
    if ok then
      return res
    end
  end
  return nil
end

local function call_toast(msg, secs)
  if is_func(toast) then pcall(function() toast(msg) end) end
  if is_func(toast_with_time) and secs then pcall(function() toast_with_time(msg, secs) end) end
end

-- ============================================================================
-- CONTROL BAR
-- ============================================================================
local controls = {
  height = 40,
  buttons = {
    {id = "save", label = "Save", x = 10, w = 80},
    {id = "run",  label = "Run",  x = 100, w = 80}
  }
}

local function safe_col(c) return c or 7 end

-- ============================================================================
-- KEYCODES (LibGDX mapping fallback names)
-- ============================================================================
KEY_UP=19; KEY_DOWN=20; KEY_LEFT=21; KEY_RIGHT=22
KEY_HOME=3; KEY_END=123; KEY_PGUP=92; KEY_PGDN=93
KEY_ENTER=66; KEY_BACK=67; KEY_TAB=61; KEY_DEL=112; KEY_SPACE=62
KEY_SHIFT_L=59; KEY_SHIFT_R=60
KEY_CTRL_L=129; KEY_CTRL_R=130
KEY_ALT_L=57; KEY_ALT_R=58
KEY_A=29; KEY_C=31; KEY_F=34; KEY_R=46; KEY_S=47
KEY_V=50; KEY_X=52; KEY_Y=53; KEY_Z=54
KEY_MINUS = 69
KEY_EQUALS = 70

-- ============================================================================
-- BACKSPACE REPEAT STATE
-- ============================================================================
local _back_repeat = 0
local BACK_INITIAL_DELAY = 15    -- frames before repeating starts
local BACK_REPEAT_INTERVAL = 4   -- frames between repeated deletes

-- ============================================================================
-- BUFFER & TAB MANAGEMENT
-- ============================================================================
local tabs = {}
local current_tab = 1
local current_file = "main.lua"
local untitled_counter = 1

-- Helper to split text into lines (preserve trailing empty line)
local function split_lines_from_text(txt)
  if not txt or txt == "" then return {""} end
  local lines = {}
  local pos = 1
  for line in string.gmatch(txt, "([^\n]*)\n?") do
    table.insert(lines, line)
  end
  return lines
end

local function make_undostack()
  local UndoStack = {}
  UndoStack.__index = UndoStack
  function UndoStack.new()
    local u = setmetatable({}, UndoStack)
    u.stack = {}
    u.position = 0
    return u
  end
  function UndoStack:push(state)
    while #self.stack > self.position do table.remove(self.stack) end
    table.insert(self.stack, state)
    self.position = #self.stack
    if #self.stack > 100 then
      table.remove(self.stack, 1)
      self.position = self.position - 1
    end
  end
  function UndoStack:undo()
    if self.position > 1 then
      self.position = self.position - 1
      return self.stack[self.position]
    end
    return nil
  end
  function UndoStack:redo()
    if self.position < #self.stack then
      self.position = self.position + 1
      return self.stack[self.position]
    end
    return nil
  end
  return UndoStack.new()
end

local function buffer_new(path, text)
  local b = {}
  b.path = path or nil
  b.lines = split_lines_from_text(text or "")
  b.clipboard = ""
  b.undo = make_undostack()
  b.cx = 0; b.cy = 1; b.scroll_y = 0
  b.sel_start_x, b.sel_start_y, b.sel_end_x, b.sel_end_y = nil, nil, nil, nil
  b.modified = false
  b.blink = 0
  b.bracket_match = nil
  b.mouse_selecting = false
  b.find_mode = false
  return b
end

local function open_tab(path, content)
  local b = buffer_new(path, content)
  table.insert(tabs, b)
  current_tab = #tabs
  if path then current_file = path end
  return b
end

local function close_tab(idx)
  if idx < 1 or idx > #tabs then return end
  table.remove(tabs, idx)
  if current_tab > #tabs then current_tab = math.max(1, #tabs) end
end

-- On startup, prefer loading project's main.lua if present; otherwise open a blank main.lua
if #tabs == 0 then
  local loaded = nil
  if type(project) == "table" and type(project.read) == "function" then
    local ok, content = pcall(function() return project.read("main.lua") end)
    if ok and content and #content > 0 then loaded = content end
  else
    local ok, content = pcall(function() if type(load_file) == "function" then return load_file("main.lua") end end)
    if ok and content and #content > 0 then loaded = content end
  end
  if loaded then
    open_tab("main.lua", loaded)
  else
    open_tab("main.lua", "-- New file\n")
  end
end

-- ensure current_tab is valid before any cur() use
local function ensure_current_tab()
  if #tabs == 0 then
    local loaded = nil
    if type(project) == "table" and type(project.read) == "function" then
      local ok, content = pcall(function() return project.read("main.lua") end)
      if ok and content and #content > 0 then loaded = content end
    end
    if loaded then open_tab("main.lua", loaded) else open_tab("main.lua", "-- New file\n") end
  end
  if current_tab < 1 then current_tab = 1 end
  if current_tab > #tabs then current_tab = #tabs end
end

local function cur()
  ensure_current_tab()
  return tabs[current_tab]
end

local function get_state()
  local st = {}
  local b = cur()
  for i, ln in ipairs(b.lines) do st[i] = ln end
  return st
end

local function restore_state(s)
  local b = cur()
  b.lines = {}
  for i, ln in ipairs(s) do b.lines[i] = ln end
end

-- ============================================================================
-- SYNTAX HIGHLIGHTING (very simple)
-- ============================================================================
local Syntax = {}
Syntax.__index = Syntax
function Syntax.new()
  local s = setmetatable({}, Syntax)
  s.keywords = {
    ["and"]=1,["break"]=1,["do"]=1,["else"]=1,["elseif"]=1,["end"]=1,
    ["false"]=1,["for"]=1,["function"]=1,["if"]=1,["in"]=1,["local"]=1,
    ["nil"]=1,["not"]=1,["or"]=1,["repeat"]=1,["return"]=1,["then"]=1,
    ["true"]=1,["until"]=1,["while"]=1
  }
  s.funcs = {}
  for k,_ in pairs(api_docs or {}) do s.funcs[k] = 1 end
  s.patterns = {
    {"^%-%-.*", config.colors.comment},
    {'^"[^"]*"', config.colors.str},
    {"^'[^']*'", config.colors.str},
    {"^0x%x+", config.colors.num},
    {"^%d+%.?%d*", config.colors.num},
    {"^[%a_][%w_]*", "word"},
    {"^[%+%-*/%^%%#=<>~]+", config.colors.text},
    {"^[%(%)%[%]{}.,;:]", "word"},
    {"^%s+", "skip"},
    {"^.", config.colors.text}
  }
  return s
end

function Syntax:parse(text)
  if not text then return {} end
  local tokens = {}
  local pos = 1
  while pos <= #text do
    local sub = string.sub(text, pos); local matched = false
    for _, pattern in ipairs(self.patterns) do
      local s,e = string.find(sub, pattern[1])
      if s == 1 then
        local token_text = string.sub(sub, s, e)
        local col = pattern[2]
        if col == "word" then
          local ch = token_text
          if self.keywords[ch] then col = config.colors.keyword
          elseif self.funcs[ch] then col = config.colors.func
          elseif ch == "(" or ch == ")" or ch == "[" or ch == "]" or ch == "{" or ch == "}" then
            col = config.colors.text
            table.insert(tokens, {text = ch, color = col, bracket = true})
            pos = pos + 1; matched = true; break
          else col = config.colors.text end
        elseif col == "skip" then pos = pos + (e - s + 1); matched = true; break end
        table.insert(tokens, {text = token_text, color = col})
        pos = pos + (e - s + 1); matched = true; break
      end
    end
    if not matched then pos = pos + 1 end
  end
  return tokens
end

local syntax = Syntax.new()

-- ============================================================================
-- SELECTION HELPERS
-- ============================================================================
local function buffer_has_selection(buf)
  return buf.sel_start_x and buf.sel_start_y and buf.sel_end_x and buf.sel_end_y and not (buf.sel_start_x == buf.sel_end_x and buf.sel_start_y == buf.sel_end_y)
end

local function get_selection_bounds(buf)
  if not buffer_has_selection(buf) then return nil end
  local sy,sx = buf.sel_start_y, buf.sel_start_x; local ey,ex = buf.sel_end_y, buf.sel_end_x
  if sy > ey or (sy == ey and sx > ex) then sy,sx,ey,ex = ey,ex,sy,sx end
  return sy,sx,ey,ex
end

local function get_selected_text(buf)
  local sy,sx,ey,ex = get_selection_bounds(buf)
  if not sy then return "" end
  if sy == ey then return string.sub(buf.lines[sy] or "", sx + 1, ex) end
  local res = string.sub(buf.lines[sy] or "", sx + 1) .. "\n"
  for i = sy + 1, ey - 1 do res = res .. (buf.lines[i] or "") .. "\n" end
  res = res .. string.sub(buf.lines[ey] or "", 1, ex)
  return res
end

local function delete_selection(buf)
  local sy,sx,ey,ex = get_selection_bounds(buf)
  if not sy then return end
  buf.undo:push(get_state())
  if sy == ey then
    local line = buf.lines[sy] or ""
    buf.lines[sy] = string.sub(line, 1, sx) .. string.sub(line, ex + 1)
  else
    local first = string.sub(buf.lines[sy] or "", 1, sx)
    local last = string.sub(buf.lines[ey] or "", ex + 1)
    buf.lines[sy] = first .. last
    for i = ey, sy + 1, -1 do table.remove(buf.lines, i) end
  end
  buf.cy, buf.cx = sy, sx
  buf.sel_start_x, buf.sel_start_y, buf.sel_end_x, buf.sel_end_y = nil, nil, nil, nil
  buf.modified = true
end

local function find_matching_bracket(buf, line, col)
  local line_text = buf.lines[line] or ""
  local ch = string.sub(line_text, col + 1, col + 1)
  if not ch or ch == "" then return nil end
  local open_chars = {["("]=")", ["["]="]", ["{"]="}"}
  local close_chars = {[")"]="(", ["]"]="[", ["}"]="{"}
  if open_chars[ch] then
    local target,depth = open_chars[ch],1
    for y = line, #buf.lines do
      local text = buf.lines[y] or ""; local start_col = (y==line) and (col+2) or 1
      for x = start_col, #text do
        local c = string.sub(text,x,x)
        if c == ch then depth = depth + 1
        elseif c == target then depth = depth - 1; if depth == 0 then return {y,x-1} end end
      end
    end
  elseif close_chars[ch] then
    local target,depth = close_chars[ch],1
    for y = line, 1, -1 do
      local text = buf.lines[y] or ""; local start_col = (y==line) and col or #text
      for x = start_col, 1, -1 do
        local c = string.sub(text,x,x)
        if c == ch then depth = depth + 1
        elseif c == target then depth = depth - 1; if depth == 0 then return {y,x-1} end end
      end
    end
  end
  return nil
end

-- ============================================================================
-- INPUT HELPERS
-- ============================================================================
local function is_ctrl()
  return btn_safe(KEY_CTRL_L) or btn_safe(KEY_CTRL_R)
end
local function is_shift()
  return btn_safe(KEY_SHIFT_L) or btn_safe(KEY_SHIFT_R)
end

local function change_font_size_by(delta)
  config.font_size = (config.font_size or 20) + delta
  if config.font_size < 8 then config.font_size = 8 end
  local tbl = call_set_editor_font_size and call_set_editor_font_size(config.font_size) or nil
  if tbl and type(tbl) == "table" then
    config.font_w = tbl.font_w or config.font_w
    config.font_h = tbl.font_h or config.font_h
    local lh = tbl.line_h or config.line_h or (config.font_h + 6)
    lh = math.max(lh, math.floor((config.font_h or 16) * 1.35) + 2)
    config.line_h = lh
  else
    config.line_h = math.max(config.line_h, math.floor((config.font_h or 16) * 1.35) + 2)
  end
  call_toast("Font size: " .. tostring(config.font_size), 1.2)
end

-- ============================================================================
-- KEYBOARD HANDLING
-- ============================================================================
local function handle_keyboard()
  ensure_current_tab()
  local buf = cur()
  if not buf then return end
  buf.blink = (buf.blink or 0) + 1

  local ctrl = is_ctrl()
  local shift = is_shift()

  if ctrl then
    if btnp_safe(KEY_TAB) then
      if shift then current_tab = current_tab - 1; if current_tab < 1 then current_tab = #tabs end
      else current_tab = current_tab + 1; if current_tab > #tabs then current_tab = 1 end end
      return
    end

    local ch = kbchar()
    while ch do
      if ch == "+" or ch == "=" then change_font_size_by(2); return end
      if ch == "-" then change_font_size_by(-2); return end
      ch = kbchar()
    end
    if btnp_safe(KEY_EQUALS) then change_font_size_by(2); return end
    if btnp_safe(KEY_MINUS) then change_font_size_by(-2); return end

    if btnp_safe(KEY_C) then if buffer_has_selection(buf) then buf.clipboard = get_selected_text(buf) end; return end
    if btnp_safe(KEY_V) then
      if buf.clipboard and #buf.clipboard > 0 then
        buf.undo:push(get_state())
        if buffer_has_selection(buf) then delete_selection(buf) end
        local line = buf.lines[buf.cy] or ""
        local byte_pos = visual_to_byte_offset(line, buf.cx)
        local before = string.sub(line, 1, byte_pos)
        local after = string.sub(line, byte_pos + 1)
        local lines = {}
        for s in string.gmatch(buf.clipboard, "([^\n]*)\n?") do table.insert(lines, s) end
        if #lines == 0 then return end
        buf.lines[buf.cy] = before .. lines[1]
        for i = 2, #lines do table.insert(buf.lines, buf.cy + i -1, lines[i]) end
        if #lines > 1 then buf.cy = buf.cy + #lines -1; buf.cx = utf8_char_count(lines[#lines]) else buf.cx = buf.cx + utf8_char_count(lines[1]) end
        buf.modified = true
      end
      return
    end
    if btnp_safe(KEY_X) then if buffer_has_selection(buf) then buf.clipboard = get_selected_text(buf); delete_selection(buf) end; return end
    if btnp_safe(KEY_Z) then local s = buf.undo:undo(); if s then buf.lines = s end; return end
    if btnp_safe(KEY_Y) then local s = buf.undo:redo(); if s then buf.lines = s end; return end
    if btnp_safe(KEY_F) then buf.find_mode = not buf.find_mode; return end
    if btnp_safe(KEY_S) then local path = buf.path or current_file; local ok = call_save(path, table.concat(buf.lines, "\n")); if ok then buf.path = path; buf.modified = false; call_toast("Saved " .. path, 1.2) end; return end
    if btnp_safe(KEY_R) then local path = buf.path or current_file; call_run(path); call_toast("Running " .. path, 1.2); return end
  end

  if btnp_safe(KEY_LEFT) then
    if shift and not buffer_has_selection(buf) then buf.sel_start_x, buf.sel_start_y = buf.cx, buf.cy end
    if buf.cx > 0 then buf.cx = buf.cx - 1 elseif buf.cy > 1 then buf.cy = buf.cy -1; buf.cx = utf8_char_count(buf.lines[buf.cy] or "") end
    if shift then buf.sel_end_x, buf.sel_end_y = buf.cx, buf.cy else buf.sel_start_x, buf.sel_start_y, buf.sel_end_x, buf.sel_end_y = nil,nil,nil,nil end
    return
  end
  if btnp_safe(KEY_RIGHT) then
    if shift and not buffer_has_selection(buf) then buf.sel_start_x, buf.sel_start_y = buf.cx, buf.cy end
    if buf.cx < utf8_char_count(buf.lines[buf.cy] or "") then buf.cx = buf.cx + 1 elseif buf.cy < #buf.lines then buf.cy = buf.cy +1; buf.cx = 0 end
    if shift then buf.sel_end_x, buf.sel_end_y = buf.cx, buf.cy else buf.sel_start_x, buf.sel_start_y, buf.sel_end_x, buf.sel_end_y = nil,nil,nil,nil end
    return
  end
  if btnp_safe(KEY_UP) then 
    if shift and not buffer_has_selection(buf) then 
      buf.sel_start_x, buf.sel_start_y = buf.cx, buf.cy 
    end
    if buf.cy > 1 then 
      buf.cy = buf.cy - 1
      buf.cx = math.min(buf.cx, utf8_char_count(buf.lines[buf.cy] or ""))
    end
    if shift then 
      buf.sel_end_x, buf.sel_end_y = buf.cx, buf.cy 
    else 
      buf.sel_start_x, buf.sel_start_y, buf.sel_end_x, buf.sel_end_y = nil,nil,nil,nil 
    end
    return 
  end
  if btnp_safe(KEY_DOWN) then 
    if shift and not buffer_has_selection(buf) then 
      buf.sel_start_x, buf.sel_start_y = buf.cx, buf.cy 
    end
    if buf.cy < #buf.lines then 
      buf.cy = buf.cy + 1
      buf.cx = math.min(buf.cx, utf8_char_count(buf.lines[buf.cy] or ""))
    end
    if shift then 
      buf.sel_end_x, buf.sel_end_y = buf.cx, buf.cy 
    else 
      buf.sel_start_x, buf.sel_start_y, buf.sel_end_x, buf.sel_end_y = nil,nil,nil,nil 
    end
    return 
  end
  if btnp_safe(KEY_HOME) then buf.cx = 0; return end
  if btnp_safe(KEY_END) then buf.cx = utf8_char_count(buf.lines[buf.cy] or ""); return end
  if btnp_safe(KEY_PGUP) then buf.cy = math.max(1, buf.cy - 10); return end
  if btnp_safe(KEY_PGDN) then buf.cy = math.min(#buf.lines, buf.cy + 10); return end

  if btn_safe(KEY_BACK) then
    _back_repeat = _back_repeat + 1
  else
    _back_repeat = 0
  end

  if btnp_safe(KEY_BACK) or (_back_repeat > BACK_INITIAL_DELAY and ((_back_repeat - BACK_INITIAL_DELAY) % BACK_REPEAT_INTERVAL == 0)) then
    if buffer_has_selection(buf) then 
      delete_selection(buf)
    else
      buf.undo:push(get_state())
      if buf.cx > 0 then
        local line = buf.lines[buf.cy] or ""
        local byte_pos = visual_to_byte_offset(line, buf.cx)
        local before = string.sub(line, 1, byte_pos)
        local after = string.sub(line, byte_pos + 1)
        -- Remove last UTF-8 codepoint from before
        local new_before = string.gsub(before, UTF8_CHAR_PATTERN .. "$", "")
        if new_before then before = new_before end
        buf.lines[buf.cy] = before .. after
        buf.cx = math.max(0, buf.cx - 1)
      elseif buf.cy > 1 then
        local current = buf.lines[buf.cy] or ""
        local prev = buf.lines[buf.cy -1] or ""
        buf.cx = utf8_char_count(prev)
        buf.lines[buf.cy -1] = prev .. current
        table.remove(buf.lines, buf.cy)
        buf.cy = buf.cy - 1
      end
      buf.modified = true
    end
    return
  end

  if btnp_safe(KEY_DEL) then
    if buffer_has_selection(buf) then 
      delete_selection(buf)
    else
      buf.undo:push(get_state())
      local line = buf.lines[buf.cy] or ""
      local line_visual_len = utf8_char_count(line)
      if buf.cx < line_visual_len then 
        local byte_pos = visual_to_byte_offset(line, buf.cx)
        local before = string.sub(line, 1, byte_pos)
        local after = string.sub(line, byte_pos + 1)
        -- Remove first character from after
        local new_after = string.gsub(after, "^" .. UTF8_CHAR_PATTERN, "")
        if new_after then after = new_after end
        buf.lines[buf.cy] = before .. after
      elseif buf.cy < #buf.lines then 
        local next_line = buf.lines[buf.cy + 1] or ""
        buf.lines[buf.cy] = line .. next_line
        table.remove(buf.lines, buf.cy + 1)
      end
      buf.modified = true
    end
    return
  end

  if btnp_safe(KEY_ENTER) then
    buf.undo:push(get_state())
    if buffer_has_selection(buf) then delete_selection(buf) end
    local line = buf.lines[buf.cy] or ""
    local byte_pos = visual_to_byte_offset(line, buf.cx)
    local before = string.sub(line, 1, byte_pos)
    local after = string.sub(line, byte_pos + 1)
    local spaces = string.match(before, "^(%s*)") or ""; local indent = #spaces
    local trimmed = string.match(before, "^%s*(.-)%s*$")
    if trimmed and (trimmed:match("^function") or trimmed:match("^if") or trimmed:match("^for") or trimmed:match("^while") or trimmed:match("^repeat") or trimmed == "do" or trimmed:match("then%s*$")) then indent = indent + config.tab_width end
    buf.lines[buf.cy] = before; table.insert(buf.lines, buf.cy + 1, string.rep(" ", indent) .. after); buf.cy = buf.cy + 1; buf.cx = indent; buf.modified = true
    return
  end

  if btnp_safe(KEY_TAB) then
    buf.undo:push(get_state())
    if buffer_has_selection(buf) then delete_selection(buf) end
    local spaces = string.rep(" ", config.tab_width)
    local line = buf.lines[buf.cy] or ""
    local byte_pos = visual_to_byte_offset(line, buf.cx)
    buf.lines[buf.cy] = string.sub(line, 1, byte_pos) .. spaces .. string.sub(line, byte_pos + 1)
    buf.cx = buf.cx + config.tab_width
    buf.modified = true
    return
  end

  local ch = kbchar()
  while ch do
    if not ctrl then
      buf.undo:push(get_state())
      if buffer_has_selection(buf) then delete_selection(buf) end
      local line = buf.lines[buf.cy] or ""
      local byte_pos = visual_to_byte_offset(line, buf.cx)
      buf.lines[buf.cy] = string.sub(line, 1, byte_pos) .. ch .. string.sub(line, byte_pos + 1)
      buf.cx = buf.cx + utf8_char_count(ch)
      buf.modified = true
    end
    ch = kbchar()
  end
end

-- ============================================================================
-- MOUSE / SELECTION / FONT SIZE SCROLL
-- ============================================================================
local old_mouse_click = false
local old_mouse_right = false
local context_open = false
local context_tab = nil
local context_x, context_y = 0, 0

local function mouse_to_editor_pos(m, win_x, win_y, win_w, win_h)
  ensure_current_tab()
  local b = cur()
  if not b then return 1, 0 end
  local content_h = win_h - config.help_h - controls.height - 30
  local content_top = win_y + win_h - controls.height - 30
  local rel_x = m.x - (win_x + 40)
  local dist_from_top = content_top - m.y
  local line_idx = b.scroll_y + math.floor(dist_from_top / config.line_h) + 1
  local col = math.floor((rel_x - 4) / config.font_w)
  if col < 0 then col = 0 end
  line_idx = math.max(1, math.min(#b.lines, line_idx))
  local visual_len = utf8_char_count(b.lines[line_idx] or "")
  col = math.max(0, math.min(visual_len, col))
  return line_idx, col
end

local function draw_tabs_bar(win_x, win_y, win_w, win_h)
  local tb_y = win_y + win_h - controls.height - 30
  if rect then rect(win_x, tb_y, win_w, 30, safe_col(config.colors.gutter_bg)) end
  local tx = win_x + 8
  for i,t in ipairs(tabs) do
    local label = t.path or ("untitled" .. tostring(i))
    local w = math.max(60, (#label + 2) * config.font_w)
    if print then print(label, tx + 6, tb_y + 20, safe_col(i == current_tab and config.colors.help_example or config.colors.help_text)) end
    if rect then rect(tx, tb_y + 6, w, 20, safe_col(i == current_tab and config.colors.help_bg or config.colors.gutter_bg)) end
    tx = tx + w + 6
  end
  local plus_x = tx
  if rect then rect(plus_x, tb_y + 6, 36, 20, safe_col(config.colors.help_bg)) end
  if print then print("+", plus_x + 12, tb_y + 20, safe_col(config.colors.help_text)) end
  return tb_y, plus_x
end

function _update(win_x, win_y, win_w, win_h)
  win_x = win_x or 0; win_y = win_y or 0; win_w = win_w or config.win_min_width; win_h = win_h or config.win_min_height

  handle_keyboard()

  local m = get_mouse()
  if m then
    if m.scroll and m.scroll ~= 0 and is_ctrl() then
      if m.scroll > 0 then change_font_size_by(2) else change_font_size_by(-2) end
    end

    local content_h = win_h - config.help_h - controls.height - 30
    local content_top = win_y + win_h - controls.height - 30
    local editor_top = win_y + config.help_h
    local editor_left = win_x + 40
    local editor_right = win_x + win_w

    if m.click and not old_mouse_click and m.y >= editor_top and m.y <= content_top and m.x >= editor_left and m.x <= editor_right then
      ensure_current_tab()
      local line_idx, col = mouse_to_editor_pos(m, win_x, win_y, win_w, win_h)
      local b = cur()
      if b then
        b.sel_start_x, b.sel_start_y = col, line_idx
        b.sel_end_x, b.sel_end_y = col, line_idx
        b.mouse_selecting = true
        b.cx, b.cy = col, line_idx
      end
    end

    if m.left and old_mouse_click and cur() and cur().mouse_selecting then
      local line_idx, col = mouse_to_editor_pos(m, win_x, win_y, win_w, win_h)
      local b = cur()
      if b then
        b.sel_end_x, b.sel_end_y = col, line_idx
        b.cx, b.cy = col, line_idx
      end
    end

    if not m.left and old_mouse_click and cur() and cur().mouse_selecting then
      local b = cur()
      if b then b.mouse_selecting = false end
    end

    local cb_top = win_y + win_h - controls.height
    local rel_x = m.x - win_x
    local rel_y = m.y - cb_top
    if m.click and not old_mouse_click and rel_y >= 0 and rel_y <= controls.height then
      local local_x = rel_x
      for _, b in ipairs(controls.buttons) do
        if local_x >= b.x and local_x <= b.x + b.w then
          if b.id == "save" then
            ensure_current_tab()
            local buf = cur()
            if buf then
              local ok = call_save(buf.path or current_file, table.concat(buf.lines, "\n"))
              if ok then buf.path = buf.path or current_file; buf.modified = false; call_toast("Saved", 1.0) end
            end
          elseif b.id == "run" then
            ensure_current_tab()
            local buf = cur()
            if buf then call_run(buf.path or current_file); call_toast("Running", 1.0) end
          end
        end
      end
    end

    local tb_y, plus_x = draw_tabs_bar(win_x, win_y, win_w, win_h)
    local tab_local_x = m.x - win_x
    local tab_local_y = m.y - tb_y

    if m.click and not old_mouse_click and tab_local_y >= 0 and tab_local_y <= 30 then
      local tx = 8; local clicked_tab = nil
      for i, t in ipairs(tabs) do
        local label = t.path or ("untitled" .. tostring(i))
        local w = math.max(60, (#label + 2) * config.font_w)
        if tab_local_x >= tx and tab_local_x <= tx + w then clicked_tab = i; break end
        tx = tx + w + 6
      end
      if clicked_tab then current_tab = clicked_tab else
        if tab_local_x >= plus_x and tab_local_x <= plus_x + 36 then
          untitled_counter = untitled_counter + 1
          open_tab("untitled" .. tostring(untitled_counter) .. ".lua", "-- new file\n")
          call_toast("New file opened", 1.0)
        end
      end
    end

    if m.right and not old_mouse_right then
      local tb_y2, plus_x2 = draw_tabs_bar(win_x, win_y, win_w, win_h)
      local tab_local_x2 = m.x - win_x
      local tab_local_y2 = m.y - tb_y2
      if tab_local_y2 >= 0 and tab_local_y2 <= 30 then
        local tx = 8; local clicked_tab = nil
        for i, t in ipairs(tabs) do
          local label = t.path or ("untitled" .. tostring(i))
          local w = math.max(60, (#label + 2) * config.font_w)
          if tab_local_x2 >= tx and tab_local_x2 <= tx + w then clicked_tab = i; break end
          tx = tx + w + 6
        end
        if clicked_tab then context_open = true; context_tab = clicked_tab; context_x, context_y = m.x, m.y end
      end
    end

    if context_open and m.click and not old_mouse_click then
      local menu_x, menu_y = context_x, context_y; local menu_w, menu_h = 160, 40
      if m.x >= menu_x and m.x <= menu_x + menu_w and m.y >= menu_y - menu_h and m.y <= menu_y then
        local rel = menu_y - m.y; local option = math.floor(rel / 20) + 1
        if option == 1 then close_tab(context_tab); call_toast("Tab closed", 0.9)
        elseif option == 2 then
          local res = call_import_dialog()
          if res and res.path and res.content then open_tab(res.path:match("([^/\\]+)$") or res.path, res.content); call_toast("Imported", 1.2)
          else call_toast("Import cancelled", 1.2) end
        end
      end
      context_open = false; context_tab = nil
    end

    old_mouse_click = m.click; old_mouse_right = m.right
  end
end

-- ============================================================================
-- DRAW
-- ============================================================================
function _draw(win_x, win_y, win_w, win_h)
  ensure_current_tab()
  win_x = win_x or 0; win_y = win_y or 0; win_w = win_w or config.win_min_width; win_h = win_h or config.win_min_height

  local content_h = win_h - config.help_h - controls.height - 30
  local content_y = win_y + config.help_h
  local content_top = win_y + win_h - controls.height - 30

  if rect then rect(win_x, win_y, win_w, win_h, safe_col(config.colors.bg)) end
  if rect then rect(win_x, content_y, 40, content_h, safe_col(config.colors.gutter_bg)) end

  local cb_x = win_x; local cb_y = win_y + win_h - controls.height
  if rect then rect(cb_x, cb_y, win_w, controls.height, safe_col(config.colors.gutter_bg)) end
  for i,b in ipairs(controls.buttons) do
    local bx,by,bw,bh = cb_x + b.x, cb_y + 6, b.w, controls.height - 12
    if rect then rect(bx,by,bw,bh, safe_col(config.colors.help_bg)) end
    if print then print(b.label, bx + 8, by + math.floor(bh/2) + (config.font_h/2) - 2, safe_col(config.colors.help_text)) end
  end

  local tb_y, plus_x = draw_tabs_bar(win_x, win_y, win_w, win_h)

  local buf = cur()
  if not buf then return end

  local visible_lines = math.floor(content_h / config.line_h)
  local sy,sx,ey,ex = get_selection_bounds(buf)

  for i = 0, visible_lines do
    local line_idx = buf.scroll_y + i + 1
    if line_idx > #buf.lines then break end
    local line_y = content_top - (i * config.line_h) - 4
    if line_y < content_y then break end

    if line_idx == buf.cy and rect then rect(win_x, line_y - config.font_h, win_w, config.line_h, safe_col(config.colors.current_line)) end
    if print then print(tostring(line_idx), win_x + 4, line_y, safe_col(config.colors.gutter_fg)) end

    if sy and line_idx >= sy and line_idx <= ey and rect then
      local sel_x_start = (line_idx == sy) and sx or 0
      local sel_x_end = (line_idx == ey) and ex or utf8_char_count(buf.lines[line_idx] or "")
      if sel_x_end > sel_x_start then
        local sel_pixel_x = win_x + 40 + 4 + (sel_x_start * config.font_w)
        local sel_pixel_w = (sel_x_end - sel_x_start) * config.font_w
        rect(sel_pixel_x, line_y - config.font_h, sel_pixel_w, config.line_h, safe_col(config.colors.selection))
      end
    end

    local tokens = syntax:parse(buf.lines[line_idx] or "")
    local cur_x = win_x + 40 + 4
    local char_pos = 0
    for _, token in ipairs(tokens) do
      if print and token.text then
        local col = token.color
        if token.bracket and buf.bracket_match then
          local by,bx = buf.bracket_match[1], buf.bracket_match[2]
          if (line_idx == buf.cy and char_pos == buf.cx) or (line_idx == by and char_pos == bx) then col = safe_col(config.colors.bracket) end
        end
        print(token.text, cur_x, line_y, col)
        local tlen = utf8_char_count(token.text)
        cur_x = cur_x + (tlen * config.font_w)
        char_pos = char_pos + tlen
      end
    end

    if line_idx == buf.cy and (buf.blink or 0) % 30 < 15 then
      local cursor_x = win_x + 40 + 4 + (buf.cx * config.font_w)
      if rect then rect(cursor_x, line_y - config.font_h, 2, config.line_h - 4, safe_col(config.colors.cursor)) end
    end
  end

  if rect then rect(win_x, win_y, win_w, config.help_h, safe_col(config.colors.help_bg)) end
  if print then
    local stats = string.format("Tab %d/%d  Line %d  Col %d  %d lines", current_tab, #tabs, buf.cy, buf.cx + 1, #buf.lines)
    print(stats, win_x + 4, win_y + 20, safe_col(config.colors.comment))
    print("Ctrl+C/V/X Copy/Paste/Cut  Ctrl+Z/Y Undo/Redo  Ctrl+F Find  Ctrl+S Save  Ctrl+R Run  Ctrl+Tab Switch Tab  Ctrl+/- Font", win_x + 4, win_y + 36, safe_col(config.colors.comment))
  end
end

-- ============================================================================
-- EXPOSE API
-- ============================================================================
CodeEditor.open_tab = function(path, content) open_tab(path, content) end
CodeEditor.close_tab = function(i) close_tab(i) end
CodeEditor.current_tab_index = function() return current_tab end
CodeEditor.get_tabs = function() return tabs end
CodeEditor.save_current_tab = function()
  ensure_current_tab(); local b = cur(); if b and call_save(b.path or current_file, table.concat(b.lines, "\n")) then b.modified = false; call_toast("Saved", 1.2) end
end
CodeEditor.run_current_tab = function()
  ensure_current_tab(); local b = cur(); if b then call_run(b.path or current_file); call_toast("Running",1.2) end
end

return CodeEditor
