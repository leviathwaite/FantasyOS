-- input.lua
local M = {}
local MouseHandler = require("system/tools/code_module/mouse_handler")
local Clipboard = require("system/tools/code_module/clipboard")
local Search = require("system/tools/code_module/search")
local Indent = require("system/tools/code_module/indent")

local state_ref = nil

function M.init(state)
    state_ref = state
end

-- Constants (to match original usage)
local KEY_BACK = 67 -- or state_ref.config.keys.BACK
local KEY_ENTER = 66
local KEY_TAB = 61
local KEY_DEL = 112
local KEY_SPACE = 62
local KEY_LEFT = 21
local KEY_RIGHT = 22
local KEY_UP = 19
local KEY_DOWN = 20
local KEY_HOME = 3
local KEY_END = 123
local KEY_PGUP = 92
local KEY_PGDN = 93
local KEY_A = 29
local KEY_C = 31
local KEY_F = 34
local KEY_G = 35
local KEY_R = 46
local KEY_S = 47
local KEY_V = 50
local KEY_X = 52
local KEY_Y = 53
local KEY_Z = 54
local KEY_F3 = 133
local KEY_MINUS = 69
local KEY_EQUALS = 70

local BACK_INITIAL_DELAY = 20
local BACK_REPEAT_INTERVAL = 2
local _back_repeat = 0

-- Safe toast function helper
local function show_toast(msg)
    if state_ref and state_ref.io and state_ref.io.toast then
        state_ref.io.toast(msg)
    end
end

-- Helper Wrappers
local function btn_safe(i)
    if type(btn) == "function" then return btn(i) end
    return false
end

local function btnp_safe(i)
    if type(btnp) == "function" then return btnp(i) end
    return false
end

local function kbchar()
    if type(char) == "function" then 
        local ok, ch = pcall(char)
        if ok and ch then return ch end
    end
    return nil
end

local function is_ctrl()
    if not state_ref or not state_ref.config then return false end
    local K = state_ref.config.keys
    return btn_safe(K.CTRL_L) or btn_safe(K.CTRL_R)
end

local function is_shift()
    if not state_ref or not state_ref.config then return false end
    local K = state_ref.config.keys
    return btn_safe(K.SHIFT_L) or btn_safe(K.SHIFT_R)
end

local function calculate_indent(str)
    if Indent and Indent.get_indent_level then
        return Indent.get_indent_level(str)
    end
    -- Fallback
    local spaces = 0
    for i = 1, #str do
        if string.sub(str, i, i) == " " then spaces = spaces + 1
        elseif string.sub(str, i, i) == "\t" then spaces = spaces + 2
        else break end
    end
    return spaces
end

local function insert_text_at_cursor(buf, text)
    local line = buf.lines[buf.cy] or ""
    local before = string.sub(line, 1, buf.cx)
    local after = string.sub(line, buf.cx + 1)
    buf.lines[buf.cy] = before .. text .. after
    buf.cx = buf.cx + #text
    buf.modified = true
end

