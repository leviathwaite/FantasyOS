-- syntax.lua
local M = {}

function M.init(state) end

local Syntax = {}
Syntax.__index = Syntax
function Syntax.new(api_docs, config)
  local s = setmetatable({}, Syntax)
  s.keywords = {
    ["and"]=1,["break"]=1,["do"]=1,["else"]=1,["elseif"]=1,["end"]=1,
    ["false"]=1,["for"]=1,["function"]=1,["if"]=1,["in"]=1,["local"]=1,
    ["nil"]=1,["not"]=1,["or"]=1,["repeat"]=1,["return"]=1,["then"]=1,
    ["true"]=1,["until"]=1,["while"]=1
  }
  s.funcs = {}
  for k,_ in pairs(api_docs or {}) do s.funcs[k] = 1 end
  s.patterns = {
    {"^%-%-.*", config.colors.comment},
    {'^"[^"]*"', config.colors.str},
    {"^'[^']*'", config.colors.str},
    {"^0x%x+", config.colors.num},
    {"^%d+%.?%d*", config.colors.num},
    {"^[%a_][%w_]*", "word"},
    {"^[%+%-*/%^%%#=<>~]+", config.colors.text},
    {"^[%(%)%[%]{}.,;:]", "word"},
    {"^%s+", "skip"},
    {"^.", config.colors.text}
  }
  return s
end

function Syntax:parse(text)
  if not text then return {} end
  local tokens = {}
  local pos = 1
  while pos <= #text do
    local sub = string.sub(text, pos); local matched = false
    for _, pattern in ipairs(self.patterns) do
      local s,e = string.find(sub, pattern[1])
      if s == 1 then
        local token_text = string.sub(sub, s, e)
        local col = pattern[2]
        if col == "word" then
          local ch = token_text
          if self.keywords[ch] then col = 12
          elseif self.funcs[ch] then col = 14
          elseif ch == "(" or ch == ")" or ch == "[" or ch == "]" or ch == "{" or ch == "}" then
            col = 7
            table.insert(tokens, {text = ch, color = col, bracket = true})
            pos = pos + 1; matched = true; break
          else col = 7 end
        elseif col == "skip" then pos = pos + (e - s + 1); matched = true; break end
        table.insert(tokens, {text = token_text, color = col})
        pos = pos + (e - s + 1); matched = true; break
      end
    end
    if not matched then pos = pos + 1 end
  end
  return tokens
end

-- default syntax instance (constructed on demand)
local default_syntax = nil
function M.get_syntax(api_docs, config)
  if not default_syntax then default_syntax = Syntax.new(api_docs, config) end
  return default_syntax
end

return M
