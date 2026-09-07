package com.jftse.emulator.server.net;

import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.matchplay.GameSessionManager;
import com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayDealDamage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MatchPublicationTest {
    @ParameterizedTest
    @ValueSource(strings = {"session", "room", "clear", "generation"})
    void membershipSwitchCannotOvertakeEnqueueAndOldSnapshotCannotSendAfterSwitch(String change) throws Exception {
        Object previous = ReflectionTestUtils.getField(GameSessionManager.class, "instance");
        GameSessionManager sessions = new GameSessionManager();
        sessions.init();
        GameSession original = new GameSession();
        sessions.getGameSessionList().put(1, original);
        sessions.getGameSessionList().put(2, new GameSession());
        FTClient client = new FTClient();
        client.setActiveRoom(mock(Room.class));
        client.setActiveGameSession(1);
        FTConnection connection = mock(FTConnection.class);
        client.setConnection(connection);
        var snapshot = client.matchMembership();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var started = new CountDownLatch(1);
        var error = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        doAnswer(invocation -> {
            assertFalse(Thread.holdsLock(client), "Conditional enqueue must not acquire the client monitor");
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return null;
        }).when(connection).sendTCP(any());
        var packet = new S2CMatchplayDealDamage((short) 0, (short) 90, (short) 1, (byte) 6, 0, 0);
        Thread sending = Thread.ofPlatform().start(() -> {
            try { client.sendMatchPacket(snapshot, packet); } catch (Throwable failure) { error.set(failure); }
        });
        Thread switching = Thread.ofPlatform().unstarted(() -> {
            started.countDown();
            switch (change) {
                case "session" -> client.setActiveGameSession(2);
                case "room" -> client.setActiveRoom(mock(Room.class));
                case "clear" -> client.clearActiveGameSession(original);
                case "generation" -> { client.setActiveGameSession(2); client.setActiveGameSession(1); }
            }
        });
        try {
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            switching.start();
            assertTrue(started.await(3, TimeUnit.SECONDS));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            java.lang.management.ThreadInfo info;
            do {
                info = java.lang.management.ManagementFactory.getThreadMXBean().getThreadInfo(switching.threadId());
                if (info != null && info.getLockOwnerId() == sending.threadId()) break;
                Thread.yield();
            } while (System.nanoTime() < deadline);
            assertNotNull(info);
            assertEquals(sending.threadId(), info.getLockOwnerId());
        } finally {
            release.countDown();
            sending.join(5000); switching.join(5000);
            ReflectionTestUtils.setField(GameSessionManager.class, "instance", previous);
        }
        assertNull(error.get());
        assertFalse(sending.isAlive() || switching.isAlive());
        // Use the same registry for the final stale-snapshot check.
        ReflectionTestUtils.setField(GameSessionManager.class, "instance", sessions);
        try {
            client.sendMatchPacket(snapshot, packet);
            verify(connection).sendTCP(any());
        } finally { ReflectionTestUtils.setField(GameSessionManager.class, "instance", previous); }
    }
}
