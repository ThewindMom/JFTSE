package com.jftse.emulator.server.core.handler.pet;

import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.packets.inventory.S2CInventoryItemCountPacket;
import com.jftse.emulator.server.core.packets.pet.S2CPetDataAnswerPacket;
import com.jftse.emulator.server.core.packets.pet.S2CPetNameChangeAnswerPacket;
import com.jftse.emulator.server.core.service.BattlemonLifecycleService;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.pet.Pet;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.protocol.IPacket;
import com.jftse.server.core.service.PetService;
import com.jftse.server.core.service.ProfaneWordsService;
import com.jftse.server.core.shared.packets.inventory.S2CInventoryItemRemoveAnswerPacket;
import com.jftse.server.core.shared.packets.pet.CMSGPetNameCheck;

@PacketId(CMSGPetNameCheck.PACKET_ID)
public class PetNameChangeRequestPacketHandler implements PacketHandler<FTConnection, CMSGPetNameCheck> {
    private final BattlemonLifecycleService battlemonLifecycleService;
    private final PetService petService;
    private final ProfaneWordsService profaneWordsService;

    public PetNameChangeRequestPacketHandler() {
        ServiceManager serviceManager = ServiceManager.getInstance();
        battlemonLifecycleService = serviceManager.getBattlemonLifecycleService();
        petService = serviceManager.getPetService();
        profaneWordsService = serviceManager.getProfaneWordsService();
    }

    @Override
    public void handle(FTConnection connection, CMSGPetNameCheck packet) {
        FTClient client = connection.getClient();
        if (!client.hasPlayer() || packet.getNewPetName() == null ||
                profaneWordsService.textContainsProfaneWord(packet.getNewPetName())) {
            connection.sendTCP(new S2CPetNameChangeAnswerPacket((short) 1));
            return;
        }

        BattlemonLifecycleService.MutationResult result = battlemonLifecycleService.renamePet(
                client.getPlayer().getId(), client.getPlayer().getPocketId(),
                Integer.toUnsignedLong(packet.getItemId()), packet.getPetType(), packet.getNewPetName());
        if (!result.successful()) {
            connection.sendTCP(new S2CPetNameChangeAnswerPacket((short) 1));
            return;
        }

        final IPacket petRefresh = new S2CPetDataAnswerPacket(
                petService.findAllByPlayerId(client.getPlayer().getId()));
        IPacket inventoryRefresh = inventoryUpdate(result);
        refreshSelectedPet(client, result.pet());
        connection.sendTCP(new S2CPetNameChangeAnswerPacket((short) 0));
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
