-- ============================================================================
-- NerdOS UI Library v2.0 (Enhanced for Code Editor)
-- ============================================================================

local ui = {}

-- ============================================================================
-- THEME / COLORS
-- ============================================================================

ui.colors = {
  bg = 0,          -- Black
  win_bg = 1,      -- Dark Blue
  win_header = 12, -- Blue Accent
  accent = 14,     -- Pink/Red Accent
  btn_idle = 13,   -- Grey/Blue
  btn_hover = 14,  -- Pink/Red
  btn_active = 7,  -- White
  fg = 7,          -- White
  fg_dim = 5,      -- Grey
  border = 13,     -- Grey/Blue
  input_bg = 0,    -- Black
  scrollbar = 5,   -- Grey
  scrollbar_thumb = 13, -- Grey/Blue
  gutter = 1,      -- Dark Blue
  line_num = 13,   -- Grey/Blue
  selection = 2,   -- Dark Red
  cursor = 10      -- Yellow
}

ui.font_w = 8
ui.font_h = 16

-- ============================================================================
-- HELPER FUNCTIONS
-- ============================================================================

function ui.init()
  if font_width then ui.font_w = font_width() end
  if font_height then ui.font_h = font_height() end
end

function ui.point_in_rect(px, py, x, y, w, h)
  return px >= x and px <= x + w and py >= y and py <= y + h
end

-- ============================================================================
-- WINDOW
-- ============================================================================

function ui.draw_window(title, x, y, w, h)
  -- Shadow (offset down and right)
  rect(x + 4, y - 4, w, h + 40, 0)

  -- Body
  rect(x, y, w, h, ui.colors.win_bg)

  -- Header bar
  rect(x, y + h, w, 40, ui.colors.win_header)
  print(title, x + 15, y + h + 12, ui.colors.fg)

  return x, y, w, h
end

function ui.panel(x, y, w, h, color)
  color = color or ui.colors.win_bg
  rect(x, y, w, h, color)
  return x, y, w, h
end

-- ============================================================================
-- BUTTON
-- ============================================================================

function ui.button(text, x, y, w, h)
  local m = mouse()
  if not m then return false end

  local hover = ui.point_in_rect(m.x, m.y, x, y, w, h)
  local held = hover and m.left
  local clicked = hover and m.click

  -- Visual state
  local c = ui.colors.btn_idle
  if hover then c = ui.colors.btn_hover end
  if held then c = ui.colors.btn_active end

  rect(x, y, w, h, c)

  -- Text (center aligned)
  local text_w = #text * ui.font_w
  local tx = x + (w - text_w) / 2
  local ty = y + (h - ui.font_h) / 2
  if held then ty = ty - 2 end

  print(text, tx, ty, held and 0 or ui.colors.fg)

  return clicked
end

