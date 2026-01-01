local Config = require("system/tools/code_module/config")

local Brackets = {}

-- Bracket pairs
Brackets.pairs = {
    ["("] = ")",
    ["["] = "]",
    ["{"] = "}"
}

Brackets.reverse_pairs = {
    [")"] = "(",
    ["]"] = "[",
    ["}"] = "{"
}

-- Find matching bracket for cursor position
function Brackets.find_matching_bracket(buf)
    if not buf or buf.cy < 1 or buf.cy > #buf.lines then
        return nil
    end
    
    local line = buf.lines[buf.cy] or ""
    if buf.cx < 0 or buf.cx >= #line then
        return nil
    end
    
    local char = string.sub(line, buf.cx + 1, buf.cx + 1)
    
    -- Check if cursor is on an opening bracket
    if Brackets.pairs[char] then
        return Brackets.find_closing_bracket(buf, buf.cy, buf.cx, char, Brackets.pairs[char])
    end
    
    -- Check if cursor is on a closing bracket
    if Brackets.reverse_pairs[char] then
        return Brackets.find_opening_bracket(buf, buf.cy, buf.cx, char, Brackets.reverse_pairs[char])
    end
    
    return nil
end

-- Find closing bracket
function Brackets.find_closing_bracket(buf, start_line, start_col, open_char, close_char)
    local depth = 0
    
    for line_idx = start_line, #buf.lines do
        local line = buf.lines[line_idx] or ""
        local start_pos = (line_idx == start_line) and (start_col + 1) or 1
        
        for i = start_pos, #line do
            local c = string.sub(line, i, i)
            if c == open_char then
                depth = depth + 1
            elseif c == close_char then
                if depth == 0 then
                    return line_idx, i - 1
                end
                depth = depth - 1
            end
        end
    end
    
    return nil
end

-- Find opening bracket
function Brackets.find_opening_bracket(buf, start_line, start_col, close_char, open_char)
    local depth = 0
    
    for line_idx = start_line, 1, -1 do
        local line = buf.lines[line_idx] or ""
        local end_pos = (line_idx == start_line) and (start_col + 1) or #line
        
        for i = end_pos, 1, -1 do
            local c = string.sub(line, i, i)
            if c == close_char then
                depth = depth + 1
            elseif c == open_char then
                if depth == 0 then
                    return line_idx, i - 1
                end
                depth = depth - 1
            end
        end
    end
    
    return nil
end

-- Draw bracket highlight
function Brackets.draw_highlight(buf, match_line, match_col, win_x, win_y, content_top, gutter_width)
    if not match_line or not match_col then
        return
    end
    
    local line_text = buf.lines[match_line] or ""
    local text_x = win_x + gutter_width
    local line_offset = match_line - buf.scroll_y - 1
    local line_y = content_top + (line_offset * Config.line_h) + 4
    
    -- Calculate x position of bracket
    local bracket_x = text_x
    if match_col > 0 then
        if text_width then
            local text_before = string.sub(line_text, 1, match_col)
            local width = text_width(text_before)
            if width then
                bracket_x = bracket_x + width
            else
                bracket_x = bracket_x + (match_col * Config.font_w)
            end
        else
            bracket_x = bracket_x + (match_col * Config.font_w)
        end
    end
    
    -- Draw highlight rectangle around bracket
    if rect then
        rect(bracket_x - 1, line_y - 2, Config.font_w + 2, Config.line_h - 4, Config.colors.bracket)
    end
end

return Brackets
