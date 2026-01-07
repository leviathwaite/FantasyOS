package com.nerddaygames.engine.editor;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AutoCompleteProvider - Provides autocomplete suggestions for code editor
 * Includes built-in Lua API functions and user-defined functions
 */
public class AutoCompleteProvider {
    private final Globals globals;
    private final Map<String, List<String>> apiSuggestions;
    private final List<String> userFunctions;
    
    public AutoCompleteProvider(Globals globals) {
        this.globals = globals;
        this.apiSuggestions = new HashMap<>();
        this.userFunctions = new ArrayList<>();
        
        initializeApiSuggestions();
    }
    
    private void initializeApiSuggestions() {
        // Lua built-in functions
        apiSuggestions.put("", Arrays.asList(
            "print", "assert", "error", "type", "tonumber", "tostring",
            "pairs", "ipairs", "next", "select", "pcall", "xpcall",
            "require", "dofile", "load", "loadfile",
            "getmetatable", "setmetatable", "rawget", "rawset", "rawequal", "rawlen",
            "collectgarbage"
        ));
        
        // String functions
        apiSuggestions.put("string", Arrays.asList(
            "byte", "char", "dump", "find", "format", "gmatch", "gsub",
            "len", "lower", "match", "rep", "reverse", "sub", "upper"
        ));
        
        // Table functions
        apiSuggestions.put("table", Arrays.asList(
            "concat", "insert", "move", "pack", "remove", "sort", "unpack"
        ));
        
        // Math functions
        apiSuggestions.put("math", Arrays.asList(
            "abs", "acos", "asin", "atan", "atan2", "ceil", "cos", "deg",
            "exp", "floor", "fmod", "huge", "log", "max", "min", "modf",
            "pi", "pow", "rad", "random", "randomseed", "sin", "sqrt", "tan"
        ));
        
        // IO functions
        apiSuggestions.put("io", Arrays.asList(
            "close", "flush", "input", "lines", "open", "output", "read",
            "tmpfile", "type", "write"
        ));
        
        // OS functions
        apiSuggestions.put("os", Arrays.asList(
            "clock", "date", "difftime", "execute", "exit", "getenv",
            "remove", "rename", "setlocale", "time", "tmpname"
        ));
        
        // Coroutine functions
        apiSuggestions.put("coroutine", Arrays.asList(
            "create", "resume", "running", "status", "wrap", "yield"
        ));
    }
    
    public void register() {
        LuaTable autocompleteTable = new LuaTable();
        
        autocompleteTable.set("get_suggestions", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue prefixArg, LuaValue contextArg) {
                String prefix = prefixArg.checkjstring();
                String context = contextArg.optjstring("");
                
                return getSuggestions(prefix, context);
            }
        });
        
        autocompleteTable.set("add_user_function", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                String funcName = arg.checkjstring();
                if (!userFunctions.contains(funcName)) {
                    userFunctions.add(funcName);
                }
                return LuaValue.NONE;
            }
        });
        
        autocompleteTable.set("clear_user_functions", new org.luaj.vm2.lib.ZeroArgFunction() {
            @Override
            public LuaValue call() {
                userFunctions.clear();
                return LuaValue.NONE;
            }
        });
        
        globals.set("autocomplete", autocompleteTable);
    }
    
    private LuaValue getSuggestions(String prefix, String context) {
        LuaTable suggestions = new LuaTable();
        int idx = 1;
        
        // Check if we're looking for a method (e.g., "string.")
        int dotIndex = prefix.lastIndexOf('.');
        if (dotIndex > 0) {
            String tableName = prefix.substring(0, dotIndex);
            String methodPrefix = prefix.substring(dotIndex + 1).toLowerCase();
            
            List<String> methods = apiSuggestions.get(tableName);
            if (methods != null) {
                for (String method : methods) {
                    if (method.toLowerCase().startsWith(methodPrefix)) {
                        suggestions.set(idx++, LuaValue.valueOf(tableName + "." + method));
                    }
                }
            }
        } else {
            // Look for top-level functions
            String lowerPrefix = prefix.toLowerCase();
            
            // Built-in functions
            List<String> builtins = apiSuggestions.get("");
            if (builtins != null) {
                for (String func : builtins) {
                    if (func.toLowerCase().startsWith(lowerPrefix)) {
                        suggestions.set(idx++, LuaValue.valueOf(func));
                    }
                }
            }
            
            // API tables
            for (String tableName : apiSuggestions.keySet()) {
                if (!tableName.isEmpty() && tableName.toLowerCase().startsWith(lowerPrefix)) {
                    suggestions.set(idx++, LuaValue.valueOf(tableName));
                }
            }
            
            // User functions
            for (String func : userFunctions) {
                if (func.toLowerCase().startsWith(lowerPrefix)) {
                    suggestions.set(idx++, LuaValue.valueOf(func));
                }
            }
        }
        
        return suggestions;
    }
}
