package com.nerddaygames.engine;

import java.util.Arrays;

public class Ram {
    private final byte[][] banks;
    private final int bankSize;

    private int activeBankIndex = 0;
    private byte[] activeMemory;

    public Ram(int bankSize, int numBanks) {
        this.bankSize = bankSize;
        this.banks = new byte[numBanks][bankSize];
        this.activeMemory = banks[0];
    }

    public void setBank(int index) {
        if (index < 0 || index >= banks.length) return;
        this.activeBankIndex = index;
        this.activeMemory = banks[index];
    }

    public int getBank() { return activeBankIndex; }

    // --- 8-BIT ACCESS (0-255) ---
    public int peek(int addr) {
        if (addr < 0 || addr >= bankSize) return 0;
        return activeMemory[addr] & 0xFF;
    }

    public void poke(int addr, int value) {
        if (addr < 0 || addr >= bankSize) return;
        activeMemory[addr] = (byte) value;
    }

    // --- 16-BIT ACCESS (0-65535) ---
    public int peek2(int addr) {
        int lo = peek(addr);
        int hi = peek(addr + 1);
        return (hi << 8) | lo;
    }

    public void poke2(int addr, int value) {
        poke(addr, value & 0xFF);
        poke(addr + 1, (value >> 8) & 0xFF);
    }

    // --- BULK OPS ---
    public void memcpy(int dest, int src, int len) {
        if (dest + len > bankSize) len = bankSize - dest;
        if (src + len > bankSize) len = bankSize - src;
        if (len <= 0) return;
        System.arraycopy(activeMemory, src, activeMemory, dest, len);
    }

    public void memset(int dest, int val, int len) {
        if (dest < 0) dest = 0;
        if (dest + len > bankSize) len = bankSize - dest;
        if (len <= 0) return;
        Arrays.fill(activeMemory, dest, dest + len, (byte)val);
    }
}
