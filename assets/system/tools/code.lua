-- ============================================================================
-- NerdOS Code Editor v21.0 (UITheme Integrated)
-- ============================================================================

-- ============================================================================
-- 1. UI THEME CLASS
-- ============================================================================

local UITheme = {}
UITheme.__index = UITheme

-- The Palette Definitions
-- Note: Mapped to Integer IDs (0-15) for standard rect/print compatibility.
UITheme.Palette = {
    Black       = 0,
    DeepNavy    = 1,  -- Using Dark Purple/Blue slot
    DarkSlate   = 1,
    Slate       = 13, -- Light Blue/Grey
    White       = 12,
    Red         = 2,
    Orange      = 9,
    Green       = 6,
    Teal        = 11,
    Yellow      = 10,
    Grey        = 5,
    DarkRed     = 2,
}

-- Semantic Colors ( The "Active" Theme )
UITheme.Colors = {
    -- Backgrounds
    Background      = UITheme.Palette.Black,
    PanelBackground = UITheme.Palette.DeepNavy, -- Header/Toolbar
    Gutter          = UITheme.Palette.DeepNavy,

    -- Text
    TextPrimary     = UITheme.Palette.White,
    TextSecondary   = UITheme.Palette.Slate,    -- Line Numbers
    TextDisabled    = UITheme.Palette.Grey,

    -- Syntax Highlighting
    SyntaxKeyword   = UITheme.Palette.Teal,     -- 11 or 6
    SyntaxString    = UITheme.Palette.Green,    -- 11
    SyntaxNumber    = UITheme.Palette.Slate,    -- 13
    SyntaxComment   = UITheme.Palette.Grey,     -- 5
    SyntaxFunc      = UITheme.Palette.Orange,   -- 9

    -- Interface Elements
    Caret           = UITheme.Palette.Yellow,   -- 10
    Selection       = UITheme.Palette.DarkRed,  -- 2
    ToastBg         = UITheme.Palette.Grey,     -- 5

    -- Buttons
    ButtonHover     = 14, -- Standard highlight
    ButtonIdle      = 13,
    ButtonText      = 12,
    ButtonTextHover = 0,
}

function UITheme.new()
    local self = setmetatable({}, UITheme)
    return self
end

-- Initialize the global theme instance
local theme = UITheme.new()


-- ============================================================================
-- 2. UI HELPERS (Using Theme)
-- ============================================================================

local ui = {}

function ui.toolbar(x, y, w, h)
  rect(x, y, w, h, theme.Colors.PanelBackground)
end

function ui.icon_button(text, x, y, size)
  local m = mouse()
  if not m then return false end
  local hover = m.x >= x and m.x <= x+size and m.y >= y and m.y <= y+size
  local clicked = hover and m.click

  local bg = hover and theme.Colors.ButtonHover or theme.Colors.ButtonIdle
  local fg = clicked and theme.Colors.ButtonTextHover or theme.Colors.ButtonText

  rect(x, y, size, size, bg)
  print(text, x+4, y+4, fg)
  return clicked
end

-- ============================================================================
-- 3. CONFIGURATION
-- ============================================================================

local config = {
  font_w = 8,
  font_h = 16,
  line_h = 20,
  toolbar_h = 36,
  header_h = 40,
  text_offset_y = 2,
  text_left_padding = 10,

  blink_rate = 30,
  undo_limit = 50,

  -- Note: Colors moved to UITheme class above
}

local Keys = {
  UP = 19, DOWN = 20, LEFT = 21, RIGHT = 22,
  ENTER = 66, BACKSPACE = 67, TAB = 61, DEL = 112,
  SHIFT_LEFT = 59, SHIFT_RIGHT = 60,
  CONTROL_LEFT = 129, CONTROL_RIGHT = 130,
  A = 29, C = 31, R = 46, S = 47, V = 50, X = 52, Z = 54
}

