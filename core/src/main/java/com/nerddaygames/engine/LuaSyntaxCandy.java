package com.nerddaygames.engine;

public class LuaSyntaxCandy {

    public static String process(String script) {
        if (script == null) return "";
        StringBuffer sb = new StringBuffer();
        // Handle Windows/Mac line endings correctly
        String[] lines = script.replace("\r\n", "\n").split("\n");

        for (String line : lines) {
            sb.append(processLine(line)).append("\n");
        }
        return sb.toString();
    }

    private static String processLine(String line) {
        // 1. Comments: // -> --
        if (line.contains("//")) line = line.replace("//", "--");

        // 2. Not Equals: != -> ~=
        if (line.contains("!=")) line = line.replace("!=", "~=");

        // 3. Increment: x++ -> x = x + 1
        if (line.contains("++")) {
            line = line.replaceAll("([\\w\\.]+)\\+\\+", "$1 = $1 + 1");
        }

        // 4. Decrement: x-- -> x = x - 1
        if (line.contains("--")) {
            // Only replace if it looks like a variable (preceded by word char)
            line = line.replaceAll("(?<=\\w)\\-\\-", " = $0 - 1");
            // Cleanup the replacement artifact
            line = line.replaceAll("([\\w\\.]+)\\-\\-", "$1 = $1 - 1");
        }

        // 5. Compound Assignment: x += 1 -> x = x + (1)
        // SMART REGEX: Stops capturing the value when it sees 'end', 'else', or comment
        if (line.matches(".*(\\+|-|\\*|/|%)=.*")) {
            line = line.replaceAll(
                "([\\w\\.]+)\\s*(\\+|-|\\*|/|%)=\\s*(.*?)(?=\\s+(?:end|else|elseif)\\b|--|$)",
                "$1 = $1 $2 ($3)"
            );
        }

        return line;
    }
}
