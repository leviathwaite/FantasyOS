-- input.lua - MOUSE CLICK FIXED
local M = {}
local state_ref = nil

local old_mouse_click = false
local old_mouse_right = false
local old_mouse_left = false
local context_open = false
local context_tab = nil
local context_x, context_y = 0, 0

local function is_func(v) return type(v) == "function" end
local function btn_safe(i)
  if not is_func(btn) then return false end
  local ok, res = pcall(function() return btn(i) end)
  return ok and (res and true or false)
end
local function btnp_safe(i)
  if not is_func(btnp) then return false end
  local ok, res = pcall(function() return btnp(i) end)
  return ok and (res and true or false)
end
local function kbchar()
  if is_func(char) then
    local ok, res = pcall(function() return char() end)
    if ok then return res end
  end
  return nil
end

local KEY_UP=19; KEY_DOWN=20; KEY_LEFT=21; KEY_RIGHT=22
local KEY_HOME=3; KEY_END=123; KEY_PGUP=92; KEY_PGDN=93
local KEY_ENTER=66; KEY_BACK=67; KEY_TAB=61; KEY_DEL=112; KEY_SPACE=62
local KEY_SHIFT_L=59; KEY_SHIFT_R=60
local KEY_CTRL_L=129; KEY_CTRL_R=130
local KEY_A=29; KEY_C=31; KEY_F=34; KEY_R=46; KEY_S=47
local KEY_V=50; KEY_X=52; KEY_Y=53; KEY_Z=54
local KEY_MINUS=69; KEY_EQUALS=70

local _back_repeat = 0
local BACK_INITIAL_DELAY = 15
local BACK_REPEAT_INTERVAL = 4

function M.init(state)
  state_ref = state
end

local function is_ctrl()
  return btn_safe(KEY_CTRL_L) or btn_safe(KEY_CTRL_R)
end
local function is_shift()
  return btn_safe(KEY_SHIFT_L) or btn_safe(KEY_SHIFT_R)
end

