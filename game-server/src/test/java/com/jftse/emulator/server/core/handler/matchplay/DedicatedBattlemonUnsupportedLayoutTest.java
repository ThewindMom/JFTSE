package com.jftse.emulator.server.core.handler.matchplay;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.client.PetView;
import com.jftse.emulator.server.core.constants.RoomPositionState;
import com.jftse.emulator.server.core.constants.RoomStatus;
import com.jftse.emulator.server.core.constants.RoomType;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.matchplay.GameSessionManager;
import com.jftse.emulator.server.core.rabbit.service.RProducerService;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.pet.Pet;
import com.jftse.entities.database.model.pet.PetStatistic;
import com.jftse.entities.database.model.player.EquippedItemStats;
import com.jftse.emulator.server.core.client.EquippedItemParts;
import com.jftse.server.core.protocol.IPacket;
import com.jftse.server.core.service.AuthenticationService;
import com.jftse.server.core.service.PetService;
import com.jftse.server.core.shared.ServerConfService;
import com.jftse.server.core.shared.packets.matchplay.CMSGStartGame;
import com.jftse.server.core.shared.rabbit.messages.RelaySessionAuthorizationMessage;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Dedicated Battlemon is 2 owners at 0/1 plus pets at 2/3.
 * CMSG_RoomJoin has no spectator flag. Unsupported layouts fail admission.
 */
class DedicatedBattlemonUnsupportedLayoutTest {
    @Test
    void dedicatedBattlemonStartRejectsOneOwnerThreeHumansAndSpectators() {
        Object previousGameManager = ReflectionTestUtils.getField(GameManager.class, "instance");
        Object previousServiceManager = ReflectionTestUtils.getField(ServiceManager.class, "instance");
        Object previousSessionManager = ReflectionTestUtils.getField(GameSessionManager.class, "instance");
        try {
            AuthenticationService authenticationService = mock(AuthenticationService.class);
            PetService petService = mock(PetService.class);
            ServiceManager serviceManager = mock(ServiceManager.class);
            when(serviceManager.getAuthenticationService()).thenReturn(authenticationService);
            when(serviceManager.getPetService()).thenReturn(petService);
            ReflectionTestUtils.setField(ServiceManager.class, "instance", serviceManager);

            ServerConfService serverConfService = mock(ServerConfService.class);
            when(serverConfService.get("RelayPort", Integer.class)).thenReturn(5896);
            RProducerService producer = mock(RProducerService.class);
            GameManager gameManager = mock(GameManager.class);
            when(gameManager.getServerConfService()).thenReturn(serverConfService);
            when(gameManager.getRProducerService()).thenReturn(producer);
            ReflectionTestUtils.setField(GameManager.class, "instance", gameManager);

            GameSessionManager sessionManager = new GameSessionManager();
            sessionManager.init();

            RoomStartGamePacketHandler handler = new RoomStartGamePacketHandler();

            Room oneOwner = dedicatedBattlemonRoom();
            Pet onlyPet = pet(10L, "Only pet");
            FTClient only = guardianClientInRoom(oneOwner, 100L, (short) 0, onlyPet);
            only.getRoomPlayer().setMaster(true);
            only.getRoomPlayer().setPet(PetView.of(onlyPet));
            when(gameManager.getClientsInRoom(oneOwner.getRoomId())).thenReturn(List.of(only));
            when(petService.findByIdAndPlayerId(10L, 100L)).thenReturn(onlyPet);
            handler.handle(only.getConnection(), CMSGStartGame.builder().build());
            assertEquals(RoomStatus.NotRunning, oneOwner.getStatus());
            assertEquals(List.of(0x17E6), sentPacketIds(only.getConnection()));
            assertTrue(sessionManager.getGameSessionList().isEmpty());

            Room threeHumans = dedicatedBattlemonRoom();
            Pet firstPet = pet(11L, "First pet");
            Pet secondPet = pet(12L, "Second pet");
            Pet thirdPet = pet(13L, "Third pet");
            FTClient first = guardianClientInRoom(threeHumans, 101L, (short) 0, firstPet);
            FTClient second = guardianClientInRoom(threeHumans, 102L, (short) 1, secondPet);
            FTClient third = guardianClientInRoom(threeHumans, 103L, (short) 4, thirdPet);
            first.getRoomPlayer().setMaster(true);
            second.getRoomPlayer().setReady(true);
            third.getRoomPlayer().setReady(true);
            first.getRoomPlayer().setPet(PetView.of(firstPet));
            second.getRoomPlayer().setPet(PetView.of(secondPet));
            when(gameManager.getClientsInRoom(threeHumans.getRoomId())).thenReturn(List.of(first, second, third));
            handler.handle(first.getConnection(), CMSGStartGame.builder().build());
            assertEquals(RoomStatus.NotRunning, threeHumans.getStatus());
            assertEquals(List.of(0x17E6), sentPacketIds(first.getConnection()));
            assertTrue(sessionManager.getGameSessionList().isEmpty());
            verify(producer, never()).sendRelayActorPolicy(
                    any(RelaySessionAuthorizationMessage.class),
                    eq("MatchplaySystem(GameServer)"));
        } finally {
            ReflectionTestUtils.setField(GameManager.class, "instance", previousGameManager);
            ReflectionTestUtils.setField(ServiceManager.class, "instance", previousServiceManager);
            ReflectionTestUtils.setField(GameSessionManager.class, "instance", previousSessionManager);
        }
    }