-- ============================================================================
-- 4. UTILS
-- ============================================================================

local function clamp(val, min, max)
  return math.max(min, math.min(max, val))
end

local function split(str, sep)
  local t = {}
  for s in string.gmatch(str .. sep, "(.-)" .. sep) do
    table.insert(t, s)
  end
  return t
end

-- ============================================================================
-- 5. SYNTAX
-- ============================================================================

local Syntax = {}
Syntax.__index = Syntax

function Syntax.new()
  local self = setmetatable({}, Syntax)
  -- Updated to use theme.Colors
  self.patterns = {
    { "^%-%-.*", theme.Colors.SyntaxComment },
    { '^".-"', theme.Colors.SyntaxString },
    { "^'.-'", theme.Colors.SyntaxString },
    { "^0x%x+", theme.Colors.SyntaxNumber },
    { "^%d+%.?%d*", theme.Colors.SyntaxNumber },
    { "^[%a_][%w_]*", "word" },
    { "^.", theme.Colors.TextPrimary }
  }
  self.keywords = {
    ["if"]=1, ["then"]=1, ["else"]=1, ["elseif"]=1, ["end"]=1,
    ["function"]=1, ["local"]=1, ["return"]=1, ["while"]=1,
    ["do"]=1, ["for"]=1, ["in"]=1, ["break"]=1
  }
  self.funcs = {
    ["print"]=1, ["rect"]=1, ["cls"]=1, ["btn"]=1, ["mouse"]=1
  }
  return self
end

function Syntax:parse(text)
  local tokens = {}
  local pos = 1
  while pos <= #text do
    local matched = false
    local sub = string.sub(text, pos)
    for _, rule in ipairs(self.patterns) do
      local s, e = string.find(sub, rule[1])
      if s == 1 then
        local content = string.sub(sub, s, e)
        local col = rule[2]
        if col == "word" then
          if self.keywords[content] then col = theme.Colors.SyntaxKeyword
          elseif self.funcs[content] then col = theme.Colors.SyntaxFunc
          else col = theme.Colors.TextPrimary end
        end
        table.insert(tokens, { text = content, color = col })
        pos = pos + (e - s + 1); matched = true; break
      end
    end
    if not matched then pos = pos + 1 end
  end
  return tokens
end

-- ============================================================================
-- 6. DOCUMENT
-- ============================================================================

local Doc = {}
Doc.__index = Doc

function Doc.new(filename)
  local self = setmetatable({}, Doc)
  self.lines = {""}
  self.filename = filename or "main.lua"
  self.undo_stack = {}
  self.modified = false
  return self
end

function Doc:load()
  local c = project.read(self.filename)
  if c and #c > 0 then
    self.lines = split(c, "\n")
  else
    self.lines = {
      "-- New Project",
      "function _init()",
      "  cls(0)",
      "  print('Hello World', 80, 60, 12)",
      "end",
      "",
      "function _update()",
      "  -- update logic",
      "end",
      "",
      "function _draw()",
      "  -- draw logic",
      "end"
    }
  end
  self.undo_stack = {}
  self.modified = false
end

function Doc:save()
  project.write(self.filename, table.concat(self.lines, "\n"))
  self.modified = false
end

function Doc:snapshot()
  if #self.undo_stack > config.undo_limit then
    table.remove(self.undo_stack, 1)
  end
  local s = {}
  for i, l in ipairs(self.lines) do s[i] = l end
  table.insert(self.undo_stack, s)
end

function Doc:undo()
  if #self.undo_stack > 0 then
    self.lines = table.remove(self.undo_stack)
    self.modified = true
    return true
  end
  return false
end

