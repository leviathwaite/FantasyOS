package com.nerddaygames.engine.editor;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SyntaxTokenizer - Tokenizes source code for syntax highlighting
 * Supports Lua and can be extended for other languages
 */
public class SyntaxTokenizer {
    private final Globals globals;
    
    // Lua keywords
    private static final Set<String> LUA_KEYWORDS = new HashSet<>(Arrays.asList(
        "and", "break", "do", "else", "elseif", "end", "false", "for", "function",
        "if", "in", "local", "nil", "not", "or", "repeat", "return", "then",
        "true", "until", "while", "goto"
    ));
    
    // Lua built-in functions
    private static final Set<String> LUA_BUILTINS = new HashSet<>(Arrays.asList(
        "assert", "collectgarbage", "dofile", "error", "getmetatable", "ipairs",
        "load", "loadfile", "next", "pairs", "pcall", "print", "rawequal",
        "rawget", "rawlen", "rawset", "require", "select", "setmetatable",
        "tonumber", "tostring", "type", "xpcall", "_G", "_VERSION",
        "string", "table", "math", "io", "os", "debug", "coroutine", "package"
    ));
    
    // Token patterns
    private static final Pattern COMMENT_PATTERN = Pattern.compile("--.*$");
    private static final Pattern MULTILINE_COMMENT_START = Pattern.compile("--\\[\\[");
    private static final Pattern MULTILINE_COMMENT_END = Pattern.compile("\\]\\]");
    private static final Pattern STRING_PATTERN = Pattern.compile("(\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*')");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b\\d+\\.?\\d*\\b");
    private static final Pattern WORD_PATTERN = Pattern.compile("\\b[a-zA-Z_][a-zA-Z0-9_]*\\b");
    private static final Pattern OPERATOR_PATTERN = Pattern.compile("[+\\-*/%^#=~<>(){}\\[\\];:,.]");
    
    public SyntaxTokenizer(Globals globals) {
        this.globals = globals;
    }
    
    public void register() {
        LuaTable tokenizerTable = new LuaTable();
        
        tokenizerTable.set("tokenize", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue lineArg, LuaValue langArg) {
                String line = lineArg.checkjstring();
                String language = langArg.optjstring("lua");
                
                return tokenizeLine(line, language);
            }
        });
        
        tokenizerTable.set("detect_language", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                String filepath = arg.checkjstring();
                return LuaValue.valueOf(detectLanguage(filepath));
            }
        });
        
        tokenizerTable.set("get_languages", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                LuaTable languages = new LuaTable();
                languages.set(1, LuaValue.valueOf("lua"));
                languages.set(2, LuaValue.valueOf("text"));
                return languages;
            }
        });
        
        globals.set("tokenizer", tokenizerTable);
    }
    
    private LuaValue tokenizeLine(String line, String language) {
        LuaTable tokens = new LuaTable();
        
        if ("lua".equals(language)) {
            tokenizeLua(line, tokens);
        } else {
            // Default: treat as plain text
            LuaTable token = new LuaTable();
            token.set("type", LuaValue.valueOf("text"));
            token.set("text", LuaValue.valueOf(line));
            tokens.set(1, token);
        }
        
        return tokens;
    }
    
    private void tokenizeLua(String line, LuaTable tokens) {
        int idx = 1;
        int pos = 0;
        
        // Check for comments first
        Matcher commentMatcher = COMMENT_PATTERN.matcher(line);
        if (commentMatcher.find()) {
            int commentStart = commentMatcher.start();
            
            // Tokenize before comment
            if (commentStart > 0) {
                tokenizeNonComment(line.substring(0, commentStart), tokens, idx);
                idx = tokens.length() + 1;
            }
            
            // Add comment token
            LuaTable commentToken = new LuaTable();
            commentToken.set("type", LuaValue.valueOf("comment"));
            commentToken.set("text", LuaValue.valueOf(line.substring(commentStart)));
            tokens.set(idx, commentToken);
            return;
        }
        
        // No comment, tokenize entire line
        tokenizeNonComment(line, tokens, idx);
    }
    
    private void tokenizeNonComment(String text, LuaTable tokens, int startIdx) {
        int idx = startIdx;
        int pos = 0;
        
        while (pos < text.length()) {
            char ch = text.charAt(pos);
            
            // Skip whitespace
            if (Character.isWhitespace(ch)) {
                int start = pos;
                while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                    pos++;
                }
                LuaTable token = new LuaTable();
                token.set("type", LuaValue.valueOf("text"));
                token.set("text", LuaValue.valueOf(text.substring(start, pos)));
                tokens.set(idx++, token);
                continue;
            }
            
            // Check for strings
            if (ch == '"' || ch == '\'') {
                int start = pos;
                char quote = ch;
                pos++;
                boolean escaped = false;
                
                while (pos < text.length()) {
                    ch = text.charAt(pos);
                    if (escaped) {
                        escaped = false;
                    } else if (ch == '\\') {
                        escaped = true;
                    } else if (ch == quote) {
                        pos++;
                        break;
                    }
                    pos++;
                }
                
                LuaTable token = new LuaTable();
                token.set("type", LuaValue.valueOf("string"));
                token.set("text", LuaValue.valueOf(text.substring(start, pos)));
                tokens.set(idx++, token);
                continue;
            }
            
            // Check for numbers
            if (Character.isDigit(ch)) {
                int start = pos;
                while (pos < text.length() && (Character.isDigit(text.charAt(pos)) || text.charAt(pos) == '.')) {
                    pos++;
                }
                
                LuaTable token = new LuaTable();
                token.set("type", LuaValue.valueOf("number"));
                token.set("text", LuaValue.valueOf(text.substring(start, pos)));
                tokens.set(idx++, token);
                continue;
            }
            
            // Check for words (identifiers/keywords)
            if (Character.isLetter(ch) || ch == '_') {
                int start = pos;
                while (pos < text.length()) {
                    ch = text.charAt(pos);
                    if (Character.isLetterOrDigit(ch) || ch == '_') {
                        pos++;
                    } else {
                        break;
                    }
                }
                
                String word = text.substring(start, pos);
                LuaTable token = new LuaTable();
                
                if (LUA_KEYWORDS.contains(word)) {
                    token.set("type", LuaValue.valueOf("keyword"));
                } else if (LUA_BUILTINS.contains(word)) {
                    token.set("type", LuaValue.valueOf("function"));
                } else {
                    token.set("type", LuaValue.valueOf("variable"));
                }
                
                token.set("text", LuaValue.valueOf(word));
                tokens.set(idx++, token);
                continue;
            }
            
            // Operators and punctuation
            int start = pos;
            pos++;
            
            LuaTable token = new LuaTable();
            token.set("type", LuaValue.valueOf("operator"));
            token.set("text", LuaValue.valueOf(text.substring(start, pos)));
            tokens.set(idx++, token);
        }
    }
    
    private String detectLanguage(String filepath) {
        if (filepath.endsWith(".lua")) {
            return "lua";
        } else if (filepath.endsWith(".txt") || filepath.endsWith(".md")) {
            return "text";
        } else {
            return "text";
        }
    }
}
