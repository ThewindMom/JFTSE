package com.jftse.emulator.server.core.life.room;

import com.jftse.emulator.server.core.client.GuildView;
import com.jftse.server.core.constants.GameMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClubMatchRulesTest {
    private static final GuildView RED_GUILD = guild(10, "Red Club");
    private static final GuildView BLUE_GUILD = guild(20, "Blue Club");
    private static final GuildView THIRD_GUILD = guild(30, "Third Club");

    @Test
    void clientRoomIdentityActivatesClubMatchOnlyOnClubListener() {
        assertTrue(ClubMatchRules.isClubMatch(7, 6, 0));
        assertTrue(ClubMatchRules.isClubMatch(7, 7, 1));

        assertFalse(ClubMatchRules.isClubMatch(1, 6, 0));
        assertFalse(ClubMatchRules.isClubMatch(7, 0, 0));
        assertFalse(ClubMatchRules.isClubMatch(7, 6, 1));
        assertFalse(ClubMatchRules.isClubMatch(7, 7, 0));
    }

    @Test
    void nativeQuickCreateRequestMapsModesToClientClubRoomTypes() {
        assertTrue(ClubMatchRules.isClubRoomRequest(7, 0));
        assertEquals(ClubMatchRules.CLUB_ROOM_TYPE,
                ClubMatchRules.roomTypeForWireMode(GameMode.BASIC));
        assertEquals(ClubMatchRules.CLUB_PET_ROOM_TYPE,
                ClubMatchRules.roomTypeForWireMode(GameMode.BATTLE));

        assertFalse(ClubMatchRules.isClubRoomRequest(1, 0));
        assertFalse(ClubMatchRules.isClubRoomRequest(7, 1));
    }

    @Test
    void onlyBasicWarfareIsImplementedInFirstSlice() {
        assertTrue(ClubMatchRules.isImplementedWireMode(GameMode.BASIC));
        assertFalse(ClubMatchRules.isImplementedWireMode(GameMode.BATTLE));
    }

    @Test
    void creationRejectsPetModeAndInvalidCapacityOnClubListener() {
        assertEquals(ClubMatchRules.SUCCESS,
                ClubMatchRules.validateCreation(7, 0, GameMode.BASIC, 2, RED_GUILD));
        assertEquals(ClubMatchRules.UNSUPPORTED_MODE,
                ClubMatchRules.validateCreation(7, 1, GameMode.BASIC, 2, RED_GUILD));
        assertEquals(ClubMatchRules.UNSUPPORTED_MODE,
                ClubMatchRules.validateCreation(7, 0, GameMode.BATTLE, 2, RED_GUILD));
        assertEquals(ClubMatchRules.UNSUPPORTED_CAPACITY,
                ClubMatchRules.validateCreation(7, 0, GameMode.BASIC, 3, RED_GUILD));
        assertEquals(ClubMatchRules.NOT_GUILD_MEMBER,
                ClubMatchRules.validateCreation(7, 0, GameMode.BASIC, 2, null));
    }

    @Test
    void ordinaryRoomCreationIsNotSubjectToClubRules() {
        assertEquals(ClubMatchRules.SUCCESS,
                ClubMatchRules.validateCreation(1, 1, GameMode.BATTLE, 8, null));
    }

    @Test
    void playerWithoutClubCannotJoin() {
        Room room = doublesRoom(player(1, RED_GUILD, 0));

        ClubMatchRules.JoinDecision decision = ClubMatchRules.decideJoin(room, null);

        assertEquals(ClubMatchRules.NOT_GUILD_MEMBER, decision.result());
        assertFalse(decision.allowed());
    }

    @Test
    void firstClubOwnsRedSideAndSecondClubOwnsBlueSide() {
        Room room = doublesRoom(player(1, RED_GUILD, 0));

        ClubMatchRules.JoinDecision redDecision = ClubMatchRules.decideJoin(room, RED_GUILD);
        ClubMatchRules.JoinDecision blueDecision = ClubMatchRules.decideJoin(room, BLUE_GUILD);

        assertEquals(2, redDecision.position());
        assertEquals(1, blueDecision.position());
    }

    @Test
    void existingBlueClubKeepsBlueSideWhenRedSideIsEmpty() {
        Room room = doublesRoom(player(1, BLUE_GUILD, 1));

        ClubMatchRules.JoinDecision decision = ClubMatchRules.decideJoin(room, BLUE_GUILD);

        assertEquals(3, decision.position());
    }

    @Test
    void thirdClubCannotJoin() {
        Room room = doublesRoom(
                player(1, RED_GUILD, 0),
                player(2, BLUE_GUILD, 1));

        ClubMatchRules.JoinDecision decision = ClubMatchRules.decideJoin(room, THIRD_GUILD);

        assertEquals(ClubMatchRules.GUILD_COUNT_LIMIT, decision.result());
        assertFalse(decision.allowed());
    }

    @Test
    void fullClubSideCannotOverflowIntoOpponentSide() {
        Room room = doublesRoom(
                player(1, RED_GUILD, 0),
                player(2, RED_GUILD, 2),
                player(3, BLUE_GUILD, 1));

        ClubMatchRules.JoinDecision decision = ClubMatchRules.decideJoin(room, RED_GUILD);

        assertEquals(ClubMatchRules.MY_GUILD_FULL, decision.result());
        assertFalse(decision.allowed());
    }

    @Test
    void validSinglesAndDoublesTeamsCanStart() {
        Room singles = singlesRoom(
                player(1, RED_GUILD, 0),
                player(2, BLUE_GUILD, 1));
        Room nativeBasicDoubles = doublesRoom(
                player(1, RED_GUILD, 0),
                player(2, BLUE_GUILD, 1));
        Room doubles = doublesRoom(
                player(1, RED_GUILD, 0),
                player(2, BLUE_GUILD, 1),
                player(3, RED_GUILD, 2),
                player(4, BLUE_GUILD, 3));

        assertTrue(ClubMatchRules.hasValidTeams(singles));
        assertTrue(ClubMatchRules.hasValidTeams(nativeBasicDoubles));
        assertTrue(ClubMatchRules.hasValidTeams(doubles));
    }

    @Test
    void mixedOrSameClubOpponentsCannotStart() {
        Room mixedSide = doublesRoom(
                player(1, RED_GUILD, 0),
                player(2, BLUE_GUILD, 1),
                player(3, BLUE_GUILD, 2),
                player(4, RED_GUILD, 3));
        Room sameClubOpponents = singlesRoom(
                player(1, RED_GUILD, 0),
                player(2, RED_GUILD, 1));
        Room unbalanced = doublesRoom(
                player(1, RED_GUILD, 0),
                player(2, BLUE_GUILD, 1),
                player(3, RED_GUILD, 2));

        assertFalse(ClubMatchRules.hasValidTeams(mixedSide));
        assertFalse(ClubMatchRules.hasValidTeams(sameClubOpponents));
        assertFalse(ClubMatchRules.hasValidTeams(unbalanced));
    }

    @Test
    void clubMemberCannotMoveToOpponentSide() {
        Room room = doublesRoom(
                player(1, RED_GUILD, 0),
                player(2, BLUE_GUILD, 1));
        RoomPlayer redPlayer = room.getRoomPlayerList().getFirst();

        assertTrue(ClubMatchRules.canMoveTo(room, redPlayer, (short) 2));
        assertFalse(ClubMatchRules.canMoveTo(room, redPlayer, (short) 3));
    }

    @Test
    void clubMemberCannotMoveOutsideActiveSlots() {
        Room room = doublesRoom(player(1, RED_GUILD, 0));
        RoomPlayer redPlayer = room.getRoomPlayerList().getFirst();

        assertFalse(ClubMatchRules.canMoveTo(room, redPlayer, (short) 4));
        assertFalse(ClubMatchRules.canMoveTo(room, redPlayer, (short) 9));
    }

    @Test
    void onlySinglesAndDoublesCapacitiesAreSupported() {
        assertTrue(ClubMatchRules.isSupportedCapacity(2));
        assertTrue(ClubMatchRules.isSupportedCapacity(4));
        assertFalse(ClubMatchRules.isSupportedCapacity(1));
        assertFalse(ClubMatchRules.isSupportedCapacity(3));
        assertFalse(ClubMatchRules.isSupportedCapacity(8));
    }

    private static Room singlesRoom(RoomPlayer... players) {
        Room room = room((byte) 2, players);
        room.getPositions().set(2, (short) 2);
        room.getPositions().set(3, (short) 2);
        return room;
    }

    private static Room doublesRoom(RoomPlayer... players) {
        return room((byte) 4, players);
    }

    private static Room room(byte capacity, RoomPlayer... players) {
        Room room = new Room();
        room.setGameServerType(ClubMatchRules.CLUB_SERVER_TYPE);
        room.setRoomType(ClubMatchRules.CLUB_ROOM_TYPE);
        room.setMode((byte) GameMode.BASIC);
        room.setPlayers(capacity);
        for (RoomPlayer player : players) {
            room.getRoomPlayerList().add(player);
            room.getPositions().set(player.getPosition(), (short) 1);
        }
        return room;
    }

    private static RoomPlayer player(long playerId, GuildView guild, int position) {
        RoomPlayer player = new RoomPlayer(null) {
            @Override
            public long getPlayerId() {
                return playerId;
            }

            @Override
            public GuildView getGuild() {
                return guild;
            }
        };
        player.setPosition((short) position);
        return player;
    }

    private static GuildView guild(long id, String name) {
        return new GuildView(id, name, 0, 0, 0, 0, 0, 0);
    }
}
