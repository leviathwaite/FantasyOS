-- disk/system/editor/split_pane.lua
-- Split-pane view management

local split_pane = {}

split_pane.mode = "single"  -- "single", "vertical", "horizontal", "three"
split_pane.panes = {
    {file = nil, cursor = {line = 1, col = 0}, scroll = 0},
}

-- Split vertically (side by side)
function split_pane.split_vertical()
    if split_pane.mode == "single" then
        split_pane.mode = "vertical"
        table.insert(split_pane.panes, {
            file = nil,
            cursor = {line = 1, col = 0},
            scroll = 0
        })
    end
end

-- Split horizontally (top and bottom)
function split_pane.split_horizontal()
    if split_pane.mode == "single" then
        split_pane.mode = "horizontal"
        table.insert(split_pane.panes, {
            file = nil,
            cursor = {line = 1, col = 0},
            scroll = 0
        })
    end
end

-- Split into three panes
function split_pane.split_three()
    split_pane.mode = "three"
    while #split_pane.panes < 3 do
        table.insert(split_pane.panes, {
            file = nil,
            cursor = {line = 1, col = 0},
            scroll = 0
        })
    end
end

-- Close split and return to single pane
function split_pane.close_split()
    split_pane.mode = "single"
    split_pane.panes = {split_pane.panes[1]}
end

-- Get pane layout (returns array of pane bounds)
function split_pane.get_layout(x, y, width, height)
    local layouts = {}
    
    if split_pane.mode == "single" then
        table.insert(layouts, {x = x, y = y, width = width, height = height, index = 1})
    elseif split_pane.mode == "vertical" then
        local half_width = math.floor(width / 2)
        table.insert(layouts, {x = x, y = y, width = half_width - 2, height = height, index = 1})
        table.insert(layouts, {x = x + half_width + 2, y = y, width = half_width - 2, height = height, index = 2})
    elseif split_pane.mode == "horizontal" then
        local half_height = math.floor(height / 2)
        table.insert(layouts, {x = x, y = y, width = width, height = half_height - 2, index = 1})
        table.insert(layouts, {x = x, y = y + half_height + 2, width = width, height = half_height - 2, index = 2})
    elseif split_pane.mode == "three" then
        local third_width = math.floor(width / 3)
        table.insert(layouts, {x = x, y = y, width = third_width - 2, height = height, index = 1})
        table.insert(layouts, {x = x + third_width + 2, y = y, width = third_width - 2, height = height, index = 2})
        table.insert(layouts, {x = x + third_width * 2 + 4, y = y, width = third_width - 2, height = height, index = 3})
    end
    
    return layouts
end

-- Draw split pane separators
function split_pane.draw_separators(x, y, width, height, theme)
    if split_pane.mode == "vertical" then
        local half_width = math.floor(width / 2)
        set_color(theme.to_color(theme.colors.gutter_text))
        fill_rect(x + half_width - 1, y, 2, height)
    elseif split_pane.mode == "horizontal" then
        local half_height = math.floor(height / 2)
        set_color(theme.to_color(theme.colors.gutter_text))
        fill_rect(x, y + half_height - 1, width, 2)
    elseif split_pane.mode == "three" then
        local third_width = math.floor(width / 3)
        set_color(theme.to_color(theme.colors.gutter_text))
        fill_rect(x + third_width - 1, y, 2, height)
        fill_rect(x + third_width * 2 - 1, y, 2, height)
    end
end

return split_pane
