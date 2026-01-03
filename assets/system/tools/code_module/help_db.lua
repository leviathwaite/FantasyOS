-- system/tools/code_module/help_db.lua
local M = {}

M.funcs = {
    -- Graphics
    cls = { sig = "cls(color)", desc = "Clear screen with color" },
    print = { sig = "print(text, x, y, color)", desc = "Draw text" },
    spr = { sig = "spr(path, x, y, [w, h])", desc = "Draw sprite from file" },
    rect = { sig = "rect(x, y, w, h, color)", desc = "Draw filled rectangle" },
    line = { sig = "line(x0, y0, x1, y1, color)", desc = "Draw line" },
    pset = { sig = "pset(x, y, color)", desc = "Set pixel color" },
    pget = { sig = "pget(x, y)", desc = "Get pixel color" },
    circ = { sig = "circ(x, y, r, color)", desc = "Draw circle outline" },
    circfill = { sig = "circfill(x, y, r, color)", desc = "Draw filled circle" },
    pal = { sig = "pal(c0, c1)", desc = "Swap color c0 with c1" },
    palt = { sig = "palt(c, t)", desc = "Set transparency for color" },
    
    -- Input
    btn = { sig = "btn(i)", desc = "Check if button i is held" },
    btnp = { sig = "btnp(i)", desc = "Check if button i was pressed" },
    mouse = { sig = "mouse()", desc = "Get mouse state {x,y,click...}" },

    -- Map
    mget = { sig = "mget(x, y)", desc = "Get map tile at x,y" },
    mset = { sig = "mset(x, y, v)", desc = "Set map tile at x,y" },
    map = { sig = "map(mx, my, sx, sy, w, h)", desc = "Draw section of map" },

    -- Audio
    sfx = { sig = "sfx(id)", desc = "Play sound effect" },
    music = { sig = "music(id)", desc = "Play music track" },

    -- Math extensions
    rnd = { sig = "rnd(x)", desc = "Random number 0..x" },
    flr = { sig = "flr(x)", desc = "Floor value" },
    ceil = { sig = "ceil(x)", desc = "Ceil value" },
    mid = { sig = "mid(x, y, z)", desc = "Middle value of 3" },
    abs = { sig = "abs(x)", desc = "Absolute value" },
    sin = { sig = "sin(x)", desc = "Sine (0..1)" },
    cos = { sig = "cos(x)", desc = "Cosine (0..1)" },
    atan2 = { sig = "atan2(dy, dx)", desc = "Arc tangent" },
    sqrt = { sig = "sqrt(x)", desc = "Square root" },
    
    -- String extensions
    sub = { sig = "sub(str, s, [e])", desc = "Substring" },
    len = { sig = "len(str)", desc = "String length" },
    
    -- System
    run = { sig = "run(path)", desc = "Run a Lua file" },
    exit = { sig = "exit()", desc = "Exit program" },
}

return M
