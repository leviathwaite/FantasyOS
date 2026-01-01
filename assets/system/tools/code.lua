-- system/tools/code.lua
-- Code editor tool

local Config = require("system/tools/code_module/config")
local State = require("system/tools/code_module/editor_state")
local Renderer = require("system/tools/code_module/renderer")
local MouseHandler = require("system/tools/code_module/mouse_handler")

local tool_width = 1024
local tool_height = 1024

function onResize(w, h)
    tool_width = w
    tool_height = h
end

_init = function()
    if #State.tabs == 0 then
        local content = "-- Code Editor\nfunction _init()\n  x=240\n  y=240\nend\n\nfunction _update() end\n\nfunction _draw()\n  print('Test',x,y)\n  cls(2)\nend\n"

        if type(project) == "table" and project.read then
            local ok, result = pcall(function() return project.read("main.lua") end)
            if ok and result then content = result end
        end

        State.open_tab("main.lua", content)
    end
end

_update = function()
    State.handle_input()
    local buf = State.get_current()
    MouseHandler.handle_mouse(buf, 0, 0, tool_width, tool_height)
end

_draw = function()
    Renderer.draw(State, 0, 0, tool_width, tool_height)
end

return State
