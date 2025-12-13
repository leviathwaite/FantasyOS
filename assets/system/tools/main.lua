-- main.lua
local UITheme = require("UITheme")
local WM = require("WindowManager")
local Desktop = require("Desktop")
local CodeEditor = require("CodeEditor")

local theme, wm, desktop

function _init()
    -- Initialize System
    theme = UITheme.new()
    wm = WM.new(theme)
    desktop = Desktop.new(wm, theme)

    -- Setup Code Editor App
    local doc = CodeEditor.Doc.new("main.lua")
    doc:load()
    local view = CodeEditor.View.new(doc, theme)

    -- Launch Initial Window
    wm:create_window(view, "Code Editor", 50, 30, 160, 100)
end

function _update()
    desktop:update()
end

function _draw()
    desktop:draw()
end
