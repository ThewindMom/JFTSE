package com.jftse.emulator.server.core.handler.item;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.packets.inventory.S2CInventoryItemCountPacket;
import com.jftse.emulator.server.core.packets.item.S2CPersonalBoardPacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.protocol.IPacket;
import com.jftse.server.core.service.PersonalBoardService;
import com.jftse.server.core.service.ProfaneWordsService;
import com.jftse.server.core.shared.packets.inventory.S2CInventoryItemRemoveAnswerPacket;
import com.jftse.server.core.shared.packets.item.CMSGPersonalBoard;

@PacketId(CMSGPersonalBoard.PACKET_ID)
public class PersonalBoardRequestPacketHandler implements PacketHandler<FTConnection, CMSGPersonalBoard> {
    private static final int MIN_MESSAGE_LENGTH = 2;
    private static final int MAX_MESSAGE_LENGTH = 80;

    private final PersonalBoardService personalBoardService;
    private final ProfaneWordsService profaneWordsService;
    private final GameManager gameManager;

    public PersonalBoardRequestPacketHandler() {
        this(
                ServiceManager.getInstance().getPersonalBoardService(),
                ServiceManager.getInstance().getProfaneWordsService(),
                GameManager.getInstance()
        );
    }

    PersonalBoardRequestPacketHandler(
            PersonalBoardService personalBoardService,
            ProfaneWordsService profaneWordsService,
            GameManager gameManager
    ) {
        this.personalBoardService = personalBoardService;
        this.profaneWordsService = profaneWordsService;
        this.gameManager = gameManager;
    }

    @Override
    public void handle(FTConnection connection, CMSGPersonalBoard packet) {
        FTClient client = connection.getClient();
        if (client == null || !client.hasPlayer())
            return;

        Room room = client.getActiveRoom();
        RoomPlayer roomPlayer = client.getRoomPlayer();
        String message = packet.getMessage();
        if (room == null || roomPlayer == null || !isValidMessage(message))
            return;

        FTPlayer player = client.getPlayer();
        PersonalBoardService.UseResult result = personalBoardService.use(
                player.getPocketId(),
                (long) packet.getPlayerPocketId()
        );
        if (result.status() != PersonalBoardService.UseStatus.SUCCESS)
            return;

        IPacket inventoryPacket = result.itemRemoved()
                ? new S2CInventoryItemRemoveAnswerPacket(packet.getPlayerPocketId())
                : new S2CInventoryItemCountPacket(result.item());
        connection.sendTCP(inventoryPacket);

        room.getPersonalBoardMessages().put(player.getId(), message);
        S2CPersonalBoardPacket response = new S2CPersonalBoardPacket(player.getName(), message);
        gameManager.getClientsInRoom(room.getRoomId()).forEach(roomClient -> {
            if (roomClient.getConnection() != null)
                roomClient.getConnection().sendTCP(response);
        });
    }

    private boolean isValidMessage(String message) {
        return message != null
                && message.length() >= MIN_MESSAGE_LENGTH
                && message.length() <= MAX_MESSAGE_LENGTH
                && !profaneWordsService.textContainsProfaneWord(message);
    }
}
