local Config = {}

Config.win_min_width = 1900
Config.win_min_height = 1024
Config.font_w = 8
Config.font_h = 18
Config.line_h = 22
Config.help_h = 60
Config.tab_width = 2
Config.scroll_speed = 3
Config.font_size = 20

Config.colors = {
  bg = 0, text = 7, keyword = 12, func = 14, num = 9, str = 11,
  comment = 13, cursor = 10, gutter_bg = 1, gutter_fg = 6,
  help_bg = 1, help_title = 10, help_text = 7, help_example = 11,
  selection = 2, current_line = 1, bracket = 10, error = 8
}

Config.keys = {
  UP=19, DOWN=20, LEFT=21, RIGHT=22,
  HOME=3, END=123, PGUP=92, PGDN=93,
  ENTER=66, BACK=67, TAB=61, DEL=112, SPACE=62,
  SHIFT_L=59, SHIFT_R=60,
  CTRL_L=129, CTRL_R=130,
  ALT_L=57, ALT_R=58,
  A=29, C=31, F=34, R=46, S=47,
  V=50, X=52, Y=53, Z=54,
  MINUS=69, EQUALS=70
}

-- Controls Bar
Config.controls = {
  height = 40,
  buttons = {
    {id = "save", label = "Save", x = 10, w = 80},
    {id = "run",  label = "Run",  x = 100, w = 80}
  }
}

return Config
