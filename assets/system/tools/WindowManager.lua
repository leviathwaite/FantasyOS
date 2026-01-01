local Config = require("system/tools/code_module/config")
local UITheme = require("system/tools/code_module/UITheme")
local Reference = require("system/tools/code_module/reference")

local WinMan = {}
local syntax = Reference.Syntax.new()

function WinMan.draw(State, win_x, win_y, win_w, win_h)
    local buf = State.cur()
    if not buf then return end

    local content_h = win_h - Config.help_h - Config.controls.height - 30
    local content_top = win_y + win_h - Config.controls.height - 30

    -- Background
    if rect then rect(win_x, win_y, win_w, win_h, UITheme.safe_col(UITheme.colors.bg)) end

    -- Draw Code Lines
    local visible_lines = math.floor(content_h / Config.line_h)
    for i = 0, visible_lines do
        local line_idx = buf.scroll_y + i + 1
        if line_idx > #buf.lines then break end
        local line_y = content_top - (i * Config.line_h) - 4

        -- Gutter
        if print then print(tostring(line_idx), win_x + 4, line_y, UITheme.safe_col(UITheme.colors.gutter_fg)) end

        -- Text with Syntax Highlighting
        local tokens = syntax:parse(buf.lines[line_idx] or "")
        local cur_x = win_x + 44
        for _, token in ipairs(tokens) do
            if print then print(token.text, cur_x, line_y, token.color) end
            cur_x = cur_x + (#token.text * Config.font_w)
        end

        -- Cursor
        if line_idx == buf.cy and (buf.blink % 30 < 15) then
            local cx = win_x + 44 + (buf.cx * Config.font_w)
            if rect then rect(cx, line_y - Config.font_h, 2, Config.line_h - 4, UITheme.safe_col(UITheme.colors.cursor)) end
        end
    end

    -- Draw Status Bar
    if rect then rect(win_x, win_y, win_w, Config.help_h, UITheme.safe_col(UITheme.colors.help_bg)) end
    if print then
        print("Line " .. buf.cy .. " Col " .. buf.cx, win_x + 4, win_y + 20, UITheme.colors.help_text)
    end
end

return WinMan
