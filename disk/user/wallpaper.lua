-- Example Live Wallpaper
-- This file demonstrates how to create an animated desktop background
-- Place in disk/user/wallpaper.lua to use

-- Screen dimensions
local SCREEN_WIDTH = 240
local SCREEN_HEIGHT = 136

local stars = {}
local time = 0

function _init()
  -- Create starfield
  for i = 1, 50 do
    table.insert(stars, {
      x = math.random(0, SCREEN_WIDTH),
      y = math.random(0, SCREEN_HEIGHT),
      speed = math.random(5, 20) / 10,
      brightness = math.random(5, 10)
    })
  end
end

function _update()
  time = time + 0.016  -- ~60fps
  
  -- Move stars
  for i = 1, #stars do
    local s = stars[i]
    s.y = s.y + s.speed * 0.5
    
    -- Wrap around
    if s.y > SCREEN_HEIGHT then
      s.y = 0
      s.x = math.random(0, SCREEN_WIDTH)
    end
  end
end

function _draw()
  -- Dark gradient background
  for y = 0, SCREEN_HEIGHT - 1 do
    local c = math.floor(y / SCREEN_HEIGHT * 3) + 1
    line(0, y, SCREEN_WIDTH, y, c)
  end
  
  -- Draw stars
  for i = 1, #stars do
    local s = stars[i]
    pset(s.x, s.y, s.brightness)
  end
  
  -- Pulsing text
  local alpha = (math.sin(time * 2) + 1) / 2
  local color = math.floor(alpha * 5) + 5
  print("FantasyOS", 10, 10, color)
end
