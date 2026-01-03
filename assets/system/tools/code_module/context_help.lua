-- system/tools/code_module/context_help.lua
local HelpDB = require("system/tools/code_module/help_db")

local M = {}

function M.get_help(line_text, col)
    if not line_text or not col then return nil end
    
    -- Heuristic: find word under cursor
    -- "print(hello)" -> cursor at 3 ('i') -> "print"
    -- "hello = rnd(5)" -> cursor at 10 ('r') -> "rnd"
    
    -- Simple approach: scan back and forward from col just for alphanumeric/_
    
    local len = #line_text
    -- If col is beyond length (cursor at end), clamp it
    if col > len then col = len end
    if col < 1 then return nil end

    local s = col
    local e = col

    -- Scan left
    while s > 0 do
        local c = string.sub(line_text, s, s)
        if not c:match("[%w_]") then
            s = s + 1
            break
        end
        s = s - 1
    end
    if s < 1 then s = 1 end

    -- Scan right
    while e <= len do
        local c = string.sub(line_text, e, e)
        if not c:match("[%w_]") then
            e = e - 1
            break
        end
        e = e + 1
    end
    
    if s > e then return nil end
    local word = string.sub(line_text, s, e)
    
    if HelpDB.funcs[word] then
        return HelpDB.funcs[word]
    end
    
    return nil
end

return M
