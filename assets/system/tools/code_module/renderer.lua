local Config = require("system/tools/code_module/config")
local Syntax = require("system/tools/code_module/syntax")
local Scroll = require("system/tools/code_module/scroll")
local Brackets = require("system/tools/code_module/brackets")
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
    -- NOTE: EditorScreen now provides the global toolbar at the top; the module should
    -- render into the area passed to it (win_y, win_h) without reserving extra header space.
    local header_height = 0 -- previously Config.header_height (handled by EditorScreen)
    local tab_bar_height = 0 -- module no longer draws its own tab bar; tabs are drawn by EditorScreen
    local status_height = Config.help_h or 32
    local scrollbar_height = 16
    local gutter_width = 44

    -- Vertical Layout (Top to Bottom within the provided win_* area):
    -- 1. Content (fills the full area passed in)
    -- 2. Scrollbar
    -- 3. Status Bar

    -- Screen Y (0 at Bottom, H at Top) for the module's area
    local top_y = win_y + win_h
    local status_y = win_y
    local scrollbar_y = status_y + status_height

    local content_top_y = top_y
    local content_bottom_y = scrollbar_y

    local content_height = content_top_y - content_bottom_y
    local visible_lines = math.floor(content_height / Config.line_h)
    local visible_width = win_w - gutter_width

    -- Update scroll to keep cursor visible
    Scroll.ensure_cursor_visible_vertical(buf, visible_lines)
    if Config.features.horizontal_scroll then
        Scroll.ensure_cursor_visible_horizontal(buf, visible_width)
    end

    -- Draw Content Area Background
    if rect then
        rect(win_x, content_bottom_y, win_w, content_height, Config.colors.bg)
    end

    -- Draw Horizontal Scrollbar
    if Config.features.horizontal_scroll and rect then
        rect(win_x, scrollbar_y, win_w, scrollbar_height, Config.colors.gutter_bg)

        -- Calculate Max Width
        local max_width = visible_width
        for _, l in ipairs(buf.lines) do
             local w = 0
             if text_width then w = text_width(l) else w = #l * Config.font_w end
             if w > max_width then max_width = w end
        end
        max_width = max_width + 100 -- Padding

        -- Draw Thumb
        local view_ratio = math.min(1, visible_width / max_width)
        local thumb_w = math.max(20, win_w * view_ratio)
        local scroll_ratio = (buf.scroll_x or 0) / (max_width - visible_width)
        scroll_ratio = math.max(0, math.min(1, scroll_ratio))
        local max_thumb_x = win_w - thumb_w
        local thumb_x = win_x + (max_thumb_x * scroll_ratio)

        rect(thumb_x, scrollbar_y + 2, thumb_w, scrollbar_height - 4, Config.colors.gutter_fg)
    end

    -- Draw Status Bar (Bottom)
    if rect then
        rect(win_x, status_y, win_w, status_height, Config.colors.help_bg)
    end
    if print then
        local status_text = string.format("Ln %d, Col %d", buf.cy, buf.cx)
        print(status_text, win_x + 10, status_y + 8, Config.colors.help_text)
        print("Ctrl+S: Save  Ctrl+R: Run", win_x + win_w - 250, status_y + 8, Config.colors.help_text)
    end

    -- Draw Content Area (again, to ensure layering)
    local content_render_y_bottom = status_y + status_height
    if rect then
        rect(win_x, content_render_y_bottom, win_w, content_height, Config.colors.bg)
    end

    -- Draw line numbers and text
    for i = 0, visible_lines - 1 do
        local line_idx = buf.scroll_y + i + 1
        if line_idx > #buf.lines then break end

        -- Calculate Render Y
        -- content_top_y is the top-most pixel of content area.
        -- We draw downwards from there.
        local line_top_y = content_top_y - (i * Config.line_h)
        local text_draw_y = line_top_y - 12
        local line_rect_y = line_top_y - Config.line_h

        -- Draw gutter background
        if rect then
            rect(win_x, line_rect_y, gutter_width, Config.line_h, Config.colors.gutter_bg)
        end

        -- Draw line number
        if print then
            print(tostring(line_idx), win_x + 4, text_draw_y, Config.colors.gutter_fg)
        end

        -- Draw selection highlight if applicable
        if buf.sel_start_y and buf.sel_end_y then
             local sel_start_y = math.min(buf.sel_start_y, buf.sel_end_y)
            local sel_end_y = math.max(buf.sel_start_y, buf.sel_end_y)
            local sel_start_x = (buf.sel_start_y < buf.sel_end_y or (buf.sel_start_y == buf.sel_end_y and buf.sel_start_x <= buf.sel_end_x)) and buf.sel_start_x or buf.sel_end_x
            local sel_end_x = (buf.sel_start_y < buf.sel_end_y or (buf.sel_start_y == buf.sel_end_y and buf.sel_start_x <= buf.sel_end_x)) and buf.sel_end_x or buf.sel_start_x

            if line_idx >= sel_start_y and line_idx <= sel_end_y then
                local line_text = buf.lines[line_idx] or ""
                local text_x = win_x + gutter_width
                local highlight_start_x = text_x
                local highlight_width = 0

                if line_idx == sel_start_y and line_idx == sel_end_y then
                    -- Selection on single line
                    if text_width then
                        local before_text = string.sub(line_text, 1, sel_start_x)
                        local sel_text = string.sub(line_text, sel_start_x + 1, sel_end_x)
                        highlight_start_x = text_x + (text_width(before_text) or (sel_start_x * Config.font_w))
                        highlight_width = text_width(sel_text) or ((sel_end_x - sel_start_x) * Config.font_w)
                    else
                        highlight_start_x = text_x + (sel_start_x * Config.font_w)
                        highlight_width = (sel_end_x - sel_start_x) * Config.font_w
                    end
                elseif line_idx == sel_start_y then
                    -- First line
                    if text_width then
                        local before_text = string.sub(line_text, 1, sel_start_x)
                        highlight_start_x = text_x + (text_width(before_text) or (sel_start_x * Config.font_w))
                        highlight_width = text_width(string.sub(line_text, sel_start_x + 1)) or ((#line_text - sel_start_x) * Config.font_w)
                    else
                        highlight_start_x = text_x + (sel_start_x * Config.font_w)
                        highlight_width = (#line_text - sel_start_x) * Config.font_w
                    end
                elseif line_idx == sel_end_y then
                    -- Last line
                    if text_width then
                        local sel_text = string.sub(line_text, 1, sel_end_x)
                        highlight_width = text_width(sel_text) or (sel_end_x * Config.font_w)
                    else
                        highlight_width = sel_end_x * Config.font_w
                    end
                else
                    -- Middle line
                    if text_width then
                        highlight_width = text_width(line_text) or (#line_text * Config.font_w)
                    else
                        highlight_width = #line_text * Config.font_w
                    end
                end

                -- Draw selection highlight
                if rect and highlight_width > 0 then
                    rect(highlight_start_x, line_rect_y, highlight_width, Config.line_h, Config.colors.selection)
                end
            end
        end

        -- Draw text line with syntax highlighting
        local line_text = buf.lines[line_idx] or ""
        local text_x = win_x + gutter_width

        -- Apply horizontal scroll offset
        local scroll_offset = 0
        if Config.features.horizontal_scroll and buf.scroll_x then
            scroll_offset = buf.scroll_x
        end

        if print and #line_text > 0 then
            -- Parse tokens for syntax highlighting
            local tokens = Syntax:parse(line_text)
            local current_x = text_x - scroll_offset

            for _, token in ipairs(tokens) do
                -- Only render tokens that are visible
                local token_width = 0
                if text_width then
                    token_width = text_width(token.text) or (#token.text * Config.font_w)
                else
                    token_width = #token.text * Config.font_w
                end

                if current_x + token_width >= text_x and current_x < text_x + visible_width then
                    print(token.text, current_x, text_draw_y, token.color)
                end

                current_x = current_x + token_width
            end
        end

        -- Draw cursor on current line
        if line_idx == buf.cy and (buf.blink % 30 < 15) then
            local cursor_x = text_x
            if buf.cx > 0 then
                if text_width then
                    local text_before = string.sub(line_text, 1, buf.cx)
                    local width = text_width(text_before)
                    if width then cursor_x = cursor_x + width else cursor_x = cursor_x + (buf.cx * Config.font_w) end
                 else
                    cursor_x = cursor_x + (buf.cx * Config.font_w)
                end
            end
             if Config.features.horizontal_scroll and buf.scroll_x then
                cursor_x = cursor_x - buf.scroll_x
            end
            if rect then
                rect(cursor_x, line_rect_y + 2, 2, Config.line_h - 4, Config.colors.cursor)
            end
        end
    end

     -- Draw dialog overlay
    if State.dialog_mode then
        local overlay_h = 80
        local overlay_y = win_y + (win_h - overlay_h) / 2
        local overlay_w = 400

        local overlay_x = win_x + (win_w - overlay_w) / 2

        if rect then
            rect(overlay_x, overlay_y, overlay_w, overlay_h, Config.colors.gutter_bg)
        end

        if print then
            local label_y = overlay_y + overlay_h - 25 -- Top of box
            if State.dialog_mode == "goto" then
                print("Go to Line:", overlay_x + 10, label_y, Config.colors.help_title)
            elseif State.dialog_mode == "search" then
                print("Find:", overlay_x + 10, label_y, Config.colors.help_title)
            end

            local input_y = label_y - 25
            local input_text = State.dialog_input .. "_"
            print(input_text, overlay_x + 10, input_y, Config.colors.help_example)
        end
    end
end

return Renderer
