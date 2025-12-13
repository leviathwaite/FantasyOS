-- Desktop.lua
local Desktop = {}
Desktop.__index = Desktop

function Desktop.new(wm, theme)
    local self = setmetatable({}, Desktop)
    self.wm = wm
    self.theme = theme
    -- Icons
    self.icons = {
        { label = "Code", icon="{}", x=20, y=20, type="code" }
    }
    return self
end

function Desktop:update()
    self.wm:update()

    local m = mouse()
    if m.click then
        -- Check Icons
        for _, icon in ipairs(self.icons) do
            if m.x > icon.x and m.x < icon.x + 32 and m.y > icon.y and m.y < icon.y + 32 then
               -- Check if we already have a window (Simple Singleton logic for demo)
               if #self.wm.windows == 0 then
                   -- Signal main to create window (or do it here if we had access to the App class)
                   -- For now, handled in main.lua or we assume windows exist
               end
            end
        end
    end
end

function Desktop:draw()
    local sw, sh = display_width(), display_height()

    -- Wallpaper
    cls(self.theme.Colors.PanelBackground)
    for i=0, sw, 40 do
        for j=0, sh, 40 do pix(i,j, self.theme.Colors.Slate) end
    end

    -- Icons
    for _, icon in ipairs(self.icons) do
        rect(icon.x, icon.y, 32, 32, self.theme.Colors.Gutter)
        print(icon.icon, icon.x + 10, icon.y + 10, self.theme.Colors.SyntaxKeyword)
        print(icon.label, icon.x + 2, icon.y + 36, self.theme.Colors.TextPrimary)
    end

    -- Windows
    self.wm:draw()

    -- Taskbar
    local tb_h = 24
    rect(0, sh - tb_h, sw, tb_h, self.theme.Colors.Gutter)
    rect(0, sh - tb_h, 40, tb_h, self.theme.Colors.Selection) -- Start
    print("OS", 12, sh - tb_h + 8, self.theme.Colors.TextPrimary)

    -- Window Buttons in Taskbar
    local bx = 42
    for _, win in ipairs(self.wm.windows) do
        local is_active = (win == self.wm.windows[#self.wm.windows])
        rect(bx, sh - tb_h + 2, 80, 20, is_active and self.theme.Colors.PanelBackground or self.theme.Colors.Background)
        clip(bx, sh-tb_h, 80, 20)
        print(win.title, bx + 4, sh - tb_h + 8, self.theme.Colors.TextSecondary)
        clip()
        bx = bx + 82
    end
end

return Desktop
