package com.jftse.emulator.server.core.rabbit.messages;

import com.jftse.emulator.server.core.rabbit.MessageTypes;
import lombok.Builder;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class PlayWithFriendMessage extends InviteFriendMessage {
    @Builder(builderMethodName = "playWithFriendBuilder")
    public PlayWithFriendMessage(long senderId, int senderRoomId, long senderServerId, String playerName, int serverId) {
        super(senderId, senderRoomId, senderServerId, playerName, serverId);
    }

    @Override
    public String getMessageType() {
        return MessageTypes.PLAY_WITH_FRIEND.getValue();
    }
}
