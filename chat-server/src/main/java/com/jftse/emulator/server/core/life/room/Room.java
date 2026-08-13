package com.jftse.emulator.server.core.life.room;

import com.jftse.emulator.server.core.constants.RoomStatus;
import lombok.Getter;
import lombok.Setter;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Getter
@Setter
public class Room {
    public Room() {
        bannedPlayers = new ConcurrentLinkedDeque<>();
        roomPlayerList = new ConcurrentLinkedDeque<>();
        personalBoardMessages = new ConcurrentHashMap<>();
        status = RoomStatus.NotRunning;
    }

    private short roomId;
    private String roomName;
    private byte roomType;
    private byte allowBattlemon;
    private byte mode;
    private byte rule;
    private byte players;
    private boolean isPrivate;
    private boolean skillFree;
    private boolean quickSlot;
    private byte level;
    private byte levelRange;
    private char bettingType;
    private int bettingAmount;
    private byte map;
    private int ball;
    private String password;
    private ConcurrentLinkedDeque<Long> bannedPlayers;
    private ConcurrentLinkedDeque<RoomPlayer> roomPlayerList;
    private ConcurrentHashMap<Long, String> personalBoardMessages;
    private int status;

    // Guardian
    private boolean isHardMode; // Guardians are very strong
    private boolean isArcade; // You have to play against all guardians there are
    private boolean isRandomGuardians; // Always random guardians are spawned.

    public Map<Short, String> getPersonalBoardMessagesByPosition() {
        Map<Short, String> result = new LinkedHashMap<>();
        roomPlayerList.stream()
                .sorted(Comparator.comparingInt(RoomPlayer::getPosition))
                .forEach(roomPlayer -> {
                    String message = personalBoardMessages.get(roomPlayer.getPlayerId());
                    if (message != null)
                        result.put(roomPlayer.getPosition(), message);
                });
        return result;
    }
}
