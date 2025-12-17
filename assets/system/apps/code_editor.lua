-- Code Editor Module (Project Support)
local ui = require("system/ui")
local editor = {}

editor.text = ""
editor.target_file = "project_1.lua" -- Default
editor.scroll_offset = 0

function editor.init()
  -- Auto-load if target exists, otherwise default text
  editor.open(editor.target_file)
end

function editor.open(path)
  editor.target_file = path
  editor.scroll_offset = 0

  if fs.exists(path) then
     local content = fs.read(path)
     if content then
        editor.text = content
     else
        editor.text = "-- Error reading file"
     end
  else
     editor.text = "-- New Project: " .. path .. "\n\nfunction _init()\nend\n\nfunction _update()\nend\n\nfunction _draw()\n  cls(1)\n  print('Hello', 10, 10, 7)\nend"
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

  -- Scroll
  local m = mouse()
  if m.scroll ~= 0 then
     editor.scroll_offset = editor.scroll_offset - (m.scroll * 3)
     if editor.scroll_offset < 0 then editor.scroll_offset = 0 end
  end
end

function editor.draw()
  local cx, cy, cw, ch = ui.draw_window(editor.target_file .. " - Code Editor", 200, 150, 1520, 800)

  rect(cx, cy, cw, ch, 0)

  -- Toolbar
  local ty = cy + ch
  rect(cx, ty, cw, 40, 18)

  if ui.button("Save", cx+10, ty+5, 100, 30) then
     fs.write(editor.target_file, editor.text)
     log("Saved " .. editor.target_file)
  end

  -- Renders text
  local line_h = 24
  local line_num = 1
  -- Standard text is top-down.
  local top_y = cy + ch
  local draw_y = top_y - line_h + (editor.scroll_offset * line_h)

  if editor.text then
      for line in string.gmatch(editor.text, "[^\r\n]+") do
         if draw_y < top_y and draw_y > cy then
            rect(cx, draw_y, 50, line_h, 17)
            print(tostring(line_num), cx + 5, draw_y + 4, 6)
            print(line, cx + 60, draw_y + 4, 7)
         end
         draw_y = draw_y - line_h
         line_num = line_num + 1
      end
  end

  -- Cursor (blinking)
  local time_val = (os and os.time) and os.time() or 0
  if (time_val * 3) % 2 == 0 then
     rect(cx + 60, draw_y + 4, 12, 20, 12)
  end
end

return editor
