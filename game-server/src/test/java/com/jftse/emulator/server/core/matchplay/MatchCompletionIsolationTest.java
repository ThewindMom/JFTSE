package com.jftse.emulator.server.core.matchplay;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.constants.PacketEventType;
import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.matchplay.event.EventHandler;
import com.jftse.emulator.server.core.matchplay.event.PacketEvent;
import com.jftse.emulator.server.core.matchplay.game.MatchplayBattleGame;
import com.jftse.emulator.server.core.matchplay.handler.MatchplayBattleModeHandler;
import com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayBackToRoom;
import com.jftse.emulator.server.core.service.MatchResultService;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.service.GameLogService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MatchCompletionIsolationTest {
    @Test
    void delayedBackToRoomCannotFollowClientThroughAnotherCompletedMatch() {
        FTClient client = new FTClient();
        FTConnection connection = mock(FTConnection.class);
        client.setConnection(connection);
        client.setActiveGameSession(7);
        PacketEvent event = new PacketEvent(connection, client, new S2CMatchplayBackToRoom(),
                PacketEventType.FIRE_DELAYED, 0, 12000);
        client.setActiveGameSession(null);
        client.setActiveGameSession(8);
        client.setActiveGameSession(null);
        event.fire();
        verify(connection, never()).sendTCP(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"basic", "battle", "guardian"})
    void replacementDuringResultTransactionIsNotClearedByOldCompletion(String mode) throws Exception {
        Object previousManager = ReflectionTestUtils.getField(GameManager.class, "instance");
        Object previousServices = ReflectionTestUtils.getField(ServiceManager.class, "instance");
        Object previousSessions = ReflectionTestUtils.getField(GameSessionManager.class, "instance");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            GameManager manager = mock(GameManager.class);
            ServiceManager services = mock(ServiceManager.class);
            EventHandler events = new EventHandler();
            events.init();
            when(manager.getEventHandler()).thenReturn(events);
            when(services.getGameLogService()).thenReturn(mock(GameLogService.class));
            MatchResultService results = mock(MatchResultService.class);
            when(services.getMatchResultService()).thenReturn(results);
            when(results.executeOnce(any(), any())).thenAnswer(invocation -> {
                entered.countDown();
                assertTrue(release.await(5, TimeUnit.SECONDS));
                invocation.<Runnable>getArgument(1).run();
                return true;
            });
            ReflectionTestUtils.setField(GameManager.class, "instance", manager);
            ReflectionTestUtils.setField(ServiceManager.class, "instance", services);
            GameSessionManager sessions = new GameSessionManager();
            sessions.init();
            GameSession old = new GameSession(true);
            MatchplayGame game;
            MatchplayHandleable handler;
            if (mode.equals("guardian")) {
                var guardianGame = mock(com.jftse.emulator.server.core.matchplay.game.MatchplayGuardianGame.class);
                when(guardianGame.getBossBattleActive()).thenReturn(new AtomicBoolean());
                when(guardianGame.getIsHardMode()).thenReturn(new AtomicBoolean());
                when(guardianGame.getIsRandomGuardiansMode()).thenReturn(new AtomicBoolean());
                when(guardianGame.getPlayerBattleStates()).thenReturn(new java.util.concurrent.ConcurrentLinkedDeque<>());
                when(guardianGame.getGuardianBattleStates()).thenReturn(new java.util.concurrent.ConcurrentLinkedDeque<>());
                when(guardianGame.getMap()).thenReturn(mock(com.jftse.entities.database.model.map.SMaps.class));
                game = guardianGame;
                handler = new com.jftse.emulator.server.core.matchplay.handler.MatchplayGuardianModeHandler(guardianGame);
            } else if (mode.equals("basic")) {
                var basicGame = mock(com.jftse.emulator.server.core.matchplay.game.MatchplayBasicGame.class);
                when(basicGame.getSetsRedTeam()).thenReturn(new java.util.concurrent.atomic.AtomicInteger(2));
                when(basicGame.getSetsBlueTeam()).thenReturn(new java.util.concurrent.atomic.AtomicInteger());
                game = basicGame;
                handler = new com.jftse.emulator.server.core.matchplay.handler.MatchplayBasicModeHandler(basicGame);
            } else {
                var battleGame = mock(MatchplayBattleGame.class);
                game = battleGame;
                handler = new MatchplayBattleModeHandler(battleGame);
            }
            when(game.getFinished()).thenReturn(new AtomicBoolean());
            when(game.getMatchRewards()).thenReturn(new MatchplayReward());
            old.setMatchplayGame(game);
            sessions.getGameSessionList().put(7, old);
            sessions.getGameSessionList().put(8, new GameSession());
            FTClient client = new FTClient();
            client.refreshPlayer(mock(FTPlayer.class));
            Room room = mock(Room.class);
            when(room.getRoomPlayerList()).thenReturn(new java.util.concurrent.ConcurrentLinkedDeque<>());
            RoomPlayer originalSeat = mock(RoomPlayer.class);
            when(originalSeat.getPosition()).thenReturn((short) 4);
            room.getRoomPlayerList().add(originalSeat);
            client.setActiveRoom(room);
            client.setRoomPlayer(originalSeat);
            client.setConnection(mock(FTConnection.class));
            client.setActiveGameSession(7);
            old.getClients().add(client);
            FTClient loadingEndpoint = mock(FTClient.class);
            when(loadingEndpoint.getActiveGameSession()).thenReturn(old);
            old.getClients().add(loadingEndpoint);
            var task = executor.submit(() -> handler.onEnd(client));
            try {
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                handler.onEnd(client);
                verify(results).executeOnce(any(), any());
                client.setActiveGameSession(8);
                RoomPlayer replacement = mock(RoomPlayer.class);
                when(replacement.getPosition()).thenReturn((short) 4);
                client.setRoomPlayer(replacement);
                room.getRoomPlayerList().clear();
                room.getRoomPlayerList().add(replacement);
                release.countDown();
                task.get(5, TimeUnit.SECONDS);
                assertEquals(8, client.getGameSessionId());
                verify(replacement, never()).setReady(false);
                verify(client.getConnection(), never()).sendTCP(any());
                assertTrue(events.getFireableDeque().stream().noneMatch(PacketEvent.class::isInstance));
            } finally {
                release.countDown();
            }
        } finally {
            ReflectionTestUtils.setField(GameManager.class, "instance", previousManager);
            ReflectionTestUtils.setField(ServiceManager.class, "instance", previousServices);
            ReflectionTestUtils.setField(GameSessionManager.class, "instance", previousSessions);
        }
    }
}
