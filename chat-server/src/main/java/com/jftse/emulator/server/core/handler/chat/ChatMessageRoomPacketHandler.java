package com.jftse.emulator.server.core.handler.chat;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.command.CommandManager;
import com.jftse.emulator.server.core.constants.MiscConstants;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.chat.CMSGChatMessageRoom;
import com.jftse.server.core.shared.packets.chat.SMSGChatMessageRoom;
import com.jftse.server.core.translation.ChatTranslationServices;
import com.jftse.server.core.translation.LibreTranslateTranslationService;

import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

@PacketId(CMSGChatMessageRoom.PACKET_ID)
public class ChatMessageRoomPacketHandler implements PacketHandler<FTConnection, CMSGChatMessageRoom> {
    @Override
    public void handle(FTConnection connection, CMSGChatMessageRoom chatRoomReqPacket) {
        FTClient client = connection.getClient();
        final Room room = client.getActiveRoom();
        if (room == null)
            return;

        if (!client.hasPlayer()) {
            return;
        }

        final FTPlayer player = client.getPlayer();
        final RoomPlayer roomPlayer = client.getRoomPlayer();

        boolean playerInSecretGmSlot = roomPlayer != null && roomPlayer.getPosition() == MiscConstants.InvisibleGmSlot;
        byte messageType = playerInSecretGmSlot ? (byte) 2 : chatRoomReqPacket.getType();
        SMSGChatMessageRoom chatRoomMessage = SMSGChatMessageRoom.builder()
                .type(messageType)
                .sender(player.getName())
                .message(chatRoomReqPacket.getMessage())
                .textColor(client.getTextMode())
                .build();

        if (CommandManager.getInstance().isCommand(chatRoomReqPacket.getMessage())) {
            connection.sendTCP(chatRoomMessage);
            CommandManager.getInstance().handle(connection, chatRoomReqPacket.getMessage());
            return;
        }

        boolean isTeamChat = chatRoomReqPacket.getType() == 1;
        if (isTeamChat && roomPlayer != null) {
            short senderPos = roomPlayer.getPosition();

            if (senderPos < 0) return;
            for (FTClient c : GameManager.getInstance().getClientsInRoom(room.getRoomId())) {
                RoomPlayer rp = c.getRoomPlayer();
                if (rp == null)
                    continue;

                boolean playerCanSeeMessage = areInSameTeam(senderPos, rp.getPosition()) || rp.getPosition() == MiscConstants.InvisibleGmSlot;
                if (c.hasPlayer() && rp.getPlayerId() == c.getPlayer().getId() && playerCanSeeMessage) {
                    sendMessage(
                            c,
                            client,
                            chatRoomReqPacket,
                            messageType,
                            () -> isEligibleTeamRecipient(c, room, senderPos),
                            translationService()
                    );
                }
            }
            sendMessage(
                    client,
                    client,
                    chatRoomReqPacket,
                    messageType,
                    () -> client.getActiveRoom() == room,
                    translationService()
            );
        } else {
            LibreTranslateTranslationService translationService = translationService();
            GameManager.getInstance().getClientsInRoom(room.getRoomId()).forEach(recipient ->
                    sendMessage(
                            recipient,
                            client,
                            chatRoomReqPacket,
                            messageType,
                            () -> recipient.getActiveRoom() == room,
                            translationService
                    )
            );
        }
    }

    private static LibreTranslateTranslationService translationService() {
        return ChatTranslationServices.get();
    }

    private static void sendMessage(
            FTClient recipient,
            FTClient sender,
            CMSGChatMessageRoom request,
            byte messageType,
            BooleanSupplier stillEligible,
            LibreTranslateTranslationService translationService
    ) {
        String senderName = sender.getPlayer().getName();
        int textColor = sender.getTextMode();
        long membershipGeneration = recipient.getRoomMembershipGeneration();
        recipient.getChatDelivery().enqueue(
                messageForRecipient(sender, recipient, request.getMessage(), translationService),
                message -> {
                    FTConnection connection = recipient.getConnection();
                    if (recipient.getRoomMembershipGeneration() != membershipGeneration
                            || !stillEligible.getAsBoolean()
                            || connection == null) {
                        return;
                    }
                    connection.sendTCP(
                            SMSGChatMessageRoom.builder()
                                    .type(messageType)
                                    .sender(senderName)
                                    .message(message)
                                    .textColor(textColor)
                                    .build()
                    );
                }
        );
    }

    private static boolean isEligibleTeamRecipient(FTClient recipient, Room room, short senderPosition) {
        if (recipient.getActiveRoom() != room || !recipient.hasPlayer()) {
            return false;
        }
        RoomPlayer roomPlayer = recipient.getRoomPlayer();
        return roomPlayer != null
                && roomPlayer.getPlayerId() == recipient.getPlayer().getId()
                && (areInSameTeam(senderPosition, roomPlayer.getPosition())
                || roomPlayer.getPosition() == MiscConstants.InvisibleGmSlot);
    }

    static CompletableFuture<String> messageForRecipient(
            FTClient sender,
            FTClient recipient,
            String message,
            LibreTranslateTranslationService translationService
    ) {
        if (recipient == sender || !recipient.isTranslateChatToEnglish()) {
            return CompletableFuture.completedFuture(message);
        }
        return translationService.translateToEnglish(message);
    }

    private static boolean areInSameTeam(int playerPos1, int playerPos2) {
        boolean bothInRedTeam = (playerPos1 == 0 && playerPos2 == 2) || (playerPos1 == 2 && playerPos2 == 0);
        boolean bothInBlueTeam = (playerPos1 == 1 && playerPos2 == 3) || (playerPos1 == 3 && playerPos2 == 1);
        return bothInRedTeam || bothInBlueTeam;
    }
}
