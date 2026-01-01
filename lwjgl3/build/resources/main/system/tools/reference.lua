local UITheme = require("system/tools/code_module/UITheme")
local Reference = {}

Reference.Syntax = {}
Reference.Syntax.__index = Reference.Syntax

function Reference.Syntax.new()
  local s = setmetatable({}, Reference.Syntax)
  s.keywords = {
    ["and"]=1,["break"]=1,["do"]=1,["else"]=1,["elseif"]=1,["end"]=1,
    ["false"]=1,["for"]=1,["function"]=1,["if"]=1,["in"]=1,["local"]=1,
    ["nil"]=1,["not"]=1,["or"]=1,["repeat"]=1,["return"]=1,["then"]=1,
    ["true"]=1,["until"]=1,["while"]=1
  }

  -- Load globals if available
  s.funcs = {}
  if api_docs then
      for k,_ in pairs(api_docs) do s.funcs[k] = 1 end
  end

  s.patterns = {
    {"^%-%-.*", UITheme.colors.comment},
    {'^"[^"]*"', UITheme.colors.str},
    {"^'[^']*'", UITheme.colors.str},
    {"^0x%x+", UITheme.colors.num},
    {"^%d+%.?%d*", UITheme.colors.num},
    {"^[%a_][%w_]*", "word"},
    {"^[%+%-*/%^%%#=<>~]+", UITheme.colors.text},
    {"^[%(%)%[%]{}.,;:]", "word"},
    {"^%s+", "skip"},
    {"^.", UITheme.colors.text}
  }
  return s
end

function Reference.Syntax:parse(text)
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
          if self.keywords[ch] then col = UITheme.colors.keyword
          elseif self.funcs[ch] then col = UITheme.colors.func
          elseif ch:match("[%(%)%[%]{}]") then
             col = UITheme.colors.text
             table.insert(tokens, {text = ch, color = col, bracket = true})
             pos = pos + 1; matched = true; break
          else col = UITheme.colors.text end
        elseif col == "skip" then pos = pos + (e - s + 1); matched = true; break end
        table.insert(tokens, {text = token_text, color = col})
        pos = pos + (e - s + 1); matched = true; break
      end
    end
    if not matched then pos = pos + 1 end
  end
  return tokens
end

return Reference