function Doc:insert(y, x, text)
  self:snapshot()
  self.modified = true
  local line = self.lines[y] or ""
  local pre = string.sub(line, 1, x)
  local post = string.sub(line, x + 1)

  if string.find(text, "\n") then
    local parts = split(text, "\n")
    self.lines[y] = pre .. parts[1]
    for i = 2, #parts - 1 do
      table.insert(self.lines, y + i - 1, parts[i])
    end
    table.insert(self.lines, y + #parts - 1, parts[#parts] .. post)
    return y + #parts - 1, #parts[#parts]
  else
    self.lines[y] = pre .. text .. post
    return y, x + #text
  end
end

function Doc:remove(y, x, count)
  self:snapshot()
  self.modified = true
  local line = self.lines[y] or ""
  if x + count <= #line then
    self.lines[y] = string.sub(line, 1, x) .. string.sub(line, x + count + 1)
  elseif y < #self.lines then
    self.lines[y] = string.sub(line, 1, x) .. (self.lines[y + 1] or "")
    table.remove(self.lines, y + 1)
  end
end

-- ============================================================================
-- 7. INPUT WRAPPER
-- ============================================================================

local Input = {}

function Input.isCtrlDown()
  if input and input.isCtrlDown then return input.isCtrlDown() end
  return btn(Keys.CONTROL_LEFT) or btn(Keys.CONTROL_RIGHT)
end

function Input.isShiftDown()
  if input and input.isShiftDown then return input.isShiftDown() end
  return btn(Keys.SHIFT_LEFT) or btn(Keys.SHIFT_RIGHT)
end

function Input.isKeyJustPressed(keyCode)
  return btnp(keyCode)
end

function Input.getChar()
  if input and input.getNextChar then return input.getNextChar() end
  if char then return char() end
  return nil
end

-- ============================================================================
-- 8. VIEW (EDITOR)
-- ============================================================================

local View = {}
View.__index = View

function View.new(doc)
  local self = setmetatable({}, View)
  self.doc = doc
  self.syntax = Syntax.new()
  self.cx = 0; self.cy = 1
  self.sx = 0; self.sy = 1
  self.scroll_x = 0; self.scroll_y = 0
  self.gutter_w = 40
  self.msg = ""; self.msg_timer = 0; self.blink = 0
  return self
end

function View:toast(msg)
  self.msg = msg; self.msg_timer = 120
end

function View:has_sel()
  return self.sx ~= self.cx or self.sy ~= self.cy
end

function View:clear_sel()
  self.sx, self.sy = self.cx, self.cy
end

function View:get_sel()
  if self.sy < self.cy or (self.sy == self.cy and self.sx < self.cx) then
    return self.sx, self.sy, self.cx, self.cy
  else
    return self.cx, self.cy, self.sx, self.sy
  end
end

function View:del_sel()
  if not self:has_sel() then return end
  local x1, y1, x2, y2 = self:get_sel()
  self.doc:snapshot()
  local l1 = self.doc.lines[y1]
  local l2 = self.doc.lines[y2]
  self.doc.lines[y1] = string.sub(l1, 1, x1) .. string.sub(l2, x2 + 1)
  for i = 1, (y2 - y1) do
    table.remove(self.doc.lines, y1 + 1)
  end
  self.cx, self.cy = x1, y1
  self:clear_sel()
end

function View:handle_input()
  local is_ctrl = Input.isCtrlDown()
  local is_shift = Input.isShiftDown()

  if is_ctrl then
    if Input.isKeyJustPressed(Keys.S) then
      self.doc:save(); self:toast("Saved!"); self.blink = 0; return
    end
    if Input.isKeyJustPressed(Keys.Z) then
      if self.doc:undo() then
        self.cy = clamp(self.cy, 1, #self.doc.lines)
        self.cx = clamp(self.cx, 0, #self.doc.lines[self.cy])
        self:toast("Undo")
      end
      self.blink = 0; return
    end
    if Input.isKeyJustPressed(Keys.R) then
      self.doc:save()
      if sys and sys.run then sys.run() end
      self.blink = 0; return
    end
    if Input.isKeyJustPressed(Keys.A) then
      self:select_all(); self.blink = 0; return
    end
    if Input.isKeyJustPressed(Keys.C) then
      self:copy(); self.blink = 0; return
    end
    if Input.isKeyJustPressed(Keys.V) then
      self:paste(); self.blink = 0; return
    end
    if Input.isKeyJustPressed(Keys.X) then
      self:cut(); self.blink = 0; return
    end
    self:handle_mouse(); self.blink = self.blink + 1; return
  end

  if Input.isKeyJustPressed(Keys.ENTER) then
    self:newline(); self.blink = 0; self:handle_mouse(); self.blink = self.blink + 1; return
  end
  if Input.isKeyJustPressed(Keys.BACKSPACE) then
    self:backspace(); self.blink = 0; self:handle_mouse(); self.blink = self.blink + 1; return
  end
  if Input.isKeyJustPressed(Keys.DEL) then
    self:delete_forward(); self.blink = 0; self:handle_mouse(); self.blink = self.blink + 1; return
  end
  if Input.isKeyJustPressed(Keys.TAB) then
    self:insert_char("  "); self.blink = 0; self:handle_mouse(); self.blink = self.blink + 1; return
  end

  if Input.isKeyJustPressed(Keys.UP) then self:move(0, -1, is_shift); self.blink = 0 end
  if Input.isKeyJustPressed(Keys.DOWN) then self:move(0, 1, is_shift); self.blink = 0 end
  if Input.isKeyJustPressed(Keys.LEFT) then self:move(-1, 0, is_shift); self.blink = 0 end
  if Input.isKeyJustPressed(Keys.RIGHT) then self:move(1, 0, is_shift); self.blink = 0 end

  local c = Input.getChar()
  while c do
    self:insert_char(c); self.blink = 0; c = Input.getChar()
  end

  self:handle_mouse(); self.blink = self.blink + 1
end

function View:insert_char(t)
  if self:has_sel() then self:del_sel() end
  self.cy, self.cx = self.doc:insert(self.cy, self.cx, t)
  self:clear_sel(); self:scroll_to()
end

function View:newline()
  if self:has_sel() then self:del_sel() end
  self.doc:snapshot()
  local l = self.doc.lines[self.cy]
  local ind = string.match(l, "^%s*") or ""
  if string.match(l, "then%s*$") or string.match(l, "do%s*$") or string.match(l, "function") then
    ind = ind .. "  "
  end
  local pre = string.sub(l, 1, self.cx)
  local post = string.sub(l, self.cx + 1)
  self.doc.lines[self.cy] = pre
  table.insert(self.doc.lines, self.cy + 1, ind .. post)
  self.cy = self.cy + 1; self.cx = #ind
  self:clear_sel(); self:scroll_to()
end

function View:backspace()
  if self:has_sel() then self:del_sel()
  elseif self.cx > 0 then
    self.doc:remove(self.cy, self.cx - 1, 1); self.cx = self.cx - 1
  elseif self.cy > 1 then
    local prev_len = #self.doc.lines[self.cy - 1]
    self.doc:remove(self.cy - 1, prev_len, 0)
    self.cy = self.cy - 1; self.cx = prev_len
  end
  self:clear_sel(); self:scroll_to()
end

function View:delete_forward()
  if self:has_sel() then self:del_sel()
  elseif self.cx < #self.doc.lines[self.cy] then self.doc:remove(self.cy, self.cx, 1)
  elseif self.cy < #self.doc.lines then self.doc:remove(self.cy, self.cx, 0)
  end
  self:clear_sel(); self:scroll_to()
end

function View:select_all()
  self.sx, self.sy = 0, 1
  self.cy = #self.doc.lines; self.cx = #self.doc.lines[self.cy]
end

function View:copy()
  if not self:has_sel() then return end
  local x1, y1, x2, y2 = self:get_sel()
  local t = ""
  if y1 == y2 then t = string.sub(self.doc.lines[y1], x1 + 1, x2)
  else
    t = string.sub(self.doc.lines[y1], x1 + 1) .. "\n"
    for i = y1 + 1, y2 - 1 do t = t .. self.doc.lines[i] .. "\n" end
    t = t .. string.sub(self.doc.lines[y2], 1, x2)
  end
  if clipboard then clipboard(t); self:toast("Copied") end
end

function View:paste()
  if not clipboard then return end
  local t = clipboard()
  if t and t ~= "" then
    if self:has_sel() then self:del_sel() end
    self.cy, self.cx = self.doc:insert(self.cy, self.cx, t)
    self:clear_sel(); self:scroll_to()
  end
end

function View:cut()
  self:copy(); self:del_sel(); self:toast("Cut")
end

function View:move(dx, dy, sel)
  if not sel then self:clear_sel() end
  self.cy = clamp(self.cy + dy, 1, #self.doc.lines)
  self.cx = clamp(self.cx + dx, 0, #self.doc.lines[self.cy])
  if not sel then self.sx, self.sy = self.cx, self.cy end
  self:scroll_to()
end

function View:scroll_to()
  local sh = display_height()
  local view_h = math.floor((sh - config.header_h - config.toolbar_h) / config.line_h)
  if self.cy <= self.scroll_y then self.scroll_y = self.cy - 1 end
  if self.cy > self.scroll_y + view_h then self.scroll_y = self.cy - view_h end
  self.scroll_y = math.max(0, self.scroll_y)
end

function View:handle_mouse()
  local m = mouse()
  if not m then return end
  local sw, sh = display_width(), display_height()

  if m.scroll and m.scroll ~= 0 then
    self.scroll_y = clamp(self.scroll_y - m.scroll, 0, math.max(0, #self.doc.lines - 5))
  end

  if m.click then
    local header_y = sh - config.header_h - config.toolbar_h
    local text_left_padding = config.text_left_padding
    if m.y < header_y and m.x > self.gutter_w then
      local dist_from_header = header_y - m.y
      local line_offset = math.floor(dist_from_header / config.line_h)
      local line_idx = self.scroll_y + line_offset + 1
      if line_idx >= 1 and line_idx <= #self.doc.lines then
        self.cy = line_idx
        local text_x = m.x - self.gutter_w - text_left_padding
        local char_x = math.floor(text_x / config.font_w) + self.scroll_x
        self.cx = clamp(char_x, 0, #self.doc.lines[self.cy])
        self:clear_sel()
      end
    end
  end
end

function View:draw()
  cls(theme.Colors.Background) -- Using Theme
  local sw, sh = display_width(), display_height()

  -- Toolbar at top
  local toolbar_y = sh - config.toolbar_h
  ui.toolbar(0, toolbar_y, sw, config.toolbar_h)

  local btn_x = 10
  local btn_y = toolbar_y + 6
  local btn_size = 24

  if ui.icon_button("S", btn_x, btn_y, btn_size) then
    self.doc:save(); self:toast("Saved!")
  end
  btn_x = btn_x + btn_size + 5

  if ui.icon_button("R", btn_x, btn_y, btn_size) then
    self.doc:save()
    if sys and sys.run then sys.run() end
  end
  btn_x = btn_x + btn_size + 5

  if ui.icon_button("Z", btn_x, btn_y, btn_size) then
    if self.doc:undo() then
      self.cy = clamp(self.cy, 1, #self.doc.lines)
      self.cx = clamp(self.cx, 0, #self.doc.lines[self.cy])
      self:toast("Undo")
    end
  end

  -- Header below toolbar
  local header_y = toolbar_y - config.header_h
  rect(0, header_y, sw, config.header_h, theme.Colors.PanelBackground) -- Using Theme
  local title = self.doc.filename .. (self.doc.modified and " *" or "")
  print(title, 10, header_y + 12, theme.Colors.TextPrimary) -- Using Theme

  -- Content area
  local content_height = header_y
  local visible_lines = math.floor(content_height / config.line_h)
  local digits = #tostring(#self.doc.lines)
  self.gutter_w = math.max(40, digits * config.font_w + 20)
  local text_left_padding = config.text_left_padding

  local x1, y1, x2, y2 = self:get_sel()
  local has_sel = self:has_sel()

  for i = 0, visible_lines do
    local idx = self.scroll_y + i + 1
    if idx > #self.doc.lines then break end
    local y = header_y - ((i + 1) * config.line_h)
    if y < 0 then break end
    local txt = self.doc.lines[idx]

    rect(0, y, self.gutter_w, config.line_h, theme.Colors.Gutter) -- Using Theme
    local n = tostring(idx)
    local num_x = self.gutter_w - #n * config.font_w - 8
    -- Fix: Line numbers use same baseline as text
    local num_y = y + config.line_h - (config.line_h - config.font_h) / 2 - 4
    print(n, num_x, num_y, theme.Colors.TextSecondary) -- Using Theme

    if has_sel and idx >= y1 and idx <= y2 then
      local sx, ex = 0, #txt
      if idx == y1 then sx = x1 end
      if idx == y2 then ex = x2 end
      local px = self.gutter_w + text_left_padding + (sx - self.scroll_x) * config.font_w
      local w = (ex - sx) * config.font_w
      if w > 0 then rect(px, y, w, config.line_h, theme.Colors.Selection) end -- Using Theme
    end

    local tokens = self.syntax:parse(txt)
    local cur_x = 0
    for _, t in ipairs(tokens) do
      local px = self.gutter_w + text_left_padding + (cur_x - self.scroll_x) * config.font_w
      -- Fix: Position text baseline at the correct height within the line
      -- BitmapFont draws from baseline, so we need to offset from top of line
      local text_y = y + config.line_h - (config.line_h - config.font_h) / 2 - 4
      print(t.text, px, text_y, t.color)
      cur_x = cur_x + #t.text
    end

    if idx == self.cy and (self.blink % config.blink_rate < config.blink_rate / 2) then
      local px = self.gutter_w + text_left_padding + (self.cx - self.scroll_x) * config.font_w
      -- Fix: Cursor uses same baseline calculation as text
      local cursor_y = y + config.line_h - (config.line_h - config.font_h) / 2 - 4 - config.font_h
      rect(px, cursor_y, 2, config.font_h, theme.Colors.Caret) -- Using Theme
    end
  end

  local last_line_y = header_y - ((visible_lines + 1) * config.line_h)
  if last_line_y > 0 then
    rect(0, 0, self.gutter_w, last_line_y, theme.Colors.Gutter) -- Using Theme
  end

  if self.msg_timer > 0 then
    local toast_w = #self.msg * config.font_w + 20
    local toast_h = config.font_h + 20
    local toast_x = sw / 2 - toast_w / 2
    local toast_y = config.line_h * 3
    rect(toast_x, toast_y, toast_w, toast_h, theme.Colors.ToastBg) -- Using Theme
    print(self.msg, toast_x + 10, toast_y + 10, theme.Colors.TextPrimary) -- Using Theme
    self.msg_timer = self.msg_timer - 1
  end
end

-- ============================================================================
-- 9. MAIN
-- ============================================================================

local editor = nil

function _init()
  if font_width then
    config.font_w = font_width() + 1
  end
  if font_height then
    config.font_h = font_height()
    config.line_h = math.floor(config.font_h * 1.25)
    config.text_offset_y = math.floor((config.line_h - config.font_h) * 0.3)
    config.header_h = config.line_h * 2
  end

  local doc = Doc.new(filepath or "main.lua")
  doc:load()
  editor = View.new(doc)
  editor:toast("Editor Ready")
end

function _update()
  if editor then editor:handle_input() end
end

function _draw()
  if editor then editor:draw() end
end
