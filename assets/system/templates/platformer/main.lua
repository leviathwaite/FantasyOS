-- Platformer Template
-- Basic platformer with gravity and jumping

function _init()
  player = {
    x = 64,
    y = 64,
    vx = 0,
    vy = 0,
    w = 8,
    h = 8,
    grounded = false
  }
  
  gravity = 0.5
  jump_speed = -8
  move_speed = 2
  
  platforms = {
    {x = 0, y = 120, w = 240, h = 16},
    {x = 50, y = 90, w = 40, h = 8},
    {x = 120, y = 70, w = 40, h = 8}
  }
end

function _update()
  -- Horizontal movement
  player.vx = 0
  if btn(0) then player.vx = -move_speed end  -- Left
  if btn(1) then player.vx = move_speed end   -- Right
  
  -- Jump
  if btnp(4) and player.grounded then
    player.vy = jump_speed
    player.grounded = false
  end
  
  -- Apply gravity
  if not player.grounded then
    player.vy = player.vy + gravity
  end
  
  -- Update position
  player.x = player.x + player.vx
  player.y = player.y + player.vy
  
  -- Collision with platforms
  player.grounded = false
  for i = 1, #platforms do
    local p = platforms[i]
    if player.x + player.w > p.x and player.x < p.x + p.w and
       player.y + player.h > p.y and player.y < p.y + p.h then
      if player.vy > 0 then  -- Falling down
        player.y = p.y - player.h
        player.vy = 0
        player.grounded = true
      end
    end
  end
end

function _draw()
  cls(1)  -- Dark blue background
  
  -- Draw platforms
  for i = 1, #platforms do
    local p = platforms[i]
    rectfill(p.x, p.y, p.x + p.w, p.y + p.h, 5)
  end
  
  -- Draw player
  rectfill(player.x, player.y, player.x + player.w, player.y + player.h, 8)
  
  -- Instructions
  print("Arrow keys to move", 10, 10, 7)
  print("Z to jump", 10, 20, 7)
end
