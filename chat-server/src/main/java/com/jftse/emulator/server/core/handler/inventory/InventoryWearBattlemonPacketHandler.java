package com.jftse.emulator.server.core.handler.inventory;

import com.jftse.emulator.server.core.client.EquippedPetSlots;
import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.packets.inventory.S2CInventoryWearBattlemonAnswerPacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.player.BattlemonSlotEquipment;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.service.BattlemonSlotEquipmentService;
import com.jftse.server.core.shared.packets.inventory.CMSGInventoryWearBattlemon;

@PacketId(CMSGInventoryWearBattlemon.PACKET_ID)
public class InventoryWearBattlemonPacketHandler implements PacketHandler<FTConnection, CMSGInventoryWearBattlemon> {
    private final BattlemonSlotEquipmentService battlemonSlotEquipmentService;

    public InventoryWearBattlemonPacketHandler() {
        battlemonSlotEquipmentService = ServiceManager.getInstance().getBattlemonSlotEquipmentService();
    }

    @Override
    public void handle(FTConnection connection, CMSGInventoryWearBattlemon packet) {
        FTClient client = connection.getClient();
        if (!client.hasPlayer())
            return;

        FTPlayer player = client.getPlayer();
        BattlemonSlotEquipment equipment = battlemonSlotEquipmentService.updateBattlemonSlots(
                player.getPlayer(), packet.getBattlemonSlotList());
        EquippedPetSlots slots = EquippedPetSlots.of(equipment);
        player.setPetSlots(slots);

        connection.sendTCP(new S2CInventoryWearBattlemonAnswerPacket(slots.toList()));
    }
}
