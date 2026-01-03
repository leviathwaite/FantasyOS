local Clipboard = {}

Clipboard.content = ""

-- Get selected text from buffer
function Clipboard.get_selection_text(buf)
    if not buf.sel_start_y or not buf.sel_end_y then
        return nil
    end
    
    -- Normalize selection (start should be before end)
    local start_y = math.min(buf.sel_start_y, buf.sel_end_y)
    local end_y = math.max(buf.sel_start_y, buf.sel_end_y)
    local start_x, end_x
    
    if buf.sel_start_y < buf.sel_end_y or (buf.sel_start_y == buf.sel_end_y and buf.sel_start_x <= buf.sel_end_x) then
        start_x = buf.sel_start_x
        end_x = buf.sel_end_x
    else
        start_x = buf.sel_end_x
        end_x = buf.sel_start_x
    end
    
    if start_y == end_y then
        -- Single line selection
        local line = buf.lines[start_y] or ""
        return string.sub(line, start_x + 1, end_x)
    else
        -- Multi-line selection
        local text_parts = {}
        for i = start_y, end_y do
            local line = buf.lines[i] or ""
            if i == start_y then
                table.insert(text_parts, string.sub(line, start_x + 1))
            elseif i == end_y then
                table.insert(text_parts, string.sub(line, 1, end_x))
            else
                table.insert(text_parts, line)
            end
        end
        return table.concat(text_parts, "\n")
    end
end

-- Delete selected text from buffer
function Clipboard.delete_selection(buf)
    if not buf.sel_start_y or not buf.sel_end_y then
        return false
    end
    
    -- Normalize selection
    local start_y = math.min(buf.sel_start_y, buf.sel_end_y)
    local end_y = math.max(buf.sel_start_y, buf.sel_end_y)
    local start_x, end_x
    
    if buf.sel_start_y < buf.sel_end_y or (buf.sel_start_y == buf.sel_end_y and buf.sel_start_x <= buf.sel_end_x) then
        start_x = buf.sel_start_x
        end_x = buf.sel_end_x
    else
        start_x = buf.sel_end_x
        end_x = buf.sel_start_x
    end
    
    if start_y == end_y then
        -- Single line deletion
        local line = buf.lines[start_y] or ""
        buf.lines[start_y] = string.sub(line, 1, start_x) .. string.sub(line, end_x + 1)
    else
        -- Multi-line deletion
        local first_line = buf.lines[start_y] or ""
        local last_line = buf.lines[end_y] or ""
        buf.lines[start_y] = string.sub(first_line, 1, start_x) .. string.sub(last_line, end_x + 1)
        
        -- Remove lines in between
        for i = end_y, start_y + 1, -1 do
            table.remove(buf.lines, i)
        end
    end
    
    -- Move cursor to start of selection
    buf.cy = start_y
    buf.cx = start_x
    
    -- Clear selection
    buf.sel_start_x = nil
    buf.sel_start_y = nil
    buf.sel_end_x = nil
    buf.sel_end_y = nil
    
    buf.modified = true
    return true
end

-- Copy selection to clipboard
function Clipboard.copy(buf)
    local text = Clipboard.get_selection_text(buf)
    if text then
        Clipboard.content = text
        if clipboard and clipboard.set then
            clipboard.set(text)
        end
        return true, #text
    end
    return false, 0
end

-- Cut selection to clipboard
function Clipboard.cut(buf)
    local text = Clipboard.get_selection_text(buf)
    if text then
        Clipboard.content = text
        if clipboard and clipboard.set then
            clipboard.set(text)
        end
        Clipboard.delete_selection(buf)
        return true, #text
    end
    return false, 0
end

-- Paste clipboard content at cursor
function Clipboard.paste(buf, split_lines_fn)
    local content = Clipboard.content
    
    -- Try system clipboard first
    if clipboard and clipboard.get then
        local sys_content = clipboard.get()
        if sys_content then content = sys_content end
    end

    if not content or #content == 0 then
        return false
    end
    
    -- Delete selection if any
    if buf.sel_start_y and buf.sel_end_y then
        Clipboard.delete_selection(buf)
    end
    
    -- Insert clipboard content
    local lines = split_lines_fn(content)
    local current_line = buf.lines[buf.cy] or ""
    
    if #lines == 1 then
        -- Single line paste
        buf.lines[buf.cy] = string.sub(current_line, 1, buf.cx) .. lines[1] .. string.sub(current_line, buf.cx + 1)
        buf.cx = buf.cx + #lines[1]
    else
        -- Multi-line paste
        local before = string.sub(current_line, 1, buf.cx)
        local after = string.sub(current_line, buf.cx + 1)
        
        buf.lines[buf.cy] = before .. lines[1]
        for i = 2, #lines - 1 do
            table.insert(buf.lines, buf.cy + i - 1, lines[i])
        end
        table.insert(buf.lines, buf.cy + #lines - 1, lines[#lines] .. after)
        
        buf.cy = buf.cy + #lines - 1
        buf.cx = #lines[#lines]
    end
    
    buf.modified = true
    return true
end

return Clipboard
