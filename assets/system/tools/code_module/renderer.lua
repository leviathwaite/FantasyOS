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
    local tab_bar_height = 30
    local controls_height = Config.controls.height or 40
    local help_height = Config.help_h or 60
    local gutter_width = 44

    -- Content area: between help bar and tab bar
    local content_top = win_y + help_height
    local content_bottom = win_y + win_h - controls_height - tab_bar_height
    local content_height = content_bottom - content_top

    local visible_lines = math.floor(content_height / Config.line_h)
    local visible_width = win_w - gutter_width

    -- Update scroll to keep cursor visible
    Scroll.ensure_cursor_visible_vertical(buf, visible_lines)
    if Config.features.horizontal_scroll then
        Scroll.ensure_cursor_visible_horizontal(buf, visible_width)
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
        rect(win_x, content_top, win_w, content_height, Config.colors.bg)
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

        -- Draw selection highlight if applicable
        if buf.sel_start_y and buf.sel_end_y then
            local sel_start_y = math.min(buf.sel_start_y, buf.sel_end_y)
            local sel_end_y = math.max(buf.sel_start_y, buf.sel_end_y)
            local sel_start_x = (buf.sel_start_y < buf.sel_end_y or (buf.sel_start_y == buf.sel_end_y and buf.sel_start_x <= buf.sel_end_x)) and buf.sel_start_x or buf.sel_end_x
            local sel_end_x = (buf.sel_start_y < buf.sel_end_y or (buf.sel_start_y == buf.sel_end_y and buf.sel_start_x <= buf.sel_end_x)) and buf.sel_end_x or buf.sel_start_x
            
            if line_idx >= sel_start_y and line_idx <= sel_end_y then
                local line_text = buf.lines[line_idx] or ""
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
                    -- First line of multi-line selection
                    if text_width then
                        local before_text = string.sub(line_text, 1, sel_start_x)
                        highlight_start_x = text_x + (text_width(before_text) or (sel_start_x * Config.font_w))
                        highlight_width = text_width(string.sub(line_text, sel_start_x + 1)) or ((#line_text - sel_start_x) * Config.font_w)
                    else
                        highlight_start_x = text_x + (sel_start_x * Config.font_w)
                        highlight_width = (#line_text - sel_start_x) * Config.font_w
                    end
                elseif line_idx == sel_end_y then
                    -- Last line of multi-line selection
                    if text_width then
                        local sel_text = string.sub(line_text, 1, sel_end_x)
                        highlight_width = text_width(sel_text) or (sel_end_x * Config.font_w)
                    else
                        highlight_width = sel_end_x * Config.font_w
                    end
                else
                    -- Middle line of multi-line selection
                    if text_width then
                        highlight_width = text_width(line_text) or (#line_text * Config.font_w)
                    else
                        highlight_width = #line_text * Config.font_w
                    end
                end
                
                -- Draw selection highlight
                if rect and highlight_width > 0 then
                    rect(highlight_start_x, content_top + (i * Config.line_h), highlight_width, Config.line_h, Config.colors.selection)
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
                    print(token.text, current_x, line_y, token.color)
                end
                
                current_x = current_x + token_width
            end
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
            
            -- Apply horizontal scroll offset to cursor
            if Config.features.horizontal_scroll and buf.scroll_x then
                cursor_x = cursor_x - buf.scroll_x
            end

            -- Draw cursor
            if rect then
                rect(cursor_x, line_y - 2, 2, Config.line_h - 4, Config.colors.cursor)
            end
        end
    end

    -- Draw bracket matching highlight
    if Config.features.bracket_matching then
        local match_line, match_col = Brackets.find_matching_bracket(buf)
        if match_line and match_col then
            -- Check if matching bracket is visible
            local visible_start = buf.scroll_y + 1
            local visible_end = buf.scroll_y + visible_lines
            if match_line >= visible_start and match_line <= visible_end then
                Brackets.draw_highlight(buf, match_line, match_col, win_x, win_y, content_top, gutter_width)
            end
        end
    end

    -- Draw tab bar at bottom (above controls)
    local tab_bar_y = content_bottom
    if rect then
        rect(win_x, tab_bar_y, win_w, tab_bar_height, Config.colors.gutter_bg)
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
    
    -- Draw dialog overlay if active
    if State.dialog_mode then
        local overlay_h = 80
        local overlay_y = win_y + (win_h - overlay_h) / 2
        local overlay_w = 400
        local overlay_x = win_x + (win_w - overlay_w) / 2
        
        if rect then
            rect(overlay_x, overlay_y, overlay_w, overlay_h, Config.colors.gutter_bg)
        end
        
        if print then
            local label_y = overlay_y + 10
            if State.dialog_mode == "goto" then
                print("Go to Line:", overlay_x + 10, label_y, Config.colors.help_title)
            elseif State.dialog_mode == "search" then
                print("Find:", overlay_x + 10, label_y, Config.colors.help_title)
            end
            
            -- Draw input with cursor
            local input_y = label_y + 25
            local input_text = State.dialog_input .. "_"
            print(input_text, overlay_x + 10, input_y, Config.colors.help_example)
        end
    end
end

return Renderer
