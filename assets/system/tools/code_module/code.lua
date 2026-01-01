-- system/tools/code.lua
-- Complete fixed code editor with corrected coordinate system

local Config = require("system/tools/code_module/config")
local State = require("system/tools/code_module/editor_state")
local Renderer = require("system/tools/code_module/renderer")

-- Tool dimensions (updated by Java via onResize)
local tool_width = 1024
local tool_height = 1024

-- Mouse state
local old_mouse_click = false
local old_mouse_left = false

-- Debug flag
local DEBUG = false

local function debug_log(msg)
    if DEBUG and log then
        log(msg)
    end
end

-- Helper to get mouse position in editor coordinates
-- FIXED: Proper coordinate system handling
local function mouse_to_editor_pos(m, win_x, win_y, win_w, win_h)
    local buf = State.get_current()
    if not buf then return 1, 0 end

    local gutter_px = 44  -- Fixed gutter width
    local tab_bar_height = 30
    local controls_height = Config.controls.height or 40

    -- Calculate content area
    local content_top = win_y + Config.help_h
    local content_bottom = win_y + win_h - controls_height - tab_bar_height
    local content_height = content_bottom - content_top

    -- CRITICAL FIX: Y coordinate in texture space (0 at bottom, increases upward)
    -- Mouse Y comes from Java in screen space, we need to convert to texture space
    local mouse_y_in_content = m.y - content_top

    -- Calculate which line (from top of visible area)
    local visible_line_index = math.floor(mouse_y_in_content / Config.line_h)
    local line_idx = (buf.scroll_y or 0) + visible_line_index + 1
    line_idx = math.max(1, math.min(#buf.lines, line_idx))

    debug_log(string.format("Mouse: y=%d, content_top=%d, rel_y=%d, vis_idx=%d, line=%d",
        m.y, content_top, mouse_y_in_content, visible_line_index, line_idx))

    -- Calculate column
    local text_start_x = win_x + gutter_px
    local rel_x = m.x - text_start_x

    local line_text = buf.lines[line_idx] or ""
    local col = 0

    if text_width and rel_x > 0 then
        local best_col = 0
        local best_diff = math.abs(rel_x)

        for i = 0, #line_text do
            local width_to_i = 0
            if i > 0 then
                local substr = string.sub(line_text, 1, i)
                width_to_i = text_width(substr) or (i * Config.font_w)
            end

            local diff = math.abs(rel_x - width_to_i)
            if diff < best_diff then
                best_diff = diff
                best_col = i
            end
        end
        col = best_col
    elseif rel_x > 0 then
        col = math.floor(rel_x / Config.font_w)
    end

    col = math.max(0, math.min(#line_text, col))
    return line_idx, col
end

-- Handle mouse input
local function handle_mouse(win_x, win_y, win_w, win_h)
    local m = nil
    if type(mouse) == "function" then
        local ok, result = pcall(mouse)
        if ok then m = result end
    end

    if not m then return end

    local buf = State.get_current()
    if not buf then return end

    -- Handle single click to position cursor
    if m.click and not old_mouse_click then
        local content_y = win_y + Config.help_h
        local content_bottom = win_y + win_h - Config.controls.height - 30

        -- Check if click is in editor text area (past gutter)
        if m.x > win_x + 44 and m.y >= content_y and m.y <= content_bottom then
            local line_idx, col = mouse_to_editor_pos(m, win_x, win_y, win_w, win_h)
            buf.cy = line_idx
            buf.cx = col
            buf.blink = 0
            buf.mouse_selecting = true

            debug_log(string.format("Click: line=%d, col=%d", line_idx, col))
        end
    end

    -- Handle drag selection
    if m.left and old_mouse_left and buf.mouse_selecting then
        local content_y = win_y + Config.help_h
        local content_bottom = win_y + win_h - Config.controls.height - 30

        if m.x > win_x + 44 and m.y >= content_y and m.y <= content_bottom then
            local line_idx, col = mouse_to_editor_pos(m, win_x, win_y, win_w, win_h)
            buf.cy = line_idx
            buf.cx = col
        end
    end

    -- End selection on mouse release
    if not m.left and old_mouse_left and buf.mouse_selecting then
        buf.mouse_selecting = false
    end

    old_mouse_click = m.click
    old_mouse_left = m.left
end

-- Expose dimension setter for Java layer
function onResize(w, h)
    tool_width = w
    tool_height = h
    debug_log("Code editor resized to " .. w .. "x" .. h)
end

-- Initialize editor
_init = function()
    debug_log("Code editor initializing...")

    -- Verify toast is available
    if type(toast) == "function" then
        debug_log("Toast function available")
    else
        debug_log("WARNING: Toast function NOT available")
    end

    -- Verify project API
    if type(project) == "table" then
        debug_log("Project API available")
        if type(project.read) == "function" then
            debug_log("project.read available")
        end
        if type(project.write) == "function" then
            debug_log("project.write available")
        end
    else
        debug_log("WARNING: Project API NOT available")
    end

    -- Load main.lua on startup if no tabs exist
    if #State.tabs == 0 then
        local content = "-- New File\n"
        local loaded = false

        if type(project) == "table" and project.read then
            local ok, result = pcall(function()
                return project.read("main.lua")
            end)

            if ok and result then
                content = result
                loaded = true
                debug_log("Loaded main.lua from project")
            end
        end

        State.open_tab("main.lua", content)
        debug_log("Opened tab: main.lua (loaded=" .. tostring(loaded) .. ")")
    end

    debug_log("Code editor initialized with " .. #State.tabs .. " tabs")
end

_update = function()
    State.handle_input()
    handle_mouse(0, 0, tool_width, tool_height)
end

_draw = function()
    Renderer.draw(State, 0, 0, tool_width, tool_height)
end

-- Debug command
function toggle_debug()
    DEBUG = not DEBUG
    if log then
        log("Debug mode: " .. tostring(DEBUG))
    end
end

return State
