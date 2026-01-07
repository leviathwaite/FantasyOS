-- disk/system/editor/file_browser.lua
-- File navigation panel

local file_browser = {}
local theme = require("system.editor.theme")

file_browser.visible = false
file_browser.files = {}
file_browser.selected = 1
file_browser.path = ""

-- Load file list
function file_browser.load(path)
    file_browser.path = path or ""
    file_browser.files = {}
    file_browser.selected = 1
    
    if project and project.list then
        local list = project.list(path)
        if list then
            for i = 1, #list do
                table.insert(file_browser.files, list[i])
            end
        end
    end
end

-- Show file browser
function file_browser.show()
    file_browser.visible = true
    file_browser.load("")
end

-- Hide file browser
function file_browser.hide()
    file_browser.visible = false
end

-- Move selection up
function file_browser.select_previous()
    if file_browser.selected > 1 then
        file_browser.selected = file_browser.selected - 1
    end
end

-- Move selection down
function file_browser.select_next()
    if file_browser.selected < #file_browser.files then
        file_browser.selected = file_browser.selected + 1
    end
end

-- Get selected file
function file_browser.get_selected()
    if #file_browser.files > 0 then
        return file_browser.files[file_browser.selected]
    end
    return nil
end

-- Open selected file
function file_browser.open_selected()
    local selected = file_browser.get_selected()
    if not selected then
        return nil
    end
    
    -- Check if it's a directory
    if selected:sub(-1) == "/" then
        local dir_name = selected:sub(1, -2)
        local new_path = file_browser.path ~= "" and (file_browser.path .. "/" .. dir_name) or dir_name
        file_browser.load(new_path)
        return nil
    end
    
    -- It's a file, read it
    if project and project.read then
        local file_path = file_browser.path ~= "" and (file_browser.path .. "/" .. selected) or selected
        local content = project.read(file_path)
        return {
            path = file_path,
            content = content
        }
    end
    
    return nil
end

-- Draw file browser
function file_browser.draw(x, y, width, height)
    if not file_browser.visible then
        return
    end
    
    -- Background
    set_color(theme.to_color(theme.colors.gutter_bg))
    fill_rect(x, y, width, height)
    
    -- Border
    set_color(theme.to_color(theme.colors.gutter_text))
    draw_rect(x, y, width, height)
    
    -- Header
    set_color(theme.to_color(theme.colors.status_bar_bg))
    fill_rect(x, y, width, 30)
    
    begin_batch()
    draw_text_colored("Files", x + 10, y + 20, theme.to_color(theme.colors.status_bar_text))
    
    -- Current path
    if file_browser.path ~= "" then
        draw_text_colored(file_browser.path, x + 10, y + 45, theme.to_color(theme.colors.gutter_text))
    end
    
    -- File list
    local item_height = 20
    local start_y = y + 60
    local visible_items = math.floor((height - 60) / item_height)
    
    for i = 1, math.min(#file_browser.files, visible_items) do
        local item_y = start_y + (i - 1) * item_height
        
        -- Highlight selected
        if i == file_browser.selected then
            end_batch()
            set_color(theme.to_color(theme.colors.selection))
            fill_rect(x + 1, item_y + 1, width - 2, item_height - 2)
            begin_batch()
        end
        
        -- Draw file name
        local color = (i == file_browser.selected) and theme.colors.status_bar_text or theme.colors.text
        local file_name = file_browser.files[i]
        
        -- Add icon for directories
        if file_name:sub(-1) == "/" then
            file_name = "📁 " .. file_name:sub(1, -2)
        else
            file_name = "📄 " .. file_name
        end
        
        draw_text_colored(file_name, x + 10, item_y + item_height - 5, theme.to_color(color))
    end
    
    end_batch()
end

return file_browser
