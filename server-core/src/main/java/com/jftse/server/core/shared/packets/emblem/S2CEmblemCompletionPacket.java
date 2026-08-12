package com.jftse.server.core.shared.packets.emblem;

import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.protocol.PacketOperations;
import com.jftse.server.core.service.EmblemCompletionResult;
import com.jftse.server.core.service.EmblemRewardItem;

public final class S2CEmblemCompletionPacket extends Packet {
    public S2CEmblemCompletionPacket(EmblemCompletionResult result) {
        super(PacketOperations.S2CEmblemComplete);
        write(result.status().wireValue());
        if (result.status().wireValue() != 0) return;
        write(result.level(), result.gold(), result.exp(), (char) result.rewards().size());
        for (EmblemRewardItem item : result.rewards()) {
            write(item.pocketId(), item.category(), item.itemIndex(), item.useType(), item.count(), item.created(),
                    item.enchantStrength(), item.enchantStamina(), item.enchantDexterity(), item.enchantWillpower(),
                    item.enchantElement(), item.enchantLevel());
        }
    }
}
