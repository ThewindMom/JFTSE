package com.jftse.emulator.server.core.packets.messenger;

import com.jftse.server.core.client.FTFriend;
import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.protocol.PacketOperations;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class S2CRelationshipAnswerPacket extends Packet {
    public S2CRelationshipAnswerPacket(FTFriend friend) {
        super(PacketOperations.S2CRelationshipAnswer);
        this.write((int) friend.getPlayerId());
        this.write(friend.getName());
        this.write(friend.getPlayerType());
        this.write((short) friend.getServerId());
    }
}
