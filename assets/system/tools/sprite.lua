-- NerdOS Sprite Editor v3.1 (PRO - Fixed)
-- FEATURES: Animation, Onion Skin, Undo/Redo, Tiling, 9-Slice

-- STATE
spr_id = 0       -- Current Sprite Index (0-255)
curr_col = 7     -- Current Color
tool = 1         -- 1=Pen, 2=Fill, 3=Pick, 4=Move
show_grid = true
show_tiling = false
show_guides = false -- 9-slice guides
onion_skin = false
pal_cols = 16

-- ANIMATION STATE
timeline = {0}   -- List of Sprite IDs in animation
curr_frame = 1   -- Current index in timeline
is_playing = false
play_timer = 0
play_speed = 8   -- Frames to wait before update

-- UNDO SYSTEM
undo_stack = {}
redo_stack = {}
MAX_UNDO = 20

-- CLIPBOARD
clipboard_data = nil

-- UI STATE
toast_msg = ""
toast_timer = 0
m_down = false

-- UI MEASUREMENTS
canvas_x, canvas_y, canvas_size = 0, 0, 0
sheet_x, sheet_y, sheet_size = 0, 0, 0
pal_x, pal_y, pal_size = 0, 0, 0
time_x, time_y, time_w, time_h = 0, 0, 0, 0

function _init()
    show_toast("v3.1 Ready - Press H for Help")
    timeline[1] = spr_id
end

function show_toast(msg)
    toast_msg = msg
    toast_timer = 90
    if log then log(msg) end
end

function layout()
    local sw = display_width()
    local sh = display_height()
    local padding = 10

    -- Bottom: Timeline (Fixed height)
    time_h = 40
    time_x = padding
    time_y = padding
    time_w = sw - (padding * 2)

    -- Right: Sidebar
    sheet_size = 256 -- 128x128 sprite sheet scaled 2x
    sheet_x = sw - sheet_size - padding
    sheet_y = sh - sheet_size - padding

    pal_size = 24
    pal_x = sheet_x
    pal_y = sheet_y - pal_size - padding

    -- Center: Canvas (Remaining space)
    local avail_w = sheet_x - (padding * 2)
    local avail_h = sh - time_h - (padding * 3)

    canvas_size = math.min(avail_w, avail_h)
    canvas_x = padding + (avail_w - canvas_size) / 2
    canvas_y = time_y + time_h + padding + (avail_h - canvas_size) / 2
end

