-- WindowManager.lua
local WM = {}
WM.__index = WM

function WM.new(theme)
    local self = setmetatable({}, WM)
    self.theme = theme
    self.windows = {}

    -- Interaction State
    self.drag_win = nil
    self.resize_win = nil

    -- Offsets for smooth dragging/resizing
    self.drag_off_x = 0
    self.drag_off_y = 0
    self.resize_start_w = 0
    self.resize_start_h = 0
    self.mouse_start_x = 0
    self.mouse_start_y = 0

    return self
end

function WM:create_window(app_view, title, x, y, w, h)
    local win = {
        app = app_view,
        title = title,
        x = x, y = y, w = w, h = h,
        min_w = 120, min_h = 80 -- 120x80
    }
    table.insert(self.windows, win)
    return win
end

-- Bring window to the front
function WM:focus(win)
    for i, w in ipairs(self.windows) do
        if w == win then
            table.remove(self.windows, i)
            table.insert(self.windows, win)
            return
        end
    end
end

function WM:close(win)
    for i, w in ipairs(self.windows) do
        if w == win then
            table.remove(self.windows, i)
            return
        end
    end
end

function WM:update()
    local m = mouse()
    if not m then return end

    -- 1. Handle Active Operations (Drag/Resize)
    -- If we are already dragging/resizing, ignore other windows
    if self.resize_win then
        if m.left then
            local dx = m.x - self.mouse_start_x
            local dy = m.y - self.mouse_start_y
            self.resize_win.w = math.max(self.resize_win.min_w, self.resize_start_w + dx)
            self.resize_win.h = math.max(self.resize_win.min_h, self.resize_start_h + dy)
        else
            self.resize_win = nil
        end
        return
    end

    if self.drag_win then
        if m.left then
            self.drag_win.x = m.x - self.drag_off_x
            self.drag_win.y = m.y - self.drag_off_y
        else
            self.drag_win = nil
        end
        return
    end

    -- 2. Check Input (Iterate Top-to-Bottom / Front-to-Back)
    -- We do this so clicks are caught by the top window first
    local input_consumed = false

    for i = #self.windows, 1, -1 do
        local win = self.windows[i]
        local head_h = 24
        local resize_zone = 15

        -- Hit Box Checks
        local in_resize = m.x > win.x + win.w - resize_zone and m.x < win.x + win.w and
                          m.y > win.y + win.h - resize_zone and m.y < win.y + win.h

        local in_close = m.x > win.x + win.w - 18 and m.x < win.x + win.w - 2 and
                         m.y > win.y + 2 and m.y < win.y + 18

        local in_title = m.x >= win.x and m.x <= win.x + win.w and
                         m.y >= win.y and m.y <= win.y + head_h

        local in_win = m.x >= win.x and m.x <= win.x + win.w and
                       m.y >= win.y and m.y <= win.y + win.h

        if m.click and not input_consumed then
            if in_resize then
                self:focus(win)
                self.resize_win = win
                self.mouse_start_x = m.x; self.mouse_start_y = m.y
                self.resize_start_w = win.w; self.resize_start_h = win.h
                input_consumed = true
            elseif in_close then
                self:close(win)
                input_consumed = true
            elseif in_title then
                self:focus(win)
                self.drag_win = win
                self.drag_off_x = m.x - win.x; self.drag_off_y = m.y - win.y
                input_consumed = true
            elseif in_win then
                self:focus(win)
                input_consumed = true
            end
        end

        -- Pass Update to App (Only if it's the Active Window)
        if i == #self.windows and not input_consumed then
            if win.app.update then
                -- Calculate Inner Content Area
                local inner_x = win.x + 1
                local inner_y = win.y + head_h
                local inner_w = win.w - 2
                local inner_h = win.h - head_h - 1

                win.app:update(inner_x, inner_y, inner_w, inner_h)
            end
        end

        if input_consumed then break end
    end
end

function WM:draw()
    -- Render Windows (Iterate Bottom-to-Top / Back-to-Front)
    for _, win in ipairs(self.windows) do
        local head_h = 24
        local active = (win == self.windows[#self.windows])

        -- 1. Shadow (Simple offset rect)
        rect(win.x + 4, win.y + 4, win.w, win.h, self.theme.Palette.Black)

        -- 2. Window Border/Body
        rect(win.x, win.y, win.w, win.h, self.theme.Colors.PanelBackground)

        -- 3. Title Bar
        local t_col = active and self.theme.Colors.ActiveTitle or self.theme.Colors.InactiveTitle
        rect(win.x + 1, win.y + 1, win.w - 2, head_h, t_col)
        print(win.title, win.x + 6, win.y + 8, self.theme.Colors.TextPrimary)

        -- 4. Close Button
        rect(win.x + win.w - 14, win.y + 6, 8, 8, self.theme.Colors.Selection)

        -- 5. Content Area (Clipped)
        -- We clip to ensure the app doesn't draw over the title bar or outside the window
        clip(win.x + 1, win.y + head_h, win.w - 2, win.h - head_h - 1)

        -- Draw App Background
        rect(win.x + 1, win.y + head_h, win.w - 2, win.h - head_h - 1, self.theme.Colors.Background)

        if win.app.draw then
            win.app:draw(win.x + 1, win.y + head_h, win.w - 2, win.h - head_h - 1)
        end

        clip() -- Reset clip

        -- 6. Resize Handle (Visual Indicator)
        local rx, ry = win.x + win.w, win.y + win.h
        line(rx - 3, ry - 10, rx - 10, ry - 3, self.theme.Colors.TextSecondary)
        line(rx - 4, ry - 14, rx - 14, ry - 4, self.theme.Colors.TextSecondary)
    end
end

return WM
