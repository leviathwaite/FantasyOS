local Config = require("system/tools/code_module/config")
local Clipboard = require("system/tools/code_module/clipboard")
local Indent = require("system/tools/code_module/indent")
local Search = require("system/tools/code_module/search")

local State = {}

State.tabs = {}
State.current_tab = 1
State.untitled_counter = 1
State.clipboard = ""  -- Kept for backwards compatibility
State.dialog_mode = nil  -- "search", "goto", nil
State.dialog_input = ""

-- Debug helper
local function debug_log(msg)
    if type(log) == "function" then
        pcall(function() log("[State] " .. msg) end)
    end
end

local function btnp_safe(i)
    if type(btnp) ~= "function" then return false end
    local ok, res = pcall(btnp, i)
    return ok and res
end

local function kbchar()
    if type(char) == "function" then local ok, r = pcall(char); if ok then return r end end
    if type(keyboard_char) == "function" then local ok, r = pcall(keyboard_char); if ok then return r end end
    return nil
end

-- Safe toast function with fallback
local function show_toast(msg)
    debug_log("Toast: " .. msg)
    if type(toast) == "function" then
        local ok = pcall(function() toast(msg) end)
        if not ok then
            debug_log("Toast call failed")
        end
    else
        debug_log("Toast function not available")
        -- Fallback: print to console
        if type(log) == "function" then
            pcall(function() log("TOAST: " .. msg) end)
        end
    end
end

-- Undo Stack
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
end
function UndoStack:redo()
  if self.position < #self.stack then self.position = self.position + 1; return self.stack[self.position] end
end

function State.split_lines(txt)
    local lines = {}
    for line in string.gmatch((txt or "").."\n", "(.-)\n") do table.insert(lines, line) end
    if #lines == 0 then lines = {""} end
    return lines
end

function State.new_buffer(path, text)
    return {
        path = path,
        lines = State.split_lines(text),
        undo = UndoStack.new(),
        cx = 0, cy = 1, scroll_y = 0, scroll_x = 0,
        modified = false, blink = 0,
        sel_start_x = nil, sel_start_y = nil, sel_end_x = nil, sel_end_y = nil,
        mouse_selecting = false
    }
end

function State.open_tab(path, content)
    local b = State.new_buffer(path, content)
    table.insert(State.tabs, b)
    State.current_tab = #State.tabs
    debug_log("Opened tab: " .. (path or "untitled"))
    return b
end

function State.get_current()
    if #State.tabs == 0 then
        local content = "-- New File\n"
        if type(project) == "table" and project.read then
            local ok, result = pcall(function() return project.read("main.lua") end)
            if ok and result then content = result end
        end
        State.open_tab("main.lua", content)
    end
    if State.current_tab < 1 then State.current_tab = 1 end
    if State.current_tab > #State.tabs then State.current_tab = #State.tabs end
    return State.tabs[State.current_tab]
end

function State.get_snapshot(buf)
    local st = {}
    for i,l in ipairs(buf.lines) do st[i] = l end
    return st
end

-- Get selected text if any
function State.get_selection_text(buf)
    if not buf.sel_start_y or not buf.sel_end_y then
        return nil
    end
    
    -- Normalize selection (start should be before end)
    local start_y = math.min(buf.sel_start_y, buf.sel_end_y)
    local end_y = math.max(buf.sel_start_y, buf.sel_end_y)
    local start_x, end_x
    
    if buf.sel_start_y < buf.sel_end_y or (buf.sel_start_y == buf.sel_end_y and buf.sel_start_x <= buf.sel_end_x) then
        start_x = buf.sel_start_x
        end_x = buf.sel_end_x
    else
        start_x = buf.sel_end_x
        end_x = buf.sel_start_x
    end
    
    if start_y == end_y then
        -- Single line selection
        local line = buf.lines[start_y] or ""
        return string.sub(line, start_x + 1, end_x)
    else
        -- Multi-line selection
        local text_parts = {}
        for i = start_y, end_y do
            local line = buf.lines[i] or ""
            if i == start_y then
                table.insert(text_parts, string.sub(line, start_x + 1))
            elseif i == end_y then
                table.insert(text_parts, string.sub(line, 1, end_x))
            else
                table.insert(text_parts, line)
            end
        end
        return table.concat(text_parts, "\n")
    end
end

