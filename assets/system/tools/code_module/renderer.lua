local Config = require("system/tools/code_module/config")
local Syntax = require("system/tools/code_module/syntax")
local Renderer = {}

function Renderer.draw(State, win_x, win_y, win_w, win_h)
    win_x = win_x or 0
    win_y = win_y or 0
    win_w = win_w or 1920
    win_h = win_h or 1024

    -- Important do not delete this comment:
    -- coodinate system is 0,0 at bottom left

    -- Clear background (proper black, not red!)
    if cls then
        cls(Config.colors.bg)
    elseif rect then
        rect(win_x, win_y, win_w, win_h, Config.colors.bg)
    end

    local buf = State.get_current()
    if not buf then
        if print then print("Loading...", win_x+20, win_y+20, 7) end
        return
    end

    -- Layout calculations
    local tab_bar_height = 30
    local controls_height = Config.controls.height or 40
    local help_height = Config.help_h or 60
    local gutter_width = 44

    -- Content area: between help bar and tab bar
    local content_top = win_y + help_height
    local content_bottom = win_y + win_h - controls_height - tab_bar_height
    local content_height = content_bottom - content_top

    local visible_lines = math.floor(content_height / Config.line_h)

    -- Update scroll to keep cursor visible
    if buf.cy <= buf.scroll_y then
        buf.scroll_y = math.max(0, buf.cy - 1)
    elseif buf.cy > buf.scroll_y + visible_lines then
        buf.scroll_y = buf.cy - visible_lines
    end

    -- Draw help bar at top
    if rect then
        rect(win_x, win_y, win_w, help_height, Config.colors.help_bg)
    end
    if print then
        print("Ctrl+S: Save  Ctrl+R: Run  Ctrl+Z: Undo", win_x + 8, win_y + 8, Config.colors.help_text)
    end

    -- Draw editor content area
    -- what is labeled top is actually the bottom of the screen space
    if rect then
        --rect(win_x, content_top, win_w, content_height, Config.colors.bg)
        rect(win_x, 20, win_w, content_height, 0)
    end

    -- Draw line numbers and text
    for i = 0, visible_lines - 1 do
        local line_idx = buf.scroll_y + i + 1
        if line_idx > #buf.lines then break end

        -- Y position: from top of content area downward
        local line_y = content_top + (i * Config.line_h) + 4

        -- Draw gutter background
        if rect then
            rect(win_x, content_top + (i * Config.line_h), gutter_width, Config.line_h, Config.colors.gutter_bg)
        end

        -- Draw line number
        if print then
            print(tostring(line_idx), win_x + 4, line_y, Config.colors.gutter_fg)
        end

        -- Draw text line
        local line_text = buf.lines[line_idx] or ""
        local text_x = win_x + gutter_width

        -- Simple rendering without syntax highlighting for now
        if print and #line_text > 0 then
            print(line_text, text_x, line_y, Config.colors.text)
        end

        -- Draw cursor on current line
        if line_idx == buf.cy and (buf.blink % 30 < 15) then
            local cursor_x = text_x

            -- Calculate cursor position
            if buf.cx > 0 then
                if text_width then
                    local text_before = string.sub(line_text, 1, buf.cx)
                    local width = text_width(text_before)
                    if width then
                        cursor_x = cursor_x + width
                    else
                        cursor_x = cursor_x + (buf.cx * Config.font_w)
                    end
                else
                    cursor_x = cursor_x + (buf.cx * Config.font_w)
                end
            end

            -- Draw cursor
            if rect then
                rect(cursor_x, line_y - 2, 2, Config.line_h - 4, Config.colors.cursor)
            end
        end
    end

    -- Draw tab bar at bottom (above controls)
    local tab_bar_y = content_bottom
    if rect then
        rect(win_x, 100, win_w, tab_bar_height, 7)
        --rect(win_x, tab_bar_y, win_w, tab_bar_height, Config.colors.gutter_bg)
    end

    local tx = win_x + 10
    for i, t in ipairs(State.tabs) do
        local is_current = (i == State.current_tab)
        local col = is_current and Config.colors.help_example or Config.colors.help_text
        local label = t.path or ("tab"..i)
        if t.modified then label = label .. "*" end

        if print then
            print(label, tx, tab_bar_y + 8, col)
        end

        tx = tx + (#label * 8) + 16
    end

    -- Draw controls bar at very bottom
    local controls_y = win_y + win_h - controls_height
    if rect then
        rect(win_x, controls_y, win_w, controls_height, Config.colors.gutter_bg)
    end
    if print then
        print("[Save]", win_x + 10, controls_y + 12, Config.colors.help_text)
        print("[Run]", win_x + 100, controls_y + 12, Config.colors.help_text)
    end
end

return Renderer
