local Config = require("system/tools/code_module/config")
local Input = require("system/tools/code_module/input_module")
local WinMan = require("system/tools/code_module/WindowManager")

local Main = {}

-- State Management
Main.tabs = {}
Main.current_tab = 1
Main.current_file = "main.lua"

-- Helper to create buffers
function Main.buffer_new(path, text)
    local b = {
        path = path,
        lines = {}, -- You need a split function here (like in previous code)
        cx = 0, cy = 1, scroll_y = 0,
        modified = false, blink = 0
    }
    -- Simple split for now
    for line in string.gmatch((text or "").."\n", "(.-)\n") do table.insert(b.lines, line) end
    if #b.lines == 0 then b.lines = {""} end
    return b
end

function Main.cur()
    if #Main.tabs == 0 then
        table.insert(Main.tabs, Main.buffer_new("main.lua", "-- New File"))
    end
    return Main.tabs[Main.current_tab]
end

-- Init
function Main.init()
    -- Load initial files if needed
end

-- Update Loop
function Main._update(win_x, win_y, win_w, win_h)
    local buf = Main.cur()
    Input.handle_keyboard(buf, Main)

    local m = Input.get_mouse()
    if m then
        -- Handle mouse logic here if needed
    end
end

-- Draw Loop
function Main._draw(win_x, win_y, win_w, win_h)
    WinMan.draw(Main, win_x, win_y, win_w, win_h)
end

return Main