-- Delete selected text
function State.delete_selection(buf)
    if not buf.sel_start_y or not buf.sel_end_y then
        return false
    end
    
    -- Normalize selection
    local start_y = math.min(buf.sel_start_y, buf.sel_end_y)
    local end_y = math.max(buf.sel_start_y, buf.sel_end_y)
    local start_x, end_x
    
    if buf.sel_start_y < buf.sel_end_y or (buf.sel_start_y == buf.sel_end_y and buf.sel_start_x <= buf.sel_end_x) then
        start_x = buf.sel_start_x
        end_x = buf.sel_end_x
    else
        start_x = buf.sel_end_x
        end_x = buf.sel_start_x
    end
    
    if start_y == end_y then
        -- Single line deletion
        local line = buf.lines[start_y] or ""
        buf.lines[start_y] = string.sub(line, 1, start_x) .. string.sub(line, end_x + 1)
    else
        -- Multi-line deletion
        local first_line = buf.lines[start_y] or ""
        local last_line = buf.lines[end_y] or ""
        buf.lines[start_y] = string.sub(first_line, 1, start_x) .. string.sub(last_line, end_x + 1)
        
        -- Remove lines in between
        for i = end_y, start_y + 1, -1 do
            table.remove(buf.lines, i)
        end
    end
    
    -- Move cursor to start of selection
    buf.cy = start_y
    buf.cx = start_x
    
    -- Clear selection
    buf.sel_start_x = nil
    buf.sel_start_y = nil
    buf.sel_end_x = nil
    buf.sel_end_y = nil
    
    buf.modified = true
    return true
end



-- Buffer API Grouping
State.buffer = {}

function State.buffer.cur()
    return State.get_current()
end

function State.buffer.get_tabs()
    return State.tabs
end

function State.buffer.set_current_tab(i)
    if i >= 1 and i <= #State.tabs then
        State.current_tab = i
        debug_log("Switched to tab " .. i)
    end
end

function State.buffer.switch_tab_relative(delta)
    local n = #State.tabs
    if n == 0 then return end
    State.current_tab = State.current_tab + delta
    if State.current_tab > n then State.current_tab = 1 end
    if State.current_tab < 1 then State.current_tab = n end
end

function State.buffer.open_tab(path, content)
    return State.open_tab(path, content)
end

function State.buffer.close_tab(index)
    if index and State.tabs[index] then
        table.remove(State.tabs, index)
        if State.current_tab >= index and State.current_tab > 1 then
            State.current_tab = State.current_tab - 1
        end
    end
end

function State.buffer.increment_untitled()
    State.untitled_counter = State.untitled_counter + 1
end

function State.buffer.get_untitled_counter()
    return State.untitled_counter
end

-- Selection Wrappers
function State.buffer.has_selection(buf)
    return buf.sel_start_y ~= nil
end

function State.buffer.start_selection(buf)
    if not buf.sel_start_y then
        buf.sel_start_y = buf.cy
        buf.sel_start_x = buf.cx
        buf.sel_end_y = buf.cy
        buf.sel_end_x = buf.cx
        buf.mouse_selecting = true
    end
end

function State.buffer.update_selection_end(buf)
    if buf.sel_start_y then
        buf.sel_end_y = buf.cy
        buf.sel_end_x = buf.cx
    end
end

function State.buffer.clear_selection(buf)
    buf.sel_start_y = nil
    buf.sel_start_x = nil
    buf.sel_end_y = nil
    buf.sel_end_x = nil
    buf.mouse_selecting = false
end

function State.buffer.delete_selection(buf)
    return State.delete_selection(buf)
end

-- State/Undo Wrappers
function State.buffer.get_state()
    local buf = State.get_current()
    if not buf then return nil end
    local s = {
        lines = {},
        cx = buf.cx, cy = buf.cy,
        path = buf.path
    }
    for _,l in ipairs(buf.lines) do table.insert(s.lines, l) end
    return s
end

function State.buffer.restore_state(s)
    local buf = State.get_current()
    if not buf or not s then return end
    buf.lines = {}
    for _,l in ipairs(s.lines) do table.insert(buf.lines, l) end
    buf.cx = s.cx
    buf.cy = s.cy
    buf.modified = true
end

-- Expose Config
State.config = Config

-- Expose IO (Bridge to LuaTool globals)
State.io = {
    get_mouse = function() 
        if type(mouse) == "function" then return mouse() end 
        return nil 
    end,
    run = function(path)
        if type(run_project) == "function" then 
            run_project(path)
        elseif type(run) == "function" then
            run(path)
        end
    end,
    save = function(path, content)
        if type(project) == "table" and project.write then
            return project.write(path, content)
        end
        return false
    end,
    toast = show_toast,
    set_editor_font_size = function(sz)
        local new_sz = math.max(6, math.min(64, sz))
        Config.font_size = new_sz
        if type(set_editor_font_size) == "function" then
            local ok, m = pcall(set_editor_font_size, new_sz)
            if ok and m then
                Config.font_w = m.font_w or Config.font_w
                Config.font_h = m.font_h or Config.font_h
                Config.line_h = m.line_h or Config.line_h
            end
        end
        show_toast("Font size: " .. Config.font_size)
    end,
    import_dialog = function()
        -- Placeholder for file dialog
        return nil
    end
}

return State
