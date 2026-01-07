-- disk/system/editor/init.lua
-- Main code editor entry point

_TITLE = "Code Editor"

local theme = require("system.editor.theme")

-- Editor state
local editor = {
    filename = "main.lua",
    cursor_line = 1,
    cursor_col = 0,
    scroll_y = 0,
    cursor_blink = 0,
    modified = false,
}

-- Font metrics (will be set on init)
local metrics = {
    char_width = 8,
    line_height = 16,
}

-- Initialize editor
function _init()
    print("Code Editor initializing...")
    
    -- Get font metrics
    if get_font_metrics then
        metrics = get_font_metrics()
        print("Font metrics: char_width=" .. metrics.char_width .. " line_height=" .. metrics.line_height)
    end
    
    -- Initialize buffer with sample content
    if buffer and buffer.set_content then
        local content = [[-- Welcome to the Code Editor
function _init()
    x = 240
    y = 136
end

function _update()
    -- Update game logic here
end

function _draw()
    -- Draw graphics here
    set_color(212, 212, 212)
    draw_text("Hello, World!", x, y)
end
]]
        buffer.set_content(content)
        print("Buffer initialized with " .. buffer.line_count() .. " lines")
    end
    
    print("Code Editor initialized successfully")
end

-- Update editor state
function _update()
    -- Update cursor blink
    editor.cursor_blink = editor.cursor_blink + 1
    if editor.cursor_blink > 60 then
        editor.cursor_blink = 0
    end
end

-- Draw editor
function _draw()
    local sw = screen_width()
    local sh = screen_height()
    
    -- Draw background
    set_color(theme.to_color(theme.colors.background))
    fill_rect(0, 0, sw, sh)
    
    -- Draw gutter background
    local gutter_width = 60
    set_color(theme.to_color(theme.colors.gutter_bg))
    fill_rect(0, 0, gutter_width, sh - 30)
    
    -- Draw status bar
    set_color(theme.to_color(theme.colors.status_bar_bg))
    fill_rect(0, sh - 30, sw, 30)
    
    -- Draw text content
    if buffer and buffer.line_count then
        local line_count = buffer.line_count()
        local visible_lines = math.floor((sh - 30) / metrics.line_height)
        local start_line = math.max(1, editor.scroll_y + 1)
        local end_line = math.min(line_count, start_line + visible_lines)
        
        begin_batch()
        
        for i = start_line, end_line do
            local y = (i - start_line) * metrics.line_height + metrics.line_height
            
            -- Draw line number
            set_color(theme.to_color(theme.colors.gutter_text))
            draw_text_colored(tostring(i), 10, y, theme.to_color(theme.colors.gutter_text))
            
            -- Get line text
            local line = buffer.get_line(i) or ""
            
            -- Highlight current line
            if i == editor.cursor_line then
                -- Draw current line background
                end_batch()
                set_color(theme.to_color(theme.colors.current_line))
                fill_rect(gutter_width, y - metrics.line_height + 2, sw - gutter_width, metrics.line_height)
                begin_batch()
            end
            
            -- Tokenize and draw line with syntax highlighting
            if tokenizer and tokenizer.tokenize then
                local tokens = tokenizer.tokenize(line, "lua")
                local x = gutter_width + 10
                
                for j = 1, #tokens do
                    local token = tokens[j]
                    local color = theme.colors.text
                    
                    if token.type == "keyword" then
                        color = theme.colors.syntax_keyword
                    elseif token.type == "string" then
                        color = theme.colors.syntax_string
                    elseif token.type == "number" then
                        color = theme.colors.syntax_number
                    elseif token.type == "comment" then
                        color = theme.colors.syntax_comment
                    elseif token.type == "function" then
                        color = theme.colors.syntax_function
                    elseif token.type == "operator" then
                        color = theme.colors.syntax_operator
                    elseif token.type == "variable" then
                        color = theme.colors.syntax_variable_local
                    end
                    
                    draw_text_colored(token.text, x, y, theme.to_color(color))
                    x = x + measure_text(token.text)
                end
            else
                -- Fallback: draw plain text
                set_color(theme.to_color(theme.colors.text))
                draw_text_colored(line, gutter_width + 10, y, theme.to_color(theme.colors.text))
            end
        end
        
        -- Draw cursor
        if editor.cursor_blink < 30 then
            local cursor_x = gutter_width + 10 + (editor.cursor_col * metrics.char_width)
            local cursor_y = (editor.cursor_line - start_line) * metrics.line_height + metrics.line_height
            
            set_color(theme.to_color(theme.colors.cursor))
            draw_line(cursor_x, cursor_y - metrics.line_height + 2, cursor_x, cursor_y + 2)
        end
        
        end_batch()
    end
    
    -- Draw status bar text
    begin_batch()
    set_color(theme.to_color(theme.colors.status_bar_text))
    local status_text = editor.filename .. " | Line " .. editor.cursor_line .. ", Col " .. editor.cursor_col
    if editor.modified then
        status_text = status_text .. " [Modified]"
    end
    draw_text_colored(status_text, 10, sh - 10, theme.to_color(theme.colors.status_bar_text))
    end_batch()
end

return editor
