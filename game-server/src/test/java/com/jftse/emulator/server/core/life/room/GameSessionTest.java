package com.jftse.emulator.server.core.life.room;

import com.jftse.emulator.server.net.FTClient;
import com.jftse.entities.database.model.pet.Pet;
import com.jftse.entities.database.model.pet.PetStatistic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GameSessionTest {
    @Test
    void ordinaryBasicSessionIsOneHumanSeatPerClient() {
        GameSession session = new GameSession();
        RoomPlayer firstPlayer = roomPlayer(100L, (short) 0);
        RoomPlayer secondPlayer = roomPlayer(200L, (short) 1);
        session.getClients().add(clientFor(firstPlayer));
        session.getClients().add(clientFor(secondPlayer));

        session.initializeGameplayActorPositions();

        assertFalse(session.isDedicatedBattlemonRoom());
        assertFalse(session.hasOwnedPetSeats());
        assertEquals(List.of((short) 0, (short) 1), session.getGameplayActorPositions());
        assertTrue(session.isHumanSeat(0));
        assertTrue(session.isHumanSeat(1));
        assertTrue(session.isActorOwnedBy(firstPlayer, 0));
        assertFalse(session.isActorOwnedBy(firstPlayer, 1));
        assertEquals(0, session.getOwnerPositionForActor(0));
        assertEquals(1, session.getOwnerPositionForActor(1));
        assertNull(session.getOwnedPetSeat(100L));
    }

    @Test
    void battlemonActorsUseOwnerPositionPlusTwoAndOwnerEndpoint() {
        GameSession session = new GameSession(true);
        RoomPlayer firstPlayer = roomPlayer(100L, (short) 0);
        RoomPlayer secondPlayer = roomPlayer(200L, (short) 1);
        session.getClients().add(clientFor(firstPlayer));
        session.getClients().add(clientFor(secondPlayer));

        session.addOwnedPetSeat(firstPlayer, pet(10L, "First pet"));
        session.addOwnedPetSeat(secondPlayer, pet(20L, "Second pet"));
        session.initializeGameplayActorPositions();
        GameplayActor firstPet = session.getActor(2);
        GameplayActor secondPet = session.getActor(3);

        assertEquals((short) 2, firstPet.position());
        assertEquals((short) 3, secondPet.position());
        assertEquals(List.of((short) 0, (short) 1, (short) 2, (short) 3), session.getGameplayActorPositions());
        assertSame(firstPet, session.getOwnedPetSeat(100L));
        assertSame(secondPet, session.getOwnedPetSeat(200L));
        assertTrue(session.isHumanSeat(0));
        assertTrue(session.isHumanSeat(1));
        assertFalse(session.isHumanSeat(2));
        assertFalse(session.isHumanSeat(3));
        assertEquals((short) 0, firstPet.ownerPosition());
        assertEquals((short) 1, secondPet.ownerPosition());
        assertTrue(session.isActorOwnedBy(firstPlayer, 0));
        assertTrue(session.isActorOwnedBy(firstPlayer, 2));
        assertFalse(session.isActorOwnedBy(firstPlayer, 1));
        assertFalse(session.isActorOwnedBy(firstPlayer, 3));
    }

    @Test
    void guardianSessionCanOwnPetActors() {
        GameSession session = new GameSession();
        RoomPlayer firstPlayer = roomPlayer(100L, (short) 0);
        RoomPlayer secondPlayer = roomPlayer(200L, (short) 1);
        session.getClients().add(clientFor(firstPlayer));
        session.getClients().add(clientFor(secondPlayer));

        session.addOwnedPetSeat(firstPlayer, pet(10L, "First pet"));
        session.addOwnedPetSeat(secondPlayer, pet(20L, "Second pet"));
        session.initializeGameplayActorPositions();

        assertFalse(session.isDedicatedBattlemonRoom());
        assertEquals(List.of((short) 0, (short) 1, (short) 2, (short) 3), session.getGameplayActorPositions());
        assertTrue(session.isActorOwnedBy(firstPlayer, 2));
        assertTrue(session.isActorOwnedBy(secondPlayer, 3));
    }

    @Test
    void battlemonActorsRejectPositionsWithoutAHumanOwner() {
        GameSession session = new GameSession(true);
        RoomPlayer invalidOwner = roomPlayer(100L, (short) 2);

        assertThrows(IllegalArgumentException.class, () -> session.addOwnedPetSeat(invalidOwner, pet(10L, "Pet")));
        assertTrue(session.getOwnedPetSeats().isEmpty());
    }

    @Test
    void battlemonActorsRequireMatchingHumanEndpoint() {
        GameSession session = new GameSession(true);
        RoomPlayer owner = roomPlayer(100L, (short) 0);
        RoomPlayer differentPlayer = roomPlayer(200L, (short) 0);
        session.getClients().add(clientFor(differentPlayer));

        assertThrows(IllegalArgumentException.class, () -> session.addOwnedPetSeat(owner, pet(10L, "Pet")));
        assertTrue(session.getOwnedPetSeats().isEmpty());
    }

    @Test
    void battlemonActorsRejectDuplicateOwner() {
        GameSession session = new GameSession(true);
        RoomPlayer owner = roomPlayer(100L, (short) 0);
        session.getClients().add(clientFor(owner));
        session.addOwnedPetSeat(owner, pet(10L, "First pet"));

        assertThrows(IllegalStateException.class, () -> session.addOwnedPetSeat(owner, pet(20L, "Second pet")));
        assertEquals(1, session.getOwnedPetSeats().size());
        assertEquals(10L, session.getActor(2).pet().id());
    }

    @Test
    void gameplayActorRosterDoesNotChangeWhenAnEndpointDisconnects() {
        GameSession session = new GameSession(true);
        RoomPlayer firstPlayer = roomPlayer(100L, (short) 0);
        RoomPlayer secondPlayer = roomPlayer(200L, (short) 1);
        FTClient firstClient = clientFor(firstPlayer);
        FTClient secondClient = clientFor(secondPlayer);
        session.getClients().add(firstClient);
        session.getClients().add(secondClient);
        session.addOwnedPetSeat(firstPlayer, pet(10L, "First pet"));
        session.addOwnedPetSeat(secondPlayer, pet(20L, "Second pet"));
        session.initializeGameplayActorPositions();

        session.getClients().remove(secondClient);

        assertEquals(List.of((short) 0, (short) 1, (short) 2, (short) 3), session.getGameplayActorPositions());
        assertEquals(0, session.getOwnerPositionForActor(2));
        assertEquals(1, session.getOwnerPositionForActor(3));
    }

    @Test
    void battlemonSpectatorsAreNotGameplayEndpoints() {
        GameSession session = new GameSession(true);
        RoomPlayer firstPlayer = roomPlayer(100L, (short) 0);
        RoomPlayer secondPlayer = roomPlayer(200L, (short) 1);
        FTClient firstClient = clientFor(firstPlayer);
        FTClient secondClient = clientFor(secondPlayer);
        FTClient spectatorClient = clientFor(roomPlayer(300L, (short) 4));
        session.getClients().add(firstClient);
        session.getClients().add(secondClient);
        session.getClients().add(spectatorClient);
        session.addOwnedPetSeat(firstPlayer, pet(10L, "First pet"));
        session.addOwnedPetSeat(secondPlayer, pet(20L, "Second pet"));
        session.initializeGameplayActorPositions();

        assertTrue(session.isGameplayEndpoint(firstClient));
        assertTrue(session.isGameplayEndpoint(secondClient));
        assertFalse(session.isGameplayEndpoint(spectatorClient));

        FTClient detachedSpectator = mock(FTClient.class);
        when(detachedSpectator.isSpectator()).thenReturn(true);
        session.getClients().add(detachedSpectator);
        assertFalse(session.isGameplayEndpoint(detachedSpectator));
    }

    private static FTClient clientFor(RoomPlayer roomPlayer) {
        FTClient client = mock(FTClient.class);
        when(client.getRoomPlayer()).thenReturn(roomPlayer);
        return client;
    }

    private static RoomPlayer roomPlayer(long playerId, short position) {
        RoomPlayer roomPlayer = mock(RoomPlayer.class);
        when(roomPlayer.getPlayerId()).thenReturn(playerId);
        when(roomPlayer.getPosition()).thenReturn(position);
        return roomPlayer;
    }

    private static Pet pet(long petId, String name) {
        Pet pet = mock(Pet.class);
        PetStatistic statistic = new PetStatistic();
        when(pet.getId()).thenReturn(petId);
        when(pet.getName()).thenReturn(name);
        when(pet.getPetStatistic()).thenReturn(statistic);
        return pet;
    }
}
