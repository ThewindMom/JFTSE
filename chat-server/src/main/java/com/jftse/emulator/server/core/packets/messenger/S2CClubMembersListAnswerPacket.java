package com.jftse.emulator.server.core.packets.messenger;

import com.jftse.server.core.client.FTFriend;
import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.protocol.PacketOperations;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class S2CClubMembersListAnswerPacket extends Packet {
    public S2CClubMembersListAnswerPacket(List<FTFriend> guildMembers) {
        super(PacketOperations.S2CClubMembersListAnswer);

        this.write((byte) guildMembers.size());
        for (FTFriend guildMember : guildMembers) {
            this.write((int) guildMember.getPlayerId());
            this.write(guildMember.getName());
            this.write(guildMember.getPlayerType());
            this.write((short) guildMember.getServerId());
        }
    }
}
