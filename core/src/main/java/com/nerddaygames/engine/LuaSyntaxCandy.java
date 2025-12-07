package com.nerddaygames.engine;

public class LuaSyntaxCandy {
    public static String process(String script) {
        if (script == null) return "";
        StringBuffer sb = new StringBuffer();
        String[] lines = script.replace("\r\n", "\n").split("\n");
        for (String line : lines) {
            sb.append(processLine(line)).append("\n");
        }
        return sb.toString();
    }

    private static String processLine(String line) {
        int commentPos = line.indexOf("--");
        String code = (commentPos >= 0) ? line.substring(0, commentPos) : line;
        String comment = (commentPos >= 0) ? line.substring(commentPos) : "";

        code = code.replace("!=", "~=");
        code = code.replaceAll("([\\w\\.\\[\\]]+)\\+\\+", "$1 = $1 + 1");
        code = code.replaceAll("([\\w\\.\\[\\]]+)\\-\\-", "$1 = $1 - 1");
        code = code.replaceAll(
            "([\\w\\.\\[\\]]+)\\s*(\\+|-|\\*|/|%)=\\s*(.+?)(?=\\s+(?:then|do|end|else|elseif|until)\\b|$)",
            "$1 = $1 $2 ($3)"
        );

        comment = comment.replace("//", "--");
        return code + comment;
    }
}
