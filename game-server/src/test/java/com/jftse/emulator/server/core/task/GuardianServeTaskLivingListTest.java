package com.jftse.emulator.server.core.task;

import com.jftse.emulator.common.utilities.BitKit;
import com.jftse.emulator.server.core.matchplay.game.MatchplayGuardianGame;
import com.jftse.emulator.server.core.packets.matchplay.S2CGameSetNameColorAndRemoveBlackBar;
import com.jftse.server.core.matchplay.battle.PlayerBattleState;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GuardianServeTaskLivingListTest {

    @Test
    void serveTaskSourceUsesLivingPlayersAndDoesNotConstructPacketWithNull() throws IOException {
        String source = Files.readString(guardianServeTaskSource());

        assertTrue(source.contains("new S2CGameSetNameColorAndRemoveBlackBar(game.livingPlayers())"),
                "GuardianServeTask must build 0x183A from livingPlayers()");
        assertFalse(source.contains("new S2CGameSetNameColorAndRemoveBlackBar(null)"),
                "old count-0 serve contract must stay gone");
    }

    @Test
    void livingPlayersIsTheListServeWouldSendAndIsNotCountZeroWhenSomeoneLives() {
        MatchplayGuardianGame game = mock(MatchplayGuardianGame.class);
        ConcurrentLinkedDeque<PlayerBattleState> states = new ConcurrentLinkedDeque<>();
        states.add(player((short) 0, false, 60));
        states.add(player((short) 1, true, 0));
        states.add(player((short) 2, false, 40));
        states.add(player((short) 10, false, 8000));
        when(game.getPlayerBattleStates()).thenReturn(states);
        when(game.livingPlayers()).thenCallRealMethod();

        List<PlayerBattleState> serveList = game.livingPlayers();
        S2CGameSetNameColorAndRemoveBlackBar packet = new S2CGameSetNameColorAndRemoveBlackBar(serveList);

        assertEquals(2, serveList.size());
        assertEquals(0, serveList.get(0).getPosition());
        assertEquals(2, serveList.get(1).getPosition());
        assertTrue(BitKit.bytesToChar(packet.getData(), 0) > 0);
        assertEquals(2, BitKit.bytesToChar(packet.getData(), 0));
    }

    private static Path guardianServeTaskSource() {
        Path cwd = Path.of("").toAbsolutePath();
        Path inModule = cwd.resolve("src/main/java/com/jftse/emulator/server/core/task/GuardianServeTask.java");
        if (Files.exists(inModule)) {
            return inModule;
        }
        return cwd.resolve("game-server/src/main/java/com/jftse/emulator/server/core/task/GuardianServeTask.java");
    }

    private static PlayerBattleState player(short position, boolean dead, int hp) {
        PlayerBattleState state = new PlayerBattleState(position, position + 500L, 100, 10, 10, 10, 10);
        state.getCurrentHealth().set(hp);
        state.setDead(dead);
        return state;
    }
}
