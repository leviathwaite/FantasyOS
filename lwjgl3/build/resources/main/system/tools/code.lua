-- system/tools/code_diagnostic.lua
-- Diagnostic version showing coordinate system conversion

local Config = require("system/tools/code_module/config")
local State = require("system/tools/code_module/editor_state")
local Renderer = require("system/tools/code_module/renderer")

local tool_width = 1024
local tool_height = 1024
local old_mouse_click = false
local old_mouse_left = false

-- Debug state
local last_mouse_x = 0
local last_mouse_y = 0
local last_mouse_y_converted = 0
local last_click_line = 0
local last_click_col = 0
local show_debug_overlay = true

local function mouse_to_editor_pos(m, win_x, win_y, win_w, win_h)
    local buf = State.get_current()
    if not buf then return 1, 0 end

    local gutter_px = 44
    local tab_bar_height = 30
    local controls_height = Config.controls.height or 40
    local help_height = Config.help_h or 60

    -- Store raw mouse values
    last_mouse_x = m.x
    last_mouse_y = m.y

    -- Convert mouse Y from bottom-left to top-left
    local mouse_y_top_left = win_h - m.y
    last_mouse_y_converted = mouse_y_top_left

    -- Content boundaries (top-left coordinates)
    local content_top = help_height
    local content_bottom = win_h - controls_height - tab_bar_height
    local content_height = content_bottom - content_top

    if mouse_y_top_left < content_top or mouse_y_top_left > content_bottom then
        return buf.cy, buf.cx
    end

    local dist_from_content_top = mouse_y_top_left - content_top
    local visible_line_index = math.floor(dist_from_content_top / Config.line_h)
    local line_idx = (buf.scroll_y or 0) + visible_line_index + 1
    line_idx = math.max(1, math.min(#buf.lines, line_idx))

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

    last_click_line = line_idx
    last_click_col = col

    return line_idx, col
end

local function handle_mouse(win_x, win_y, win_w, win_h)
    local m = nil
    if type(mouse) == "function" then
        local ok, result = pcall(mouse)
        if ok then m = result end
    end

    if not m then return end

    local buf = State.get_current()
    if not buf then return end

    local mouse_y_top_left = win_h - m.y
    local controls_height = Config.controls.height or 40
    local tab_bar_height = 30
    local help_height = Config.help_h or 60
    local content_top = help_height
    local content_bottom = win_h - controls_height - tab_bar_height

    if m.click and not old_mouse_click then
        if m.x > win_x + 44 and mouse_y_top_left >= content_top and mouse_y_top_left <= content_bottom then
            local line_idx, col = mouse_to_editor_pos(m, win_x, win_y, win_w, win_h)
            buf.cy = line_idx
            buf.cx = col
            buf.blink = 0
            buf.mouse_selecting = true
        end
    end

    if m.left and old_mouse_left and buf.mouse_selecting then
        if m.x > win_x + 44 and mouse_y_top_left >= content_top and mouse_y_top_left <= content_bottom then
            local line_idx, col = mouse_to_editor_pos(m, win_x, win_y, win_w, win_h)
            buf.cy = line_idx
            buf.cx = col
        end
    end

    if not m.left and old_mouse_left and buf.mouse_selecting then
        buf.mouse_selecting = false
    end

    old_mouse_click = m.click
    old_mouse_left = m.left
end

function onResize(w, h)
    tool_width = w
    tool_height = h
end

_init = function()
    if #State.tabs == 0 then
        local content = "-- Diagnostic Mode\n-- Mouse: bottom-left (0,0)\n-- Drawing: top-left (0,0)\n-- Conversion: y_top = height - y_bottom\nfunction _init()\n  x=240\n  y=240\nend\n\nfunction _update() end\n\nfunction _draw()\n  print('Test',x,y)\n  cls(2)\nend\n"

        if type(project) == "table" and project.read then
            local ok, result = pcall(function() return project.read("main.lua") end)
            if ok and result then content = result end
        end

        State.open_tab("main.lua", content)
    end
end

_update = function()
    State.handle_input()
    handle_mouse(0, 0, tool_width, tool_height)
end

_draw = function()
    Renderer.draw(State, 0, 0, tool_width, tool_height)

    -- Draw debug overlay at TOP
    if show_debug_overlay and print and rect then
        local buf = State.get_current()
        if buf then
            local debug_x = 10
            local debug_y = 10
            local debug_w = 350
            local debug_h = 140

            -- Background
            rect(debug_x, debug_y, debug_w, debug_h, 1)

            -- Info
            local y = debug_y + 10
            print("=== COORDINATE DEBUG ===", debug_x + 5, y, 10)
            y = y + 15
            print("Mouse (bottom-left): " .. last_mouse_x .. ", " .. last_mouse_y, debug_x + 5, y, 7)
            y = y + 15
            print("Converted (top-left): " .. last_mouse_x .. ", " .. last_mouse_y_converted, debug_x + 5, y, 11)
            y = y + 15
            print("Tool: " .. tool_width .. "x" .. tool_height, debug_x + 5, y, 7)
            y = y + 15
            print("Clicked: Line " .. last_click_line .. ", Col " .. last_click_col, debug_x + 5, y, 11)
            y = y + 15
            print("Cursor: Line " .. buf.cy .. ", Col " .. buf.cx, debug_x + 5, y, 10)
            y = y + 15
            print("Scroll: " .. buf.scroll_y .. " | Total lines: " .. #buf.lines, debug_x + 5, y, 7)
            y = y + 15
            print("Press D to toggle debug", debug_x + 5, y, 13)
        end
    end
end

return State
