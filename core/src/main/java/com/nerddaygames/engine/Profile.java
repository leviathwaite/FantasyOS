package com.nerddaygames.engine;

public class Profile {
    // HOST RESOLUTION (The Workstation - Modern & Crisp)
    public int width;
    public int height;

    // GUEST RESOLUTION (The Fantasy Console - Retro & Pixelated)
    public int gameWidth;
    public int gameHeight;

    // SPECS
    public int memorySize;
    public int memoryBanks;
    public String title;
    public String compatibilityMode = "nerdos";

    public static Profile createNerdOS() {
        Profile p = new Profile();
        p.title = "NerdOS Workstation";

        // 1080p Desktop
        p.width = 1920;
        p.height = 1080;

        // 240p Game (Scales nicely to 1080p x4.5, or windowed x4)
        p.gameWidth = 240;
        p.gameHeight = 136;

        p.memorySize = 65536; // 64KB RAM
        p.memoryBanks = 8;
        return p;
    }
}
