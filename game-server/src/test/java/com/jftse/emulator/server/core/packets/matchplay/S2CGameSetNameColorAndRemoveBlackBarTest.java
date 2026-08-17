package com.jftse.emulator.server.core.packets.matchplay;

import com.jftse.emulator.common.utilities.BitKit;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.matchplay.game.MatchplayGuardianGame;
import com.jftse.server.core.matchplay.battle.PlayerBattleState;
import com.jftse.server.core.protocol.PacketOperations;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    }

    private static PlayerBattleState player(short position, boolean dead, int hp) {
        PlayerBattleState state = new PlayerBattleState(position, position + 100L, 100, 10, 10, 10, 10);
        state.getCurrentHealth().set(hp);
        state.setDead(dead);
        return state;
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
