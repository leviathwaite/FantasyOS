-- disk/system/editor/find_replace.lua
-- Find and replace dialog and logic

local find_replace = {}
local theme = require("system.editor.theme")

find_replace.visible = false
find_replace.mode = "find"  -- "find" or "replace"
find_replace.find_text = ""
find_replace.replace_text = ""
find_replace.matches = {}
find_replace.current_match = 0

-- Show find dialog
function find_replace.show_find()
    find_replace.visible = true
    find_replace.mode = "find"
end

-- Show replace dialog
function find_replace.show_replace()
    find_replace.visible = true
    find_replace.mode = "replace"
end

-- Hide dialog
function find_replace.hide()
    find_replace.visible = false
end

-- Find all matches in buffer
function find_replace.find_all(text)
    find_replace.matches = {}
    find_replace.current_match = 0
    
    if not buffer or not buffer.line_count or text == "" then
        return
    end
    
    local line_count = buffer.line_count()
    for line_num = 1, line_count do
        local line = buffer.get_line(line_num)
        if line then
            local col = 1
            while true do
                local start_pos, end_pos = line:find(text, col, true)
                if not start_pos then
                    break
                end
                table.insert(find_replace.matches, {
                    line = line_num,
                    col = start_pos - 1,
                    length = #text
                })
                col = end_pos + 1
            end
        end
    end
    
    if #find_replace.matches > 0 then
        find_replace.current_match = 1
    end
end

-- Go to next match
function find_replace.next_match()
    if #find_replace.matches == 0 then
        return nil
    end
    
    find_replace.current_match = find_replace.current_match + 1
    if find_replace.current_match > #find_replace.matches then
        find_replace.current_match = 1
    end
    
    return find_replace.matches[find_replace.current_match]
end

-- Go to previous match
function find_replace.prev_match()
    if #find_replace.matches == 0 then
        return nil
    end
    
    find_replace.current_match = find_replace.current_match - 1
    if find_replace.current_match < 1 then
        find_replace.current_match = #find_replace.matches
    end
    
    return find_replace.matches[find_replace.current_match]
end

-- Replace current match
function find_replace.replace_current()
    if #find_replace.matches == 0 or find_replace.current_match == 0 then
        return
    end
    
    local match = find_replace.matches[find_replace.current_match]
    if buffer and buffer.delete_range and buffer.insert_text then
        buffer.delete_range(match.line, match.col, match.line, match.col + match.length)
        buffer.insert_text(match.line, match.col, find_replace.replace_text)
    end
end

-- Replace all matches
function find_replace.replace_all()
    for i = #find_replace.matches, 1, -1 do
        local match = find_replace.matches[i]
        if buffer and buffer.delete_range and buffer.insert_text then
            buffer.delete_range(match.line, match.col, match.line, match.col + match.length)
            buffer.insert_text(match.line, match.col, find_replace.replace_text)
        end
    end
    find_replace.find_all(find_replace.replace_text)
end

-- Draw find/replace dialog
function find_replace.draw(screen_width, screen_height)
    if not find_replace.visible then
        return
    end
    
    local dialog_width = 400
    local dialog_height = find_replace.mode == "replace" and 150 or 100
    local x = (screen_width - dialog_width) / 2
    local y = (screen_height - dialog_height) / 2
    
    -- Shadow
    set_color(0, 0, 0, 0.5)
    fill_rect(x + 4, y + 4, dialog_width, dialog_height)
    
    -- Background
    set_color(theme.to_color(theme.colors.background))
    fill_rect(x, y, dialog_width, dialog_height)
    
    -- Border
    set_color(theme.to_color(theme.colors.gutter_text))
    draw_rect(x, y, dialog_width, dialog_height)
    
    -- Title bar
    set_color(theme.to_color(theme.colors.status_bar_bg))
    fill_rect(x, y, dialog_width, 30)
    
    begin_batch()
    -- Title
    local title = find_replace.mode == "replace" and "Find & Replace" or "Find"
    draw_text_colored(title, x + 10, y + 20, theme.to_color(theme.colors.status_bar_text))
    
    -- Find input
    draw_text_colored("Find:", x + 10, y + 50, theme.to_color(theme.colors.text))
    draw_text_colored(find_replace.find_text .. "_", x + 60, y + 50, theme.to_color(theme.colors.text))
    
    -- Replace input (if in replace mode)
    if find_replace.mode == "replace" then
        draw_text_colored("Replace:", x + 10, y + 80, theme.to_color(theme.colors.text))
        draw_text_colored(find_replace.replace_text .. "_", x + 80, y + 80, theme.to_color(theme.colors.text))
    end
    
    -- Match count
    local status = #find_replace.matches .. " matches"
    if find_replace.current_match > 0 then
        status = "Match " .. find_replace.current_match .. " of " .. #find_replace.matches
    end
    draw_text_colored(status, x + 10, y + dialog_height - 20, theme.to_color(theme.colors.gutter_text))
    
    end_batch()
end

return find_replace
