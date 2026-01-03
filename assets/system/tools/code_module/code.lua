-- system/tools/code.lua
-- Complete fixed code editor with corrected coordinate system

local Config = require("system/tools/code_module/config")
local State = require("system/tools/code_module/editor_state")
local Renderer = require("system/tools/code_module/renderer")
local Input = require("system/tools/code_module/input")

-- Tool dimensions (updated by Java via onResize)
local tool_width = 1024
local tool_height = 1024

-- Debug flag
local DEBUG = false

local function debug_log(msg)
    if DEBUG and log then
        log(msg)
    end
end

-- Expose dimension setter for Java layer
function onResize(w, h)
    tool_width = w
    tool_height = h
    debug_log("Code editor resized to " .. w .. "x" .. h)
end

-- Initialize editor
_init = function()
    debug_log("Code editor initializing...")

    -- Verify toast is available
    if type(toast) == "function" then
        debug_log("Toast function available")
    else
        debug_log("WARNING: Toast function NOT available")
    end

    -- Verify project API
    if type(project) == "table" then
        debug_log("Project API available")
        if type(project.read) == "function" then
            debug_log("project.read available")
        end
        if type(project.write) == "function" then
            debug_log("project.write available")
        end
    else
        debug_log("WARNING: Project API NOT available")
    end

    -- Load main.lua on startup, or create it if missing
    if #State.tabs == 0 then
        local content = "-- New Project\n-- main.lua\n\nfunction _init()\n  \nend\n\nfunction _update()\n  \nend\n\nfunction _draw()\n  cls(5)\n  print('Hello World', 40, 40, 7)\nend\n"
        local loaded = false

        if type(project) == "table" then
            print("[code.lua] project table exists")
            -- Try to read existing main.lua
            if project.read then
                print("[code.lua] Attempting to read main.lua...")
                local ok, result = pcall(function()
                    return project.read("main.lua")
                end)
                
                print("[code.lua] pcall ok=" .. tostring(ok) .. ", result type=" .. type(result))
                if result then
                    print("[code.lua] result length=" .. tostring(#result))
                end

                if ok and result and type(result) == "string" and #result > 0 then
                    content = result
                    loaded = true
                    print("[code.lua] Loaded existing main.lua: " .. #content .. " chars")
                else
                    -- Create new main.lua
                    print("[code.lua] main.lua not found or empty, creating new one...")
                    if project.write then
                        project.write("main.lua", content)
                    end
                end
            end
        else
            print("[code.lua] project table NOT available!")
        end

        State.open_tab("main.lua", content)
        print("[code.lua] Opened tab: main.lua (loaded=" .. tostring(loaded) .. ")")
    end
    
    -- Initialize Input module
    Input.init(State)

    debug_log("Code editor initialized with " .. #State.tabs .. " tabs")
end

_update = function()
    -- Delegate all input handling to Input module
    Input.update(0, 0, tool_width, tool_height)
    
    -- Update Window Title for Java Wrapper
    local buf = State.buffer.cur()
    if buf then
        local name = buf.path or "untitled"
        if buf.modified then name = name .. "*" end
        _TITLE = name
    else
        _TITLE = "Code"
    end
end

_draw = function()
    Renderer.draw(State, 0, 0, tool_width, tool_height)
end

-- Debug command
function toggle_debug()
    DEBUG = not DEBUG
    if log then
        log("Debug mode: " .. tostring(DEBUG))
    end
end

return State
