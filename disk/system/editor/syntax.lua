-- disk/system/editor/syntax.lua
-- Syntax highlighting coordination

local syntax = {}

-- Default colors for token types
syntax.token_colors = {
    keyword = "syntax_keyword",
    string = "syntax_string",
    number = "syntax_number",
    comment = "syntax_comment",
    ["function"] = "syntax_function",
    operator = "syntax_operator",
    type = "syntax_type",
    variable = "syntax_variable_local",
    text = "text",
}

-- Get color for token type
function syntax.get_token_color(token_type)
    return syntax.token_colors[token_type] or "text"
end

-- Tokenize line for rendering
function syntax.tokenize_line(line, language)
    if not tokenizer or not tokenizer.tokenize then
        -- Fallback: return entire line as text token
        return {{type = "text", text = line}}
    end
    
    local tokens = tokenizer.tokenize(line, language or "lua")
    
    -- Convert Lua table (1-indexed) to array
    local result = {}
    if tokens then
        for i = 1, #tokens do
            table.insert(result, tokens[i])
        end
    end
    
    return result
end

-- Detect language from filename
function syntax.detect_language(filename)
    if not tokenizer or not tokenizer.detect_language then
        return "text"
    end
    
    return tokenizer.detect_language(filename)
end

-- Parse user-defined functions from buffer
function syntax.parse_user_functions()
    if not buffer or not buffer.line_count then
        return {}
    end
    
    local functions = {}
    local line_count = buffer.line_count()
    
    for i = 1, line_count do
        local line = buffer.get_line(i)
        if line then
            -- Match function definitions
            local func_name = line:match("^%s*function%s+([%w_]+)%s*%(")
            if func_name then
                table.insert(functions, func_name)
            end
            
            -- Match local function definitions
            func_name = line:match("^%s*local%s+function%s+([%w_]+)%s*%(")
            if func_name then
                table.insert(functions, func_name)
            end
            
            -- Match table function definitions
            func_name = line:match("^%s*function%s+[%w_]+%.([%w_]+)%s*%(")
            if func_name then
                table.insert(functions, func_name)
            end
        end
    end
    
    return functions
end

-- Update autocomplete with user functions
function syntax.update_autocomplete()
    if not autocomplete or not autocomplete.clear_user_functions then
        return
    end
    
    autocomplete.clear_user_functions()
    
    local functions = syntax.parse_user_functions()
    for _, func in ipairs(functions) do
        if autocomplete.add_user_function then
            autocomplete.add_user_function(func)
        end
    end
end

return syntax
