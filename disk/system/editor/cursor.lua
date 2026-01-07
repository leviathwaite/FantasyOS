-- disk/system/editor/cursor.lua
-- Cursor movement and positioning logic

local cursor = {}

-- Move cursor left
function cursor.move_left(line, col)
    if col > 0 then
        return line, col - 1
    elseif line > 1 then
        -- Move to end of previous line
        return line - 1, -1  -- -1 means "end of line"
    end
    return line, col
end

-- Move cursor right
function cursor.move_right(line, col, max_col)
    if col < max_col then
        return line, col + 1
    else
        -- Move to start of next line
        return line + 1, 0
    end
end

-- Move cursor up
function cursor.move_up(line, col)
    if line > 1 then
        return line - 1, col
    end
    return line, col
end

-- Move cursor down
function cursor.move_down(line, col, max_line)
    if line < max_line then
        return line + 1, col
    end
    return line, col
end

-- Move to start of line
function cursor.move_home(line)
    return line, 0
end

-- Move to end of line
function cursor.move_end(line, max_col)
    return line, max_col
end

-- Move to start of document
function cursor.move_doc_start()
    return 1, 0
end

-- Move to end of document
function cursor.move_doc_end(max_line, max_col)
    return max_line, max_col
end

-- Move by word (left)
function cursor.word_left(line_text, col)
    if col <= 0 then
        return col
    end
    
    -- Skip whitespace
    while col > 0 and line_text:sub(col, col):match("%s") do
        col = col - 1
    end
    
    -- Skip word characters
    while col > 0 and line_text:sub(col, col):match("[%w_]") do
        col = col - 1
    end
    
    return col
end

-- Move by word (right)
function cursor.word_right(line_text, col)
    local len = #line_text
    if col >= len then
        return col
    end
    
    -- Skip word characters
    while col < len and line_text:sub(col + 1, col + 1):match("[%w_]") do
        col = col + 1
    end
    
    -- Skip whitespace
    while col < len and line_text:sub(col + 1, col + 1):match("%s") do
        col = col + 1
    end
    
    return col
end

-- Page up (move up by visible lines)
function cursor.page_up(line, visible_lines)
    return math.max(1, line - visible_lines), 0
end

-- Page down (move down by visible lines)
function cursor.page_down(line, visible_lines, max_line)
    return math.min(max_line, line + visible_lines), 0
end

return cursor
