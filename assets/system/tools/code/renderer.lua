-- renderer.lua (updated to compute gutter dynamically and use measured widths)
local M = {}
local state_ref = nil

function M.init(state)
  state_ref = state
end

local function safe_col(c) return c or 7 end

-- compute gutter width and push to Java (LuaTool) via set_gutter_left(px)
local function compute_and_set_gutter()
  local buf = state_ref.buffer.cur()
  if not buf then return end
  local max_line = #buf.lines
  local max_label = tostring(max_line)
  local padding = 12 -- extra padding for gutter
  local number_width = 0
  if text_width then
    number_width = text_width(max_label) or 0
  else
    number_width = #max_label * state_ref.config.font_w
  end
  local gutter_px = math.max(40, number_width + padding)
  if set_gutter_left then set_gutter_left(gutter_px) end
  return gutter_px
end

local function draw_tabs_bar(win_x, win_y, win_w, win_h, gutter_px)
  local tb_y = win_y + win_h - state_ref.controls.get_controls().height - 30
  if rect then rect(win_x, tb_y, win_w, 30, safe_col(state_ref.config.colors.gutter_bg)) end
  local tx = win_x + 8
  for i,t in ipairs(state_ref.buffer.get_tabs()) do
    local label = t.path or ("untitled" .. tostring(i))
    local w = math.max(60, (#label + 2) * state_ref.config.font_w)
    if print then print(label, tx + 6, tb_y + 20, safe_col(i == state_ref.buffer.current_tab_index() and state_ref.config.colors.help_example or state_ref.config.colors.help_text)) end
    if rect then rect(tx, tb_y + 6, w, 20, safe_col(i == state_ref.buffer.current_tab_index() and state_ref.config.colors.help_bg or state_ref.config.colors.gutter_bg)) end
    tx = tx + w + 6
  end
  local plus_x = tx
  if rect then rect(plus_x, tb_y + 6, 36, 20, safe_col(state_ref.config.colors.help_bg)) end
  if print then print("+", plus_x + 12, tb_y + 20, safe_col(state_ref.config.colors.help_text)) end
  return tb_y, plus_x
end

function M.draw(win_x, win_y, win_w, win_h)
  local ensure_current_tab = state_ref.buffer.ensure_current_tab or function() end
  ensure_current_tab()

  win_x = win_x or 0; win_y = win_y or 0; win_w = win_w or state_ref.config.win_min_width; win_h = win_h or state_ref.config.win_min_height

  -- Recompute gutter based on current buffer and inform Java (LuaTool)
  local gutter_px = compute_and_set_gutter() or 44
  local padding_left = 4

  local content_h = win_h - state_ref.config.help_h - state_ref.controls.get_controls().height - 30
  local content_y = win_y + state_ref.config.help_h
  local content_top = win_y + win_h - state_ref.controls.get_controls().height - 30

  if rect then rect(win_x, win_y, win_w, win_h, safe_col(state_ref.config.colors.bg)) end
  if rect then rect(win_x, content_y, gutter_px, content_h, safe_col(state_ref.config.colors.gutter_bg)) end

  local cb_x = win_x; local cb_y = win_y + win_h - state_ref.controls.get_controls().height
  if rect then rect(cb_x, cb_y, win_w, state_ref.controls.get_controls().height, safe_col(state_ref.config.colors.gutter_bg)) end
  for i,b in ipairs(state_ref.controls.get_controls().buttons) do
    local bx,by,bw,bh = cb_x + b.x, cb_y + 6, b.w, state_ref.controls.get_controls().height - 12
    if rect then rect(bx,by,bw,bh, safe_col(state_ref.config.colors.help_bg)) end
    if print then print(b.label, bx + 8, by + math.floor(bh/2) + (state_ref.config.font_h/2) - 2, safe_col(state_ref.config.colors.help_text)) end
  end

  local tb_y, plus_x = draw_tabs_bar(win_x, win_y, win_w, win_h, gutter_px)

  local buf = state_ref.buffer.cur()
  if not buf then return end

  local visible_lines = math.floor(content_h / state_ref.config.line_h)
  local sy,sx,ey,ex = nil,nil,nil,nil
  if state_ref.buffer.get_selection_bounds then sy,sx,ey,ex = state_ref.buffer.get_selection_bounds(buf) end

  for i = 0, visible_lines do
    local line_idx = buf.scroll_y + i + 1
    if line_idx > #buf.lines then break end
    local line_y = content_top - (i * state_ref.config.line_h) - 4
    if line_y < content_y then break end

    if line_idx == buf.cy and rect then rect(win_x, line_y - state_ref.config.font_h, win_w, state_ref.config.line_h, safe_col(state_ref.config.colors.current_line)) end

    -- draw line numbers in gutter
    if print then print(tostring(line_idx), win_x + (gutter_px -  (text_width and (text_width(tostring(line_idx)) or 0) or (#tostring(line_idx) * state_ref.config.font_w)) - 6, line_y, safe_col(state_ref.config.colors.gutter_fg)) end

    -- selection highlighting using measured widths
    if sy and line_idx >= sy and line_idx <= ey and rect then
      local line_text = buf.lines[line_idx] or ""
      local sel_x_start = (line_idx == sy) and sx or 0
      local sel_x_end = (line_idx == ey) and ex or state_ref.buffer.utf8_char_count(line_text)
      if sel_x_end > sel_x_start then
        local prefix_width = 0
        if sel_x_start > 0 and text_width_sub then
          prefix_width = text_width_sub(line_text, sel_x_start) or 0
        end
        local total_width = 0
        if text_width_sub then total_width = text_width_sub(line_text, sel_x_end) or 0 end
        local sel_width = (total_width - prefix_width)
        local sel_pixel_x = win_x + gutter_px + padding_left + prefix_width
        rect(sel_pixel_x, line_y - state_ref.config.font_h, sel_width, state_ref.config.line_h, safe_col(state_ref.config.colors.selection))
      end
    end

    local syntax_inst = state_ref.syntax.get_syntax(nil, state_ref.config)
    local tokens = syntax_inst:parse(buf.lines[line_idx] or "")
    local cur_x = win_x + gutter_px + padding_left
    local char_pos = 0
    for _, token in ipairs(tokens) do
      if print and token.text then
        local col = token.color
        if token.bracket and buf.bracket_match then
          local by,bx = buf.bracket_match[1], buf.bracket_match[2]
          if (line_idx == buf.cy and char_pos == buf.cx) or (line_idx == by and char_pos == bx) then col = safe_col(state_ref.config.colors.bracket) end
        end
        print(token.text, cur_x, line_y, col)
        local token_width = 0
        if text_width then
          token_width = text_width(token.text) or 0
        else
          local tlen = state_ref.buffer.utf8_char_count(token.text)
          token_width = tlen * state_ref.config.font_w
        end
        cur_x = cur_x + token_width
        char_pos = char_pos + state_ref.buffer.utf8_char_count(token.text)
      end
    end

    -- caret (use measured width up to cursor)
    if line_idx == buf.cy and (buf.blink or 0) % 30 < 15 then
      local line_text = buf.lines[buf.cy] or ""
      local cursor_x = win_x + gutter_px + padding_left
      if buf.cx > 0 and text_width_sub then
        local text_before_cursor = text_width_sub(line_text, buf.cx) or 0
        cursor_x = cursor_x + text_before_cursor
      else
        cursor_x = cursor_x + (buf.cx * state_ref.config.font_w)
      end
      if rect then rect(cursor_x + (caret_offset_x or 0), line_y - state_ref.config.font_h, 2, state_ref.config.line_h - 4, safe_col(state_ref.config.colors.cursor)) end
    end
  end

  if rect then rect(win_x, win_y, win_w, state_ref.config.help_h, safe_col(state_ref.config.colors.help_bg)) end
  if print then
    local stats = string.format("Tab %d/%d  Line %d  Col %d  %d lines", state_ref.buffer.current_tab_index(), #state_ref.buffer.get_tabs(), buf.cy, buf.cx + 1, #buf.lines)
    print(stats, win_x + 4, win_y + 20, safe_col(state_ref.config.colors.comment))
    print("Ctrl+C/V/X Copy/Paste/Cut  Ctrl+Z/Y Undo/Redo  Ctrl+F Find  Ctrl+S Save  Ctrl+R Run  Ctrl+Tab Switch Tab  Ctrl+/- Font", win_x + 4, win_y + 36, safe_col(state_ref.config.colors.comment))
  end
end

return M