    private static List<Integer> sentPacketIds(FTConnection connection) {
        return org.mockito.Mockito.mockingDetails(connection).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("sendTCP"))
                .flatMap(invocation -> Arrays.stream(invocation.getArguments()))
                .flatMap(argument -> argument instanceof IPacket[] packets
                        ? Arrays.stream(packets)
                        : argument instanceof IPacket packet
                        ? java.util.stream.Stream.of(packet)
                        : java.util.stream.Stream.empty())
                .map(packet -> (int) packet.getPacketId())
                .toList();
    }

    private static FTClient guardianClientInRoom(Room room, long playerId, short position, Pet pet) {
        FTPlayer player = mock(FTPlayer.class);
        when(player.getId()).thenReturn(playerId);
        when(player.getItemStats()).thenReturn(new EquippedItemStats());
        when(player.getItemPartsItemIndex()).thenReturn(
                new EquippedItemParts(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        RoomPlayer roomPlayer = new RoomPlayer(player);
        roomPlayer.setPosition(position);
        room.getRoomPlayerList().add(roomPlayer);
        FTClient client = mock(FTClient.class);
        when(client.hasPlayer()).thenReturn(true);
        when(client.getPlayer()).thenReturn(player);
        when(client.getActiveRoom()).thenReturn(room);
        when(client.getRoomPlayer()).thenReturn(roomPlayer);
        when(client.getActivePet()).thenReturn(PetView.of(pet));
        when(client.getGameSessionId()).thenReturn(null);
        FTConnection connection = mock(FTConnection.class);
        when(connection.getClient()).thenReturn(client);
        when(client.getConnection()).thenReturn(connection);
        return client;
    }

    private static Room dedicatedBattlemonRoom() {
        Room room = new Room();
        room.setRoomType((byte) RoomType.BATTLEMON);
        room.setMode((byte) com.jftse.server.core.constants.GameMode.BASIC);
        room.setAllowBattlemon((byte) 1);
        room.setPlayers((byte) 4);
        room.getPositions().set(0, RoomPositionState.InUse);
        room.getPositions().set(1, RoomPositionState.Free);
        return room;
    }

    private static Pet pet(long id, String name) {
        Pet pet = new Pet();
        pet.setId(id);
        pet.setType((byte) 1);
        pet.setName(name);
        pet.setLevel(1);
        pet.setHp(100);
        pet.setStrength((byte) 1);
        pet.setStamina((byte) 1);
        pet.setDexterity((byte) 1);
        pet.setWillpower((byte) 1);
        pet.setHunger(100);
        pet.setEnergy(100);
        pet.setAlive(true);
        pet.setValidUntil(Date.from(Instant.now().plus(30, ChronoUnit.DAYS)));
        pet.setPetStatistic(new PetStatistic());
        return pet;
    }
}
