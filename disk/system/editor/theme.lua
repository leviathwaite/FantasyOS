-- disk/system/editor/theme.lua
-- Color theme definitions for the code editor

local theme = {}

-- Default Dark Theme
theme.colors = {
    background = {30, 30, 30},
    text = {212, 212, 212},
    cursor = {255, 255, 255},
    current_line = {40, 40, 40},
    selection = {38, 79, 120},
    gutter_bg = {25, 25, 25},
    gutter_text = {133, 133, 133},
    gutter_text_active = {200, 200, 200},
    status_bar_bg = {0, 122, 204},
    status_bar_text = {255, 255, 255},
    
    -- Syntax highlighting colors
    syntax_keyword = {86, 156, 214},
    syntax_string = {206, 145, 120},
    syntax_number = {181, 206, 168},
    syntax_comment = {106, 153, 85},
    syntax_function = {220, 220, 170},
    syntax_function_user = {180, 220, 140},
    syntax_operator = {212, 212, 212},
    syntax_type = {78, 201, 176},
    syntax_variable_local = {156, 220, 254},
    syntax_variable_global = {220, 156, 156},
    syntax_variable_table = {180, 180, 220},
}

-- Convert RGB to 0-1 range for LibGDX
function theme.to_color(rgb)
    return rgb[1] / 255, rgb[2] / 255, rgb[3] / 255, 1.0
end

-- Get color by name
function theme.get_color(name)
    return theme.colors[name] or theme.colors.text
end

return theme
