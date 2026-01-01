local Config = require("system/tools/code_module/config")

local Search = {}

Search.query = ""
Search.replace_text = ""
Search.matches = {}
Search.current_match_index = 0
Search.active = false
Search.replace_mode = false

-- Find all matches in buffer
function Search.find_all(buf, query)
    Search.matches = {}
    Search.current_match_index = 0
    
    if not query or #query == 0 then
        return
    end
    
    for line_idx = 1, #buf.lines do
        local line = buf.lines[line_idx]
        local start_pos = 1
        
        while true do
            local match_start, match_end = string.find(line, query, start_pos, true)
            if not match_start then break end
            
            table.insert(Search.matches, {
                line = line_idx,
                col_start = match_start - 1,
                col_end = match_end
            })
            
            start_pos = match_end + 1
        end
    end
end

-- Go to next match
function Search.find_next(buf)
    if #Search.matches == 0 then
        return false
    end
    
    Search.current_match_index = Search.current_match_index + 1
    if Search.current_match_index > #Search.matches then
        Search.current_match_index = 1
    end
    
    local match = Search.matches[Search.current_match_index]
    buf.cy = match.line
    buf.cx = match.col_start
    
    -- Set selection to highlight the match
    buf.sel_start_y = match.line
    buf.sel_start_x = match.col_start
    buf.sel_end_y = match.line
    buf.sel_end_x = match.col_end
    
    return true
end

-- Go to previous match
function Search.find_previous(buf)
    if #Search.matches == 0 then
        return false
    end
    
    Search.current_match_index = Search.current_match_index - 1
    if Search.current_match_index < 1 then
        Search.current_match_index = #Search.matches
    end
    
    local match = Search.matches[Search.current_match_index]
    buf.cy = match.line
    buf.cx = match.col_start
    
    -- Set selection to highlight the match
    buf.sel_start_y = match.line
    buf.sel_start_x = match.col_start
    buf.sel_end_y = match.line
    buf.sel_end_x = match.col_end
    
    return true
end

-- Replace current match
function Search.replace_current(buf)
    if Search.current_match_index == 0 or #Search.matches == 0 then
        return false
    end
    
    local match = Search.matches[Search.current_match_index]
    local line = buf.lines[match.line]
    
    buf.lines[match.line] = string.sub(line, 1, match.col_start) .. 
                             Search.replace_text .. 
                             string.sub(line, match.col_end + 1)
    
    buf.modified = true
    
    -- Update matches after replacement
    Search.find_all(buf, Search.query)
    
    return true
end

-- Replace all matches
function Search.replace_all(buf)
    if #Search.matches == 0 then
        return 0
    end
    
    local count = 0
    
    -- Replace in reverse order to maintain positions
    for i = #Search.matches, 1, -1 do
        local match = Search.matches[i]
        local line = buf.lines[match.line]
        
        buf.lines[match.line] = string.sub(line, 1, match.col_start) .. 
                                 Search.replace_text .. 
                                 string.sub(line, match.col_end + 1)
        count = count + 1
    end
    
    buf.modified = true
    
    -- Clear matches after replacement
    Search.matches = {}
    Search.current_match_index = 0
    
    return count
end

-- Activate search mode
function Search.activate(query)
    Search.query = query or ""
    Search.active = true
    Search.replace_mode = false
end

-- Activate replace mode
function Search.activate_replace(query, replace_text)
    Search.query = query or ""
    Search.replace_text = replace_text or ""
    Search.active = true
    Search.replace_mode = true
end

-- Deactivate search
function Search.deactivate()
    Search.active = false
    Search.replace_mode = false
    Search.matches = {}
    Search.current_match_index = 0
end

-- Draw search overlay
function Search.draw_overlay(win_x, win_y, win_w, win_h)
    if not Search.active then return end
    
    -- Draw semi-transparent overlay
    local overlay_h = Search.replace_mode and 120 or 80
    local overlay_y = win_y + (win_h - overlay_h) / 2
    
    if rect then
        rect(win_x + 100, overlay_y, win_w - 200, overlay_h, Config.colors.gutter_bg)
    end
    
    if print then
        local label_y = overlay_y + 10
        print("Find: " .. Search.query, win_x + 110, label_y, Config.colors.help_text)
        
        if Search.replace_mode then
            print("Replace: " .. Search.replace_text, win_x + 110, label_y + 30, Config.colors.help_text)
        end
        
        local info = #Search.matches .. " match" .. (#Search.matches ~= 1 and "es" or "")
        if Search.current_match_index > 0 then
            info = Search.current_match_index .. " of " .. info
        end
        print(info, win_x + 110, label_y + (Search.replace_mode and 60 or 30), Config.colors.help_example)
    end
end

return Search
