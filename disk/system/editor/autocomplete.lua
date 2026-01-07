-- disk/system/editor/autocomplete.lua
-- Autocomplete popup and selection

local autocomplete = {}
local theme = require("system.editor.theme")

autocomplete.visible = false
autocomplete.suggestions = {}
autocomplete.selected = 1
autocomplete.prefix = ""

-- Show autocomplete popup
function autocomplete.show(prefix, cursor_x, cursor_y)
    -- Check if the global autocomplete API is available from Java
    if not _G.autocomplete or not _G.autocomplete.get_suggestions then
        return
    end
    
    autocomplete.prefix = prefix
    autocomplete.suggestions = _G.autocomplete.get_suggestions(prefix, "") or {}
    autocomplete.selected = 1
    autocomplete.visible = (#autocomplete.suggestions > 0)
end

-- Hide autocomplete popup
function autocomplete.hide()
    autocomplete.visible = false
    autocomplete.suggestions = {}
    autocomplete.selected = 1
end

-- Move selection up
function autocomplete.select_previous()
    if autocomplete.selected > 1 then
        autocomplete.selected = autocomplete.selected - 1
    end
end

-- Move selection down
function autocomplete.select_next()
    if autocomplete.selected < #autocomplete.suggestions then
        autocomplete.selected = autocomplete.selected + 1
    end
end

-- Get selected suggestion
function autocomplete.get_selected()
    if autocomplete.visible and #autocomplete.suggestions > 0 then
        return autocomplete.suggestions[autocomplete.selected]
    end
    return nil
end

-- Draw autocomplete popup
function autocomplete.draw(x, y, max_width)
    if not autocomplete.visible or #autocomplete.suggestions == 0 then
        return
    end
    
    local item_height = 20
    local popup_width = math.min(max_width or 200, 200)
    local popup_height = math.min(#autocomplete.suggestions * item_height, 10 * item_height)
    
    -- Background
    set_color(theme.to_color(theme.colors.background))
    fill_rect(x, y, popup_width, popup_height)
    
    -- Border
    set_color(theme.to_color(theme.colors.gutter_text))
    draw_rect(x, y, popup_width, popup_height)
    
    -- Items
    begin_batch()
    for i = 1, math.min(#autocomplete.suggestions, 10) do
        local item_y = y + (i - 1) * item_height
        
        -- Highlight selected
        if i == autocomplete.selected then
            end_batch()
            set_color(theme.to_color(theme.colors.selection))
            fill_rect(x + 1, item_y + 1, popup_width - 2, item_height - 2)
            begin_batch()
        end
        
        -- Draw suggestion text
        local color = (i == autocomplete.selected) and theme.colors.status_bar_text or theme.colors.text
        draw_text_colored(autocomplete.suggestions[i], x + 5, item_y + item_height - 5, theme.to_color(color))
    end
    end_batch()
end

return autocomplete
