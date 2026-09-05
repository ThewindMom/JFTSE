package com.jftse.emulator.server.net;

import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.entities.database.model.ServerType;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.net.Connection;
import com.jftse.server.core.protocol.IPacket;
import com.jftse.server.core.protocol.PacketRegistry;
import com.jftse.server.core.shared.MetricsService;
import com.jftse.server.core.shared.packets.matchplay.CMSGPlayerUseSkill;
import com.jftse.server.core.shared.packets.matchplay.CMSGSpellHitsTarget;
import com.jftse.server.core.thread.ThreadManager;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CombatPacketOrderingTest {
    @Test
    @SuppressWarnings("unchecked")
    void receivedCastAuthorizesHitBeforeFollowingHitIsHandled() throws Exception {
        Object previousServices = ReflectionTestUtils.getField(ServiceManager.class, "instance");
        Object previousThreads = ReflectionTestUtils.getField(ThreadManager.class, "instance");
        Map<Integer, PacketHandler<? extends Connection<?>, ? extends IPacket>> handlers =
                (Map<Integer, PacketHandler<? extends Connection<?>, ? extends IPacket>>)
                        ReflectionTestUtils.getField(PacketRegistry.class, "HANDLERS");
        Map<Integer, PacketHandler<? extends Connection<?>, ? extends IPacket>> previousHandlers = new HashMap<>(handlers);
        try {
            ServiceManager services = mock(ServiceManager.class);
            when(services.getMetricsService()).thenReturn(mock(MetricsService.class));
            ReflectionTestUtils.setField(ServiceManager.class, "instance", services);
            ThreadManager threads = mock(ThreadManager.class);
            List<Runnable> deferred = new ArrayList<>();
            doAnswer(invocation -> {
                deferred.add(invocation.getArgument(0));
                return null;
            }).when(threads).newTask(any(Runnable.class));
            ReflectionTestUtils.setField(ThreadManager.class, "instance", threads);

            GameSession session = new GameSession(true);
            List<Boolean> hitResults = new ArrayList<>();
            PacketHandler<FTConnection, CMSGPlayerUseSkill> cast = (connection, packet) ->
                    session.authorizeSkillHits(0, 1, 6, 1_000L);
            PacketHandler<FTConnection, CMSGSpellHitsTarget> hit = (connection, packet) ->
                    hitResults.add(session.tryConsumeSkillHit(0, 1, 6, 2_000L));
            handlers.put(CMSGPlayerUseSkill.PACKET_ID, cast);
            handlers.put(CMSGSpellHitsTarget.PACKET_ID, hit);

            FTConnection connection = new FTConnection(0, 0, ServerType.GAME_SERVER);
            connection.queuePacket(CMSGPlayerUseSkill.builder().build());
            connection.queuePacket(CMSGSpellHitsTarget.builder().build());
            connection.queuePacket(CMSGSpellHitsTarget.builder().build());
            connection.update(0);
            assertEquals(List.of(), hitResults);

            FTConnection otherConnection = new FTConnection(0, 0, ServerType.GAME_SERVER);
            otherConnection.queuePacket(CMSGSpellHitsTarget.builder().build());
            otherConnection.update(0);
            assertEquals(List.of(false), hitResults);
            hitResults.clear();

            deferred.forEach(Runnable::run);
            connection.update(0);

            assertEquals(List.of(true, false), hitResults);

            deferred.clear();
            hitResults.clear();
            java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
            PacketHandler<FTConnection, CMSGPlayerUseSkill> blockedCast = (c, p) -> {
                entered.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                session.authorizeSkillHits(0, 1, 6, 1_000L);
            };
            handlers.put(CMSGPlayerUseSkill.PACKET_ID, blockedCast);
            connection.queuePacket(CMSGPlayerUseSkill.builder().build());
            connection.queuePacket(CMSGSpellHitsTarget.builder().build());
            java.util.stream.IntStream.range(0, 100).parallel().forEach(i -> connection.update(0));
            assertEquals(1, deferred.size());
            Thread worker = Thread.ofVirtual().start(deferred.getFirst());
            try {
                assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
                org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(2), () -> {
                    connection.update(0);
                    otherConnection.queuePacket(CMSGSpellHitsTarget.builder().build());
                    otherConnection.update(0);
                });
                assertEquals(List.of(false), hitResults);
            } finally {
                release.countDown();
                worker.join(2_000);
            }
            assertFalse(worker.isAlive());
            connection.update(0);
            assertEquals(List.of(false, true), hitResults);

            deferred.clear();
            hitResults.clear();
            PacketHandler<FTConnection, CMSGPlayerUseSkill> failedCast = (c, p) -> {
                throw new IllegalStateException("simulated persistence failure");
            };
            handlers.put(CMSGPlayerUseSkill.PACKET_ID, failedCast);
            connection.queuePacket(CMSGPlayerUseSkill.builder().build());
            connection.queuePacket(CMSGSpellHitsTarget.builder().build());
            connection.update(0);
            deferred.forEach(Runnable::run);
            assertFalse(connection.getCastInFlight().get());
            connection.update(0);
            assertEquals(List.of(false), hitResults);

            deferred.clear();
            handlers.put(CMSGPlayerUseSkill.PACKET_ID, cast);
            connection.queuePacket(CMSGPlayerUseSkill.builder().build());
            connection.update(0);
            connection.getIsClosingConnection().set(true);
            deferred.forEach(Runnable::run);
            assertFalse(connection.getCastInFlight().get());
            assertFalse(session.tryConsumeSkillHit(0, 1, 6, 2_000L));

            doAnswer(invocation -> {
                throw new java.util.concurrent.RejectedExecutionException("executor stopping");
            }).when(threads).newTask(any(Runnable.class));
            FTConnection rejected = new FTConnection(0, 0, ServerType.GAME_SERVER);
            rejected.queuePacket(CMSGPlayerUseSkill.builder().build());
            assertFalse(rejected.update(0));
            assertFalse(rejected.getCastInFlight().get());
            assertTrue(rejected.getIsClosingConnection().get());
        } finally {
            handlers.clear();
            handlers.putAll(previousHandlers);
            ReflectionTestUtils.setField(ServiceManager.class, "instance", previousServices);
            ReflectionTestUtils.setField(ThreadManager.class, "instance", previousThreads);
        }
    }
}
