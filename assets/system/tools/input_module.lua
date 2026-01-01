local Config = require("system/tools/code_module/config")
local InputMapper = require("system/tools/code_module/input_mapper")
local Input = {}
local K = InputMapper.Keys

-- Safe Wrappers
function Input.is_func(v) return type(v) == "function" end
function Input.btnp_safe(i)
    if not Input.is_func(btnp) then return false end
    local ok, res = pcall(function() return btnp(i) end)
    return ok and res
end
function Input.kbchar()
    if Input.is_func(char) then local ok, r = pcall(char); if ok then return r end end
    if Input.is_func(keyboard_char) then local ok, r = pcall(keyboard_char); if ok then return r end end
    return nil
end
function Input.is_ctrl() return Input.btnp_safe(K.CTRL_L) or Input.btnp_safe(K.CTRL_R) end
function Input.get_mouse()
    if Input.is_func(mouse) then local ok, r = pcall(mouse); if ok then return r end end
    return nil
end

-- Core Input Logic
function Input.handle_keyboard(buf, State)
    if not buf then return end
    buf.blink = (buf.blink or 0) + 1

    -- Navigation
    if Input.btnp_safe(K.LEFT) then
        if buf.cx > 0 then buf.cx = buf.cx - 1
        elseif buf.cy > 1 then buf.cy = buf.cy - 1; buf.cx = #(buf.lines[buf.cy] or "") end
        return
    end
    if Input.btnp_safe(K.RIGHT) then
        if buf.cx < #(buf.lines[buf.cy] or "") then buf.cx = buf.cx + 1
        elseif buf.cy < #buf.lines then buf.cy = buf.cy + 1; buf.cx = 0 end
        return
    end
    if Input.btnp_safe(K.UP) then
        if buf.cy > 1 then buf.cy = buf.cy - 1; buf.cx = math.min(buf.cx, #(buf.lines[buf.cy] or "")) end
        return
    end
    if Input.btnp_safe(K.DOWN) then
        if buf.cy < #buf.lines then buf.cy = buf.cy + 1; buf.cx = math.min(buf.cx, #(buf.lines[buf.cy] or "")) end
        return
    end

    -- Typing
    local ch = Input.kbchar()
    while ch do
        if not Input.is_ctrl() then
            -- Note: Add Undo logic here if you move UndoStack to a shared utils or state file
            local line = buf.lines[buf.cy] or ""
            buf.lines[buf.cy] = string.sub(line,1,buf.cx) .. ch .. string.sub(line, buf.cx + 1)
            buf.cx = buf.cx + 1
            buf.modified = true
        end
        ch = Input.kbchar()
    end

    -- Add Backspace/Enter logic similarly...
end

return Input
