-- disk/system/editor/tooltip.lua
-- Function tooltip display

local tooltip = {}
local theme = require("system.editor.theme")

tooltip.visible = false
tooltip.text = ""
tooltip.x = 0
tooltip.y = 0

-- Show tooltip
function tooltip.show(func_name, x, y)
    if not tooltip or not tooltip.get_tooltip then
        return
    end
    
    local tooltip_text = tooltip.get_tooltip(func_name)
    if tooltip_text then
        tooltip.visible = true
        tooltip.text = tooltip_text
        tooltip.x = x
        tooltip.y = y
    end
end

-- Hide tooltip
function tooltip.hide()
    tooltip.visible = false
    tooltip.text = ""
end

-- Draw tooltip
function tooltip.draw()
    if not tooltip.visible or tooltip.text == "" then
        return
    end
    
    local lines = {}
    for line in tooltip.text:gmatch("([^\n]+)") do
        table.insert(lines, line)
    end
    
    local max_width = 0
    for _, line in ipairs(lines) do
        local width = measure_text and measure_text(line) or (#line * 8)
        if width > max_width then
            max_width = width
        end
    end
    
    local line_height = 16
    local padding = 10
    local width = max_width + padding * 2
    local height = #lines * line_height + padding * 2
    
    -- Shadow
    set_color(0, 0, 0, 0.5)
    fill_rect(tooltip.x + 4, tooltip.y + 4, width, height)
    
    -- Background
    set_color(theme.to_color(theme.colors.background))
    fill_rect(tooltip.x, tooltip.y, width, height)
    
    -- Border
    set_color(theme.to_color(theme.colors.gutter_text))
    draw_rect(tooltip.x, tooltip.y, width, height)
    
    -- Text
    begin_batch()
    for i, line in ipairs(lines) do
        local y = tooltip.y + padding + (i - 1) * line_height + line_height - 4
        draw_text_colored(line, tooltip.x + padding, y, theme.to_color(theme.colors.text))
    end
    end_batch()
end

return tooltip
