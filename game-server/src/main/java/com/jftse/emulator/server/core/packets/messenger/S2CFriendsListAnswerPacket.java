package com.jftse.emulator.server.core.packets.messenger;

import com.jftse.server.core.client.FTFriend;
import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.protocol.PacketOperations;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class S2CFriendsListAnswerPacket extends Packet {
    public S2CFriendsListAnswerPacket(List<FTFriend> friendList) {
        super(PacketOperations.S2CFriendsListAnswer);

        this.write((byte) friendList.size());
        for (FTFriend friend : friendList) {
            this.write((int) friend.getPlayerId());
            this.write(friend.getName());
            this.write(friend.getPlayerType());
            this.write(friend.getServerId());
        }
    }
}
