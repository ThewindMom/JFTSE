package com.jftse.emulator.server.core.handler.lobby.room;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.constants.RoomPositionState;
import com.jftse.emulator.server.core.constants.RoomStatus;
import com.jftse.emulator.server.core.constants.RoomType;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.packets.lobby.room.S2CPetRequestRoomAnswerPacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.pet.Pet;
import com.jftse.server.core.constants.GameMode;
import com.jftse.server.core.protocol.IPacket;
import com.jftse.server.core.service.PetService;
import com.jftse.server.core.shared.packets.pet.CMSGRequestPet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoomRequestPetPacketHandlerTest {
    private static final long PLAYER_ID = 5L;
    private static final long PET_ID = 10L;

    private Object previousGameManager;
    private Object previousServiceManager;
    private GameManager gameManager;
    private PetService petService;
    private FTConnection connection;
    private FTClient client;
    private Room room;
    private RoomPlayer roomPlayer;

    @BeforeEach
    void setUp() {
        previousGameManager = ReflectionTestUtils.getField(GameManager.class, "instance");
        previousServiceManager = ReflectionTestUtils.getField(ServiceManager.class, "instance");
        gameManager = mock(GameManager.class);
        ReflectionTestUtils.setField(GameManager.class, "instance", gameManager);
        petService = mock(PetService.class);
        ServiceManager serviceManager = mock(ServiceManager.class);
        when(serviceManager.getPetService()).thenReturn(petService);
        ReflectionTestUtils.setField(ServiceManager.class, "instance", serviceManager);

        FTPlayer player = mock(FTPlayer.class);
        when(player.getId()).thenReturn(PLAYER_ID);

        room = new Room();
        room.setRoomType((byte) RoomType.BATTLEMON);
        room.setAllowBattlemon((byte) 1);
        room.setPlayers((byte) 4);
        room.getPositions().set(0, RoomPositionState.InUse);
        roomPlayer = new RoomPlayer(player);
        roomPlayer.setPosition((short) 0);
        room.getRoomPlayerList().add(roomPlayer);

        connection = mock(FTConnection.class);
        client = new FTClient();
        client.setConnection(connection);
        client.refreshPlayer(player);
        client.setActiveRoom(room);
        when(connection.getClient()).thenReturn(client);
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(GameManager.class, "instance", previousGameManager);
        ReflectionTestUtils.setField(ServiceManager.class, "instance", previousServiceManager);
    }

    @Test
    void dedicatedBattlemonSeatsThePetTwoSlotsBehindItsOwnerAndBroadcasts() {
        Pet pet = alivePet();
        client.setActivePet(pet);
        when(petService.findByIdAndPlayerId(PET_ID, PLAYER_ID)).thenReturn(pet);

        new RoomRequestPetPacketHandler().handle(connection, CMSGRequestPet.builder().slot((byte) 0).build());

        assertEquals(RoomPositionState.InUse, room.getPositions().get(2));
        assertEquals(PET_ID, roomPlayer.getPet().id());
        ArgumentCaptor<IPacket> broadcast = ArgumentCaptor.forClass(IPacket.class);
        verify(gameManager).sendPacketToAllClientsInSameRoom(broadcast.capture(), any());
        byte[] payload = ((S2CPetRequestRoomAnswerPacket) broadcast.getValue()).getData();
        assertEquals(S2CPetRequestRoomAnswerPacket.SUCCESS, payload[0]);
        assertEquals(1, payload[1]);
        assertEquals(0, payload[2]);
        verify(connection, never()).sendTCP(any(IPacket.class));
    }

    @Test
    void dedicatedBattlemonRejectsAForeignSlot() {
        client.setActivePet(alivePet());

        new RoomRequestPetPacketHandler().handle(connection, CMSGRequestPet.builder().slot((byte) 1).build());

        assertNull(roomPlayer.getPet());
        assertEquals(RoomPositionState.Free, room.getPositions().get(2));
        assertEquals(S2CPetRequestRoomAnswerPacket.CAN_NOT_ADD_PET, answeredResult());
        verify(gameManager, never()).sendPacketToAllClientsInSameRoom(any(), any());
    }

    @Test
    void dedicatedBattlemonRejectsWhileTheRoomIsStarting() {
        client.setActivePet(alivePet());
        room.setStatus(RoomStatus.StartingGame);

        new RoomRequestPetPacketHandler().handle(connection, CMSGRequestPet.builder().slot((byte) 0).build());

        assertNull(roomPlayer.getPet());
        assertEquals(S2CPetRequestRoomAnswerPacket.CAN_NOT_ADD_PET, answeredResult());
    }

    @Test
    void dedicatedBattlemonRejectsAnExpiredPet() {
        Pet pet = alivePet();
        pet.setValidUntil(new Date(System.currentTimeMillis() - 1_000L));
        client.setActivePet(pet);
        when(petService.findByIdAndPlayerId(PET_ID, PLAYER_ID)).thenReturn(pet);

        new RoomRequestPetPacketHandler().handle(connection, CMSGRequestPet.builder().slot((byte) 0).build());

        assertNull(roomPlayer.getPet());
        assertEquals(RoomPositionState.Free, room.getPositions().get(2));
        assertEquals(S2CPetRequestRoomAnswerPacket.CAN_NOT_ADD_PET, answeredResult());
    }

    @Test
    void ordinaryRoomWithoutBattlemonStaysClosedToPets() {
        room.setRoomType((byte) RoomType.MATCH);
        room.setMode((byte) GameMode.BASIC);
        room.setAllowBattlemon((byte) 0);
        client.setActivePet(alivePet());

        new RoomRequestPetPacketHandler().handle(connection, CMSGRequestPet.builder().slot((byte) 0).build());

        assertNull(roomPlayer.getPet());
        assertEquals(S2CPetRequestRoomAnswerPacket.PET_NOT_ALLOWED, answeredResult());
        verify(petService, never()).findByIdAndPlayerId(any(), any());
    }

    private byte answeredResult() {
        ArgumentCaptor<IPacket> answer = ArgumentCaptor.forClass(IPacket.class);
        verify(connection).sendTCP(answer.capture());
        return ((S2CPetRequestRoomAnswerPacket) answer.getValue()).getData()[0];
    }

    private static Pet alivePet() {
        Pet pet = new Pet();
        pet.setId(PET_ID);
        pet.setType((byte) 1);
        pet.setName("Pet");
        pet.setLevel(1);
        pet.setHp(100);
        pet.setStrength((byte) 1);
        pet.setStamina((byte) 1);
        pet.setDexterity((byte) 1);
        pet.setWillpower((byte) 1);
        pet.setHunger(100);
        pet.setEnergy(100);
        pet.setAlive(true);
        pet.setValidUntil(new Date(System.currentTimeMillis() + 86_400_000L));
        return pet;
    }
}
