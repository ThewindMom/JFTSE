package com.jftse.emulator.server.core.rabbit.messages;

import com.jftse.emulator.server.core.rabbit.MessageTypes;
import com.jftse.server.core.rabbit.AbstractBaseMessage;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class InviteFriendMessage extends AbstractBaseMessage {
    private long senderId;
    private int senderRoomId;
    private long senderServerId;
    private String playerName;
    private int serverId;

    @Builder
    public InviteFriendMessage(long senderId, int senderRoomId, long senderServerId, String playerName, int serverId) {
        this.senderId = senderId;
        this.senderRoomId = senderRoomId;
        this.senderServerId = senderServerId;
        this.playerName = playerName;
        this.serverId = serverId;
    }

    @Override
    public String getMessageType() {
        return MessageTypes.INVITE_FRIEND.getValue();
    }
}
