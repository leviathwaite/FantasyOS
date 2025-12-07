-- UITheme.lua
-- A class to manage the palette and semantic theme colors

local UITheme = {}
UITheme.__index = UITheme

-- 1. The Raw Palette extracted from the image
-- Ordered approximately left-to-right as they appear in the gradient
UITheme.Palette = {
    -- The Dark/Blue/White Gradient
    Black        = "#000000",
    DeepNavy     = "#0d1021",
    DarkSlate    = "#202642",
    Slate        = "#414e7a",
    Periwinkle   = "#6d86b8",
    IceBlue      = "#a8c9e8",
    White        = "#ffffff",

    -- The Pink/Mauve Gradient
    PalePeach    = "#fcdcd4",
    Salmon       = "#f2a6a6",
    DustyRose    = "#d17c8e",
    OldMauve     = "#9e5366",
    Plum         = "#693345",
    DarkPlum     = "#3d192a",

    -- The Magenta/Pink Gradient
    Berry        = "#a8326b",
    HotPink      = "#e6458c",
    LightPink    = "#ff8ab5",

    -- The Green/Teal Gradient
    Mint         = "#8aff99",
    Grass        = "#4add75",
    SeaGreen     = "#24916a",
    DeepTeal     = "#135754",
    DarkTeal     = "#092e33",

    -- The Blue/Purple Gradient
    Midnight     = "#121530",
    RoyalBlue    = "#2d4296",
    SkyBlue      = "#4ba0e3",
    Lavender     = "#997df0",
    Purple       = "#6c41bd",

    -- The Yellow/Red Gradient
    Cream        = "#ffeaa3",
    Apricot      = "#ffb366",
    Coral        = "#ff6e59",
    Red          = "#d93636",
    Brick        = "#9e2626",
    Burgundy     = "#5e1717",

    -- Cyan/Turquoise variants (towards the right)
    Turquoise    = "#4ff0d6",
    Cyan         = "#3bdbbe",
    TealBlue     = "#1fa19c"
}

-- 2. Semantic Mapping (The "Active" Theme)
-- Map generic UI concepts to specific colors from the palette above.
UITheme.Colors = {
    -- Backgrounds
    Background       = UITheme.Palette.DeepNavy,
    PanelBackground  = UITheme.Palette.DarkSlate,
    InputBackground  = UITheme.Palette.Black,

    -- Text
    TextPrimary      = UITheme.Palette.White,
    TextSecondary    = UITheme.Palette.IceBlue,
    TextDisabled     = UITheme.Palette.Slate,
    TextDark         = UITheme.Palette.Black, -- For use on light buttons

    -- Accents / Interactables
    Primary          = UITheme.Palette.RoyalBlue,
    PrimaryHover     = UITheme.Palette.SkyBlue,

    Secondary        = UITheme.Palette.OldMauve,
    SecondaryHover   = UITheme.Palette.DustyRose,

    Success          = UITheme.Palette.Grass,
    Warning          = UITheme.Palette.Apricot,
    Error            = UITheme.Palette.Red,

    -- Borders / Separators
    Border           = UITheme.Palette.Slate,
    Separator        = UITheme.Palette.DarkSlate
}

-- Constructor
function UITheme.new()
    local self = setmetatable({}, UITheme)
    return self
end

-- Helper: Convert Hex String to RGB numbers (0-1 or 0-255)
-- Useful because LuaJ often interfaces with Java libraries requiring Integers
function UITheme:getRGB(colorName, asFloat)
    local hex = self.Colors[colorName] or self.Palette[colorName] or "#000000"
    hex = hex:gsub("#","")

    local r = tonumber("0x"..hex:sub(1,2))
    local g = tonumber("0x"..hex:sub(3,4))
    local b = tonumber("0x"..hex:sub(5,6))

    if asFloat then
        return r/255.0, g/255.0, b/255.0
    else
        return r, g, b
    end
end

-- Helper: Get the raw Hex string
function UITheme:getHex(colorName)
    return self.Colors[colorName] or self.Palette[colorName] or "#000000"
end

-- Helper: Change a semantic color dynamically
function UITheme:setThemeColor(key, paletteColorKey)
    if self.Palette[paletteColorKey] then
        self.Colors[key] = self.Palette[paletteColorKey]
    else
        print("Error: Palette color '" .. paletteColorKey .. "' not found.")
    end
end

return UITheme
