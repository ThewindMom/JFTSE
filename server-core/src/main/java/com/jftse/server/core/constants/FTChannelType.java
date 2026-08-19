package com.jftse.server.core.constants;

public enum FTChannelType {
    NONE(-1), CHAT(0), FREE(1), ROOKIE(2), PRO(3), MASTER(4), CLUB(7), TOURNAMENT(8);

    private final int value;

    FTChannelType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public String getName() {
        return toString();
    }

    public static FTChannelType fromValue(int value) {
        for (FTChannelType type : FTChannelType.values()) {
            if (type.getValue() == value) {
                return type;
            }
        }
        return null;
    }

    public static FTChannelType fromName(String name) {
        for (FTChannelType type : FTChannelType.values()) {
            if (type.getName().equals(name)) {
                return type;
            }
        }
        return null;
    }
}
