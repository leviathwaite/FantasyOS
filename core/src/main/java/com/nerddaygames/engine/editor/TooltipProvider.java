package com.nerddaygames.engine.editor;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;

import java.util.HashMap;
import java.util.Map;

/**
 * TooltipProvider - Provides function signature tooltips for code editor
 * Shows function parameters and descriptions on hover
 */
public class TooltipProvider {
    private final Globals globals;
    private final Map<String, String> tooltips;
    
    public TooltipProvider(Globals globals) {
        this.globals = globals;
        this.tooltips = new HashMap<>();
        
        initializeTooltips();
    }
    
    private void initializeTooltips() {
        // Lua built-in functions
        tooltips.put("print", "print(...)\nPrints values to standard output");
        tooltips.put("assert", "assert(v [, message])\nRaises error if v is false");
        tooltips.put("error", "error(message [, level])\nTerminates execution with error");
        tooltips.put("type", "type(v)\nReturns the type of v as a string");
        tooltips.put("tonumber", "tonumber(e [, base])\nConverts to number");
        tooltips.put("tostring", "tostring(v)\nConverts value to string");
        tooltips.put("pairs", "pairs(t)\nIterates over all key-value pairs in table");
        tooltips.put("ipairs", "ipairs(t)\nIterates over array part of table");
        tooltips.put("next", "next(table [, index])\nGets next key-value pair");
        tooltips.put("select", "select(index, ...)\nReturns arguments starting at index");
        tooltips.put("pcall", "pcall(f [, arg1, ...])\nCalls function in protected mode");
        tooltips.put("xpcall", "xpcall(f, msgh [, arg1, ...])\nCalls function with error handler");
        tooltips.put("require", "require(modname)\nLoads and returns module");
        tooltips.put("dofile", "dofile([filename])\nExecutes Lua file");
        tooltips.put("load", "load(chunk [, chunkname [, mode [, env]]])\nLoads chunk as function");
        tooltips.put("getmetatable", "getmetatable(object)\nReturns metatable of object");
        tooltips.put("setmetatable", "setmetatable(table, metatable)\nSets metatable for table");
        
        // String functions
        tooltips.put("string.byte", "string.byte(s [, i [, j]])\nReturns numeric codes of characters");
        tooltips.put("string.char", "string.char(...)\nReturns string from character codes");
        tooltips.put("string.find", "string.find(s, pattern [, init [, plain]])\nFinds pattern in string");
        tooltips.put("string.format", "string.format(formatstring, ...)\nFormats string with arguments");
        tooltips.put("string.gmatch", "string.gmatch(s, pattern)\nIterator for pattern matches");
        tooltips.put("string.gsub", "string.gsub(s, pattern, repl [, n])\nReplace pattern matches");
        tooltips.put("string.len", "string.len(s)\nReturns length of string");
        tooltips.put("string.lower", "string.lower(s)\nConverts to lowercase");
        tooltips.put("string.upper", "string.upper(s)\nConverts to uppercase");
        tooltips.put("string.match", "string.match(s, pattern [, init])\nMatches pattern in string");
        tooltips.put("string.rep", "string.rep(s, n [, sep])\nRepeats string n times");
        tooltips.put("string.reverse", "string.reverse(s)\nReverses string");
        tooltips.put("string.sub", "string.sub(s, i [, j])\nExtract substring");
        
        // Table functions
        tooltips.put("table.concat", "table.concat(list [, sep [, i [, j]]])\nConcatenates array elements");
        tooltips.put("table.insert", "table.insert(list, [pos,] value)\nInserts element into array");
        tooltips.put("table.remove", "table.remove(list [, pos])\nRemoves element from array");
        tooltips.put("table.sort", "table.sort(list [, comp])\nSorts array elements");
        tooltips.put("table.unpack", "table.unpack(list [, i [, j]])\nReturns array elements");
        
        // Math functions
        tooltips.put("math.abs", "math.abs(x)\nReturns absolute value");
        tooltips.put("math.ceil", "math.ceil(x)\nRounds up to integer");
        tooltips.put("math.floor", "math.floor(x)\nRounds down to integer");
        tooltips.put("math.max", "math.max(x, ...)\nReturns maximum value");
        tooltips.put("math.min", "math.min(x, ...)\nReturns minimum value");
        tooltips.put("math.random", "math.random([m [, n]])\nGenerates random number");
        tooltips.put("math.randomseed", "math.randomseed(x)\nSets random seed");
        tooltips.put("math.sqrt", "math.sqrt(x)\nReturns square root");
        tooltips.put("math.sin", "math.sin(x)\nReturns sine of x (in radians)");
        tooltips.put("math.cos", "math.cos(x)\nReturns cosine of x (in radians)");
        tooltips.put("math.tan", "math.tan(x)\nReturns tangent of x (in radians)");
        tooltips.put("math.pi", "math.pi\nValue of π (3.14159...)");
    }
    
    public void register() {
        LuaTable tooltipTable = new LuaTable();
        
        tooltipTable.set("get_tooltip", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                String funcName = arg.checkjstring();
                String tooltip = tooltips.get(funcName);
                
                if (tooltip != null) {
                    return LuaValue.valueOf(tooltip);
                }
                return LuaValue.NIL;
            }
        });
        
        tooltipTable.set("add_tooltip", new org.luaj.vm2.lib.TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue nameArg, LuaValue descArg) {
                String name = nameArg.checkjstring();
                String desc = descArg.checkjstring();
                tooltips.put(name, desc);
                return LuaValue.NONE;
            }
        });
        
        globals.set("tooltip", tooltipTable);
    }
}
