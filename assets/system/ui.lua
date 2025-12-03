-- Modern UI (1080p)
local ui = {}

ui.colors = {
  bg = 16, win_bg = 17, win_header = 1, accent = 12, fg = 7
}

function ui.draw_window(title, x, y, w, h)
  -- Shadow
  rect(x+10, y-10, w, h+40, 0)
  -- Body
  rect(x, y, w, h, ui.colors.win_bg)
  -- Header
  rect(x, y + h, w, 40, ui.colors.win_header)
  print(title, x + 15, y + h + 10, 7)

  return x, y, w, h
end

function ui.button(text, x, y, w, h)
  local m = mouse()
  local hover = (m.x >= x and m.x <= x+w and m.y >= y and m.y <= y+h)

  -- Visuals: Look "pressed" if holding
  local held = hover and m.left
  -- Logic: Trigger ONLY on release (click)
  local triggered = hover and m.click

  local c = hover and ui.colors.accent or 18
  if held then c = 7 end

  rect(x, y, w, h, c)
  -- Center text roughly
  local ty = y + 8
  if held then ty -= 2 end -- Pressed effect
  print(text, x + 10, ty, held and 0 or 7)

  return triggered
end

return ui
