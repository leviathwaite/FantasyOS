-- NerdOS Desktop v4.1 (High Contrast Fix)

-- 1. POLYFILL LOADER
function load_from_disk(path)
   local content = fs.read(path)
   if not content then error("Could not read " .. path) end
   local chunk, err = load(content, path)
   if not chunk then error(err) end
   return chunk()
end

-- Load Modules
_G.ui_module = load_from_disk("system/ui.lua")
local ui = _G.ui_module
local code_app = load_from_disk("system/apps/code_editor.lua")

-- UI State
start_menu_open = false
exit_dialog_open = false
new_project_dialog_open = false
open_project_dialog_open = false
current_app = nil
game_running = false

-- Project State
current_project = "boot.lua"
project_list = {}

function _init()
  log("Desktop v4.1 Loaded")
  if code_app.open then code_app.open(current_project) end
end

function _update()
  -- GLOBAL HOTKEYS
  if keyp("escape") then
     if new_project_dialog_open then
        new_project_dialog_open = false
     elseif open_project_dialog_open then
        open_project_dialog_open = false
     elseif exit_dialog_open then
        exit_dialog_open = false
     elseif start_menu_open then
        start_menu_open = false
     elseif game_running then
        game_running = false
        log("Game Stopped")
        sys.target("os")
        if _os_update then _update = _os_update end
        if _os_draw then _draw = _os_draw end
     elseif current_app then
        current_app = nil
        log("App Closed")
     else
        exit_dialog_open = true
     end
  end

  if exit_dialog_open or new_project_dialog_open or open_project_dialog_open then
     -- Freeze
  elseif game_running then
     if _user_update then
        local status, err = pcall(_user_update)
        if not status then
           log("Runtime Error (Update): " .. tostring(err))
           game_running = false
           sys.target("os")
           if _os_update then _update = _os_update end
           if _os_draw then _draw = _os_draw end
        end
     end
  elseif current_app then
     if current_app.update then current_app.update() end
  else
     update_desktop()
  end
end

function update_desktop()
  local m = mouse()
  if start_menu_open and m.click then
     local in_menu = (m.x >= 10 and m.x <= 210 and m.y >= 45 and m.y <= 285)
     local in_btn = (m.x >= 10 and m.x <= 110 and m.y >= 5 and m.y <= 35)
     if not in_menu and not in_btn then start_menu_open = false end
  end
end

function _draw()
  sys.target("os")
  -- Clear with dark blue (1) instead of 16 to ensure visibility
  cls(1)

  -- Grid
  for i=0, 1920, 60 do line(i, 0, i, 1080, 13) end
  for i=0, 1080, 60 do line(0, i, 1920, i, 13) end

  if game_running then
     draw_game_window()
  elseif current_app then
     if current_app.draw then
         local ok, err = pcall(current_app.draw)
         if not ok then
             log("App Draw Error: " .. tostring(err))
             current_app = nil
         end
     end
  else
     draw_desktop_icons()
  end

  draw_taskbar()
  if start_menu_open then draw_start_menu() end
  if exit_dialog_open then draw_exit_dialog() end
  if new_project_dialog_open then draw_new_project_dialog() end
  if open_project_dialog_open then draw_open_project_dialog() end
end

function draw_taskbar()
  -- Taskbar background (Dark Grey/Black)
  rect(0, 0, 1920, 40, 0)

  -- Separator line
  rect(0, 40, 1920, 2, 6)

  -- Start Button
  if ui.button("START", 10, 5, 100, 30) then
     start_menu_open = not start_menu_open
     exit_dialog_open = false
     new_project_dialog_open = false
     open_project_dialog_open = false
  end

  -- Clock / Project Status
  print(os.date("%H:%M") .. " | " .. current_project, 1750, 10, 7)
end

function draw_start_menu()
  local mx, my, mw, mh = 10, 45, 200, 240
  rect(mx+4, my-4, mw, mh, 0) -- Shadow
  rect(mx, my, mw, mh, 1)     -- Body (Dark Blue)
  rect(mx, my, mw, 1, 7)      -- Borders
  rect(mx, my+mh, mw, 1, 7)
  rect(mx, my, 1, mh, 7)
  rect(mx+mw, my, 1, mh, 7)

  local by = my + mh - 40

  if ui.button("Code Editor", mx+10, by, mw-20, 30) then
     current_app = code_app
     start_menu_open = false
  end
  by = by - 40

  if ui.button("Run Game", mx+10, by, mw-20, 30) then
     launch_game()
     start_menu_open = false
  end
  by = by - 40

  if ui.button("New Project", mx+10, by, mw-20, 30) then
     start_menu_open = false
     new_project_dialog_open = true
  end
  by = by - 40

  if ui.button("Open Project", mx+10, by, mw-20, 30) then
     start_menu_open = false
     refresh_project_list()
     open_project_dialog_open = true
  end
  by = by - 40

  if ui.button("Shutdown", mx+10, by, mw-20, 30) then
     start_menu_open = false
     exit_dialog_open = true
  end