local function handle_keyboard()
  local buf = state_ref.buffer.cur()
  if not buf then return end
  buf.blink = (buf.blink or 0) + 1

  local K = state_ref.config.keys
  -- Use config keys if available, else fallback to locals?
  -- Actually let's assume K is available via state_ref.config.keys or hardcoded bindings
  -- To match existing input.lua style, let's stick to local btnp_safe checks but use K constants if they match
  -- The existing input.lua defined KEY_ constants at top. We should use those or map them.
  -- Let's stick to existing KEY constants defined in input.lua for consistency.
  
  local ctrl = is_ctrl()
  local shift = is_shift()

  -- Handle dialog mode input
  if state_ref.dialog_mode then
      -- ESC to cancel dialog
      if btnp_safe(KEY_BACK) and #state_ref.dialog_input == 0 then
          state_ref.dialog_mode = nil
          state_ref.dialog_input = ""
          return
      end
      
      -- Backspace in dialog
      if btnp_safe(KEY_BACK) and #state_ref.dialog_input > 0 then
          state_ref.dialog_input = string.sub(state_ref.dialog_input, 1, -2)
          return
      end
      
      -- Enter to execute dialog action
      if btnp_safe(KEY_ENTER) then
          if state_ref.dialog_mode == "goto" then
              local line_num = tonumber(state_ref.dialog_input)
              if line_num and line_num >= 1 and line_num <= #buf.lines then
                  buf.cy = line_num
                  buf.cx = 0
                  show_toast("⇒ Go to line " .. line_num)
              else
                  show_toast("✗ Invalid line number")
              end
          elseif state_ref.dialog_mode == "search" then
              if #state_ref.dialog_input > 0 then
                  Search.activate(state_ref.dialog_input)
                  Search.find_all(buf, state_ref.dialog_input)
                  if #Search.matches > 0 then
                      Search.find_next(buf)
                      show_toast("🔍 Found " .. #Search.matches .. " matches")
                  else
                      show_toast("No matches found")
                  end
              end
          end
          state_ref.dialog_mode = nil
          state_ref.dialog_input = ""
          return
      end
      
      -- Type into dialog
      local ch = kbchar()
      while ch do
          state_ref.dialog_input = state_ref.dialog_input .. ch
          ch = kbchar()
      end
      
      return  -- Don't process other input while in dialog
  end

  -- Find Next (F3) - Assuming F3 is mapped to KEY_F3 or we need to add it
  -- KEY_F is generic. Let's assume F3 is 34? No, F is 34.
  -- input.lua didn't define F3. Let's add it. 
  -- LibGDX F3 is 133? No.
  -- LuaTool.java binds Key constants.
  -- Let's check KEY_F3.
  -- Input.Keys.F3 is 133.
  -- Let's add KEY_F3 = 133.
  local KEY_F3 = 133
  local KEY_G = 35 -- Input.Keys.G

  if btnp_safe(KEY_F3) and not ctrl then
      if Search.active and #Search.matches > 0 then
          if shift then
              Search.find_previous(buf)
          else
              Search.find_next(buf)
          end
      end
      return
  end
  
  if ctrl then
      -- Font Scaling
      if btnp_safe(KEY_EQUALS) then
          state_ref.io.set_editor_font_size(state_ref.config.font_size + 2)
          return
      end
      if btnp_safe(KEY_MINUS) then
          state_ref.io.set_editor_font_size(state_ref.config.font_size - 2)
          return
      end
      
      -- Find (Ctrl+F)
      if btnp_safe(KEY_F) then
        state_ref.dialog_mode = "search"
        state_ref.dialog_input = ""
        show_toast("🔍 Enter search query")
        return
  end
  end

  -- Go to Line (Ctrl+G)
  if ctrl and btnp_safe(KEY_G) then
        state_ref.dialog_mode = "goto"
        state_ref.dialog_input = ""
        show_toast("⇒ Enter line number")
        return
  end

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
    Clipboard.copy(buf)
    show_toast("📋 Copied")
    return
  end

  if ctrl and btnp_safe(KEY_X) then
    buf.undo:push(state_ref.buffer.get_state())
    Clipboard.cut(buf)
    show_toast("✂ Cut")
    return
  end

  if ctrl and btnp_safe(KEY_V) then
    buf.undo:push(state_ref.buffer.get_state())
    Clipboard.paste(buf, state_ref.split_lines)
    show_toast("📄 Pasted")
    return
  end

  if ctrl and btnp_safe(KEY_Z) then
    local s = buf.undo:undo()
    if s then 
        state_ref.buffer.restore_state(s)
        show_toast("↶ Undo")
    end
    return
  end

  if ctrl and btnp_safe(KEY_Y) then
    local s = buf.undo:redo()
    if s then 
        state_ref.buffer.restore_state(s)
        show_toast("↷ Redo")
    end
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

  -- Backspace/Delete handling
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
    
    -- Auto-indent apply (if enabled)
    if state_ref.config.features.auto_indent and Indent then
        Indent.apply_auto_indent(buf)
    end
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

local function get_tab_bar_layout(win_x, win_y, win_w, win_h)
  local controls_height = 48 -- Config.controls.height
  -- Tab bar is below controls in Screen Y (Top-Left 0)
  local tb_y = win_y + controls_height
  
  local tabs_info = {}
  local tx = win_x + 10

  for i, t in ipairs(state_ref.buffer.get_tabs()) do
    local label = t.path or ("untitled" .. tostring(i))
    if t.modified then label = label .. "*" end
    local w = math.max(60, (#label + 2) * 8) -- Approx width
    tabs_info[i] = {x = tx, y = tb_y, w = w, h = 32}
    tx = tx + w + 16
  end

  local plus_x = tx
  local plus_info = {x = plus_x, y = tb_y, w = 32, h = 32}
  return tb_y, tabs_info, plus_info
end

function M.update(win_x, win_y, win_w, win_h)
  handle_keyboard()

  local m = state_ref.io.get_mouse and state_ref.io.get_mouse() or nil
  if not m then return end

  local buf = state_ref.buffer.cur()
  if not buf then return end
  
  -- Handle text area interaction using dedicated handler
  MouseHandler.handle_mouse(buf, win_x, win_y, win_w, win_h)

  local tb_y, tabs_info, plus_info = get_tab_bar_layout(win_x, win_y, win_w, win_h)

  if m.click and not old_mouse_click then
    local handled = false
    
    -- Layout (Y-Up: 0 at bottom, H at top)
    local header_h = 56  -- External header
    local tab_bar_h = 40
    local tab_bar_top = win_h - header_h
    local tab_bar_bottom = tab_bar_top - tab_bar_h
    
    -- Tab Bar area (includes Save/Run buttons on right)
    if m.y <= tab_bar_top and m.y >= tab_bar_bottom then
        local icon_size = 24
        local right_margin = 10
        
        -- Run Button (Rightmost)
        local run_x = win_x + win_w - icon_size - right_margin
        if m.x >= run_x and m.x <= run_x + icon_size then
             local path = buf.path or "main.lua"
             state_ref.io.run(path)
             state_ref.io.toast("Running...", 1.0)
             handled = true
        -- Save Button (Left of Run)
        else
            local save_x = win_x + win_w - (icon_size * 2) - (right_margin * 2)
            if m.x >= save_x and m.x <= save_x + icon_size then
                 local path = buf.path or "main.lua"
                 local ok = state_ref.io.save(path, table.concat(buf.lines, "\n"))
                 if ok then
                   buf.path = path
                   buf.modified = false
                   state_ref.io.toast("Saved", 1.0)
                 end
                 handled = true
            end
        end
    end
    
    if not handled then
        -- Check tabs (using Top-Left coords)
        for i, info in ipairs(tabs_info) do
          if m.x >= info.x and m.x <= info.x + info.w and m.y >= info.y and m.y <= info.y + info.h then
            state_ref.buffer.set_current_tab(i)
            handled = true
            break
          end
        end

        if not handled and m.x >= plus_info.x and m.x <= plus_info.x + plus_info.w and
           m.y >= plus_info.y and m.y <= plus_info.y + plus_info.h then
          state_ref.buffer.increment_untitled()
          state_ref.buffer.open_tab("untitled" .. tostring(state_ref.buffer.get_untitled_counter()) .. ".lua", "-- new file\n")
          state_ref.io.toast("New file opened", 1.0)
          handled = true
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

  old_mouse_click = m.click
  old_mouse_right = m.right
  old_mouse_left = m.left
end

function M.get_context_menu()
  return context_open, context_x, context_y, context_tab
end

return M
