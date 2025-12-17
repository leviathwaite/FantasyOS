-- UITheme.lua
local UITheme = {}
UITheme.__index = UITheme

-- Mapped to standard TIC-80/NerdOS indices for compatibility
UITheme.Palette = {
    Black       = 0,
    DeepNavy    = 1,
    DarkSlate   = 1,
    Slate       = 13,
    White       = 12,
    Red         = 2,
    Orange      = 9,
    Green       = 6,
    Teal        = 11,
    Yellow      = 10,
    Grey        = 5,
    DarkRed     = 2,
    Blue        = 14,
    LightBlue   = 15
}

UITheme.Colors = {
    -- Window / Desktop
    Background      = UITheme.Palette.Black,
    PanelBackground = UITheme.Palette.DeepNavy,
    Gutter          = UITheme.Palette.DeepNavy,
    Border          = UITheme.Palette.Slate,

    -- Text
    TextPrimary     = UITheme.Palette.White,
    TextSecondary   = UITheme.Palette.Slate,
    TextDisabled    = UITheme.Palette.Grey,

    -- Syntax
    SyntaxKeyword   = UITheme.Palette.Teal,
    SyntaxString    = UITheme.Palette.Green,
    SyntaxNumber    = UITheme.Palette.Slate,
    SyntaxComment   = UITheme.Palette.Grey,
    SyntaxFunc      = UITheme.Palette.Orange,

    -- UI Interaction
    Caret           = UITheme.Palette.Yellow,
    Selection       = UITheme.Palette.DarkRed,
    ActiveTitle     = UITheme.Palette.Blue,
    InactiveTitle   = UITheme.Palette.Grey
}

function UITheme.new()
    local self = setmetatable({}, UITheme)
    return self
end

return UITheme