end

function draw_new_project_dialog()
  local dw, dh = 400, 200
  local dx, dy = (1920-dw)/2, (1080-dh)/2

  ui.draw_window("New Project", dx, dy, dw, dh)
  rect(dx, dy, dw, dh, 0)

  print("Create new project file?", dx + 40, dy + 120, 7)

  if ui.button("Cancel", dx + 40, dy + 40, 140, 40) then
     new_project_dialog_open = false
  end

  if ui.button("Create", dx + 220, dy + 40, 140, 40) then
     create_new_project()
     new_project_dialog_open = false
  end
end

function create_new_project()
  -- Find unique name
  local id = 1
  while fs.exists("project_" .. id .. ".lua") do
     id = id + 1
  end
  local name = "project_" .. id .. ".lua"

  local template = [[-- Project: ]] .. name .. [[

x = 120
y = 68

function _init()
  log("Init ]] .. name .. [[")
end

function _update()
  if btn(0) then x = x - 1 end
  if btn(1) then x = x + 1 end
  if btn(2) then y = y + 1 end
  if btn(3) then y = y - 1 end
end

function _draw()
  cls(1)
  print("]] .. name .. [[", x, y, 7)
  rect(x, y+10, 8, 8, 8)
end
]]
  fs.write(name, template)
  current_project = name
  code_app.open(current_project)
  log("Created " .. name)
end

function refresh_project_list()
   local files = fs.list("")
   project_list = {}
   for k,v in pairs(files) do
       if string.sub(v, -4) == ".lua" then
           table.insert(project_list, v)
       end
   end
end

function draw_open_project_dialog()
  local dw, dh = 600, 600
  local dx, dy = (1920-dw)/2, (1080-dh)/2

  ui.draw_window("Open Project", dx, dy, dw, dh)
  rect(dx, dy, dw, dh, 0)

  local by = dy + dh - 60

  -- List Files
  for i, filename in ipairs(project_list) do
      if by > dy + 60 then
          if ui.button(filename, dx + 20, by, dw - 40, 30) then
              current_project = filename
              code_app.open(current_project)
              open_project_dialog_open = false
              log("Opened " .. filename)
          end
          by = by - 40
      end
  end

  if ui.button("Cancel", dx + 20, dy + 20, 100, 30) then
     open_project_dialog_open = false
  end
end

function draw_exit_dialog()
  local dw, dh = 400, 200
  local dx, dy = (1920-dw)/2, (1080-dh)/2
  ui.draw_window("System Halt", dx, dy, dw, dh)
  rect(dx, dy, dw, dh, 0)
  print("Are you sure you want to quit?", dx + 40, dy + 120, 7)
  if ui.button("Cancel", dx + 40, dy + 40, 140, 40) then exit_dialog_open = false end
  if ui.button("Quit", dx + 220, dy + 40, 140, 40) then sys.exit() end
end

function draw_desktop_icons()
  local ix, iy = 60, 900
  rect(ix, iy, 64, 64, 20)
  rect(ix+16, iy+16, 32, 32, 7)
  print("Code", ix+10, iy-25, 7)

  local m = mouse()
  if m.click and m.x > ix and m.x < ix+64 and m.y > iy and m.y < iy+64 then
     current_app = code_app
  end
end

function draw_game_window()
  sys.target("game")
  if _user_draw then
      local status, err = pcall(_user_draw)
      if not status then
          sys.target("os")
          log("Runtime Error (Draw): " .. tostring(err))
          game_running = false
          if _os_update then _update = _os_update end
          if _os_draw then _draw = _os_draw end
          return
      end
  end

  sys.target("os")
  local scale = 4
  local gw, gh = 240*scale, 136*scale
  local gx, gy = (1920-gw)/2, (1080-gh)/2

  ui.draw_window("Running " .. current_project, gx-4, gy-4, gw+8, gh+40)

  if sys.draw_game then
      sys.draw_game(gx, gy, gw, gh)
  else
      rect(gx, gy, gw, gh, 0)
      print("ERROR: sys.draw_game() missing", gx+10, gy+10, 8)
  end
end

function launch_game()
  if game_running then return end

  -- 1. SAVE OS STATE
  local old_update = _update
  local old_draw = _draw

  -- 2. LOAD ACTIVE PROJECT
  if fs.exists(current_project) then
      local content = fs.read(current_project)
      local chunk, err = load(content, current_project)
      if chunk then
          local status, err2 = pcall(chunk)
          if status then
              _user_update = _update
              _user_draw = _draw
              _update = old_update
              _draw = old_draw
              _os_update = old_update
              _os_draw = old_draw
              game_running = true
              if _init then
                  pcall(_init)
              end
              log("Launched " .. current_project)
              return
          else
             log("Runtime Error: " .. tostring(err2))
          end
      else
          log("Syntax Error: " .. tostring(err))
      end
  else
      log(current_project .. " not found")
  end
end
