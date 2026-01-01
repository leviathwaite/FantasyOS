local Config = require("system/tools/code_module/config")
local IO = require("system/tools/code_module/io")
local Buffer = require("system/tools/code_module/buffer")
local Core = require("system/tools/code_module/editor_core")

local Handler = {}
local K = Config.keys

local function is_ctrl() return IO.btnp_safe(K.CTRL_L) or IO.btnp_safe(K.CTRL_R) end

function Handler.handle_keyboard()
    local buf = Core.get_current()
    if not buf then return end
    buf.blink = (buf.blink or 0) + 1

    -- Saving
    if is_ctrl() and IO.btnp_safe(K.S) then
        local content = table.concat(buf.lines, "\n")
        local ok = IO.save_wrapper(buf.path or Core.current_file, content)
        if ok then
            buf.modified = false
            IO.toast("Saved")
        end
        return
    end

    -- Undo / Redo
    if is_ctrl() and IO.btnp_safe(K.Z) then
        local s = buf.undo:undo()
        if s then buf.lines = s end
        return
    end

    -- Arrow Keys (Simplified for brevity)
    if IO.btnp_safe(K.LEFT) then
        if buf.cx > 0 then buf.cx = buf.cx - 1
        elseif buf.cy > 1 then buf.cy = buf.cy - 1; buf.cx = #(buf.lines[buf.cy] or "") end
    elseif IO.btnp_safe(K.RIGHT) then
        if buf.cx < #(buf.lines[buf.cy] or "") then buf.cx = buf.cx + 1
        elseif buf.cy < #buf.lines then buf.cy = buf.cy + 1; buf.cx = 0 end
    elseif IO.btnp_safe(K.UP) then
        if buf.cy > 1 then buf.cy = buf.cy - 1; buf.cx = math.min(buf.cx, #(buf.lines[buf.cy] or "")) end
    elseif IO.btnp_safe(K.DOWN) then
        if buf.cy < #buf.lines then buf.cy = buf.cy + 1; buf.cx = math.min(buf.cx, #(buf.lines[buf.cy] or "")) end
    end

    -- Typing
    local ch = IO.kbchar()
    while ch do
        if not is_ctrl() then
            buf.undo:push(Buffer.get_snapshot(buf))
            local line = buf.lines[buf.cy] or ""
            buf.lines[buf.cy] = string.sub(line,1,buf.cx) .. ch .. string.sub(line, buf.cx + 1)
            buf.cx = buf.cx + 1
            buf.modified = true
        end
        ch = IO.kbchar()
    end
end

return Handler
