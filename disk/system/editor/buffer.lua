-- disk/system/editor/buffer.lua
-- Lua-side buffer helpers and utilities

local buffer_helper = {}

-- Split text into lines
function buffer_helper.split_lines(text)
    local lines = {}
    for line in (text .. "\n"):gmatch("(.-)\n") do
        table.insert(lines, line)
    end
    if #lines == 0 then
        lines = {""}
    end
    return lines
end

-- Join lines into text
function buffer_helper.join_lines(lines)
    return table.concat(lines, "\n")
end

-- Get word at cursor position
function buffer_helper.get_word_at_cursor(line, col)
    if not line or col < 0 then
        return ""
    end
    
    -- Find word boundaries
    local start_col = col
    while start_col > 0 do
        local ch = line:sub(start_col, start_col)
        if not ch:match("[%w_.]") then
            start_col = start_col + 1
            break
        end
        start_col = start_col - 1
    end
    
    local end_col = col
    while end_col <= #line do
        local ch = line:sub(end_col, end_col)
        if not ch:match("[%w_]") then
            break
        end
        end_col = end_col + 1
    end
    
    return line:sub(math.max(1, start_col), math.max(0, end_col - 1))
end

-- Get indentation level of line
function buffer_helper.get_indent(line)
    local indent = 0
    for i = 1, #line do
        local ch = line:sub(i, i)
        if ch == " " then
            indent = indent + 1
        elseif ch == "\t" then
            indent = indent + 4
        else
            break
        end
    end
    return indent
end

-- Auto-indent based on previous line
function buffer_helper.auto_indent(prev_line)
    if not prev_line then
        return 0
    end
    
    local indent = buffer_helper.get_indent(prev_line)
    
    -- Check if previous line ends with keywords that increase indentation
    local trimmed = prev_line:match("^%s*(.-)%s*$")
    if trimmed:match("function%s+%w+%s*%(.*%)%s*$") or
       trimmed:match("^function%s*%(.*%)%s*$") or
       trimmed:match("^if%s+.+then%s*$") or
       trimmed:match("^for%s+.+do%s*$") or
       trimmed:match("^while%s+.+do%s*$") or
       trimmed:match("^repeat%s*$") or
       trimmed:match("then%s*$") or
       trimmed:match("do%s*$") then
        indent = indent + 4
    end
    
    return indent
end

return buffer_helper
