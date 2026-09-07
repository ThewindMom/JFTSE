package com.jftse.emulator.server.core.handler.pet;

import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.packets.inventory.S2CInventoryItemCountPacket;
import com.jftse.emulator.server.core.packets.pet.S2CPetDataAnswerPacket;
import com.jftse.emulator.server.core.packets.pet.S2CPetReviveAnswerPacket;
import com.jftse.emulator.server.core.service.BattlemonLifecycleService;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.pet.Pet;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.protocol.IPacket;
import com.jftse.server.core.service.PetService;
import com.jftse.server.core.shared.packets.inventory.S2CInventoryItemRemoveAnswerPacket;
import com.jftse.server.core.shared.packets.pet.CMSGRevivePet;

@PacketId(CMSGRevivePet.PACKET_ID)
public class PetReviveRequestPacketHandler implements PacketHandler<FTConnection, CMSGRevivePet> {
    private final BattlemonLifecycleService battlemonLifecycleService;
    private final PetService petService;

    public PetReviveRequestPacketHandler() {
        ServiceManager serviceManager = ServiceManager.getInstance();
        battlemonLifecycleService = serviceManager.getBattlemonLifecycleService();
        petService = serviceManager.getPetService();
    }

    @Override
    public void handle(FTConnection connection, CMSGRevivePet packet) {
        FTClient client = connection.getClient();
        if (!client.hasPlayer()) {
            connection.sendTCP(new S2CPetReviveAnswerPacket((short) 1));
            return;
        }

        BattlemonLifecycleService.MutationResult result = battlemonLifecycleService.revivePet(
                client.getPlayer().getId(), client.getPlayer().getPocketId(),
                Integer.toUnsignedLong(packet.getItemId()), packet.getPetType());
        if (!result.successful()) {
            connection.sendTCP(new S2CPetReviveAnswerPacket((short) 1));
            return;
        }

        IPacket petRefresh = new S2CPetDataAnswerPacket(
                petService.findAllByPlayerId(client.getPlayer().getId()));
        IPacket inventoryRefresh = inventoryUpdate(result);
        refreshSelectedPet(client, result.pet());
        connection.sendTCP(new S2CPetReviveAnswerPacket((short) 0));
        connection.sendTCP(petRefresh);
        connection.sendTCP(inventoryRefresh);
    }

    private void refreshSelectedPet(FTClient client, Pet pet) {
        if (client.getActivePet() != null && client.getActivePet().id() == pet.getId()) {
            client.setActivePet(pet);
        }
    }

    private IPacket inventoryUpdate(BattlemonLifecycleService.MutationResult result) {
        if (result.remainingItemCount() > 0) {
            return new S2CInventoryItemCountPacket(
                    result.itemPocketWireId(), result.remainingItemCount());
        }
        return new S2CInventoryItemRemoveAnswerPacket(result.itemPocketWireId());
    }
}
