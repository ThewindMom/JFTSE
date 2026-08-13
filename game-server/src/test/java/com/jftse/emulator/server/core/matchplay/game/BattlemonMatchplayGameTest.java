package com.jftse.emulator.server.core.matchplay.game;

import com.jftse.emulator.server.core.constants.ServeType;
import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.ServeInfo;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.matchplay.event.EventHandler;
import com.jftse.emulator.server.core.rabbit.MatchRallyStatsConsumer;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.server.core.matchplay.battle.PlayerBattleState;
import com.jftse.server.core.shared.packets.matchplay.CMSGPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BattlemonMatchplayGameTest {
    private Object previousGameManager;
    private Object previousServiceManager;

    @BeforeEach
    void setUpManagers() {
        previousGameManager = ReflectionTestUtils.getField(GameManager.class, "instance");
        previousServiceManager = ReflectionTestUtils.getField(ServiceManager.class, "instance");
        ReflectionTestUtils.setField(GameManager.class, "instance", mock(GameManager.class));
        ReflectionTestUtils.setField(ServiceManager.class, "instance", mock(ServiceManager.class));
    }

    @AfterEach
    void restoreManagers() {
        ReflectionTestUtils.setField(GameManager.class, "instance", previousGameManager);
        ReflectionTestUtils.setField(ServiceManager.class, "instance", previousServiceManager);
    }

    @Test
    void battlemonBasicUsesFourActorDoublesGeometryWithTwoRewardPlayers() {
        MatchplayBasicGame game = new MatchplayBasicGame((byte) 4, (byte) 2);

        assertFalse(game.isSingles());
        assertEquals(List.of(0, 1), game.getPlayerPositionsOrderedByPerformance());
        assertTrue(game.shouldPlayerServe(false, 2, 2));
        assertFalse(game.shouldPlayerServe(false, 2, 0));

        List<ServeInfo> serveInfos = serveInfos(game);
        serveInfos.get(2).setServeType(ServeType.ServeBall);
        game.setPlayerLocationsForDoubles(serveInfos);

        assertEquals(ServeType.ReceiveBall, serveInfos.get(3).getServeType());
        assertEquals(-125, serveInfos.get(2).getPlayerStartLocation().y);
        assertEquals(125, serveInfos.get(3).getPlayerStartLocation().y);
    }

    @Test
    void battlemonPointBackRequiresOnlyTheTwoOwnerEndpointsAndRetainsPetServeState() {
        MatchplayBasicGame game = new MatchplayBasicGame((byte) 4, (byte) 2);
        game.getServePlayerPosition().set(2);
        game.getReceiverPlayerPosition().set(3);

        game.setPoints((byte) 1, (byte) 0);
        game.setPointBackVote(0);
        assertFalse(game.isPointBackAvailable());

        game.setPointBackVote(1);
        assertTrue(game.isPointBackAvailable());
        assertEquals(2, game.getPreviousServePlayerPosition().get());
        assertEquals(3, game.getPreviousReceiverPlayerPosition().get());

        game.pointBack();
        assertEquals(0, game.getPointsRedTeam().get());
        assertEquals(0, game.getPointsBlueTeam().get());
    }

    @Test
    void battlemonBasicAcceptsNativePointSentinelOutsideActorPositions() {
        GameManager gameManager = GameManager.getInstance();
        when(gameManager.getEventHandler()).thenReturn(mock(EventHandler.class));
        when(gameManager.getMatchRallyStatsConsumer()).thenReturn(mock(MatchRallyStatsConsumer.class));

        MatchplayBasicGame game = new MatchplayBasicGame((byte) 4, (byte) 2);
        FTClient client = mock(FTClient.class);
        GameSession session = mock(GameSession.class);
        CMSGPoint point = mock(CMSGPoint.class);
        when(client.getActiveGameSession()).thenReturn(session);
        when(client.getActiveRoom()).thenReturn(mock(Room.class));
        when(session.getGameplayActorPositions()).thenReturn(List.of((short) 0, (short) 1, (short) 2, (short) 3));
        when(session.getCompletionHandled()).thenReturn(new AtomicBoolean(false));
        when(session.getClients()).thenReturn(new ConcurrentLinkedDeque<>());
        when(point.getPlayerPosition()).thenReturn((byte) 4);
        when(point.getPointsTeam()).thenReturn((byte) 1);

        game.getHandleable().onPoint(client, point);

        assertEquals(1, game.getPointsBlueTeam().get());
    }

    @Test
    void battlemonBattleCountsPetHealthForTeamDeathButRanksOnlyHumanRewards() {
        MatchplayBattleGame game = new MatchplayBattleGame((byte) 4, (byte) 2);
        PlayerBattleState humanRed = state((short) 0, 100L, 100);
        PlayerBattleState humanBlue = state((short) 1, 200L, 90);
        PlayerBattleState petRed = state((short) 2, 10L, 80);
        PlayerBattleState petBlue = state((short) 3, 20L, 70);
        game.getPlayerBattleStates().addAll(List.of(humanRed, humanBlue, petRed, petBlue));

        assertFalse(game.isSingles());
        assertEquals(List.of(0, 1), game.getPlayerPositionsOrderedByHighestHealth());

        humanRed.getCurrentHealth().set(0);
        assertFalse(game.isTeamDead(true));
        petRed.getCurrentHealth().set(0);
        assertTrue(game.isTeamDead(true));
        assertFalse(game.isTeamDead(false));
    }

    @Test
    void sparseOrdinaryBasicRewardsOnlyOccupiedPositions() {
        MatchplayBasicGame game = new MatchplayBasicGame((byte) 4, List.of(1, 3));

        assertEquals(Set.of(1, 3), Set.copyOf(game.getPlayerPositionsOrderedByPerformance()));

        game.setPoints((byte) 1, (byte) 0);
        game.setPointBackVote(1);
        assertFalse(game.isPointBackAvailable());
        game.setPointBackVote(3);
        assertTrue(game.isPointBackAvailable());
    }

    @Test
    void sparseOrdinaryBattleRewardsOnlyOccupiedPositions() {
        MatchplayBattleGame game = new MatchplayBattleGame((byte) 4, List.of(1, 3));
        game.getPlayerBattleStates().addAll(List.of(
                state((short) 0, 100L, 100),
                state((short) 1, 200L, 80),
                state((short) 2, 300L, 90),
                state((short) 3, 400L, 70)
        ));

        assertEquals(List.of(1, 3), game.getPlayerPositionsOrderedByHighestHealth());
    }

    private static List<ServeInfo> serveInfos(MatchplayBasicGame game) {
        List<ServeInfo> serveInfos = new ArrayList<>();
        for (short position = 0; position < 4; position++) {
            ServeInfo serveInfo = new ServeInfo();
            serveInfo.setPlayerPosition(position);
            serveInfo.setPlayerStartLocation(game.getPlayerLocationsOnMap().get(position));
            serveInfo.setServeType(ServeType.None);
            serveInfos.add(serveInfo);
        }
        return serveInfos;
    }

    private static PlayerBattleState state(short position, long id, int health) {
        return new PlayerBattleState(position, id, health, 10, 10, 10, 10);
    }
}