function ui.icon_button(icon, x, y, size)
  local m = mouse()
  if not m then return false end

  local hover = ui.point_in_rect(m.x, m.y, x, y, size, size)
  local held = hover and m.left
  local clicked = hover and m.click

  local c = hover and ui.colors.accent or ui.colors.btn_idle
  if held then c = ui.colors.btn_active end

  rect(x, y, size, size, c)

  local tx = x + (size - #icon * ui.font_w) / 2
  local ty = y + (size - ui.font_h) / 2
  print(icon, tx, ty, held and 0 or ui.colors.fg)

  return clicked
end

-- ============================================================================
-- TEXT INPUT (Basic)
-- ============================================================================

function ui.text_input(text, x, y, w, h, focused, id)
  local m = mouse()
  local hover = m and ui.point_in_rect(m.x, m.y, x, y, w, h)
  local clicked = hover and m.click

  -- Background
  rect(x, y, w, h, ui.colors.input_bg)

  -- Border
  local border_col = focused and ui.colors.accent or ui.colors.border
  -- Top/Bottom borders
  rect(x, y + h - 1, w, 1, border_col)
  rect(x, y, w, 1, border_col)
  -- Side borders
  rect(x, y, 1, h, border_col)
  rect(x + w - 1, y, 1, h, border_col)

  -- Text
  local tx = x + 5
  local ty = y + (h - ui.font_h) / 2
  print(text or "", tx, ty, ui.colors.fg)

  -- Cursor (if focused)
  if focused then
    local cursor_x = tx + #text * ui.font_w
    rect(cursor_x, ty, 2, ui.font_h, ui.colors.cursor)
  end

  return clicked, text
end

-- ============================================================================
-- CHECKBOX
-- ============================================================================

function ui.checkbox(label, checked, x, y, size)
  local m = mouse()
  if not m then return checked end

  size = size or 20
  local hover = ui.point_in_rect(m.x, m.y, x, y, size, size)
  local clicked = hover and m.click

  -- Box
  local box_col = hover and ui.colors.accent or ui.colors.border
  rect(x, y, size, size, ui.colors.input_bg)
  rect(x, y, size, 1, box_col)
  rect(x, y + size - 1, size, 1, box_col)
  rect(x, y, 1, size, box_col)
  rect(x + size - 1, y, 1, size, box_col)

  -- Check mark
  if checked then
    rect(x + 4, y + 4, size - 8, size - 8, ui.colors.accent)
  end

  -- Label
  if label then
    local lx = x + size + 10
    local ly = y + (size - ui.font_h) / 2
    print(label, lx, ly, ui.colors.fg)
  end

  if clicked then
    return not checked
  end
  return checked
end

-- ============================================================================
-- SCROLLBAR
-- ============================================================================

function ui.scrollbar(x, y, w, h, scroll_pos, content_height, visible_height)
  if content_height <= visible_height then
    return scroll_pos -- No scrolling needed
  end

  -- Track
  rect(x, y, w, h, ui.colors.scrollbar)

  -- Thumb size and position
  local thumb_ratio = visible_height / content_height
  local thumb_h = math.max(20, h * thumb_ratio)
  local scroll_ratio = scroll_pos / (content_height - visible_height)
  local thumb_y = y + (h - thumb_h) * scroll_ratio

  -- Thumb
  rect(x, thumb_y, w, thumb_h, ui.colors.scrollbar_thumb)

  -- Handle mouse interaction
  local m = mouse()
  if m and m.left and ui.point_in_rect(m.x, m.y, x, y, w, h) then
    local click_ratio = (m.y - y) / h
    scroll_pos = click_ratio * (content_height - visible_height)
    scroll_pos = math.max(0, math.min(scroll_pos, content_height - visible_height))
  end

  return scroll_pos
end

-- ============================================================================
-- TOOLBAR
-- ============================================================================

function ui.toolbar(x, y, w, h)
  rect(x, y, w, h, ui.colors.win_header)
  return x + 5, y + (h - 20) / 2, 20 -- Return position for first button
end

-- ============================================================================
-- MENU ITEM
-- ============================================================================

function ui.menu_item(text, x, y, w, h)
  local m = mouse()
  if not m then return false end

  local hover = ui.point_in_rect(m.x, m.y, x, y, w, h)
  local clicked = hover and m.click

  if hover then
    rect(x, y, w, h, ui.colors.accent)
  end

  local tx = x + 10
  local ty = y + (h - ui.font_h) / 2
  print(text, tx, ty, ui.colors.fg)

  return clicked
end

-- ============================================================================
-- TAB BAR
-- ============================================================================

function ui.tab(text, active, x, y, w, h)
  local m = mouse()
  if not m then return false end

  local hover = ui.point_in_rect(m.x, m.y, x, y, w, h)
  local clicked = hover and m.click

  local c = ui.colors.win_bg
  if active then c = ui.colors.accent end
  if hover and not active then c = ui.colors.btn_hover end

  rect(x, y, w, h, c)

  local tx = x + (w - #text * ui.font_w) / 2
  local ty = y + (h - ui.font_h) / 2
  print(text, tx, ty, ui.colors.fg)

  return clicked
end

-- ============================================================================
-- TOAST / NOTIFICATION
-- ============================================================================

function ui.toast(text, x, y)
  local w = #text * ui.font_w + 20
  local h = ui.font_h + 20

  x = x or (display_width() / 2 - w / 2)
  y = y or 60

  -- Shadow
  rect(x + 4, y - 4, w, h, 0)

  -- Background
  rect(x, y, w, h, ui.colors.win_header)

  -- Text
  local tx = x + 10
  local ty = y + 10
  print(text, tx, ty, ui.colors.fg)
end

-- ============================================================================
-- LABEL
-- ============================================================================

function ui.label(text, x, y, color)
  color = color or ui.colors.fg
  print(text, x, y, color)
end

function ui.label_dim(text, x, y)
  print(text, x, y, ui.colors.fg_dim)
end

-- ============================================================================
-- DIVIDER
-- ============================================================================

function ui.divider(x, y, w, vertical)
  if vertical then
    rect(x, y, 1, w, ui.colors.border)
  else
    rect(x, y, w, 1, ui.colors.border)
  end
end

-- ============================================================================
-- HELPER: Layout Stack
-- ============================================================================

ui.layout = {
  x = 0,
  y = 0,
  spacing = 5
}

function ui.begin_layout(x, y, spacing)
  ui.layout.x = x
  ui.layout.y = y
  ui.layout.spacing = spacing or 5
end

function ui.layout_button(text, w, h)
  local clicked = ui.button(text, ui.layout.x, ui.layout.y, w, h)
  ui.layout.y = ui.layout.y - h - ui.layout.spacing
  return clicked
end

-- ============================================================================
-- EXPORT
-- ============================================================================

return ui
