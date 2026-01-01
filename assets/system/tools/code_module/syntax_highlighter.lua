local Config = require("system/tools/code_module/config")
local Highlighter = {}
Highlighter.__index = Highlighter

function Highlighter.new()
  local s = setmetatable({}, Highlighter)
  s.keywords = {
    ["and"]=1,["break"]=1,["do"]=1,["else"]=1,["elseif"]=1,["end"]=1,
    ["false"]=1,["for"]=1,["function"]=1,["if"]=1,["in"]=1,["local"]=1,
    ["nil"]=1,["not"]=1,["or"]=1,["repeat"]=1,["return"]=1,["then"]=1,
    ["true"]=1,["until"]=1,["while"]=1
  }
  s.patterns = {
    {"^%-%-.*", Config.colors.comment},
    {'^"[^"]*"', Config.colors.str},
    {"^'[^']*'", Config.colors.str},
    {"^0x%x+", Config.colors.num},
    {"^%d+%.?%d*", Config.colors.num},
    {"^[%a_][%w_]*", "word"},
    {"^[%+%-*/%^%%#=<>~]+", Config.colors.text},
    {"^[%(%)%[%]{}.,;:]", "word"},
    {"^%s+", "skip"},
    {"^.", Config.colors.text}
  }
  return s
end

function Highlighter:parse(text)
  if not text then return {} end
  local tokens = {}
  local pos = 1
  while pos <= #text do
    local sub = string.sub(text, pos)
    local matched = false
    for _, pattern in ipairs(self.patterns) do
      local s,e = string.find(sub, pattern[1])
      if s == 1 then
        local token_text = string.sub(sub, s, e)
        local col = pattern[2]
        if col == "word" then
          if self.keywords[token_text] then col = Config.colors.keyword
          else col = Config.colors.text end
        elseif col == "skip" then
            pos = pos + (e - s + 1); matched = true; break
        end
        table.insert(tokens, {text = token_text, color = col})
        pos = pos + (e - s + 1); matched = true; break
      end
    end
    if not matched then pos = pos + 1 end
  end
  return tokens
end

return Highlighter
