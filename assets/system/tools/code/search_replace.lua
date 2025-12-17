-- Search and replace logic detached from UI.
local SearchReplace = {}
SearchReplace.__index = SearchReplace

function SearchReplace.new()
  local self = setmetatable({}, SearchReplace)
  return self
end

function SearchReplace:find_all(text, pattern, plain)
  local results = {}
  local s = 1
  while true do
    local from, to = text:find(pattern, s, plain)
    if not from then break end
    table.insert(results, { from = from, to = to })
    s = to + 1
  end
  return results
end

function SearchReplace:replace_all(text, pattern, repl, plain)
  return text:gsub(pattern, repl)
end

return SearchReplace
