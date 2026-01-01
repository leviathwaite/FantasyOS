local Core = require("system/tools/code_module/editor_core")
local Handler = require("system/tools/code_module/input_handler")
local Renderer = require("system/tools/code_module/renderer")
local IO = require("system/tools/code_module/io")

local Editor = {}

function Editor.init()
    -- Failsafe: Create a tab if none exists
    if #Core.tabs == 0 then
        -- Try to read main.lua, otherwise default text
        local content = "-- New Project\n"
        if type(project) == "table" and project.read then
            local p_content = project.read("main.lua")
            if p_content then content = p_content end
        end
        Core.open_tab("main.lua", content)
    end
end

function Editor._update()
    Editor.init() -- Ensure init runs
    Handler.handle_keyboard()
end

function Editor._draw(win_x, win_y, win_w, win_h)
    -- Ensure init runs (prevents nil errors on first frame)
    if #Core.tabs == 0 then Editor.init() end

    -- Draw
    Renderer.draw(win_x, win_y, win_w, win_h)
end

return Editor
