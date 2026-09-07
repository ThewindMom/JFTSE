package com.jftse.emulator.server.core.handler.player;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.client.PetView;
import com.jftse.emulator.server.core.life.item.BaseItem;
import com.jftse.emulator.server.core.life.item.ItemFactory;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.packets.inventory.S2CInventoryItemCountPacket;
import com.jftse.emulator.server.core.packets.pet.S2CPetDataAnswerPacket;
import com.jftse.emulator.server.core.rabbit.service.RProducerService;
import com.jftse.emulator.server.core.service.BattlemonLifecycleService;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.item.EItemCategory;
import com.jftse.server.core.protocol.IPacket;
import com.jftse.server.core.service.PetService;
import com.jftse.server.core.service.PlayerPocketService;
import com.jftse.server.core.shared.packets.inventory.S2CInventoryItemRemoveAnswerPacket;
import com.jftse.server.core.shared.packets.player.CMSGUseQuickSlot;
import com.jftse.server.core.shared.rabbit.messages.PacketMessage;
import org.springframework.util.MultiValueMap;

@PacketId(CMSGUseQuickSlot.PACKET_ID)
public class QuickSlotUseRequestHandler implements PacketHandler<FTConnection, CMSGUseQuickSlot> {
    private final RProducerService rProducerService;
    private final BattlemonLifecycleService battlemonLifecycleService;
    private final PetService petService;
    private final PlayerPocketService playerPocketService;

    public QuickSlotUseRequestHandler() {
        rProducerService = RProducerService.getInstance();
        ServiceManager serviceManager = ServiceManager.getInstance();
        battlemonLifecycleService = serviceManager.getBattlemonLifecycleService();
        petService = serviceManager.getPetService();
        playerPocketService = serviceManager.getPlayerPocketService();
    }

    @Override
    public void handle(FTConnection connection, CMSGUseQuickSlot quickSlotUseRequestPacket) {
        FTClient ftClient = connection.getClient();
        if (!ftClient.hasPlayer())
            return;

        FTPlayer player = ftClient.getPlayer();

        long itemPocketId = Integer.toUnsignedLong(quickSlotUseRequestPacket.getQuickSlotId());
        PlayerPocket playerPocket = playerPocketService.getItemAsPocket(itemPocketId, player.getPocketId());
        if (playerPocket != null && EItemCategory.PET_ITEM.getName().equals(playerPocket.getCategory())) {
            usePetItem(connection, ftClient, player, itemPocketId);
            return;
        }

        BaseItem baseItem = ItemFactory.getItem(quickSlotUseRequestPacket.getQuickSlotId(), player.getPocketId());
        if (baseItem == null)
            return;

        if (baseItem.processPlayer(player)) {
            baseItem.processPocket(player.getPocketId());
        }
        sendPackets(baseItem.getPacketsToSend());
    }

    private void usePetItem(FTConnection connection, FTClient client, FTPlayer player, long itemPocketId) {
        PetView selectedPet = client.getActivePet();
        if (selectedPet == null) return;

        BattlemonLifecycleService.MutationResult result = battlemonLifecycleService.usePetItem(
                player.getId(), player.getPocketId(), selectedPet.id(), itemPocketId);
        if (!result.successful()) return;

        IPacket petRefresh = new S2CPetDataAnswerPacket(petService.findAllByPlayerId(player.getId()));
        IPacket inventoryRefresh;
        if (result.remainingItemCount() > 0) {
            inventoryRefresh = new S2CInventoryItemCountPacket(
                    result.itemPocketWireId(), result.remainingItemCount());
        } else {
            inventoryRefresh = new S2CInventoryItemRemoveAnswerPacket(result.itemPocketWireId());
        }
        client.setActivePet(result.pet());
        connection.sendTCP(petRefresh);
        connection.sendTCP(inventoryRefresh);
    }

    private void sendPackets(MultiValueMap<Long, IPacket> packetsToSend) {
        packetsToSend.forEach((playerId, packets) -> {
            for (IPacket p : packets) {
                PacketMessage packetMessage = PacketMessage.builder()
                        .packet(p)
                        .receivingPlayerId(playerId)
                        .build();
                rProducerService.send(packetMessage, "game.player.quickSlot chat.player.quickSlot", "GameServer");
            }
        });
    }
}
