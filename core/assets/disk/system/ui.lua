-- Modern UI (1080p)
local ui = {}

ui.colors = {
  bg = 0,          -- Black
  win_bg = 1,      -- Dark Blue
  win_header = 12, -- Blue Accent
  accent = 14,     -- Pink/Red Accent
  btn_idle = 13,   -- Grey/Blue
  fg = 7           -- White
}

function ui.draw_window(title, x, y, w, h)
  -- Shadow
  rect(x+10, y-10, w, h+40, 0)
  -- Body
  rect(x, y, w, h, ui.colors.win_bg)
  -- Header
  rect(x, y + h, w, 40, ui.colors.win_header)
  print(title, x + 15, y + h + 10, ui.colors.fg)

  return x, y, w, h
end

function ui.button(text, x, y, w, h)
  local m = mouse()
  local hover = (m.x >= x and m.x <= x+w and m.y >= y and m.y <= y+h)

  -- Visuals
  local held = hover and m.left
  local triggered = hover and m.click

  local c = hover and ui.colors.accent or ui.colors.btn_idle
  if held then c = 7 end

  rect(x, y, w, h, c)

  -- Text
  local ty = y + 8
  if held then
      ty = ty - 2
  end
  print(text, x + 10, ty, held and 0 or 7)

  return triggered
end

return ui
