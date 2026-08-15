package com.jftse.emulator.server.core.handler.pet;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.client.PetView;
import com.jftse.emulator.server.core.handler.player.QuickSlotUseRequestHandler;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.packets.inventory.S2CInventoryItemCountPacket;
import com.jftse.emulator.server.core.packets.pet.S2CPetDataAnswerPacket;
import com.jftse.emulator.server.core.packets.pet.S2CPetNameChangeAnswerPacket;
import com.jftse.emulator.server.core.packets.pet.S2CPetReviveAnswerPacket;
import com.jftse.emulator.server.core.service.BattlemonLifecycleService;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.pet.Pet;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.server.core.protocol.IPacket;
import com.jftse.server.core.service.PetService;
import com.jftse.server.core.service.PlayerPocketService;
import com.jftse.server.core.service.ProfaneWordsService;
import com.jftse.server.core.shared.packets.inventory.S2CInventoryItemRemoveAnswerPacket;
import com.jftse.server.core.shared.packets.pet.CMSGPetNameCheck;
import com.jftse.server.core.shared.packets.pet.CMSGPickupPet;
import com.jftse.server.core.shared.packets.pet.CMSGRevivePet;
import com.jftse.server.core.shared.packets.pet.SMSGPickupPet;
import com.jftse.server.core.shared.packets.player.CMSGUseQuickSlot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Answers.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BattlemonLifecycleHandlerTest {
    private Object previousServiceManager;
    private ServiceManager serviceManager;
    private BattlemonLifecycleService lifecycleService;
    private PetService petService;
    private PlayerPocketService playerPocketService;
    private ProfaneWordsService profaneWordsService;
    private FTConnection connection;
    private FTClient client;
    private FTPlayer player;
    private List<IPacket> sentPackets;

    @BeforeEach
    void setUp() {
        previousServiceManager = ReflectionTestUtils.getField(ServiceManager.class, "instance");
        serviceManager = mock(ServiceManager.class);
        lifecycleService = mock(BattlemonLifecycleService.class);
        petService = mock(PetService.class);
        playerPocketService = mock(PlayerPocketService.class);
        profaneWordsService = mock(ProfaneWordsService.class);
        when(serviceManager.getBattlemonLifecycleService()).thenReturn(lifecycleService);
        when(serviceManager.getPetService()).thenReturn(petService);
        when(serviceManager.getPlayerPocketService()).thenReturn(playerPocketService);
        when(serviceManager.getProfaneWordsService()).thenReturn(profaneWordsService);
        ReflectionTestUtils.setField(ServiceManager.class, "instance", serviceManager);

        sentPackets = new ArrayList<>();
        connection = mock(FTConnection.class, invocation -> {
            if (invocation.getMethod().getName().equals("sendTCP")) {
                for (Object argument : invocation.getArguments()) {
                    if (argument instanceof IPacket packet) {
                        sentPackets.add(packet);
                    } else if (argument instanceof IPacket[] packets) {
                        sentPackets.addAll(List.of(packets));
                    }
                }
                return null;
            }
            return RETURNS_DEFAULTS.answer(invocation);
        });
        client = mock(FTClient.class);
        player = mock(FTPlayer.class);
        when(connection.getClient()).thenReturn(client);
        when(client.hasPlayer()).thenReturn(true);
        when(client.getPlayer()).thenReturn(player);
        when(player.getId()).thenReturn(5L);
        when(player.getPocketId()).thenReturn(5L);
        when(petService.findAllByPlayerId(5L)).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(ServiceManager.class, "instance", previousServiceManager);
    }

    @Test
    void renameSuccessSendsNativeResultThenPetAndInventoryRefresh() {
        Pet pet = pet(2L, (byte) 1, "Renamed");
        PetView selected = PetView.of(pet);
        when(client.getActivePet()).thenReturn(selected);
        when(lifecycleService.renamePet(5L, 5L, 22L, (byte) 1, "Renamed"))
                .thenReturn(new BattlemonLifecycleService.MutationResult(true, pet, 22L, 2));
        CMSGPetNameCheck packet = mock(CMSGPetNameCheck.class);
        when(packet.getItemId()).thenReturn(22);
        when(packet.getPetType()).thenReturn((byte) 1);
        when(packet.getNewPetName()).thenReturn("Renamed");

        new PetNameChangeRequestPacketHandler().handle(connection, packet);

        assertEquals(3, sentPackets.size());
        assertInstanceOf(S2CPetNameChangeAnswerPacket.class, sentPackets.get(0));
        assertArrayEquals(new byte[]{0, 0}, payload(sentPackets.get(0)));
        assertInstanceOf(S2CPetDataAnswerPacket.class, sentPackets.get(1));
        assertInstanceOf(S2CInventoryItemCountPacket.class, sentPackets.get(2));
        verify(client).setActivePet(pet);
    }

    @Test
    void renameFailureSendsOnlyGenericNativeFailureResult() {
        when(lifecycleService.renamePet(5L, 5L, 22L, (byte) 1, "Rejected"))
                .thenReturn(BattlemonLifecycleService.MutationResult.failed(22L));
        CMSGPetNameCheck packet = mock(CMSGPetNameCheck.class);
        when(packet.getItemId()).thenReturn(22);
        when(packet.getPetType()).thenReturn((byte) 1);
        when(packet.getNewPetName()).thenReturn("Rejected");

        new PetNameChangeRequestPacketHandler().handle(connection, packet);

        assertEquals(1, sentPackets.size());
        assertInstanceOf(S2CPetNameChangeAnswerPacket.class, sentPackets.getFirst());
        assertArrayEquals(new byte[]{1, 0}, payload(sentPackets.getFirst()));
        verify(client, never()).setActivePet(any(Pet.class));
    }

    @Test
    void reviveSuccessSendsResultThenPetRefreshAndLastItemRemoval() {
        Pet pet = pet(2L, (byte) 1, "Revived");
        when(lifecycleService.revivePet(5L, 5L, 21L, (byte) 1))
                .thenReturn(new BattlemonLifecycleService.MutationResult(true, pet, 21L, 0));
        CMSGRevivePet packet = mock(CMSGRevivePet.class);
        when(packet.getItemId()).thenReturn(21);
        when(packet.getPetType()).thenReturn((byte) 1);

        new PetReviveRequestPacketHandler().handle(connection, packet);

        assertEquals(3, sentPackets.size());
        assertInstanceOf(S2CPetReviveAnswerPacket.class, sentPackets.get(0));
        assertArrayEquals(new byte[]{0, 0}, payload(sentPackets.get(0)));
        assertInstanceOf(S2CPetDataAnswerPacket.class, sentPackets.get(1));
        assertInstanceOf(S2CInventoryItemRemoveAnswerPacket.class, sentPackets.get(2));
    }

    @Test
    void petItemUseHasNoInventedResultPacketAndRefreshesPetBeforeInventory() {
        PlayerPocket item = new PlayerPocket();
        item.setCategory("PET_ITEM");
        Pet pet = pet(2L, (byte) 1, "Fed");
        when(playerPocketService.getItemAsPocket(24L, 5L)).thenReturn(item);
        when(client.getActivePet()).thenReturn(PetView.of(pet));
        when(lifecycleService.usePetItem(5L, 5L, 2L, 24L))
                .thenReturn(new BattlemonLifecycleService.MutationResult(true, pet, 24L, 2));
        CMSGUseQuickSlot packet = mock(CMSGUseQuickSlot.class);
        when(packet.getQuickSlotId()).thenReturn(24);

        new QuickSlotUseRequestHandler().handle(connection, packet);

        assertEquals(2, sentPackets.size());
        assertInstanceOf(S2CPetDataAnswerPacket.class, sentPackets.get(0));
        assertInstanceOf(S2CInventoryItemCountPacket.class, sentPackets.get(1));
        verify(client).setActivePet(pet);
    }

    @Test
    void pickupRejectsDeadExpiredAndMissingExpiryPetsBeforeSettingActiveSelection() {
        Pet dead = pet(2L, (byte) 1, "Dead");
        dead.setAlive(false);
        dead.setValidUntil(Date.from(Instant.now().plusSeconds(3600)));
        Pet expired = pet(3L, (byte) 1, "Expired");
        expired.setAlive(true);
        expired.setValidUntil(Date.from(Instant.now().minusSeconds(1)));
        Pet missingExpiry = pet(4L, (byte) 1, "Missing expiry");
        missingExpiry.setAlive(true);
        Pet valid = pet(5L, (byte) 1, "Valid");
        valid.setAlive(true);
        valid.setValidUntil(Date.from(Instant.now().plusSeconds(3600)));
        when(petService.findAllByPlayerId(5L))
                .thenReturn(List.of(dead), List.of(expired), List.of(missingExpiry), List.of(valid));

        PetPickupRequestPacketHandler handler = new PetPickupRequestPacketHandler();
        CMSGPickupPet request = CMSGPickupPet.builder().petType(1).build();
        handler.handle(connection, request);
        handler.handle(connection, request);
        handler.handle(connection, request);
        handler.handle(connection, request);

        assertEquals(List.of((short) 1, (short) 1, (short) 1, (short) 0), sentPackets.stream()
                .map(SMSGPickupPet.class::cast)
                .map(SMSGPickupPet::getResult)
                .toList());
        verify(client).setActivePet(valid);
        verify(client, never()).setActivePet(dead);
        verify(client, never()).setActivePet(expired);
        verify(client, never()).setActivePet(missingExpiry);
    }

    private static Pet pet(long id, byte type, String name) {
        Pet pet = new Pet();
        pet.setId(id);
        pet.setType(type);
        pet.setName(name);
        pet.setLevel(1);
        pet.setHp(200);
        pet.setStrength((byte) 1);
        pet.setStamina((byte) 1);
        pet.setDexterity((byte) 1);
        pet.setWillpower((byte) 1);
        pet.setHunger(40);
        pet.setEnergy(30);
        return pet;
    }

    private static byte[] payload(IPacket packet) {
        byte[] raw = packet.toBytes();
        byte[] payload = new byte[raw.length - 8];
        System.arraycopy(raw, 8, payload, 0, payload.length);
        return payload;
    }
}
