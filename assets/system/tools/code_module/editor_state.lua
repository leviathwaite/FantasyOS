local Config = require("system/tools/code_module/config")
local State = {}

State.tabs = {}
State.current_tab = 1
State.untitled_counter = 1
State.clipboard = ""

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
        cx = 0, cy = 1, scroll_y = 0,
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

function State.handle_input()
    local buf = State.get_current()
    if not buf then return end
    buf.blink = (buf.blink or 0) + 1

    local K = Config.keys
    local ctrl = btnp_safe(K.CTRL_L) or btnp_safe(K.CTRL_R)

    -- Save (Ctrl+S) - COMPREHENSIVE FIX
    if ctrl and btnp_safe(K.S) then
        debug_log("Save hotkey pressed")

        local save_success = false
        local error_msg = nil

        -- Check if project API exists
        if type(project) ~= "table" then
            error_msg = "Project API not available"
            debug_log(error_msg)
        elseif type(project.write) ~= "function" then
            error_msg = "project.write not available"
            debug_log(error_msg)
        else
            -- Attempt save
            local content = table.concat(buf.lines, "\n")
            local path = buf.path or "main.lua"

            debug_log("Attempting to save to: " .. path)
            debug_log("Content length: " .. #content .. " bytes")

            local ok, result = pcall(function()
                return project.write(path, content)
            end)

            if ok then
                if result then
                    save_success = true
                    buf.modified = false
                    debug_log("Save successful")
                else
                    error_msg = "project.write returned false"
                    debug_log(error_msg)
                end
            else
                error_msg = "Save error: " .. tostring(result)
                debug_log(error_msg)
            end
        end

        -- Show feedback
        if save_success then
            show_toast("✓ Saved " .. (buf.path or "file"))
        else
            show_toast("✗ Save failed: " .. (error_msg or "unknown error"))
        end

        return
    end

    -- Run (Ctrl+R)
    if ctrl and btnp_safe(K.R) then
        debug_log("Run hotkey pressed")
        if type(run_project) == "function" then
            local ok = pcall(function() run_project(buf.path) end)
            if ok then
                show_toast("▶ Running " .. (buf.path or "file"))
            else
                show_toast("✗ Run failed")
            end
        else
            show_toast("✗ Run not available")
        end
        return
    end

    -- Copy (Ctrl+C)
    if ctrl and btnp_safe(K.C) then
        local selected_text = State.get_selection_text(buf)
        if selected_text then
            State.clipboard = selected_text
            show_toast("📋 Copied " .. #selected_text .. " characters")
        else
            show_toast("No selection to copy")
        end
        return
    end

    -- Cut (Ctrl+X)
    if ctrl and btnp_safe(K.X) then
        local selected_text = State.get_selection_text(buf)
        if selected_text then
            buf.undo:push(State.get_snapshot(buf))
            State.clipboard = selected_text
            State.delete_selection(buf)
            show_toast("✂ Cut " .. #selected_text .. " characters")
        else
            show_toast("No selection to cut")
        end
        return
    end

    -- Paste (Ctrl+V)
    if ctrl and btnp_safe(K.V) then
        if State.clipboard and #State.clipboard > 0 then
            buf.undo:push(State.get_snapshot(buf))
            
            -- Delete selection if any
            if buf.sel_start_y and buf.sel_end_y then
                State.delete_selection(buf)
            end
            
            -- Insert clipboard content
            local lines = State.split_lines(State.clipboard)
            local current_line = buf.lines[buf.cy] or ""
            
            if #lines == 1 then
                -- Single line paste
                buf.lines[buf.cy] = string.sub(current_line, 1, buf.cx) .. lines[1] .. string.sub(current_line, buf.cx + 1)
                buf.cx = buf.cx + #lines[1]
            else
                -- Multi-line paste
                local before = string.sub(current_line, 1, buf.cx)
                local after = string.sub(current_line, buf.cx + 1)
                
                buf.lines[buf.cy] = before .. lines[1]
                for i = 2, #lines - 1 do
                    table.insert(buf.lines, buf.cy + i - 1, lines[i])
                end
                table.insert(buf.lines, buf.cy + #lines - 1, lines[#lines] .. after)
                
                buf.cy = buf.cy + #lines - 1
                buf.cx = #lines[#lines]
            end
            
            buf.modified = true
            show_toast("📄 Pasted " .. #State.clipboard .. " characters")
        else
            show_toast("Clipboard is empty")
        end
        return
    end

    -- Arrow keys with Home/End
    if btnp_safe(K.LEFT) then
        if buf.cx > 0 then buf.cx = buf.cx - 1
        elseif buf.cy > 1 then buf.cy = buf.cy - 1; buf.cx = #(buf.lines[buf.cy] or "") end
        buf.blink = 0
    elseif btnp_safe(K.RIGHT) then
        if buf.cx < #(buf.lines[buf.cy] or "") then buf.cx = buf.cx + 1
        elseif buf.cy < #buf.lines then buf.cy = buf.cy + 1; buf.cx = 0 end
        buf.blink = 0
    elseif btnp_safe(K.UP) then
        if buf.cy > 1 then buf.cy = buf.cy - 1; buf.cx = math.min(buf.cx, #(buf.lines[buf.cy] or "")) end
        buf.blink = 0
    elseif btnp_safe(K.DOWN) then
        if buf.cy < #buf.lines then buf.cy = buf.cy + 1; buf.cx = math.min(buf.cx, #(buf.lines[buf.cy] or "")) end
        buf.blink = 0
    elseif btnp_safe(K.HOME) then
        buf.cx = 0
        buf.blink = 0
    elseif btnp_safe(K.END) then
        buf.cx = #(buf.lines[buf.cy] or "")
        buf.blink = 0
    end

    -- Undo (Ctrl+Z)
    if ctrl and btnp_safe(K.Z) then
        local s = buf.undo:undo()
        if s then
            buf.lines = {}
            for i, line in ipairs(s) do buf.lines[i] = line end
            show_toast("↶ Undo")
        end
        return
    end

    -- Redo (Ctrl+Y)
    if ctrl and btnp_safe(K.Y) then
        local s = buf.undo:redo()
        if s then
            buf.lines = {}
            for i, line in ipairs(s) do buf.lines[i] = line end
            show_toast("↷ Redo")
        end
        return
    end

    -- Typing
    local ch = kbchar()
    while ch do
        if not ctrl then
            buf.undo:push(State.get_snapshot(buf))
            local line = buf.lines[buf.cy] or ""
            buf.lines[buf.cy] = string.sub(line,1,buf.cx) .. ch .. string.sub(line, buf.cx + 1)
            buf.cx = buf.cx + 1
            buf.modified = true
            buf.blink = 0
        end
        ch = kbchar()
    end

    -- Backspace
    if btnp_safe(K.BACK) then
        buf.undo:push(State.get_snapshot(buf))
        if buf.cx > 0 then
            local line = buf.lines[buf.cy]
            buf.lines[buf.cy] = string.sub(line,1,buf.cx-1)..string.sub(line,buf.cx+1)
            buf.cx = buf.cx - 1
        elseif buf.cy > 1 then
            local line = buf.lines[buf.cy]
            local prev = buf.lines[buf.cy-1]
            buf.cx = #prev
            buf.lines[buf.cy-1] = prev .. line
            table.remove(buf.lines, buf.cy)
            buf.cy = buf.cy - 1
        end
        buf.modified = true
        buf.blink = 0
    end

    -- Delete
    if btnp_safe(K.DEL) then
        buf.undo:push(State.get_snapshot(buf))
        local line = buf.lines[buf.cy] or ""
        if buf.cx < #line then
            buf.lines[buf.cy] = string.sub(line,1,buf.cx)..string.sub(line,buf.cx+2)
        elseif buf.cy < #buf.lines then
            buf.lines[buf.cy] = line .. (buf.lines[buf.cy + 1] or "")
            table.remove(buf.lines, buf.cy + 1)
        end
        buf.modified = true
        buf.blink = 0
    end

    -- Enter
    if btnp_safe(K.ENTER) then
        buf.undo:push(State.get_snapshot(buf))
        local line = buf.lines[buf.cy] or ""
        local pre = string.sub(line, 1, buf.cx)
        local post = string.sub(line, buf.cx+1)
        buf.lines[buf.cy] = pre
        table.insert(buf.lines, buf.cy+1, post)
        buf.cy = buf.cy + 1
        buf.cx = 0
        buf.modified = true
        buf.blink = 0
    end

    -- Tab
    if btnp_safe(K.TAB) and not ctrl then
        buf.undo:push(State.get_snapshot(buf))
        local spaces = string.rep(" ", Config.tab_width or 2)
        local line = buf.lines[buf.cy] or ""
        buf.lines[buf.cy] = string.sub(line,1,buf.cx) .. spaces .. string.sub(line, buf.cx + 1)
        buf.cx = buf.cx + #spaces
        buf.modified = true
        buf.blink = 0
    end
end

return State
