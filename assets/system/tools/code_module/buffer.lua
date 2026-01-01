local UndoRedo = require("system/tools/code_module/undo_redo")
local Buffer = {}

function Buffer.split_lines(txt)
  if not txt or txt == "" then return {""} end
  local lines = {}
  for line in string.gmatch(txt, "([^\n]*)\n?") do
    table.insert(lines, line)
  end
  if #lines == 0 then lines = {""} end
  return lines
end

function Buffer.new(path, text)
  local b = {}
  b.path = path or nil
  b.lines = Buffer.split_lines(text or "")
  b.clipboard = ""
  b.undo = UndoRedo.new()
  b.cx = 0
  b.cy = 1
  b.scroll_y = 0
  b.modified = false
  b.blink = 0
  b.mouse_selecting = false
  b.find_mode = false
  -- Selection state
  b.sel_start_x = nil
  b.sel_start_y = nil
  b.sel_end_x = nil
  b.sel_end_y = nil
  return b
end

function Buffer.get_snapshot(b)
    local st = {}
    for i, ln in ipairs(b.lines) do st[i] = ln end
    return st
end

-- ==================== SELECTION FUNCTIONS ====================

function Buffer.has_selection(buf)
  return buf.sel_start_x and buf.sel_start_y and
         buf.sel_end_x and buf.sel_end_y and
         not (buf.sel_start_x == buf.sel_end_x and buf.sel_start_y == buf.sel_end_y)
end

function Buffer.start_selection(buf)
  buf.sel_start_x = buf.cx
  buf.sel_start_y = buf.cy
  buf.sel_end_x = buf.cx
  buf.sel_end_y = buf.cy
end

function Buffer.update_selection_end(buf)
  buf.sel_end_x = buf.cx
  buf.sel_end_y = buf.cy
end

function Buffer.clear_selection(buf)
  buf.sel_start_x = nil
  buf.sel_start_y = nil
  buf.sel_end_x = nil
  buf.sel_end_y = nil
end

function Buffer.get_selection_bounds(buf)
  if not Buffer.has_selection(buf) then return nil end

  local sy, sx = buf.sel_start_y, buf.sel_start_x
  local ey, ex = buf.sel_end_y, buf.sel_end_x

  -- Normalize so start is before end
  if sy > ey or (sy == ey and sx > ex) then
    sy, sx, ey, ex = ey, ex, sy, sx
  end

  return sy, sx, ey, ex
end

function Buffer.get_selected_text(buf)
  local sy, sx, ey, ex = Buffer.get_selection_bounds(buf)
  if not sy then return "" end

  if sy == ey then
    -- Single line selection
    local line = buf.lines[sy] or ""
    return string.sub(line, sx + 1, ex)
  end

  -- Multi-line selection
  local result = string.sub(buf.lines[sy] or "", sx + 1) .. "\n"
  for i = sy + 1, ey - 1 do
    result = result .. (buf.lines[i] or "") .. "\n"
  end
  result = result .. string.sub(buf.lines[ey] or "", 1, ex)

  return result
end

function Buffer.delete_selection(buf)
  local sy, sx, ey, ex = Buffer.get_selection_bounds(buf)
  if not sy then return end

  buf.undo:push(Buffer.get_snapshot(buf))

  if sy == ey then
    -- Single line
    local line = buf.lines[sy] or ""
    buf.lines[sy] = string.sub(line, 1, sx) .. string.sub(line, ex + 1)
  else
    -- Multi-line
    local first = string.sub(buf.lines[sy] or "", 1, sx)
    local last = string.sub(buf.lines[ey] or "", ex + 1)
    buf.lines[sy] = first .. last

    -- Remove lines between
    for i = ey, sy + 1, -1 do
      table.remove(buf.lines, i)
    end
  end

  buf.cy = sy
  buf.cx = sx
  Buffer.clear_selection(buf)
  buf.modified = true
end

-- ==================== UTF-8 SUPPORT (Simplified) ====================
-- For basic ASCII, these just work with string length
-- For full UTF-8, you'd need proper UTF-8 library

function Buffer.utf8_char_count(str)
  -- Simple version: just return string length
  -- This works for ASCII; for true UTF-8 you'd need to count actual characters
  return #str
end

function Buffer.visual_to_byte_offset(str, visual_pos)
  -- Simple version: visual position = byte position for ASCII
  return visual_pos
end

return Buffer
