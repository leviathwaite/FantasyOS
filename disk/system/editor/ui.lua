-- disk/system/editor/ui.lua
-- UI components for tabs, status bar, and dialogs

local ui = {}
local theme = require("system.editor.theme")

-- Draw tab bar
function ui.draw_tabs(tabs, current_tab, x, y, width, height)
    local tab_width = 120
    local tab_x = x
    
    begin_batch()
    
    for i, tab in ipairs(tabs) do
        local is_active = (i == current_tab)
        
        -- Tab background
        if is_active then
            set_color(theme.to_color(theme.colors.background))
        else
            set_color(theme.to_color(theme.colors.gutter_bg))
        end
        
        end_batch()
        fill_rect(tab_x, y, tab_width, height)
        begin_batch()
        
        -- Tab text
        if is_active then
            set_color(theme.to_color(theme.colors.text))
        else
            set_color(theme.to_color(theme.colors.gutter_text))
        end
        
        local display_name = tab.filename or "untitled"
        if tab.modified then
            display_name = display_name .. " *"
        end
        
        draw_text_colored(display_name, tab_x + 10, y + height - 8, 
                         is_active and theme.to_color(theme.colors.text) or theme.to_color(theme.colors.gutter_text))
        
        tab_x = tab_x + tab_width
    end
    
    end_batch()
end

-- Draw status bar
function ui.draw_status_bar(filename, line, col, modified, language, x, y, width, height)
    -- Background
    set_color(theme.to_color(theme.colors.status_bar_bg))
    fill_rect(x, y, width, height)
    
    -- Status text
    begin_batch()
    set_color(theme.to_color(theme.colors.status_bar_text))
    
    local status_parts = {
        filename or "untitled",
        " | ",
        "Line " .. tostring(line),
        ", Col " .. tostring(col),
        " | ",
        language or "text",
    }
    
    if modified then
        table.insert(status_parts, " [Modified]")
    end
    
    local status_text = table.concat(status_parts)
    draw_text_colored(status_text, x + 10, y + height - 10, theme.to_color(theme.colors.status_bar_text))
    end_batch()
end

-- Draw line numbers gutter
function ui.draw_gutter(start_line, end_line, current_line, x, y, width, line_height)
    -- Background
    set_color(theme.to_color(theme.colors.gutter_bg))
    fill_rect(x, y, width, (end_line - start_line + 1) * line_height)
    
    -- Line numbers
    begin_batch()
    for i = start_line, end_line do
        local line_y = y + (i - start_line) * line_height + line_height - 4
        local color = (i == current_line) and theme.colors.gutter_text_active or theme.colors.gutter_text
        draw_text_colored(tostring(i), x + 10, line_y, theme.to_color(color))
    end
    end_batch()
end

-- Draw dialog box
function ui.draw_dialog(title, content, x, y, width, height)
    -- Shadow
    set_color(0, 0, 0, 0.5)
    fill_rect(x + 4, y + 4, width, height)
    
    -- Background
    set_color(theme.to_color(theme.colors.background))
    fill_rect(x, y, width, height)
    
    -- Border
    set_color(theme.to_color(theme.colors.gutter_text))
    draw_rect(x, y, width, height)
    
    -- Title bar
    set_color(theme.to_color(theme.colors.status_bar_bg))
    fill_rect(x, y, width, 30)
    
    begin_batch()
    -- Title text
    set_color(theme.to_color(theme.colors.status_bar_text))
    draw_text_colored(title, x + 10, y + 20, theme.to_color(theme.colors.status_bar_text))
    
    -- Content
    set_color(theme.to_color(theme.colors.text))
    draw_text_colored(content, x + 10, y + 50, theme.to_color(theme.colors.text))
    end_batch()
end

-- Draw scrollbar
function ui.draw_scrollbar(x, y, width, height, scroll_pos, content_height, visible_height)
    -- Background
    set_color(theme.to_color(theme.colors.gutter_bg))
    fill_rect(x, y, width, height)
    
    -- Calculate thumb size and position
    local thumb_height = math.max(20, (visible_height / content_height) * height)
    local thumb_y = y + (scroll_pos / content_height) * height
    
    -- Thumb
    set_color(theme.to_color(theme.colors.gutter_text))
    fill_rect(x + 2, thumb_y, width - 4, thumb_height)
end

return ui
