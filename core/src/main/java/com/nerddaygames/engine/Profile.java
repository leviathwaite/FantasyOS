package com.nerddaygames.engine;

public class Profile {
    // OS/Editor screen resolution
    public int width = 1920;
    public int height = 1080;

    // Game screen resolution
    public int gameWidth = 240;
    public int gameHeight = 136;

    // Memory
    public int memorySize = 65536;
    public int memoryBanks = 8;

    public String title = "FantasyOS";

    public static Profile createNerdOS() {
        Profile p = new Profile();
        p.title = "NerdOS Workstation";
        p.width = 1920;
        p.height = 1080;
        p.gameWidth = 240;
        p.gameHeight = 136;
        p.memorySize = 65536;
        p.memoryBanks = 8;
        return p;
    }
}
