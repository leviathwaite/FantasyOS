package com.nerddaygames.engine;

import java.util.Arrays;

public class Ram {

    // --- MEMORY MAP CONSTANTS ---
    public static final int MEMORY_SIZE = 0x10000;    // 64KB total

    public static final int SPRITE_BASE = 0x0000;
    public static final int SPRITE_SIZE = 0x2000;     // 8KB

    public static final int MAP_BASE = 0x2000;
    public static final int MAP_SIZE = 0x1000;        // 4KB

    // Map Dimensions (128x64 tiles)
    public static final int MAP_WIDTH = 128;
    public static final int MAP_HEIGHT = 64;

    public static final int GFX_FLAGS_BASE = 0x3000;
    public static final int GFX_FLAGS_SIZE = 0x0200;  // 512 bytes

    public static final int MUSIC_BASE = 0x3200;
    public static final int MUSIC_SIZE = 0x0040;      // 64 bytes

    public static final int USER_DATA_BASE = 0x4300;
    public static final int USER_DATA_SIZE = 0x1B00;  // 6.7KB

    public static final int CART_DATA_BASE = 0x5E00;
    public static final int CART_DATA_SIZE = 0x0200;  // 512 bytes

    public static final int SCREEN_BUFFER_BASE = 0x6000;
    public static final int SCREEN_BUFFER_SIZE = 0x2000; // 8KB

    // --- MEMORY STORAGE ---
    public final byte[] memory = new byte[MEMORY_SIZE];
    private int activeBank = 0;

    /**
     * Read a single byte (0-255) from an address.
     */
    public int peek(int addr) {
        if (addr < 0 || addr >= memory.length) return 0;
        return memory[addr] & 0xFF; // Convert signed byte to unsigned int
    }

    /**
     * Write a single byte (0-255) to an address.
     */
    public void poke(int addr, int val) {
        if (addr < 0 || addr >= memory.length) return;
        memory[addr] = (byte) (val & 0xFF);
    }

    /**
     * Read a 16-bit integer (Little Endian).
     */
    public int peek2(int addr) {
        if (addr < 0 || addr >= memory.length - 1) return 0;
        int low = memory[addr] & 0xFF;
        int high = memory[addr + 1] & 0xFF;
        return low | (high << 8);
    }

    /**
     * Write a 16-bit integer (Little Endian).
     */
    public void poke2(int addr, int val) {
        if (addr < 0 || addr >= memory.length - 1) return;
        memory[addr] = (byte) (val & 0xFF);
        memory[addr + 1] = (byte) ((val >> 8) & 0xFF);
    }

    /**
     * Copy memory block.
     */
    public void memcpy(int dest, int src, int len) {
        if (len <= 0) return;
        if (src < 0 || src + len > memory.length) return;
        if (dest < 0 || dest + len > memory.length) return;
        System.arraycopy(memory, src, memory, dest, len);
    }

    /**
     * Set memory block.
     */
    public void memset(int dest, int val, int len) {
        if (len <= 0) return;
        if (dest < 0 || dest + len > memory.length) return;
        Arrays.fill(memory, dest, dest + len, (byte) val);
    }

    public void setBank(int bank) { this.activeBank = bank; }

    public byte[] getRawMemory() { return memory; }
}
