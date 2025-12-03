-- NerdOS Desktop v3.1 (Stable)
local ui = require("system/ui")
local code_app = require("system/apps/code_editor")

-- UI State
start_menu_open = false
exit_dialog_open = false
current_app = nil
game_running = false

function _init()
  log("Desktop v3.1 Loaded")
  code_app.init()
end

function _update()
  -- 1. GLOBAL HOTKEYS
  -- Only check Escape if we aren't typing in an app (or handle inside app?)
  -- For simplicity, Escape ALWAYS acts as "Back"
  if keyp("escape") then
     if exit_dialog_open then
        exit_dialog_open = false
     elseif start_menu_open then
        start_menu_open = false
     elseif game_running then
        game_running = false
        log("Game Stopped")
     elseif current_app then
        current_app = nil
        log("App Closed")
     else
        -- On root desktop? Open exit dialog
        exit_dialog_open = true
     end
  end

  -- 2. STATE UPDATES
  if exit_dialog_open then
     -- Freeze everything else
  elseif game_running then
     if _user_update then _user_update() end
  elseif current_app then
     -- Pass control to the app
     if current_app.update then current_app.update() end
  else
     -- Desktop Logic
     update_desktop()
  end
end

function update_desktop()
  -- Handle "Click Outside Start Menu"
  local m = mouse()
  if start_menu_open and m.click then
     -- Menu Area: x=10, y=45, w=200, h=160
     -- Start Button Area: x=10, y=5, w=100, h=30
     local in_menu = (m.x >= 10 and m.x <= 210 and m.y >= 45 and m.y <= 205)
     local in_btn = (m.x >= 10 and m.x <= 110 and m.y >= 5 and m.y <= 35)

     if not in_menu and not in_btn then
        start_menu_open = false
     end
  end
end

function _draw()
  -- 1. DRAW WALLPAPER
  sys.target("os")
  cls(16)

  -- Grid
  for i=0, 1920, 60 do line(i, 0, i, 1080, 17) end
  for i=0, 1080, 60 do line(0, i, 1920, i, 17) end

  -- 2. DRAW CONTENT
  if game_running then
     draw_game_window()
  elseif current_app then
     current_app.draw()
  else
     draw_desktop_icons()
  end

  -- 3. OVERLAYS
  draw_taskbar()
  if start_menu_open then draw_start_menu() end
  if exit_dialog_open then draw_exit_dialog() end
end

-- --- COMPONENTS ---

function draw_taskbar()
  rect(0, 0, 1920, 40, 1) -- Bar

  -- Start Button
  -- Note: We handle the toggle logic here
  if ui.button("START", 10, 5, 100, 30) then
     start_menu_open = not start_menu_open
     exit_dialog_open = false
  end

  -- Clock
  print(os.date("%H:%M"), 1850, 10, 7)
end

function draw_start_menu()
  local mx, my, mw, mh = 10, 45, 200, 160

  -- Menu Background
  rect(mx+4, my-4, mw, mh, 0) -- Shadow
  rect(mx, my, mw, mh, 17)    -- Body
  rect(mx, my, mw, 1, 12)     -- Border
  rect(mx, my+mh, mw, 1, 12)
  rect(mx, my, 1, mh, 12)
  rect(mx+mw, my, 1, mh, 12)

  local by = my + mh - 40

  -- Options
  if ui.button("Code Editor", mx+10, by, mw-20, 30) then
     current_app = code_app
     start_menu_open = false
  end
  by -= 40

  if ui.button("Run Game", mx+10, by, mw-20, 30) then
     launch_game()
     start_menu_open = false
  end
  by -= 40

  if ui.button("Shutdown", mx+10, by, mw-20, 30) then
     start_menu_open = false
     exit_dialog_open = true
  end
end

function draw_exit_dialog()
  local dw, dh = 400, 200
  local dx, dy = (1920-dw)/2, (1080-dh)/2

  ui.draw_window("System Halt", dx, dy, dw, dh)
  rect(dx, dy, dw, dh, 0)

  print("Are you sure you want to quit?", dx + 40, dy + 120, 7)

  if ui.button("Cancel", dx + 40, dy + 40, 140, 40) then
     exit_dialog_open = false
  end

  if ui.button("Quit", dx + 220, dy + 40, 140, 40) then
     sys.exit()
  end
end

function draw_desktop_icons()
  local ix, iy = 60, 900
  -- Icon
  rect(ix, iy, 64, 64, 20)
  rect(ix+16, iy+16, 32, 32, 7)
  print("Code", ix+10, iy-25, 7)

  -- Click Logic
  local m = mouse()
  if m.click and m.x > ix and m.x < ix+64 and m.y > iy and m.y < iy+64 then
     current_app = code_app
  end
end

function draw_game_window()
  sys.target("game")
  if _user_draw then _user_draw() end

  sys.target("os")
  local scale = 4
  local gw, gh = 240*scale, 136*scale
  local gx, gy = (1920-gw)/2, (1080-gh)/2

  ui.draw_window("Running Game (ESC to Close)", gx-4, gy-4, gw+8, gh+40)
  sys.draw_game(gx, gy, gw, gh)
end

function launch_game()
  dofile("boot.lua")
  _user_update = _update
  _user_draw = _draw
  _update = _os_update
  _draw = _os_draw
  game_running = true
end

_os_update = _update
_os_draw = _draw
