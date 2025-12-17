-- Shooter Template
-- Basic top-down shooter with enemies

function _init()
  player = {x = 120, y = 120, speed = 2}
  bullets = {}
  enemies = {}
  score = 0
  spawn_timer = 0
end

function _update()
  -- Player movement
  if btn(0) then player.x = player.x - player.speed end  -- Left
  if btn(1) then player.x = player.x + player.speed end  -- Right
  if btn(2) then player.y = player.y - player.speed end  -- Up
  if btn(3) then player.y = player.y + player.speed end  -- Down
  
  -- Keep player in bounds
  player.x = mid(4, player.x, 236)
  player.y = mid(4, player.y, 132)
  
  -- Shoot
  if btnp(4) then  -- Z key
    add(bullets, {x = player.x, y = player.y - 8, vy = -4})
  end
  
  -- Update bullets
  for i = #bullets, 1, -1 do
    local b = bullets[i]
    b.y = b.y + b.vy
    if b.y < 0 then
      table.remove(bullets, i)
    end
  end
  
  -- Spawn enemies
  spawn_timer = spawn_timer + 1
  if spawn_timer > 60 then
    spawn_timer = 0
    add(enemies, {x = flr(rnd(232)) + 4, y = -8, vy = 1})
  end
  
  -- Update enemies
  for i = #enemies, 1, -1 do
    local e = enemies[i]
    e.y = e.y + e.vy
    if e.y > 136 then
      table.remove(enemies, i)
    end
  end
  
  -- Collision: bullets vs enemies
  for i = #bullets, 1, -1 do
    for j = #enemies, 1, -1 do
      if bullets[i] and enemies[j] then
        local b = bullets[i]
        local e = enemies[j]
        if abs(b.x - e.x) < 4 and abs(b.y - e.y) < 4 then
          table.remove(bullets, i)
          table.remove(enemies, j)
          score = score + 10
          break
        end
      end
    end
  end
end

function _draw()
  cls(0)  -- Black background
  
  -- Draw player
  circfill(player.x, player.y, 4, 11)
  
  -- Draw bullets
  for i = 1, #bullets do
    local b = bullets[i]
    circfill(b.x, b.y, 1, 10)
  end
  
  -- Draw enemies
  for i = 1, #enemies do
    local e = enemies[i]
    circfill(e.x, e.y, 3, 8)
  end
  
  -- Draw score
  print("Score: " .. score, 10, 10, 7)
  print("Z to shoot", 10, 20, 6)
end

function add(t, v)
  table.insert(t, v)
end

function mid(a, b, c)
  return math.max(a, math.min(b, c))
end

function abs(x)
  return x < 0 and -x or x
end

function flr(x)
  return math.floor(x)
end

function rnd(x)
  return math.random() * (x or 1)
end
