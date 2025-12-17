-- buffer.lua
local M = {}

local function UTF8_CHAR_PATTERN() return "([%z\1-\127\194-\244][\128-\191]*)" end

local function utf8_char_count(s)
  if not s or s == "" then return 0 end
  local count = 0
  for _ in string.gmatch(s, UTF8_CHAR_PATTERN()) do count = count + 1 end
  return count
end

local function split_lines_from_text(txt)
  if not txt or txt == "" then return {""} end
  local lines = {}
  for line in string.gmatch(txt, "([^\n]*)\n?") do table.insert(lines, line) end
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
    if #self.stack > 100 then table.remove(self.stack, 1); self.position = self.position - 1 end
  end
  function UndoStack:undo()
    if self.position > 1 then self.position = self.position - 1; return self.stack[self.position] end
    return nil
  end
  function UndoStack:redo()
    if self.position < #self.stack then self.position = self.position + 1; return self.stack[self.position] end
    return nil
  end
  return UndoStack.new()
end

-- Internal state
local tabs = {}
local current_tab = 1
local current_file = "main.lua"
local untitled_counter = 1

function M.init(state) end -- placeholder if needed

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

function M.open_tab(path, text)
  local b = buffer_new(path, text)
  table.insert(tabs, b)
  current_tab = #tabs
  if path then current_file = path end
  return b
end

function M.close_tab(idx)
  if idx < 1 or idx > #tabs then return end
  table.remove(tabs, idx)
  if current_tab > #tabs then current_tab = math.max(1, #tabs) end
end

local function ensure_current_tab()
  if #tabs == 0 then
    M.open_tab("main.lua", "-- New file\n")
  end
  if current_tab < 1 then current_tab = 1 end
  if current_tab > #tabs then current_tab = #tabs end
end

function M.cur()
  ensure_current_tab()
  return tabs[current_tab]
end

function M.get_tabs() return tabs end
function M.current_tab_index() return current_tab end

function M.get_state()
  local st = {}
  local b = M.cur()
  for i, ln in ipairs(b.lines) do st[i] = ln end
  return st
end

function M.restore_state(s)
  local b = M.cur()
  b.lines = {}
  for i, ln in ipairs(s) do b.lines[i] = ln end
end

-- Expose little helpers used by input/renderer
M.utf8_char_count = utf8_char_count
M.split_lines = split_lines_from_text

return M
