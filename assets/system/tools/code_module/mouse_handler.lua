local Config = require("system/tools/code_module/config")

local MouseHandler = {}

MouseHandler.old_mouse_click = false
MouseHandler.old_mouse_left = false

-- Layout constants matching renderer.lua
local function get_layout(win_h)
    local header_height = Config.header_height or 56
    local tab_bar_height = 40
    local status_height = Config.help_h or 32
    local scrollbar_height = 16
    local gutter_width = 44
    
    local top_y = win_h - header_height
    local tabs_bottom = top_y - tab_bar_height
    local content_top = tabs_bottom
    local content_bottom = status_height + scrollbar_height
    
    return {
        header_height = header_height,
        tab_bar_height = tab_bar_height,
        status_height = status_height,
        scrollbar_height = scrollbar_height,
        gutter_width = gutter_width,
        content_top = content_top,
        content_bottom = content_bottom
    }
end

-- Convert mouse position to editor line and column
function MouseHandler.mouse_to_editor_pos(m, buf, win_x, win_y, win_w, win_h)
    if not buf then return 1, 0 end

    local layout = get_layout(win_h)
    local mouse_y = m.y

    -- Check if mouse is in content area
    if mouse_y > layout.content_top or mouse_y < layout.content_bottom then
        return buf.cy, buf.cx
    end

    -- Distance from top of content area (growing downwards)
    -- Subtract half line_h to center the click within the line
    local line_h = Config.line_h or 20
    local dist_from_content_top = layout.content_top - mouse_y - (line_h / 2)
    local visible_line_index = math.floor(dist_from_content_top / line_h) + 1
    local line_idx = (buf.scroll_y or 0) + visible_line_index
    line_idx = math.max(1, math.min(#buf.lines, line_idx))

    -- Calculate column using current font metrics
    local text_start_x = win_x + layout.gutter_width
    local rel_x = m.x - text_start_x
    local line_text = buf.lines[line_idx] or ""
    local col = 0
    local font_w = Config.font_w or 10

    -- Use text_width for accurate measurement (handles spaces correctly)
    if text_width and rel_x > 0 then
        local best_col = 0
        local best_diff = rel_x  -- Start with max possible difference
        for i = 0, #line_text do
            local width_to_i = 0
            if i > 0 then
                local substr = string.sub(line_text, 1, i)
                width_to_i = text_width(substr) or (i * font_w)
            end
            local diff = math.abs(rel_x - width_to_i)
            if diff < best_diff then
                best_diff = diff
                best_col = i
            end
        end
        col = best_col
    elseif rel_x > 0 then
        col = math.floor(rel_x / font_w)
    end

    col = math.max(0, math.min(#line_text, col))

    return line_idx, col
end

-- Handle mouse input for selection and positioning
function MouseHandler.handle_mouse(buf, win_x, win_y, win_w, win_h)
    local m = nil
    if type(mouse) == "function" then
        local ok, result = pcall(mouse)
        if ok then m = result end
    end

    if not m or not buf then return end

    local layout = get_layout(win_h)
    local mouse_y = m.y

    -- Handle mouse click to start selection
    if m.click and not MouseHandler.old_mouse_click then
        if m.x > win_x + layout.gutter_width and mouse_y <= layout.content_top and mouse_y >= layout.content_bottom then
            local line_idx, col = MouseHandler.mouse_to_editor_pos(m, buf, win_x, win_y, win_w, win_h)
            buf.cy = line_idx
            buf.cx = col
            buf.blink = 0
            buf.mouse_selecting = true
            
            -- Start selection
            buf.sel_start_x = col
            buf.sel_start_y = line_idx
            buf.sel_end_x = col
            buf.sel_end_y = line_idx
        end
    end

    -- Handle mouse drag for selection
    if m.left and MouseHandler.old_mouse_left and buf.mouse_selecting then
        -- Clamp Y to content area
        local clamp_y = math.max(layout.content_bottom, math.min(layout.content_top, mouse_y))
        local temp_m = {x=m.x, y=clamp_y}
        
        local line_idx, col = MouseHandler.mouse_to_editor_pos(temp_m, buf, win_x, win_y, win_w, win_h)
        buf.cy = line_idx
        buf.cx = col
        
        -- Update selection end
        buf.sel_end_x = col
        buf.sel_end_y = line_idx
    end

    -- Handle mouse release to end selection
    if not m.left and MouseHandler.old_mouse_left and buf.mouse_selecting then
        buf.mouse_selecting = false
        if buf.sel_start_x == buf.sel_end_x and buf.sel_start_y == buf.sel_end_y then
            buf.sel_start_x = nil
            buf.sel_start_y = nil
            buf.sel_end_x = nil
            buf.sel_end_y = nil
        end
    end

    MouseHandler.old_mouse_click = m.click
    MouseHandler.old_mouse_left = m.left
end

return MouseHandler
