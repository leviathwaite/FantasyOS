local Config = require("system/tools/code_module/config")

local Indent = {}

-- Keywords that increase indentation
Indent.increase_keywords = {
    "function", "if", "for", "while", "repeat", "do", "then", "else", "elseif"
}

-- Keywords that decrease indentation
Indent.decrease_keywords = {
    "end", "until", "else", "elseif"
}

-- Get indentation level of a line
function Indent.get_indent_level(line)
    local spaces = 0
    for i = 1, #line do
        local c = string.sub(line, i, i)
        if c == " " then
            spaces = spaces + 1
        elseif c == "\t" then
            spaces = spaces + (Config.tab_width or 2)
        else
            break
        end
    end
    return spaces
end

-- Get indentation string for a line
function Indent.get_indent_string(line)
    local indent = ""
    for i = 1, #line do
        local c = string.sub(line, i, i)
        if c == " " or c == "\t" then
            indent = indent .. c
        else
            break
        end
    end
    return indent
end

-- Check if line ends with keyword that increases indent
function Indent.should_increase_indent(line)
    local trimmed = string.gsub(line, "^%s*(.-)%s*$", "%1")
    -- Pad with spaces to make matching easier
    local padded = " " .. trimmed .. " "
    
    for _, keyword in ipairs(Indent.increase_keywords) do
        -- Check for whole word match: non-alphanumeric before and after
        if string.find(padded, "[^%w_]" .. keyword .. "[^%w_]") then
            -- Don't increase indent if line also ends with "end"
            if not string.match(trimmed, "end%s*$") then
                return true
            end
        end
    end
    
    return false
end

-- Check if current line starts with keyword that decreases indent
function Indent.should_decrease_indent(line)
    local trimmed = string.gsub(line, "^%s*(.-)%s*$", "%1")
    local padded = " " .. trimmed .. " "
    
    for _, keyword in ipairs(Indent.decrease_keywords) do
        -- Check for start of line match (which means after the initial padding space)
        -- Actually, should_decrease_indent checks if the line STARTS with the keyword.
        -- So we check if padded starts with " "..keyword..boundary
        if string.find(padded, "^ " .. keyword .. "[^%w_]") then
            return true
        end
    end
    
    return false
end

-- Calculate auto-indent for new line
function Indent.calculate_auto_indent(buf)
    if buf.cy < 1 or buf.cy > #buf.lines then
        return ""
    end
    
    local prev_line = buf.lines[buf.cy] or ""
    local base_indent = Indent.get_indent_string(prev_line)
    
    -- Check if previous line increases indentation
    if Indent.should_increase_indent(prev_line) then
        local tab_spaces = string.rep(" ", Config.tab_width or 2)
        return base_indent .. tab_spaces
    end
    
    return base_indent
end

-- Apply auto-indent after Enter key
function Indent.apply_auto_indent(buf)
    local new_indent = Indent.calculate_auto_indent(buf)
    
    if #new_indent > 0 then
        local line = buf.lines[buf.cy] or ""
        buf.lines[buf.cy] = new_indent .. line
        buf.cx = #new_indent
        
        -- Check if current line should decrease indent
        if Indent.should_decrease_indent(line) then
            local tab_width = Config.tab_width or 2
            if #new_indent >= tab_width then
                buf.lines[buf.cy] = string.sub(new_indent, 1, #new_indent - tab_width) .. line
                buf.cx = math.max(0, buf.cx - tab_width)
            end
        end
    end
end

return Indent
