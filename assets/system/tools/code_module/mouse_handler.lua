local Config = require("system/tools/code_module/config")

local MouseHandler = {}

MouseHandler.old_mouse_click = false
MouseHandler.old_mouse_left = false

-- Convert mouse position to editor line and column
function MouseHandler.mouse_to_editor_pos(m, buf, win_x, win_y, win_w, win_h)
    if not buf then return 1, 0 end

    local gutter_px = 44
    local tab_bar_height = 30
    local controls_height = Config.controls.height or 40
    local help_height = Config.help_h or 60

    -- Convert mouse Y from bottom-left to top-left
    local mouse_y_top_left = win_h - m.y

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

    local mouse_y_top_left = win_h - m.y
    local controls_height = Config.controls.height or 40
    local tab_bar_height = 30
    local help_height = Config.help_h or 60
    local content_top = help_height
    local content_bottom = win_h - controls_height - tab_bar_height

    -- Handle mouse click to start selection
    if m.click and not MouseHandler.old_mouse_click then
        if m.x > win_x + 44 and mouse_y_top_left >= content_top and mouse_y_top_left <= content_bottom then
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
        if m.x > win_x + 44 and mouse_y_top_left >= content_top and mouse_y_top_left <= content_bottom then
            local line_idx, col = MouseHandler.mouse_to_editor_pos(m, buf, win_x, win_y, win_w, win_h)
            buf.cy = line_idx
            buf.cx = col
            
            -- Update selection end
            buf.sel_end_x = col
            buf.sel_end_y = line_idx
        end
    end

    -- Handle mouse release to end selection
    if not m.left and MouseHandler.old_mouse_left and buf.mouse_selecting then
        buf.mouse_selecting = false
        
        -- Clear selection if start and end are the same
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
