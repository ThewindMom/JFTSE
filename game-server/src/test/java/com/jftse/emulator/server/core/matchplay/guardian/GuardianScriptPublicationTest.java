package com.jftse.emulator.server.core.matchplay.guardian;

import com.jftse.emulator.common.scripting.ScriptFile;
import com.jftse.emulator.common.scripting.ScriptManagerV2;
import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.matchplay.event.EventHandler;
import com.jftse.emulator.server.core.matchplay.event.RunnableEvent;
import com.jftse.emulator.server.core.matchplay.game.MatchplayGuardianGame;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.battle.Skill;
import com.jftse.server.core.matchplay.battle.PlayerBattleState;
import com.jftse.server.core.protocol.IPacket;
import com.jftse.server.core.service.SkillService;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GuardianScriptPublicationTest {
    @ParameterizedTest
    @ValueSource(strings = {"live", "replacement", "cancelled", "finished"})
    void actualAbyssalScriptPublishesLatestHealthOnlyInOwningMatch(String state) throws Exception {
        Object previousGame = ReflectionTestUtils.getField(GameManager.class, "instance");
        Object previousScripts = ReflectionTestUtils.getField(ScriptManagerV2.class, "instance");
        Object previousServices = ReflectionTestUtils.getField(ServiceManager.class, "instance");
        Object previousThreads = ReflectionTestUtils.getField(com.jftse.server.core.thread.ThreadManager.class, "instance");
        ReflectionTestUtils.setField(com.jftse.server.core.thread.ThreadManager.class, "instance",
                mock(com.jftse.server.core.thread.ThreadManager.class));
        EventHandler events = new EventHandler();
        events.init();
        GameManager manager = mock(GameManager.class);
        when(manager.getEventHandler()).thenReturn(events);
        ReflectionTestUtils.setField(GameManager.class, "instance", manager);
        ScriptManagerV2 scripts = null;
        ScriptFile file = null;
        try {
            MatchplayGuardianGame game = mock(MatchplayGuardianGame.class);
            when(game.getFinished()).thenReturn(new AtomicBoolean());
            AdvancedGuardianState boss = guardian(10, true, 100);
            AdvancedGuardianState minion = guardian(11, false, 0);
            when(game.getGuardianBattleStates()).thenReturn(new ConcurrentLinkedDeque<>(List.of(boss, minion)));
            when(game.getPlayerBattleStates()).thenReturn(new ConcurrentLinkedDeque<>(List.of(
                    new PlayerBattleState((short) 0, 1, 100, 0, 0, 0, 0))));
            GameSession session = new GameSession();
            session.setMatchplayGame(game);
            FTClient client = mock(FTClient.class);
            FTConnection connection = mock(FTConnection.class);
            when(connection.getClient()).thenReturn(client);
            when(client.getActiveGameSession()).thenReturn(session);
            ServiceManager services = mock(ServiceManager.class);
            ReflectionTestUtils.setField(ServiceManager.class, "instance", services);
            SkillService skills = mock(SkillService.class);
            when(services.getSkillService()).thenReturn(skills);
            Skill rebirth = new Skill();
            rebirth.setId(29L);
            when(skills.findSkillById(29L)).thenReturn(rebirth);
            file = new ScriptFile("abyssal-test", new File(getClass().getClassLoader()
                    .getResource("scripts/guardian-phase/10/4_abyssal_reckoning.js").toURI()), "GUARDIAN-PHASE", "10");
            scripts = new ScriptManagerV2(new ArrayList<>(List.of(file)));
            BossBattlePhaseable phase = scripts.getInterfaceByImplementingObject(file, "phase", BossBattlePhaseable.class,
                    Map.of("game", game, "gameManager", manager, "serviceManager", services,
                            "eventHandler", events, "log", LogManager.getLogger(getClass())));
            assertNotNull(phase);
            PhaseScript script = new PhaseScript(phase, file, scripts);
            PhaseManager phaseManager = new PhaseManager(List.of(script), scripts);
            when(game.getPhaseManager()).thenReturn(phaseManager);
            when(game.isAdvancedBossGuardianMode()).thenReturn(true);
            when(game.getStageChangingToBoss()).thenReturn(new AtomicBoolean(true));
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
                new com.jftse.emulator.server.core.task.GuardianServeTask(connection).run();
                phaseManager.update(0);
            });
            assertEquals(100, minion.getCurrentHealth().get());
            assertEquals(1, session.getFireables().size());
            RunnableEvent event = (RunnableEvent) events.poll();
            assertNotNull(event);
            minion.getCurrentHealth().set(77);
            switch (state) {
                case "replacement" -> when(client.getActiveGameSession()).thenReturn(new GameSession());
                case "cancelled" -> event.setCancelled(true);
                case "finished" -> game.getFinished().set(true);
            }
            List<Short> published = new ArrayList<>();
            doAnswer(invocation -> {
                assertTrue(Thread.holdsLock(game));
                IPacket packet = invocation.getArgument(0);
                if (packet.getPacketId() == 0x184E) {
                    published.add(ByteBuffer.wrap(packet.toBytes()).order(ByteOrder.LITTLE_ENDIAN).getShort(10));
                }
                return null;
            }).when(manager).sendPacketToAllClientsInSameGameSession(any(), any());
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> event.getRunnable().run());
            assertEquals(state.equals("live") ? List.of((short) 77) : List.of(), published);
        } finally {
            if (scripts != null) scripts.shutdownAllExecutors();
            if (file != null && file.getContext() != null) file.getContext().close(true);
            ReflectionTestUtils.setField(GameManager.class, "instance", previousGame);
            ReflectionTestUtils.setField(ScriptManagerV2.class, "instance", previousScripts);
            ReflectionTestUtils.setField(ServiceManager.class, "instance", previousServices);
            ReflectionTestUtils.setField(com.jftse.server.core.thread.ThreadManager.class, "instance", previousThreads);
        }
    }

    private static AdvancedGuardianState guardian(int position, boolean boss, int hp) {
        AdvancedGuardianState guardian = mock(AdvancedGuardianState.class);
        when(guardian.getPosition()).thenReturn(position);
        when(guardian.isBoss()).thenReturn(boss);
        when(guardian.getCurrentHealth()).thenReturn(new AtomicInteger(hp));
        when(guardian.getMaxHealth()).thenReturn(100);
        when(guardian.getSkills()).thenReturn(new ArrayList<>());
        return guardian;
    }
}
