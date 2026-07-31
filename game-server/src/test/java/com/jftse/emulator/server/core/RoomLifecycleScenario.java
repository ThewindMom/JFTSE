package com.jftse.emulator.server.core;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.handler.lobby.room.RoomJoinRequestPacketHandler;
import com.jftse.emulator.server.core.handler.lobby.room.RoomLeaveRequestPacketHandler;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.emulator.server.net.TCPChannelHandler;
import com.jftse.emulator.server.support.SingletonTestSupport;
import com.jftse.entities.database.model.player.Player;
import com.jftse.server.core.protocol.IPacket;
import com.jftse.server.core.service.BlockedIPService;
import com.jftse.server.core.service.PlayerService;
import com.jftse.server.core.service.SocialService;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomJoin;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomLeave;
import io.netty.util.AttributeKey;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class RoomLifecycleScenario {
    private RoomLifecycleScenario() {
    }

    public static void main(String[] args) {
        int failures = 0;
        failures += run("join-failure-rollback", RoomLifecycleScenario::verifyJoinFailureRollback);
        failures += run("leave-retry", RoomLifecycleScenario::verifyLeaveRetry);
        failures += run("disconnect-cleanup", RoomLifecycleScenario::verifyDisconnectCleanup);

        if (failures != 0) {
            throw new IllegalStateException(failures + " lifecycle scenario(s) failed");
        }
    }

    private static int run(String name, CheckedScenario scenario) {
        try {
            scenario.run();
            System.out.println("PASS " + name);
            return 0;
        } catch (Throwable failure) {
            System.out.println("FAIL " + name + ": " + failure.getMessage());
            return 1;
        }
    }

    private static void verifyJoinFailureRollback() {
        ServiceManager serviceManager = mock(ServiceManager.class);
        when(serviceManager.getSocialService()).thenReturn(mock(SocialService.class));
        GameManager gameManager = mock(GameManager.class);

        Object previousServiceManager = SingletonTestSupport.replace(ServiceManager.class, "instance", serviceManager);
        Object previousGameManager = SingletonTestSupport.replace(GameManager.class, "instance", gameManager);
        try {
            Room room = room((short) 7);
            when(gameManager.getRooms()).thenReturn(new ConcurrentLinkedDeque<>());
            gameManager.getRooms().add(room);

            FTPlayer player = mock(FTPlayer.class);
            when(player.getId()).thenReturn(42L);
            when(player.getLevel()).thenReturn(60);

            FTClient client = new FTClient();
            require(client.refreshPlayer(player), "player fixture was not installed");
            client.setInLobby(true);

            FTConnection connection = mock(FTConnection.class);
            client.setConnection(connection);
            when(connection.getClient()).thenReturn(client);
            when(connection.sendTCP(any(IPacket.class)))
                    .thenThrow(new IllegalStateException("simulated send failure"));

            CMSGRoomJoin request = CMSGRoomJoin.builder().roomId(room.getRoomId()).password("").build();
            try {
                new RoomJoinRequestPacketHandler().handle(connection, request);
                throw new IllegalStateException("join unexpectedly succeeded");
            } catch (IllegalStateException expected) {
                require("simulated send failure".equals(expected.getMessage()), "unexpected join failure");
            }

            require(!client.getIsJoiningOrLeavingRoom().get(), "join guard remained locked");
            require(client.getActiveRoom() == null, "failed join retained activeRoom");
            require(room.getRoomPlayerList().isEmpty(), "failed join retained room membership");
        } finally {
            SingletonTestSupport.replace(GameManager.class, "instance", previousGameManager);
            SingletonTestSupport.replace(ServiceManager.class, "instance", previousServiceManager);
        }
    }

    private static void verifyLeaveRetry() {
        GameManager gameManager = mock(GameManager.class);
        Object previousGameManager = SingletonTestSupport.replace(GameManager.class, "instance", gameManager);
        try {
            FTClient client = new FTClient();
            client.refreshPlayer(mock(FTPlayer.class));

            FTConnection connection = mock(FTConnection.class);
            client.setConnection(connection);
            when(connection.getClient()).thenReturn(client);

            AtomicInteger cleanupAttempts = new AtomicInteger();
            doAnswer(invocation -> {
                if (cleanupAttempts.getAndIncrement() == 0) {
                    throw new IllegalStateException("simulated cleanup failure");
                }
                return null;
            }).when(gameManager).handleRoomPlayerChanges(connection, true);

            RoomLeaveRequestPacketHandler handler = new RoomLeaveRequestPacketHandler();
            CMSGRoomLeave request = CMSGRoomLeave.builder().build();
            try {
                handler.handle(connection, request);
                throw new IllegalStateException("first leave unexpectedly succeeded");
            } catch (IllegalStateException expected) {
                require("simulated cleanup failure".equals(expected.getMessage()), "unexpected leave failure");
            }

            require(!client.getIsJoiningOrLeavingRoom().get(), "leave guard remained locked");
            handler.handle(connection, request);
            require(cleanupAttempts.get() == 2, "second leave never reached cleanup");
        } finally {
            SingletonTestSupport.replace(GameManager.class, "instance", previousGameManager);
        }
    }

    private static void verifyDisconnectCleanup() {
        ServiceManager serviceManager = mock(ServiceManager.class);
        GameManager gameManager = mock(GameManager.class);
        Object previousServiceManager = SingletonTestSupport.replace(ServiceManager.class, "instance", serviceManager);
        Object previousGameManager = SingletonTestSupport.replace(GameManager.class, "instance", gameManager);
        try {
            PlayerService playerService = mock(PlayerService.class);
            when(serviceManager.getPlayerService()).thenReturn(playerService);
            when(serviceManager.getBlockedIPService()).thenReturn(mock(BlockedIPService.class));

            Player persistedPlayer = mock(Player.class);
            when(playerService.save(persistedPlayer))
                    .thenThrow(new IllegalStateException("simulated persistence failure"));

            FTPlayer player = mock(FTPlayer.class);
            when(player.getId()).thenReturn(42L);
            when(player.getPlayer()).thenReturn(persistedPlayer);

            FTClient client = new FTClient();
            client.refreshPlayer(player);

            Room room = new Room();
            RoomPlayer roomPlayer = new RoomPlayer(player);
            roomPlayer.setPosition((short) 0);
            room.getRoomPlayerList().add(roomPlayer);
            client.setActiveRoom(room);

            FTConnection connection = mock(FTConnection.class);
            client.setConnection(connection);
            when(connection.getClient()).thenReturn(client);

            doAnswer(invocation -> {
                room.getRoomPlayerList().removeIf(member -> member.getPlayerId() == player.getId());
                client.setActiveRoom(null);
                return null;
            }).when(gameManager).handleRoomPlayerChanges(connection, true);

            TCPChannelHandler handler =
                    new TCPChannelHandler(AttributeKey.valueOf("room-lifecycle-scenario"));
            try {
                handler.disconnected(connection);
                throw new IllegalStateException("disconnect unexpectedly succeeded");
            } catch (IllegalStateException expected) {
                require("simulated persistence failure".equals(expected.getMessage()),
                        "unexpected disconnect failure");
            }

            require(client.getActiveRoom() == null, "disconnect retained activeRoom");
            require(room.getRoomPlayerList().isEmpty(), "disconnect retained room membership");
        } finally {
            SingletonTestSupport.replace(GameManager.class, "instance", previousGameManager);
            SingletonTestSupport.replace(ServiceManager.class, "instance", previousServiceManager);
        }
    }

    private static Room room(short roomId) {
        Room room = new Room();
        room.setRoomId(roomId);
        room.setRoomName("Lifecycle scenario");
        room.setPlayers((byte) 4);
        room.setLevel((byte) 1);
        room.setLevelRange((byte) 60);
        return room;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    @FunctionalInterface
    private interface CheckedScenario {
        void run();
    }
}
