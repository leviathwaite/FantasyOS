-- Simple tokenizer / syntax highlighter scaffold.
local Syntax = {}
Syntax.__index = Syntax

function Syntax.new(settings)
  local self = setmetatable({}, Syntax)
  self.settings = settings or {}
  self.tokens = {}
  return self
end

function Syntax:tokenize(text)
  self.tokens = {}
  local i = 1
  for line in text:gmatch("([^\n]*)\n?") do
    self.tokens[i] = { raw = line, spans = {} }
    i = i + 1
  end
  return self.tokens
end

function Syntax:get_tokens()
  return self.tokens
end

return Syntax