function _update()
    layout()

    -- ANIMATION PLAYBACK
    if is_playing then
        play_timer = play_timer + 1
        if play_timer >= play_speed then
            play_timer = 0
            curr_frame = (curr_frame % #timeline) + 1
            spr_id = timeline[curr_frame]
        end
    end

    local m = mouse()
    local ctrl = key("ctrl")
    local shift = key("shift")

    -- SHORTCUTS
    if btnp(44) then tool = 1; show_toast("Tool: Pen") end       -- P
    if btnp(34) then tool = 2; show_toast("Tool: Fill") end      -- F
    if btnp(37) then tool = 3; show_toast("Tool: Picker") end    -- I
    if btnp(45) then tool = 4; show_toast("Tool: Move") end      -- M

    if ctrl and btnp(54) then do_undo() end                      -- Ctrl+Z
    if ctrl and btnp(46) then do_redo() end                      -- Ctrl+R

    if btnp(43) then show_tiling = not show_tiling; show_toast("Tiling: " .. (show_tiling and "ON" or "OFF")) end  -- O
    if btnp(35) then show_guides = not show_guides; show_toast("Guides: " .. (show_guides and "ON" or "OFF")) end  -- G
    if btnp(51) then onion_skin = not onion_skin; show_toast("Onion: " .. (onion_skin and "ON" or "OFF")) end     -- T
    if btnp(62) then is_playing = not is_playing; show_toast(is_playing and "Playing" or "Stopped") end             -- Space

    if ctrl and btnp(31) then copy_sprite(); show_toast("Copied") end  -- Ctrl+C
    if ctrl and btnp(50) then paste_sprite(); show_toast("Pasted") end -- Ctrl+V
    if btnp(36) then show_toast("P=Pen F=Fill I=Pick M=Move O=Tile G=Guide T=Onion") end  -- H

    -- MOUSE HANDLING
    if m.left then
        if not m_down and m.click then
            if is_hovering(m.x, m.y, canvas_x, canvas_y, canvas_size, canvas_size) then
                push_undo() -- Save state before drawing
            end
            handle_click(m.x, m.y, true)
            m_down = true
        elseif m_down then
            handle_click(m.x, m.y, false)
        end
    else
        m_down = false
    end

    if toast_timer > 0 then toast_timer = toast_timer - 1 end
end

function handle_click(mx, my, just_clicked)
    -- 1. CANVAS CLICK
    if is_hovering(mx, my, canvas_x, canvas_y, canvas_size, canvas_size) then
        if is_playing then return end

        local pixel_size = canvas_size / 8
        local px = math.floor((mx - canvas_x) / pixel_size)
        local py = math.floor((my - canvas_y) / pixel_size)

        -- Convert to global sheet coords
        local sheet_gx = (spr_id % 16) * 8
        local sheet_gy = math.floor(spr_id / 16) * 8
        local abs_x = sheet_gx + px
        local abs_y = sheet_gy + (7 - py)

        if tool == 1 then -- PEN
            sset(abs_x, abs_y, curr_col)
        elseif tool == 2 and just_clicked then -- FILL
            flood_fill(abs_x, abs_y, sget(abs_x, abs_y), curr_col)
        elseif tool == 3 then -- PICKER
            curr_col = sget(abs_x, abs_y)
            tool = 1
            show_toast("Picked: " .. curr_col)
        elseif tool == 4 and just_clicked then -- MOVE
            if px < 3 then shift_sprite(-1, 0)
            elseif px > 4 then shift_sprite(1, 0)
            elseif py < 3 then shift_sprite(0, 1)
            elseif py > 4 then shift_sprite(0, -1) end
        end

        timeline[curr_frame] = spr_id
        return
    end

    -- 2. TIMELINE CLICK
    if just_clicked and is_hovering(mx, my, time_x, time_y, time_w, time_h) then
        local frame_w = 40
        if mx < time_x + 30 then -- Add Frame
            table.insert(timeline, curr_frame + 1, spr_id)
            curr_frame = curr_frame + 1
            show_toast("Frame Added")
            return
        end

        -- Click frames
        local start_x = time_x + 40
        local clicked_frame = math.floor((mx - start_x) / frame_w) + 1
        if clicked_frame > 0 and clicked_frame <= #timeline then
            curr_frame = clicked_frame
            spr_id = timeline[curr_frame]
        end
        return
    end

    -- 3. PALETTE CLICK
    if is_hovering(mx, my, pal_x, pal_y, pal_cols * pal_size, pal_size) then
        curr_col = math.floor((mx - pal_x) / pal_size)
        show_toast("Color: " .. curr_col)
        return
    end

    -- 4. SHEET CLICK
    if is_hovering(mx, my, sheet_x, sheet_y, sheet_size, sheet_size) then
        local rel_x = mx - sheet_x
        local rel_y = my - sheet_y
        local sx = math.floor(rel_x / 16)
        local sy = 15 - math.floor(rel_y / 16)
        spr_id = sx + (sy * 16)
        timeline[curr_frame] = spr_id
        show_toast("Sprite: " .. spr_id)
    end
end

-- ================= LOGIC HELPERS =================

function is_hovering(mx, my, x, y, w, h)
    return mx >= x and mx < x + w and my >= y and my < y + h
end

function push_undo()
    local data = {}
    local gx = (spr_id % 16) * 8
    local gy = math.floor(spr_id / 16) * 8
    for y = 0, 7 do
        for x = 0, 7 do
            table.insert(data, sget(gx + x, gy + y))
        end
    end

    table.insert(undo_stack, {id = spr_id, pixels = data})
    if #undo_stack > MAX_UNDO then table.remove(undo_stack, 1) end
    redo_stack = {} -- Clear redo on new action
end

function do_undo()
    if #undo_stack == 0 then show_toast("Nothing to Undo"); return end

    -- Save current to redo
    local curr_data = {}
    local gx = (spr_id % 16) * 8
    local gy = math.floor(spr_id / 16) * 8
    for y = 0, 7 do
        for x = 0, 7 do
            table.insert(curr_data, sget(gx + x, gy + y))
        end
    end
    table.insert(redo_stack, {id = spr_id, pixels = curr_data})

    -- Restore previous
    local state = table.remove(undo_stack)
    spr_id = state.id
    restore_sprite(state.pixels)
    show_toast("Undo")
end

function do_redo()
    if #redo_stack == 0 then show_toast("Nothing to Redo"); return end

    -- Save current to undo
    push_undo()

    -- Restore redo
    local state = table.remove(redo_stack)
    spr_id = state.id
    restore_sprite(state.pixels)
    show_toast("Redo")
end

function restore_sprite(data)
    local gx = (spr_id % 16) * 8
    local gy = math.floor(spr_id / 16) * 8
    local i = 1
    for y = 0, 7 do
        for x = 0, 7 do
            sset(gx + x, gy + y, data[i])
            i = i + 1
        end
    end
end

function shift_sprite(dx, dy)
    push_undo()
    local temp = {}
    local gx = (spr_id % 16) * 8
    local gy = math.floor(spr_id / 16) * 8

    for y = 0, 7 do
        for x = 0, 7 do
            local ox = (x - dx) % 8
            local oy = (y - dy) % 8
            table.insert(temp, sget(gx + ox, gy + oy))
        end
    end
    restore_sprite(temp)
end

function copy_sprite()
    clipboard_data = {}
    local gx = (spr_id % 16) * 8
    local gy = math.floor(spr_id / 16) * 8
    for y = 0, 7 do
        for x = 0, 7 do
            table.insert(clipboard_data, sget(gx + x, gy + y))
        end
    end
end

function paste_sprite()
    if not clipboard_data then show_toast("Nothing to Paste"); return end
    push_undo()
    restore_sprite(clipboard_data)
end

function flood_fill(x, y, target, rep)
    if target == rep then return end
    if x < 0 or x > 127 or y < 0 or y > 127 then return end
    if sget(x, y) ~= target then return end

    sset(x, y, rep)
    flood_fill(x - 1, y, target, rep)
    flood_fill(x + 1, y, target, rep)
    flood_fill(x, y - 1, target, rep)
    flood_fill(x, y + 1, target, rep)
end

-- ================= DRAWING =================

function _draw()
    local sw = display_width()
    local sh = display_height()
    cls(1) -- Dark Grey BG
    layout()

    -- 1. DRAW CANVAS
    local pixel_size = canvas_size / 8

    -- Tiling Preview
    if show_tiling then
        for oy = -1, 1 do
            for ox = -1, 1 do
                if ox ~= 0 or oy ~= 0 then
                    draw_sprite_custom(spr_id, canvas_x + (ox * canvas_size), canvas_y + (oy * canvas_size), pixel_size, true)
                end
            end
        end
    end

    -- Onion Skin (Previous Frame)
    if onion_skin and curr_frame > 1 then
        local prev_id = timeline[curr_frame - 1]
        draw_sprite_custom(prev_id, canvas_x, canvas_y, pixel_size, true)
    end

    -- Main Sprite
    rect(canvas_x - 2, canvas_y - 2, canvas_size + 4, canvas_size + 4, 6)
    draw_sprite_custom(spr_id, canvas_x, canvas_y, pixel_size, false)

    -- Grid Overlay
    if show_grid then
        for i = 1, 7 do
            local p = canvas_x + (i * pixel_size)
            rect(p, canvas_y, 1, canvas_size, 0)
            local py = canvas_y + (i * pixel_size)
            rect(canvas_x, py, canvas_size, 1, 0)
        end
    end

    -- 9-Patch Guides
    if show_guides then
        local g1 = canvas_x + (3 * pixel_size)
        local g2 = canvas_x + (6 * pixel_size)
        rect(g1, canvas_y, 1, canvas_size, 11)
        rect(g2, canvas_y, 1, canvas_size, 11)
        rect(canvas_x, canvas_y + (3 * pixel_size), canvas_size, 1, 11)
        rect(canvas_x, canvas_y + (6 * pixel_size), canvas_size, 1, 11)
    end

    -- 2. DRAW PALETTE
    rect(pal_x - 2, pal_y - 2, (pal_cols * pal_size) + 4, pal_size + 4, 6)
    for i = 0, pal_cols - 1 do
        local px = pal_x + (i * pal_size)
        -- Checkerboard for color 0 (transparent)
        if i == 0 then
            rect(px, pal_y, pal_size, pal_size, 7)
            rect(px, pal_y, pal_size / 2, pal_size / 2, 0)
            rect(px + pal_size / 2, pal_y + pal_size / 2, pal_size / 2, pal_size / 2, 0)
        else
            rect(px, pal_y, pal_size, pal_size, i)
        end
        -- Selection indicator
        if i == curr_col then
            rect(px + 2, pal_y + 2, pal_size - 4, pal_size - 4, 7)
            rect(px + 4, pal_y + 4, pal_size - 8, pal_size - 8, i)
        end
    end

    -- 3. DRAW SHEET
    rect(sheet_x - 2, sheet_y - 2, sheet_size + 4, sheet_size + 4, 6)
    sspr(0, 0, 128, 128, sheet_x, sheet_y, sheet_size, sheet_size)

    -- Selection box (draw hollow rect manually)
    local sx = spr_id % 16
    local sy = math.floor(spr_id / 16)
    local hx = sheet_x + (sx * 16)
    local hy = sheet_y + ((15 - sy) * 16)
    rect(hx, hy, 16, 1, 7)          -- Top
    rect(hx, hy + 15, 16, 1, 7)     -- Bottom
    rect(hx, hy, 1, 16, 7)          -- Left
    rect(hx + 15, hy, 1, 16, 7)     -- Right

    -- 4. DRAW TIMELINE
    rect(time_x, time_y, time_w, time_h, 5)
    print("+", time_x + 10, time_y + 24, 7)

    local start_x = time_x + 40
    local frame_w = 40
    for i, frame_id in ipairs(timeline) do
        local dx = start_x + ((i - 1) * frame_w)
        if dx < time_x + time_w - frame_w then
            local col = (i == curr_frame) and 12 or 1
            rect(dx, time_y + 2, frame_w - 2, time_h - 4, col)

            -- Mini preview
            local mini_gx = (frame_id % 16) * 8
            local mini_gy = math.floor(frame_id / 16) * 8
            sspr(mini_gx, mini_gy, 8, 8, dx + 4, time_y + 6, 20, 20)
            print(tostring(frame_id), dx + 26, time_y + 24, 7)
        end
    end

    -- 5. INFO OVERLAY
    local tool_names = {"Pen", "Fill", "Pick", "Move"}
    print("Tool: " .. tool_names[tool], 20, sh - 60, 7)
    print("Sprite: " .. spr_id .. " | Frame: " .. curr_frame .. "/" .. #timeline, 20, sh - 35, 7)
    print("Color: " .. curr_col, 20, sh - 10, 7)

    if toast_timer > 0 then
        local tw = #toast_msg * 12
        rect(sw / 2 - tw / 2 - 8, sh / 2 - 15, tw + 16, 30, 0)
        print(toast_msg, sw / 2 - tw / 2, sh / 2 - 5, 7)
    end
end

function draw_sprite_custom(id, dx, dy, psize, ghost)
    local gx = (id % 16) * 8
    local gy = math.floor(id / 16) * 8
    for py = 0, 7 do
        for px = 0, 7 do
            local col = sget(gx + px, gy + (7 - py))
            if col > 0 then -- Skip transparent
                if ghost then
                    if (px + py) % 2 == 0 then -- Checkerboard ghost
                        rect(dx + px * psize, dy + py * psize, psize, psize, col)
                    end
                else
                    rect(dx + px * psize, dy + py * psize, psize, psize, col)
                end
            elseif not ghost then
                -- Draw checkerboard for transparent
                local cb_col = (px + py) % 2 == 0 and 5 or 6
                rect(dx + px * psize, dy + py * psize, psize, psize, cb_col)
            end
        end
    end
end
