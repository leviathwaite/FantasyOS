-- TOON to JSON Converter
-- Converts TOON (human-readable config format) to JSON
-- Based on: https://dev.to/sreeni5018/toon-vs-json-a-modern-data-format-showdown-2ooc

local ToonConverter = {}

-- Parse a TOON string into a Lua table
function ToonConverter.parse(toon_str)
  if not toon_str or toon_str == "" then
    return {}
  end
  
  local result = {}
  local stack = {result}
  local current = result
  local indent_stack = {0}
  
  for line in toon_str:gmatch("[^\r\n]+") do
    -- Skip empty lines and comments
    local trimmed = line:match("^%s*(.-)%s*$")
    if trimmed ~= "" and not trimmed:match("^#") then
      -- Calculate indentation
      local indent = #line:match("^(%s*)")
      
      -- Handle key-value pairs
      local key, value = line:match("^%s*([%w_]+):%s*(.*)$")
      if key then
        -- Parse value
        if value == "" or value:match("^%s*$") then
          -- Empty value means object/array follows
          current[key] = {}
          table.insert(stack, current[key])
          current = current[key]
          table.insert(indent_stack, indent)
        else
          -- Inline value
          current[key] = ToonConverter.parseValue(value)
        end
      else
        -- Handle list items
        local list_value = line:match("^%s*%-%s*(.+)$")
        if list_value then
          if type(current) ~= "table" then
            current = {}
          end
          table.insert(current, ToonConverter.parseValue(list_value))
        end
      end
      
      -- Handle dedentation
      while #indent_stack > 1 and indent < indent_stack[#indent_stack] do
        table.remove(stack)
        table.remove(indent_stack)
        current = stack[#stack]
      end
    end
  end
  
  return result
end

-- Parse individual values
function ToonConverter.parseValue(str)
  str = str:match("^%s*(.-)%s*$")  -- Trim whitespace
  
  -- Boolean
  if str == "true" then return true end
  if str == "false" then return false end
  
  -- Null
  if str == "null" then return nil end
  
  -- Number
  local num = tonumber(str)
  if num then return num end
  
  -- String (remove quotes if present)
  if str:match("^[\"'].*[\"']$") then
    return str:sub(2, -2)
  end
  
  return str
end

-- Convert Lua table to JSON string
function ToonConverter.toJSON(tbl, indent)
  indent = indent or 0
  local indent_str = string.rep("  ", indent)
  local next_indent = string.rep("  ", indent + 1)
  
  if type(tbl) ~= "table" then
    if type(tbl) == "string" then
      return '"' .. tbl:gsub('"', '\\"') .. '"'
    elseif type(tbl) == "boolean" then
      return tbl and "true" or "false"
    elseif tbl == nil then
      return "null"
    else
      return tostring(tbl)
    end
  end
  
  -- Check if it's an array
  local is_array = true
  local count = 0
  for k, v in pairs(tbl) do
    count = count + 1
    if type(k) ~= "number" or k ~= count then
      is_array = false
      break
    end
  end
  
  if is_array and count > 0 then
    -- Array
    local parts = {}
    for i = 1, count do
      table.insert(parts, next_indent .. ToonConverter.toJSON(tbl[i], indent + 1))
    end
    return "[\n" .. table.concat(parts, ",\n") .. "\n" .. indent_str .. "]"
  else
    -- Object
    local parts = {}
    for k, v in pairs(tbl) do
      local key = type(k) == "string" and k or tostring(k)
      table.insert(parts, next_indent .. '"' .. key .. '": ' .. ToonConverter.toJSON(v, indent + 1))
    end
    if #parts == 0 then
      return "{}"
    end
    return "{\n" .. table.concat(parts, ",\n") .. "\n" .. indent_str .. "}"
  end
end

-- Convert TOON file to JSON file
function ToonConverter.convertFile(toon_path, json_path)
  -- Read TOON file
  local toon_content = ""
  if type(project) == "table" and type(project.read) == "function" then
    local ok, content = pcall(function() return project.read(toon_path) end)
    if ok and content then
      toon_content = content
    end
  end
  
  if toon_content == "" then
    return false, "Could not read TOON file: " .. toon_path
  end
  
  -- Parse TOON
  local ok, data = pcall(function() return ToonConverter.parse(toon_content) end)
  if not ok then
    return false, "TOON parse error: " .. tostring(data)
  end
  
  -- Convert to JSON
  local json_content = ToonConverter.toJSON(data)
  
  -- Write JSON file
  if type(project) == "table" and type(project.write) == "function" then
    local ok, res = pcall(function() return project.write(json_path, json_content) end)
    if ok and res then
      return true
    end
  end
  
  return false, "Could not write JSON file: " .. json_path
end

-- Example usage:
-- local config_data = ToonConverter.parse(toon_string)
-- local json_string = ToonConverter.toJSON(config_data)
-- ToonConverter.convertFile("config.toon", "config.json")

return ToonConverter
