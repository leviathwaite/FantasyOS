-- NerdOS Map Test
-- Press Arrows to Scroll

x = 100
y = 60
cam_x = 0
cam_y = 0

function _init()
  log("Map Unit Online")

  -- Procedural Generation:
  -- Fill the map memory area with random tiles
  for ty=0, 30 do
    for tx=0, 40 do
       local tile = 0
       -- Randomly place sprite 1 (assuming it exists in sprites.png)
       if (math.random() > 0.8) then tile = 1 end
       mset(tx, ty, tile)
    end
  end

  -- Create a floor
  for tx=0, 40 do mset(tx, 0, 2) end
end

function _update()
  if btn(0) then x -= 1 end
  if btn(1) then x += 1 end
  if btn(2) then y += 1 end
  if btn(3) then y -= 1 end

  -- Camera follows player
  cam_x = x - 120
  cam_y = y - 68
end

function _draw()
  cls(1)

  -- Draw the Map layer
  -- map(cell_x, cell_y, screen_x, screen_y, width, height)
  map(0, 0, -cam_x, -cam_y, 40, 30)

  -- Draw Player
  rect(x - cam_x, y - cam_y, 8, 8, 8)

  print("Map Test", 5, 120, 7)
end
