-- disk/system/editor/keybindings.lua
-- Keyboard shortcut handling

local keybindings = {}

-- Keyboard shortcuts mapping
keybindings.shortcuts = {
    -- File operations
    save = {key = "S", ctrl = true},
    open = {key = "O", ctrl = true},
    new = {key = "N", ctrl = true},
    
    -- Edit operations
    undo = {key = "Z", ctrl = true},
    redo = {key = "Y", ctrl = true},
    copy = {key = "C", ctrl = true},
    cut = {key = "X", ctrl = true},
    paste = {key = "V", ctrl = true},
    select_all = {key = "A", ctrl = true},
    
    -- Search operations
    find = {key = "F", ctrl = true},
    replace = {key = "H", ctrl = true},
    goto_line = {key = "G", ctrl = true},
    
    -- Code operations
    comment = {key = "/", ctrl = true},
    duplicate = {key = "D", ctrl = true},
    
    -- Navigation
    indent = {key = "TAB"},
    unindent = {key = "TAB", shift = true},
}

-- Check if a shortcut matches current input
function keybindings.match(shortcut_name, key, ctrl, shift, alt)
    local shortcut = keybindings.shortcuts[shortcut_name]
    if not shortcut then
        return false
    end
    
    if shortcut.key ~= key then
        return false
    end
    
    if shortcut.ctrl and not ctrl then
        return false
    end
    
    if shortcut.shift and not shift then
        return false
    end
    
    if shortcut.alt and not alt then
        return false
    end
    
    return true
end

-- Get human-readable shortcut text
function keybindings.get_text(shortcut_name)
    local shortcut = keybindings.shortcuts[shortcut_name]
    if not shortcut then
        return ""
    end
    
    local parts = {}
    if shortcut.ctrl then
        table.insert(parts, "Ctrl")
    end
    if shortcut.shift then
        table.insert(parts, "Shift")
    end
    if shortcut.alt then
        table.insert(parts, "Alt")
    end
    table.insert(parts, shortcut.key)
    
    return table.concat(parts, "+")
end

return keybindings
