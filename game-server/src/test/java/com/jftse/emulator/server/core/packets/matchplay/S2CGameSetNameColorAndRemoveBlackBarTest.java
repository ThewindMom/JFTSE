package com.jftse.emulator.server.core.packets.matchplay;

import com.jftse.emulator.common.utilities.BitKit;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.matchplay.game.MatchplayGuardianGame;
import com.jftse.server.core.matchplay.battle.PlayerBattleState;
import com.jftse.server.core.protocol.PacketOperations;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class S2CGameSetNameColorAndRemoveBlackBarTest {

    @Test
    void livingListWritesCountAndPositionColorPairs() {
        PlayerBattleState slot0 = player((short) 0, false, 100);
        PlayerBattleState slot2 = player((short) 2, false, 80);

        S2CGameSetNameColorAndRemoveBlackBar packet =
                new S2CGameSetNameColorAndRemoveBlackBar(List.of(slot0, slot2));

        assertEquals(PacketOperations.S2CGameSetNameColorAndRemoveBlackBar.getValue(), packet.getPacketId());
        assertEquals(2, readCount(packet));
        assertEquals(0, readPosition(packet, 0));
        assertEquals(0, readNameColor(packet, 0));
        assertEquals(2, readPosition(packet, 1));
        assertEquals(2, readNameColor(packet, 1));
    }

    @Test
    void livingCtorFourAliveWritesEverySlotAndNameColorEqualsPosition() {
        List<PlayerBattleState> living = List.of(
                player((short) 0, false, 100),
                player((short) 1, false, 90),
                player((short) 2, false, 80),
                player((short) 3, false, 70));

        S2CGameSetNameColorAndRemoveBlackBar packet = new S2CGameSetNameColorAndRemoveBlackBar(living);

        assertOpcode(packet);
        assertEquals(4, readCount(packet));
        for (int i = 0; i < 4; i++) {
            assertEquals(i, readPosition(packet, i));
            assertEquals(i, readNameColor(packet, i));
        }
    }

    @Test
    void livingCtorOmitsDeadPositionTwoWhenBuiltFromLivingPlayers() {
        MatchplayGuardianGame game = gameWith(
                player((short) 0, false, 100),
                player((short) 1, false, 90),
                player((short) 2, true, 0),
                player((short) 3, false, 70));

        S2CGameSetNameColorAndRemoveBlackBar packet =
                new S2CGameSetNameColorAndRemoveBlackBar(game.livingPlayers());

        assertOpcode(packet);
        assertEquals(3, readCount(packet));
        assertEquals(List.of(0, 1, 3), positions(packet));
        assertFalse(positions(packet).contains(2));
    }

    @Test
    void livingCtorOmitsDeadFlagTrueEvenWithLeftoverHp() {
        MatchplayGuardianGame game = gameWith(
                player((short) 0, false, 100),
                player((short) 1, true, 40));

        S2CGameSetNameColorAndRemoveBlackBar packet =
                new S2CGameSetNameColorAndRemoveBlackBar(game.livingPlayers());

        assertEquals(1, readCount(packet));
        assertEquals(0, readPosition(packet, 0));
        assertFalse(positions(packet).contains(1));
    }

    @Test
    void livingCtorOmitsHpZeroEvenWhenDeadFlagFalse() {
        MatchplayGuardianGame game = gameWith(
                player((short) 0, false, 100),
                player((short) 2, false, 0));

        S2CGameSetNameColorAndRemoveBlackBar packet =
                new S2CGameSetNameColorAndRemoveBlackBar(game.livingPlayers());

        assertEquals(1, readCount(packet));
        assertEquals(0, readPosition(packet, 0));
        assertFalse(positions(packet).contains(2));
    }

    @Test
    void livingCtorOmitsGuardianSlotsEvenIfAlive() {
        MatchplayGuardianGame game = gameWith(
                player((short) 0, false, 100),
                player((short) 4, false, 8000),
                player((short) 10, false, 9000));

        S2CGameSetNameColorAndRemoveBlackBar packet =
                new S2CGameSetNameColorAndRemoveBlackBar(game.livingPlayers());

        assertEquals(1, readCount(packet));
        assertEquals(0, readPosition(packet, 0));
        assertFalse(positions(packet).contains(4));
        assertFalse(positions(packet).contains(10));
    }

    @Test
    void livingPlayersPayloadOmitsDeadAndEmptyHp() {
        MatchplayGuardianGame game = mock(MatchplayGuardianGame.class);
        ConcurrentLinkedDeque<PlayerBattleState> states = new ConcurrentLinkedDeque<>();
        states.add(player((short) 0, false, 100));
        states.add(player((short) 1, true, 0));
        states.add(player((short) 2, false, 0));
        states.add(player((short) 10, false, 200));
        when(game.getPlayerBattleStates()).thenReturn(states);
        when(game.livingPlayers()).thenCallRealMethod();

        S2CGameSetNameColorAndRemoveBlackBar packet =
                new S2CGameSetNameColorAndRemoveBlackBar(game.livingPlayers());

        assertEquals(1, readCount(packet));
        assertEquals(0, readPosition(packet, 0));
        assertEquals(0, readNameColor(packet, 0));
    }

    @Test
    void emptyOrNullLivingListWritesCountZero() {
        S2CGameSetNameColorAndRemoveBlackBar empty =
                new S2CGameSetNameColorAndRemoveBlackBar(Collections.emptyList());
        S2CGameSetNameColorAndRemoveBlackBar nil =
                new S2CGameSetNameColorAndRemoveBlackBar((List<PlayerBattleState>) null);

        assertOpcode(empty);
        assertOpcode(nil);
        assertEquals(0, readCount(empty));
        assertEquals(0, readCount(nil));
    }

    @Test
    void servePacketIsNotCountZeroWhenLivingPlayersExist() {
        MatchplayGuardianGame game = mock(MatchplayGuardianGame.class);
        ConcurrentLinkedDeque<PlayerBattleState> states = new ConcurrentLinkedDeque<>();
        states.add(player((short) 0, false, 50));
        states.add(player((short) 1, true, 0));
        when(game.getPlayerBattleStates()).thenReturn(states);
        when(game.livingPlayers()).thenCallRealMethod();

        S2CGameSetNameColorAndRemoveBlackBar servePacket =
                new S2CGameSetNameColorAndRemoveBlackBar(game.livingPlayers());

        assertTrue(readCount(servePacket) > 0);
        assertEquals(1, readCount(servePacket));
        assertOpcode(servePacket);
    }

    @Test
    void roomConstructorStillWritesAllActiveSlots() {
        Room room = new Room();
        RoomPlayer slot0 = new RoomPlayer(null);
        slot0.setPosition((short) 0);
        RoomPlayer slot1 = new RoomPlayer(null);
        slot1.setPosition((short) 1);
        RoomPlayer spectator = new RoomPlayer(null);
        spectator.setPosition((short) 4);
        room.getRoomPlayerList().add(slot0);
        room.getRoomPlayerList().add(slot1);
        room.getRoomPlayerList().add(spectator);

        S2CGameSetNameColorAndRemoveBlackBar packet = new S2CGameSetNameColorAndRemoveBlackBar(room);

        assertEquals(2, readCount(packet));
        assertEquals(0, readPosition(packet, 0));
        assertEquals(1, readPosition(packet, 1));
        assertOpcode(packet);
    }

    @Test
    void roomCtorWritesEveryPositionUnderFourIncludingSlotThatWouldBeDeadInCombat() {
        Room room = new Room();
        for (short pos = 0; pos < 4; pos++) {
            RoomPlayer rp = new RoomPlayer(null);
            rp.setPosition(pos);
            room.getRoomPlayerList().add(rp);
        }

        S2CGameSetNameColorAndRemoveBlackBar roomPacket = new S2CGameSetNameColorAndRemoveBlackBar(room);
        assertOpcode(roomPacket);
        assertEquals(4, readCount(roomPacket));
        assertEquals(List.of(0, 1, 2, 3), positions(roomPacket));

        MatchplayGuardianGame game = gameWith(
                player((short) 0, false, 100),
                player((short) 1, false, 90),
                player((short) 2, true, 0),
                player((short) 3, false, 70));
        S2CGameSetNameColorAndRemoveBlackBar livingPacket =
                new S2CGameSetNameColorAndRemoveBlackBar(game.livingPlayers());

        assertEquals(3, readCount(livingPacket));
        assertFalse(positions(livingPacket).contains(2));
        assertTrue(positions(roomPacket).contains(2));
    }

    @Test
    void livingAndRoomCtorsShareOpcode183A() {
        S2CGameSetNameColorAndRemoveBlackBar living =
                new S2CGameSetNameColorAndRemoveBlackBar(List.of(player((short) 0, false, 10)));
        S2CGameSetNameColorAndRemoveBlackBar room = new S2CGameSetNameColorAndRemoveBlackBar(new Room());

        assertEquals(0x183A, living.getPacketId());
        assertEquals(0x183A, room.getPacketId());
        assertEquals(PacketOperations.S2CGameSetNameColorAndRemoveBlackBar.getValue(), living.getPacketId());
        assertEquals(PacketOperations.S2CGameSetNameColorAndRemoveBlackBar.getValue(), room.getPacketId());
    }

    private static MatchplayGuardianGame gameWith(PlayerBattleState... players) {
        MatchplayGuardianGame game = mock(MatchplayGuardianGame.class);
        ConcurrentLinkedDeque<PlayerBattleState> states = new ConcurrentLinkedDeque<>();
        Collections.addAll(states, players);
        when(game.getPlayerBattleStates()).thenReturn(states);
        when(game.livingPlayers()).thenCallRealMethod();
        return game;
    }

    private static PlayerBattleState player(short position, boolean dead, int hp) {
        PlayerBattleState state = new PlayerBattleState(position, position + 100L, 100, 10, 10, 10, 10);
        state.getCurrentHealth().set(hp);
        state.setDead(dead);
        return state;
    }

    private static void assertOpcode(S2CGameSetNameColorAndRemoveBlackBar packet) {
        assertEquals(PacketOperations.S2CGameSetNameColorAndRemoveBlackBar.getValue(), packet.getPacketId());
        assertEquals(0x183A, packet.getPacketId());
    }

    private static List<Integer> positions(S2CGameSetNameColorAndRemoveBlackBar packet) {
        int count = readCount(packet);
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(readPosition(packet, i));
        }
        return result;
    }

    private static int readCount(S2CGameSetNameColorAndRemoveBlackBar packet) {
        return BitKit.bytesToChar(packet.getData(), 0);
    }

    private static int readPosition(S2CGameSetNameColorAndRemoveBlackBar packet, int index) {
        return BitKit.bytesToShort(packet.getData(), 2 + index * 4);
    }

    private static int readNameColor(S2CGameSetNameColorAndRemoveBlackBar packet, int index) {
        return BitKit.bytesToShort(packet.getData(), 4 + index * 4);
    }
}
