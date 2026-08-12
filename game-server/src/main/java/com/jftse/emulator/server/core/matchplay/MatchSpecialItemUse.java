package com.jftse.emulator.server.core.matchplay;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.packets.inventory.S2CInventoryItemCountPacket;
import com.jftse.emulator.server.core.packets.inventory.S2CInventoryWearSpecialAnswerPacket;
import com.jftse.emulator.server.core.packets.player.S2CPlayerStatusPointChangePacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.service.SpecialSlotEquipmentService;
import com.jftse.server.core.shared.packets.inventory.S2CInventoryItemRemoveAnswerPacket;

import java.util.ArrayList;
import java.util.List;

public final class MatchSpecialItemUse {
    private MatchSpecialItemUse() {
    }

    public static void consume(FTClient client, short gameMode) {
        FTPlayer player = client.getPlayer();
        SpecialSlotEquipmentService.MatchStatItemUseResult result = ServiceManager.getInstance()
                .getSpecialSlotEquipmentService()
                .consumeMatchStatItems(player.getPlayer(), gameMode);

        if (result.updatedItems().isEmpty() && result.removedItemIds().isEmpty())
            return;

        List<Packet> packets = new ArrayList<>();
        result.updatedItems().stream()
                .map(S2CInventoryItemCountPacket::new)
                .forEach(packets::add);

        if (!result.removedItemIds().isEmpty()) {
            player.loadSpecialSlots();
            packets.add(new S2CInventoryWearSpecialAnswerPacket(result.specialSlots()));
            result.removedItemIds().stream()
                    .map(Math::toIntExact)
                    .map(S2CInventoryItemRemoveAnswerPacket::new)
                    .forEach(packets::add);
            packets.add(new S2CPlayerStatusPointChangePacket(player));
        }

        client.getConnection().sendTCP(packets.toArray(Packet[]::new));
    }
}
