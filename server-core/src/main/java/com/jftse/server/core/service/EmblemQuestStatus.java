package com.jftse.server.core.service;

public enum EmblemQuestStatus {
    SUCCESS(0), NOT_FOUND(1), NOT_ALLOWED(2), LIMIT_REACHED(3), PREREQUISITE_MISSING(4),
    LEVEL_RESTRICTED(5), DUPLICATE(6), NOT_ACTIVE(7), INCOMPLETE(8), INVALID_EQUIPMENT(9);

    private final byte wireValue;

    EmblemQuestStatus(int wireValue) { this.wireValue = (byte) wireValue; }
    public byte wireValue() { return wireValue; }
}
