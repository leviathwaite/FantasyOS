local Config = require("system/tools/code_module/config")

local Scroll = {}

-- Ensure cursor is visible vertically
function Scroll.ensure_cursor_visible_vertical(buf, visible_lines)
    if buf.cy <= buf.scroll_y then
        buf.scroll_y = math.max(0, buf.cy - 1)
    elseif buf.cy > buf.scroll_y + visible_lines then
        buf.scroll_y = buf.cy - visible_lines
    end
end

-- Ensure cursor is visible horizontally
function Scroll.ensure_cursor_visible_horizontal(buf, visible_width)
    if not buf.scroll_x then buf.scroll_x = 0 end
    
    local line = buf.lines[buf.cy] or ""
    local cursor_pixel_x = 0
    
    -- Calculate cursor pixel position
    if text_width and buf.cx > 0 then
        local text_before = string.sub(line, 1, buf.cx)
        cursor_pixel_x = text_width(text_before) or (buf.cx * Config.font_w)
    else
        cursor_pixel_x = buf.cx * Config.font_w
    end
    
    -- Scroll left if cursor is off-screen to the left
    if cursor_pixel_x < buf.scroll_x then
        buf.scroll_x = math.max(0, cursor_pixel_x - 20)
    end
    
    -- Scroll right if cursor is off-screen to the right
    if cursor_pixel_x > buf.scroll_x + visible_width then
        buf.scroll_x = cursor_pixel_x - visible_width + 20
    end
end

-- Handle page up
function Scroll.page_up(buf, visible_lines)
    buf.cy = math.max(1, buf.cy - visible_lines)
    buf.cx = math.min(buf.cx, #(buf.lines[buf.cy] or ""))
    buf.scroll_y = math.max(0, buf.scroll_y - visible_lines)
end

-- Handle page down
function Scroll.page_down(buf, visible_lines)
    buf.cy = math.min(#buf.lines, buf.cy + visible_lines)
    buf.cx = math.min(buf.cx, #(buf.lines[buf.cy] or ""))
    buf.scroll_y = math.min(#buf.lines - visible_lines, buf.scroll_y + visible_lines)
end

-- Scroll up by lines
function Scroll.scroll_up(buf, lines)
    buf.scroll_y = math.max(0, buf.scroll_y - lines)
end

-- Scroll down by lines
function Scroll.scroll_down(buf, lines, max_scroll)
    buf.scroll_y = math.min(max_scroll, buf.scroll_y + lines)
end

return Scroll
