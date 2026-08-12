package com.jftse.emulator.server.core.life.room;

import com.jftse.emulator.server.core.client.GuildView;
import com.jftse.emulator.server.core.constants.RoomType;
import com.jftse.emulator.server.core.constants.RoomPositionState;
import com.jftse.server.core.constants.GameMode;

import java.util.Comparator;
import java.util.List;

public final class ClubMatchRules {
    public static final byte CLUB_SERVER_TYPE = 7;
    public static final byte CLUB_ROOM_REQUEST_TYPE = RoomType.MATCH;
    public static final byte CLUB_ROOM_TYPE = 6;
    public static final byte CLUB_PET_ROOM_TYPE = 7;
    public static final int SUCCESS = 0;
    public static final int NOT_GUILD_MEMBER = -25;
    public static final int MY_GUILD_FULL = -26;
    public static final int GUILD_COUNT_LIMIT = -27;
    public static final int UNSUPPORTED_MODE = -9;
    public static final int UNSUPPORTED_CAPACITY = -10;

    private ClubMatchRules() {
    }

    public static boolean isClubMatch(Room room) {
        return room != null && isClubMatch(room.getGameServerType(), room.getRoomType(), room.getMode());
    }

    public static boolean isClubServerRoom(Room room) {
        return room != null
                && room.getGameServerType() == CLUB_SERVER_TYPE
                && isClubRoomType(room.getRoomType());
    }

    public static boolean isClubRoomRequest(int gameServerType, int roomType) {
        return gameServerType == CLUB_SERVER_TYPE && roomType == CLUB_ROOM_REQUEST_TYPE;
    }

    public static boolean isClubMatch(int gameServerType, int roomType, int mode) {
        return gameServerType == CLUB_SERVER_TYPE
                && ((roomType == CLUB_ROOM_TYPE && mode == GameMode.BASIC)
                || (roomType == CLUB_PET_ROOM_TYPE && mode == GameMode.BATTLE));
    }

    public static byte roomTypeForWireMode(int mode) {
        return switch (mode) {
            case GameMode.BASIC -> CLUB_ROOM_TYPE;
            case GameMode.BATTLE -> CLUB_PET_ROOM_TYPE;
            default -> throw new IllegalArgumentException("Unsupported Club Match mode: " + mode);
        };
    }

    private static boolean isClubRoomType(int roomType) {
        return roomType == CLUB_ROOM_TYPE || roomType == CLUB_PET_ROOM_TYPE;
    }

    public static boolean isSupportedWireMode(int mode) {
        return mode == GameMode.BASIC || mode == GameMode.BATTLE;
    }

    public static boolean isImplementedWireMode(int mode) {
        return mode == GameMode.BASIC;
    }

    public static int validateCreation(int gameServerType, int roomType, int mode, int players,
                                       GuildView guild) {
        if (gameServerType != CLUB_SERVER_TYPE) {
            return SUCCESS;
        }
        if (roomType != CLUB_ROOM_REQUEST_TYPE) {
            return UNSUPPORTED_MODE;
        }
        if (!isSupportedWireMode(mode) || !isImplementedWireMode(mode)) {
            return UNSUPPORTED_MODE;
        }
        if (guild == null) {
            return NOT_GUILD_MEMBER;
        }
        if (!isSupportedCapacity(players)) {
            return UNSUPPORTED_CAPACITY;
        }
        return SUCCESS;
    }

    public static boolean isSupportedCapacity(int players) {
        return players == 2 || players == 4;
    }

    public static JoinDecision decideJoin(Room room, GuildView guild) {
        if (guild == null) {
            return JoinDecision.rejected(NOT_GUILD_MEMBER);
        }
        if (!isSupportedCapacity(room.getPlayers())) {
            return JoinDecision.rejected(MY_GUILD_FULL);
        }

        Long redGuildId = guildIdForSide(room, 0);
        Long blueGuildId = guildIdForSide(room, 1);
        int side;
        if (redGuildId != null && redGuildId.equals(guild.id())) {
            side = 0;
        } else if (blueGuildId != null && blueGuildId.equals(guild.id())) {
            side = 1;
        } else if (redGuildId == null) {
            side = 0;
        } else if (blueGuildId == null) {
            side = 1;
        } else {
            return JoinDecision.rejected(GUILD_COUNT_LIMIT);
        }

        for (short position = (short) side; position < room.getPlayers(); position += 2) {
            if (room.getPositions().get(position) == RoomPositionState.Free) {
                return new JoinDecision(SUCCESS, position);
            }
        }
        return JoinDecision.rejected(MY_GUILD_FULL);
    }

    public static boolean canMoveTo(Room room, RoomPlayer player, short newPosition) {
        if (!isClubMatch(room)) {
            return true;
        }
        if (player != null && player.isGameMaster() && newPosition == 9) {
            return true;
        }
        if (!isSupportedCapacity(room.getPlayers()) || newPosition < 0 || newPosition >= room.getPlayers()) {
            return false;
        }
        if (player == null || player.getGuild() == null) {
            return false;
        }

        Long sideGuildId = guildIdForSide(room, newPosition % 2);
        return sideGuildId == null
                ? player.getPosition() % 2 == newPosition % 2
                : sideGuildId.equals(player.getGuild().id());
    }

    public static boolean hasValidTeams(Room room) {
        if (!isClubMatch(room)) {
            return true;
        }
        if (!isSupportedCapacity(room.getPlayers())) {
            return false;
        }

        List<RoomPlayer> activePlayers = activePlayers(room);
        if (activePlayers.size() != room.getPlayers()) {
            return false;
        }

        List<RoomPlayer> redTeam = activePlayers.stream()
                .filter(player -> player.getPosition() % 2 == 0)
                .toList();
        List<RoomPlayer> blueTeam = activePlayers.stream()
                .filter(player -> player.getPosition() % 2 == 1)
                .toList();
        int teamSize = room.getPlayers() / 2;
        if (redTeam.size() != teamSize || blueTeam.size() != teamSize) {
            return false;
        }

        Long redGuildId = singleGuildId(redTeam);
        Long blueGuildId = singleGuildId(blueTeam);
        return redGuildId != null && blueGuildId != null && !redGuildId.equals(blueGuildId);
    }

    public static Long guildIdForSide(Room room, int side) {
        return activePlayers(room).stream()
                .filter(player -> player.getPosition() % 2 == side)
                .map(RoomPlayer::getGuild)
                .filter(guild -> guild != null)
                .map(GuildView::id)
                .findFirst()
                .orElse(null);
    }

    private static Long singleGuildId(List<RoomPlayer> players) {
        if (players.stream().anyMatch(player -> player.getGuild() == null)) {
            return null;
        }
        List<Long> guildIds = players.stream()
                .map(player -> player.getGuild().id())
                .distinct()
                .toList();
        return guildIds.size() == 1 ? guildIds.getFirst() : null;
    }

    private static List<RoomPlayer> activePlayers(Room room) {
        int activePositionLimit = isSupportedCapacity(room.getPlayers()) ? room.getPlayers() : 0;
        return room.getRoomPlayerList().stream()
                .filter(player -> player.getPosition() >= 0 && player.getPosition() < activePositionLimit)
                .sorted(Comparator.comparingInt(RoomPlayer::getPosition))
                .toList();
    }

    public record JoinDecision(int result, short position) {
        public static JoinDecision rejected(int result) {
            return new JoinDecision(result, (short) -1);
        }

        public boolean allowed() {
            return result == SUCCESS;
        }
    }
}
