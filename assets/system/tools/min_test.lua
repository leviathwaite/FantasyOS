-- system/tools/min_test.lua
log("=== MODULE LOADING ===")
log("rect type: " .. tostring(type(rect)))
log("print type: " .. tostring(type(print)))
log("log type: " .. tostring(type(log)))

--1920x1024

local frame = 0

_init = function()
    log("_init() called")
end

_update = function()
    frame = frame + 1
    if frame == 1 then
        log("_update() called - frame " .. frame)
    end
end

_draw = function()
    if frame == 1 then
        log("_draw() START - frame " .. frame)
    end

    if rect then
        rect(0, 0, 2000, 2000, 8)  -- Red background
        rect(100, 100, 400, 400, 7)  -- White square
        if frame == 1 then log("Called rect() successfully") end
    else
        log("ERROR: rect is nil!")
    end

    if print then
        print("FRAME: " .. frame, 100, 100, 7)
        print("RENDERING TEST", 100, 500, 7)
        if frame == 1 then log("Called print() successfully") end
    else
        log("ERROR: print is nil!")
    end

    if frame == 1 then log("_draw() END") end
end

log("=== MODULE LOADED ===")