local function insert_text_at_cursor(buf, text)
  if not text or text == "" then return end
  local lines = {}
  for line in string.gmatch(text, "([^\n]*)\n?") do
    table.insert(lines, line)
  end
  if #lines == 0 then return end

  local current_line = buf.lines[buf.cy] or ""
  local before = string.sub(current_line, 1, buf.cx)
  local after = string.sub(current_line, buf.cx + 1)

  if #lines == 1 then
    buf.lines[buf.cy] = before .. lines[1] .. after
    buf.cx = buf.cx + #lines[1]
  else
    buf.lines[buf.cy] = before .. lines[1]
    for i = 2, #lines - 1 do
      table.insert(buf.lines, buf.cy + i - 1, lines[i])
    end
    table.insert(buf.lines, buf.cy + #lines - 1, lines[#lines] .. after)
    buf.cy = buf.cy + #lines - 1
    buf.cx = #lines[#lines]
  end
  buf.modified = true
end

local function calculate_indent(line)
  local spaces = string.match(line, "^(%s*)") or ""
  local indent = #spaces
  local trimmed = string.match(line, "^%s*(.-)%s*$")
  if trimmed and (trimmed:match("^function") or trimmed:match("^if") or
      trimmed:match("^for") or trimmed:match("^while") or
      trimmed:match("^repeat") or trimmed == "do" or trimmed:match("then%s*$")) then
    indent = indent + (state_ref.config.tab_width or 2)
  end
  return indent
end

local function handle_keyboard()
  local buf = state_ref.buffer.cur()
  if not buf then return end
  buf.blink = (buf.blink or 0) + 1

  local ctrl = is_ctrl()
  local shift = is_shift()

  if ctrl and btnp_safe(KEY_TAB) then
    if shift then state_ref.buffer.switch_tab_relative(-1)
    else state_ref.buffer.switch_tab_relative(1) end
    return
  end

  if ctrl then
    local ch = kbchar()
    while ch do
      if ch == "+" or ch == "=" then
        state_ref.io.set_editor_font_size(state_ref.config.font_size + 2)
        return
      elseif ch == "-" then
        state_ref.io.set_editor_font_size(state_ref.config.font_size - 2)
        return
      end
      ch = kbchar()
    end
    if btnp_safe(KEY_EQUALS) then state_ref.io.set_editor_font_size(state_ref.config.font_size + 2); return end
    if btnp_safe(KEY_MINUS) then state_ref.io.set_editor_font_size(state_ref.config.font_size - 2); return end
  end

  if ctrl and btnp_safe(KEY_C) then
    if state_ref.buffer.has_selection(buf) then
      buf.clipboard = state_ref.buffer.get_selected_text(buf)
    end
    return
  end

  if ctrl and btnp_safe(KEY_X) then
    if state_ref.buffer.has_selection(buf) then
      buf.clipboard = state_ref.buffer.get_selected_text(buf)
      state_ref.buffer.delete_selection(buf)
    end
    return
  end

  if ctrl and btnp_safe(KEY_V) then
    if buf.clipboard and #buf.clipboard > 0 then
      buf.undo:push(state_ref.buffer.get_state())
      if state_ref.buffer.has_selection(buf) then
        state_ref.buffer.delete_selection(buf)
      end
      insert_text_at_cursor(buf, buf.clipboard)
    end
    return
  end

  if ctrl and btnp_safe(KEY_Z) then
    local s = buf.undo:undo()
    if s then state_ref.buffer.restore_state(s) end
    return
  end

  if ctrl and btnp_safe(KEY_Y) then
    local s = buf.undo:redo()
    if s then state_ref.buffer.restore_state(s) end
    return
  end

  if ctrl and btnp_safe(KEY_S) then
    local path = buf.path or "main.lua"
    local ok = state_ref.io.save(path, table.concat(buf.lines, "\n"))
    if ok then
      buf.path = path
      buf.modified = false
      state_ref.io.toast("Saved", 1.0)
    end
    return
  end

  if ctrl and btnp_safe(KEY_R) then
    local path = buf.path or "main.lua"
    state_ref.io.run(path)
    state_ref.io.toast("Running", 1.0)
    return
  end

  if ctrl and btnp_safe(KEY_F) then
    buf.find_mode = not buf.find_mode
    return
  end

  if btnp_safe(KEY_LEFT) then
    if shift and not state_ref.buffer.has_selection(buf) then
      state_ref.buffer.start_selection(buf)
    end
    if buf.cx > 0 then buf.cx = buf.cx - 1
    elseif buf.cy > 1 then buf.cy = buf.cy - 1; buf.cx = #(buf.lines[buf.cy] or "") end
    if shift then state_ref.buffer.update_selection_end(buf)
    else state_ref.buffer.clear_selection(buf) end
    return
  end

  if btnp_safe(KEY_RIGHT) then
    if shift and not state_ref.buffer.has_selection(buf) then
      state_ref.buffer.start_selection(buf)
    end
    local line_len = #(buf.lines[buf.cy] or "")
    if buf.cx < line_len then buf.cx = buf.cx + 1
    elseif buf.cy < #buf.lines then buf.cy = buf.cy + 1; buf.cx = 0 end
    if shift then state_ref.buffer.update_selection_end(buf)
    else state_ref.buffer.clear_selection(buf) end
    return
  end

  if btnp_safe(KEY_UP) then
    if shift and not state_ref.buffer.has_selection(buf) then
      state_ref.buffer.start_selection(buf)
    end
    if buf.cy > 1 then
      buf.cy = buf.cy - 1
      buf.cx = math.min(buf.cx, #(buf.lines[buf.cy] or ""))
    end
    if shift then state_ref.buffer.update_selection_end(buf)
    else state_ref.buffer.clear_selection(buf) end
    return
  end

  if btnp_safe(KEY_DOWN) then
    if shift and not state_ref.buffer.has_selection(buf) then
      state_ref.buffer.start_selection(buf)
    end
    if buf.cy < #buf.lines then
      buf.cy = buf.cy + 1
      buf.cx = math.min(buf.cx, #(buf.lines[buf.cy] or ""))
    end
    if shift then state_ref.buffer.update_selection_end(buf)
    else state_ref.buffer.clear_selection(buf) end
    return
  end

  if btnp_safe(KEY_HOME) then buf.cx = 0; return end
  if btnp_safe(KEY_END) then buf.cx = #(buf.lines[buf.cy] or ""); return end
  if btnp_safe(KEY_PGUP) then buf.cy = math.max(1, buf.cy - 10); return end
  if btnp_safe(KEY_PGDN) then buf.cy = math.min(#buf.lines, buf.cy + 10); return end

  if btn_safe(KEY_BACK) then _back_repeat = _back_repeat + 1
  else _back_repeat = 0 end

  if btnp_safe(KEY_BACK) or (_back_repeat > BACK_INITIAL_DELAY and
      ((_back_repeat - BACK_INITIAL_DELAY) % BACK_REPEAT_INTERVAL == 0)) then
    if state_ref.buffer.has_selection(buf) then
      state_ref.buffer.delete_selection(buf)
    else
      buf.undo:push(state_ref.buffer.get_state())
      if buf.cx > 0 then
        local line = buf.lines[buf.cy] or ""
        buf.lines[buf.cy] = string.sub(line,1,buf.cx-1)..string.sub(line,buf.cx+1)
        buf.cx = buf.cx - 1
      elseif buf.cy > 1 then
        local current = buf.lines[buf.cy] or ""
        local prev = buf.lines[buf.cy - 1] or ""
        buf.cx = #prev
        buf.lines[buf.cy - 1] = prev .. current
        table.remove(buf.lines, buf.cy)
        buf.cy = buf.cy - 1
      end
      buf.modified = true
    end
    return
  end

  if btnp_safe(KEY_DEL) then
    if state_ref.buffer.has_selection(buf) then
      state_ref.buffer.delete_selection(buf)
    else
      buf.undo:push(state_ref.buffer.get_state())
      local line = buf.lines[buf.cy] or ""
      if buf.cx < #line then
        buf.lines[buf.cy] = string.sub(line,1,buf.cx)..string.sub(line,buf.cx+2)
      elseif buf.cy < #buf.lines then
        buf.lines[buf.cy] = line .. (buf.lines[buf.cy + 1] or "")
        table.remove(buf.lines, buf.cy + 1)
      end
      buf.modified = true
    end
    return
  end

  if btnp_safe(KEY_ENTER) then
    buf.undo:push(state_ref.buffer.get_state())
    if state_ref.buffer.has_selection(buf) then
      state_ref.buffer.delete_selection(buf)
    end
    local line = buf.lines[buf.cy] or ""
    local before = string.sub(line, 1, buf.cx)
    local after = string.sub(line, buf.cx + 1)
    local indent = calculate_indent(before)
    buf.lines[buf.cy] = before
    table.insert(buf.lines, buf.cy + 1, string.rep(" ", indent) .. after)
    buf.cy = buf.cy + 1
    buf.cx = indent
    buf.modified = true
    return
  end

  if btnp_safe(KEY_TAB) and not ctrl then
    buf.undo:push(state_ref.buffer.get_state())
    if state_ref.buffer.has_selection(buf) then
      state_ref.buffer.delete_selection(buf)
    end
    local spaces = string.rep(" ", state_ref.config.tab_width or 2)
    insert_text_at_cursor(buf, spaces)
    return
  end

  local ch = kbchar()
  while ch do
    if not ctrl then
      buf.undo:push(state_ref.buffer.get_state())
      if state_ref.buffer.has_selection(buf) then
        state_ref.buffer.delete_selection(buf)
      end
      insert_text_at_cursor(buf, ch)
    end
    ch = kbchar()
  end
end

-- CRITICAL FIX: Use fixed 44px gutter to match renderer
local function mouse_to_editor_pos(m, win_x, win_y, win_w, win_h)
  local b = state_ref.buffer.cur()
  if not b then return 1, 0 end

  local gutter_px = 44  -- FIXED to match renderer

  local content_h = win_h - state_ref.config.help_h - state_ref.controls.get_controls().height - 30
  local content_top = win_y + win_h - state_ref.controls.get_controls().height - 30

  -- Calculate line
  local dist_from_top = content_top - 4 - m.y
  local visible_line_index = math.floor(dist_from_top / state_ref.config.line_h)
  local line_idx = b.scroll_y + visible_line_index + 1
  line_idx = math.max(1, math.min(#b.lines, line_idx))

  -- Calculate column using FIXED gutter
  local text_start_x = win_x + gutter_px
  local rel_x = m.x - text_start_x

  local line_text = b.lines[line_idx] or ""
  local col = 0

  if text_width and rel_x > 0 then
    local best_col = 0
    local best_diff = math.abs(rel_x)
    for i = 0, #line_text do
      local width_to_i = 0
      if i > 0 then
        local substr = string.sub(line_text, 1, i)
        width_to_i = text_width(substr) or (i * state_ref.config.font_w)
      end
      local diff = math.abs(rel_x - width_to_i)
      if diff < best_diff then
        best_diff = diff
        best_col = i
      end
    end
    col = best_col
  elseif rel_x > 0 then
    col = math.floor(rel_x / state_ref.config.font_w)
  end

  col = math.max(0, math.min(#line_text, col))
  return line_idx, col
end

local function get_tab_bar_layout(win_x, win_y, win_w, win_h)
  local tb_y = win_y + win_h - state_ref.controls.get_controls().height - 30
  local tabs_info = {}
  local tx = win_x + 8

  for i, t in ipairs(state_ref.buffer.get_tabs()) do
    local label = t.path or ("untitled" .. tostring(i))
    local w = math.max(60, (#label + 2) * state_ref.config.font_w)
    tabs_info[i] = {x = tx, y = tb_y, w = w, h = 30}
    tx = tx + w + 6
  end

  local plus_x = tx
  local plus_info = {x = plus_x, y = tb_y, w = 36, h = 30}
  return tb_y, tabs_info, plus_info
end

function M.update(win_x, win_y, win_w, win_h)
  handle_keyboard()

  local m = state_ref.io.get_mouse and state_ref.io.get_mouse() or nil
  if not m then return end

  local buf = state_ref.buffer.cur()
  if not buf then return end

  local tb_y, tabs_info, plus_info = get_tab_bar_layout(win_x, win_y, win_w, win_h)

  if m.click and not old_mouse_click then
    local clicked_tab = false
    for i, info in ipairs(tabs_info) do
      if m.x >= info.x and m.x <= info.x + info.w and m.y >= info.y and m.y <= info.y + info.h then
        state_ref.buffer.set_current_tab(i)
        clicked_tab = true
        break
      end
    end

    if not clicked_tab and m.x >= plus_info.x and m.x <= plus_info.x + plus_info.w and
       m.y >= plus_info.y and m.y <= plus_info.y + plus_info.h then
      state_ref.buffer.increment_untitled()
      state_ref.buffer.open_tab("untitled" .. tostring(state_ref.buffer.get_untitled_counter()) .. ".lua", "-- new file\n")
      state_ref.io.toast("New file opened", 1.0)
      clicked_tab = true
    end

    if not clicked_tab then
      local content_y = win_y + state_ref.config.help_h
      local content_bottom = win_y + win_h - state_ref.controls.get_controls().height - 30

      if m.x > win_x + 44 and m.y >= content_y and m.y <= content_bottom then
        local line_idx, col = mouse_to_editor_pos(m, win_x, win_y, win_w, win_h)
        buf.cy = line_idx
        buf.cx = col
        buf.blink = 0
        buf.mouse_selecting = true
        state_ref.buffer.start_selection(buf)
      end
    end
  end

  if m.right and not old_mouse_right then
    for i, info in ipairs(tabs_info) do
      if m.x >= info.x and m.x <= info.x + info.w and m.y >= info.y and m.y <= info.y + info.h then
        context_open = true
        context_tab = i
        context_x, context_y = m.x, m.y
        break
      end
    end
  end

  if context_open and m.click and not old_mouse_click then
    local menu_x, menu_y = context_x, context_y
    local menu_w, menu_h = 160, 40

    if m.x >= menu_x and m.x <= menu_x + menu_w and m.y >= menu_y - menu_h and m.y <= menu_y then
      local rel = menu_y - m.y
      local option = math.floor(rel / 20) + 1

      if option == 1 then
        state_ref.buffer.close_tab(context_tab)
        state_ref.io.toast("Tab closed", 0.9)
      elseif option == 2 then
        local res = state_ref.io.import_dialog()
        if res and res.path and res.content then
          local filename = res.path:match("([^/\\]+)$") or res.path
          state_ref.buffer.open_tab(filename, res.content)
          state_ref.io.toast("Imported", 1.2)
        else
          state_ref.io.toast("Import cancelled", 1.2)
        end
      end
    end

    context_open = false
    context_tab = nil
  end

  if m.left and old_mouse_left and buf.mouse_selecting then
    local line_idx, col = mouse_to_editor_pos(m, win_x, win_y, win_w, win_h)
    buf.cy = line_idx
    buf.cx = col
    state_ref.buffer.update_selection_end(buf)
  end

  if not m.left and old_mouse_left and buf.mouse_selecting then
    buf.mouse_selecting = false
  end

  old_mouse_click = m.click
  old_mouse_right = m.right
  old_mouse_left = m.left
end

function M.get_context_menu()
  return context_open, context_x, context_y, context_tab
end

return M
