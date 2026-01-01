local Config = {}

Config.win_min_width = 1900
Config.win_min_height = 1000
Config.font_w = 8
Config.font_h = 18
Config.line_h = 22
Config.help_h = 60
Config.tab_width = 2
Config.scroll_speed = 3
Config.font_size = 20

-- Toolbar settings
Config.controls = {
  height = 40,
  buttons = {
    {id = "save", label = "Save", x = 10, w = 80},
    {id = "run",  label = "Run",  x = 100, w = 80}
  }
}

return Config
