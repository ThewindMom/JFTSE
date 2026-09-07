package com.jftse.emulator.server.core.life.room;

public record RoomCreateResult(char result, Room room) {
    public static RoomCreateResult of(char result, Room room) {
        return new RoomCreateResult(result, room);
    }
}
