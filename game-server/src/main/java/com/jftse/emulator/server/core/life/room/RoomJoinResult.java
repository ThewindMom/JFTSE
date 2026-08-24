package com.jftse.emulator.server.core.life.room;

public record RoomJoinResult(char result, Room room, RoomPlayer player) {
    public static RoomJoinResult of(char result, Room room, RoomPlayer player) {
        return new RoomJoinResult(result, room, player);
    }
}
