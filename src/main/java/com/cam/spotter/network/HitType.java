package com.cam.spotter.network;

public enum HitType {
    MISS, BLOCK, ENTITY;

    private static final HitType[] VALUES = values();

    public static HitType fromByte(byte b) {
        return VALUES[b & 0xFF];
    }

    public byte toByte() {
        return (byte) ordinal();
    }
}
