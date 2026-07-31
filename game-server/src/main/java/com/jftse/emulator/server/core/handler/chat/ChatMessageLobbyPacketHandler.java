package com.jftse.emulator.server.core.handler.chat;

import com.jftse.emulator.server.core.command.CommandManager;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.chat.CMSGChatMessageLobby;
import com.jftse.server.core.shared.packets.chat.SMSGChatMessageLobby;
import com.jftse.server.core.translation.ChatTranslationServices;
import com.jftse.server.core.translation.LibreTranslateTranslationService;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@PacketId(CMSGChatMessageLobby.PACKET_ID)
public class ChatMessageLobbyPacketHandler implements PacketHandler<FTConnection, CMSGChatMessageLobby> {
    @Override
    public void handle(FTConnection connection, CMSGChatMessageLobby chatLobbyReqPacket) {
        FTClient client = connection.getClient();
        if (!client.hasPlayer()) {
            return;
        }

        SMSGChatMessageLobby chatLobbyMessage = SMSGChatMessageLobby.builder()
                .unk(chatLobbyReqPacket.getUnk())
                .sender(client.getPlayer().getName())
                .message(chatLobbyReqPacket.getMessage())
                .textColor(client.getTextMode())
                .build();

        if (CommandManager.getInstance().isCommand(chatLobbyReqPacket.getMessage())) {
            connection.sendTCP(chatLobbyMessage);
            CommandManager.getInstance().handle(connection, chatLobbyReqPacket.getMessage());
            return;
        }

        List<FTClient> clientList = GameManager.getInstance().getClients().stream()
                .filter(FTClient::isInLobby)
                .toList();

        LibreTranslateTranslationService translationService = ChatTranslationServices.get();
        clientList.forEach(recipient ->
                sendMessage(recipient, client, chatLobbyReqPacket, translationService)
        );
    }

    private static void sendMessage(
            FTClient recipient,
            FTClient sender,
            CMSGChatMessageLobby request,
            LibreTranslateTranslationService translationService
    ) {
        String senderName = sender.getPlayer().getName();
        int textColor = sender.getTextMode();
        long membershipGeneration = recipient.getLobbyMembershipGeneration();
        recipient.getChatDelivery().enqueue(
                messageForRecipient(sender, recipient, request.getMessage(), translationService),
                message -> {
                    FTConnection connection = recipient.getConnection();
                    if (!recipient.isInLobby()
                            || recipient.getLobbyMembershipGeneration() != membershipGeneration
                            || connection == null) {
                        return;
                    }
                    connection.sendTCP(
                            SMSGChatMessageLobby.builder()
                                    .unk(request.getUnk())
                                    .sender(senderName)
                                    .message(message)
                                    .textColor(textColor)
                                    .build()
                    );
                }
        );
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
}
