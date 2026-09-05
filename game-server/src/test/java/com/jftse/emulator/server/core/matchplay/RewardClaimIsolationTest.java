package com.jftse.emulator.server.core.matchplay;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.handler.matchplay.MatchplayItemRewardPickHandler;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.task.AutoItemRewardPickerTask;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.shared.packets.matchplay.CMSGPickupItemReward;
import com.jftse.server.core.thread.ThreadManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RewardClaimIsolationTest {
    private Object previousManager, previousServices, previousSessions, previousThreads;
    private GameSessionManager sessions;
    private FTClient client;
    private FTConnection connection;
    private Room room;
    private MatchplayReward reward;

    @BeforeEach
    void setUp() {
        previousManager = ReflectionTestUtils.getField(GameManager.class, "instance");
        previousServices = ReflectionTestUtils.getField(ServiceManager.class, "instance");
        previousSessions = ReflectionTestUtils.getField(GameSessionManager.class, "instance");
        previousThreads = ReflectionTestUtils.getField(ThreadManager.class, "instance");
        ReflectionTestUtils.setField(GameManager.class, "instance", mock(GameManager.class));
        ReflectionTestUtils.setField(ServiceManager.class, "instance", mock(ServiceManager.class));
        ReflectionTestUtils.setField(ThreadManager.class, "instance", mock(ThreadManager.class));
        sessions = new GameSessionManager();
        sessions.init();
        client = new FTClient();
        connection = mock(FTConnection.class);
        when(connection.getClient()).thenReturn(client);
        client.setConnection(connection);
        client.refreshPlayer(mock(FTPlayer.class));
        room = mock(Room.class);
        when(room.getRoomId()).thenReturn((short) 7);
        RoomPlayer seat = mock(RoomPlayer.class);
        RoomPlayer other = mock(RoomPlayer.class);
        when(other.getPosition()).thenReturn((short) 1);
        when(room.getRoomPlayerList()).thenReturn(new ConcurrentLinkedDeque<>(List.of(seat, other)));
        client.setActiveRoom(room);
        client.setRoomPlayer(seat);
        reward = reward();
        sessions.addMatchplayReward(7, reward);
    }

    @AfterEach
    void restore() {
        ReflectionTestUtils.setField(GameManager.class, "instance", previousManager);
        ReflectionTestUtils.setField(ServiceManager.class, "instance", previousServices);
        ReflectionTestUtils.setField(GameSessionManager.class, "instance", previousSessions);
        ReflectionTestUtils.setField(ThreadManager.class, "instance", previousThreads);
    }

    @Test
    void sameParticipantCannotPickTwoDifferentRewardSlots() {
        MatchplayItemRewardPickHandler handler = new MatchplayItemRewardPickHandler();
        handler.handle(connection, CMSGPickupItemReward.builder().slot((byte) 0).build());
        handler.handle(connection, CMSGPickupItemReward.builder().slot((byte) 1).build());
        assertEquals(1, reward.getSlotRewards().values().stream().filter(item -> item.getClaimed().get()).count());
    }

    @Test
    void manualPickAndTimeoutPersistOneRewardWhilePersistenceIsBlocked() throws Exception {
        var products = mock(com.jftse.server.core.service.ProductService.class);
        var pockets = mock(com.jftse.server.core.service.PocketService.class);
        var items = mock(com.jftse.server.core.service.PlayerPocketService.class);
        when(ServiceManager.getInstance().getProductService()).thenReturn(products);
        when(ServiceManager.getInstance().getPocketService()).thenReturn(pockets);
        when(ServiceManager.getInstance().getPlayerPocketService()).thenReturn(items);
        var product = new com.jftse.entities.database.model.item.Product();
        product.setCategory("SPECIAL");
        product.setItem0(1);
        product.setUseType("COUNT");
        when(products.findProductByProductItemIndex(1)).thenReturn(product);
        var pocket = new com.jftse.entities.database.model.pocket.Pocket();
        when(pockets.findById(anyLong())).thenReturn(pocket);
        var existing = new com.jftse.entities.database.model.pocket.PlayerPocket();
        existing.setId(1L);
        existing.setItemCount(2);
        existing.setUseType("COUNT");
        when(items.getItemAsPocketByItemIndexAndCategoryAndPocket(anyInt(), anyString(), eq(pocket)))
                .thenReturn(existing);
        reward.getSlotRewards().values().forEach(item -> item.setProductIndex(1));
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        doAnswer(invocation -> {
            assertFalse(Thread.holdsLock(client), "No client lock across pocket persistence");
            entered.countDown();
            assertTrue(release.await(5, java.util.concurrent.TimeUnit.SECONDS));
            return invocation.getArgument(0);
        }).when(items).save(any());
        var timeout = new AutoItemRewardPickerTask(new ConcurrentLinkedDeque<>(List.of(client)), (short) 7);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var manual = executor.submit(() -> new MatchplayItemRewardPickHandler().handle(connection,
                    CMSGPickupItemReward.builder().slot((byte) 0).build()));
            try {
                assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
                executor.submit(timeout).get(2, java.util.concurrent.TimeUnit.SECONDS);
                assertEquals(1, reward.getSlotRewards().values().stream().filter(item -> item.getClaimed().get()).count());
                release.countDown();
                manual.get(2, java.util.concurrent.TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
        }
        verify(items).save(existing);
        assertEquals(3, existing.getItemCount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"reward", "room", "seat"})
    void timeoutCannotClaimReplacementRewardOrRejoinedParticipant(String replacement) {
        AutoItemRewardPickerTask task = new AutoItemRewardPickerTask(new ConcurrentLinkedDeque<>(List.of(client)), (short) 7);
        MatchplayReward current = reward;
        switch (replacement) {
            case "reward" -> {
                current = reward();
                sessions.addMatchplayReward(7, current);
            }
            case "room" -> {
                Room next = mock(Room.class);
                when(next.getRoomId()).thenReturn((short) 7);
                when(next.getRoomPlayerList()).thenReturn(new ConcurrentLinkedDeque<>());
                client.setActiveRoom(next);
            }
            case "seat" -> {
                room.getRoomPlayerList().clear();
                room.getRoomPlayerList().add(mock(RoomPlayer.class));
            }
        }
        task.run();
        assertTrue(current.getSlotRewards().values().stream().noneMatch(item -> item.getClaimed().get()));
        verify(GameManager.getInstance(), never()).sendPacketToAllClientsInSameRoom(any(), any());
    }

    private static MatchplayReward reward() {
        MatchplayReward reward = new MatchplayReward();
        reward.getSlotRewards().put((byte) 0, new MatchplayReward.ItemReward(0, 1, 1.0));
        reward.getSlotRewards().put((byte) 1, new MatchplayReward.ItemReward(0, 1, 1.0));
        return reward;
    }
}
