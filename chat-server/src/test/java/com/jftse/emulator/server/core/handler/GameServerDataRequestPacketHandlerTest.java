package com.jftse.emulator.server.core.handler;

import com.jftse.emulator.server.core.client.EquippedCardSlots;
import com.jftse.emulator.server.core.client.EquippedItemParts;
import com.jftse.emulator.server.core.client.EquippedPetSlots;
import com.jftse.emulator.server.core.client.EquippedQuickSlots;
import com.jftse.emulator.server.core.client.EquippedSpecialSlots;
import com.jftse.emulator.server.core.client.EquippedToolSlots;
import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.player.BattlemonSlotEquipment;
import com.jftse.entities.database.model.player.EquippedItemStats;
import com.jftse.entities.database.model.player.Player;
import com.jftse.entities.database.model.player.PlayerStatistic;
import com.jftse.server.core.service.BattlemonSlotEquipmentService;
import com.jftse.server.core.service.PlayerStatisticService;
import com.jftse.server.core.shared.packets.game.CMSGReceiveData;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameServerDataRequestPacketHandlerTest {
    @Test
    void playDataLoadsCanonicalPersistedMagicPocketSlots() {
        Object previousServiceManager = ReflectionTestUtils.getField(ServiceManager.class, "instance");
        try {
            ServiceManager serviceManager = mock(ServiceManager.class);
            BattlemonSlotEquipmentService equipmentService = mock(BattlemonSlotEquipmentService.class);
            PlayerStatisticService statisticService = mock(PlayerStatisticService.class);
            when(serviceManager.getBattlemonSlotEquipmentService()).thenReturn(equipmentService);
            when(serviceManager.getPlayerStatisticService()).thenReturn(statisticService);
            ReflectionTestUtils.setField(ServiceManager.class, "instance", serviceManager);

            Player entity = new Player();
            entity.setId(42L);
            PlayerStatistic statistic = new PlayerStatistic();
            statistic.setId(7L);
            statistic.setTotalGames(0);
            when(statisticService.findPlayerStatisticById(7L)).thenReturn(statistic);
            BattlemonSlotEquipment persistedSlots = new BattlemonSlotEquipment();
            persistedSlots.setId(9L);
            persistedSlots.setSlot1(30);
            persistedSlots.setSlot2(26);
            when(equipmentService.getOrCreate(entity)).thenReturn(persistedSlots);

            FTPlayer player = mock(FTPlayer.class);
            when(player.getPlayer()).thenReturn(entity);
            when(player.getPlayerStatisticId()).thenReturn(7L);
            when(player.getQuickSlots()).thenReturn(new EquippedQuickSlots(1, 0, 0, 0, 0, 0));
            when(player.getToolSlots()).thenReturn(new EquippedToolSlots(2, 0, 0, 0, 0, 0));
            when(player.getSpecialSlots()).thenReturn(new EquippedSpecialSlots(3, 0, 0, 0, 0));
            when(player.getCardSlots()).thenReturn(new EquippedCardSlots(4, 0, 0, 0, 0));
            when(player.getItemPartsPPId()).thenReturn(EquippedItemParts.of(
                    5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
            when(player.getItemStats()).thenReturn(new EquippedItemStats());
            AtomicReference<EquippedPetSlots> loadedSlots = new AtomicReference<>();
            doAnswer(invocation -> {
                loadedSlots.set(invocation.getArgument(0));
                return null;
            }).when(player).setPetSlots(org.mockito.ArgumentMatchers.any(EquippedPetSlots.class));
            when(player.getPetSlots()).thenAnswer(invocation -> loadedSlots.get());

            FTClient client = mock(FTClient.class);
            when(client.getPlayer()).thenReturn(player);
            when(client.updateDataRequestStep(3)).thenReturn(true);
            FTConnection connection = mock(FTConnection.class);
            when(connection.getClient()).thenReturn(client);

            new GameServerDataRequestPacketHandler().handle(
                    connection,
                    CMSGReceiveData.builder().dataType((byte) 3).build()
            );

            verify(equipmentService).getOrCreate(entity);
            assertEquals(new EquippedPetSlots(9, 30, 26), loadedSlots.get());
        } finally {
            ReflectionTestUtils.setField(ServiceManager.class, "instance", previousServiceManager);
        }
    }
}
