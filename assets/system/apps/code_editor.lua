-- Code Editor Module
local ui = require("system/ui")
local editor = {}

editor.text = "-- Welcome to NerdOS\n\nx = 100\ny = 60\n\nfunction _update()\n  if btn(0) then x -= 1 end\n  if btn(1) then x += 1 end\nend\n\nfunction _draw()\n  cls(1)\n  rect(x,y,8,8,8)\nend"
editor.target_file = "boot.lua"
editor.scroll_offset = 0

function editor.init()
  if fs.exists(editor.target_file) then
     editor.text = fs.read(editor.target_file)
  end
end

function editor.update()
  -- Typing Input
  local c = char()
  while c do
     local b = string.byte(c)

     if b == 8 then -- Backspace
        if #editor.text > 0 then
           editor.text = string.sub(editor.text, 1, -2)
        end
     elseif b == 13 then -- Enter
        editor.text = editor.text .. "\n"
     elseif b >= 32 and b <= 126 then -- Printable
        editor.text = editor.text .. c
     end

     c = char()
  end

  -- Note: We removed the Escape check here.
  -- The Desktop handles closing the app now.

  -- Scroll
  local m = mouse()
  if m.scroll != 0 then
     editor.scroll_offset -= m.scroll * 3
     if editor.scroll_offset < 0 then editor.scroll_offset = 0 end
  end
end

function editor.draw()
  local cx, cy, cw, ch = ui.draw_window("boot.lua - Code Editor", 200, 150, 1520, 800)

  rect(cx, cy, cw, ch, 0)

  -- Toolbar
  local ty = cy + ch
  rect(cx, ty, cw, 40, 18)

  if ui.button("Save", cx+10, ty+5, 100, 30) then
     fs.write(editor.target_file, editor.text)
     log("Saved")
  end

  -- Renders text
  local line_h = 24
  local line_num = 1
  local draw_y = cy + ch - line_h + (editor.scroll_offset * line_h)

  for line in string.gmatch(editor.text, "[^\r\n]+") do
     if draw_y < cy + ch and draw_y > cy then
        rect(cx, draw_y, 50, line_h, 17)
        print(tostring(line_num), cx + 5, draw_y + 4, 6)
        print(line, cx + 60, draw_y + 4, 7)
     end
     draw_y -= line_h
     line_num++
  end

  -- Cursor
  if (os.time() * 3) % 2 == 0 then
     rect(cx + 60 + (#editor.text % 80) * 12, draw_y + 4, 12, 20, 12)
  end
end

return editor
