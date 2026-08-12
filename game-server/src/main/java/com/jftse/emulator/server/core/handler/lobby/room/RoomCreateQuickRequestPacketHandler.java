package com.jftse.emulator.server.core.handler.lobby.room;

import com.jftse.emulator.common.service.ConfigService;
import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.constants.RoomType;
import com.jftse.emulator.server.core.life.room.ClubMatchRules;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.packets.lobby.room.S2CRoomCreateAnswerPacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.constants.GameMode;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomCreateQuick;

import java.util.Random;

@PacketId(CMSGRoomCreateQuick.PACKET_ID)
public class RoomCreateQuickRequestPacketHandler implements PacketHandler<FTConnection, CMSGRoomCreateQuick> {
    @Override
    public void handle(FTConnection connection, CMSGRoomCreateQuick packet) {
        FTClient client = connection.getClient();
        // prevent multiple room creations, this might have to be adjusted into a "room join answer"
        if (client.getActiveRoom() != null)
            return;

        if (!client.hasPlayer())
            return;

        if (packet.getRoomType() == RoomType.BATTLEMON) {
            //GameManager.getInstance().handleChatLobbyJoin(client);
            return;
        }

        FTPlayer player = client.getPlayer();

        if (!client.getIsJoiningOrLeavingRoom().compareAndSet(false, true)) {
            return;
        }

        try {
            byte roomType = packet.getRoomType();
            boolean isClubRoomRequest = ClubMatchRules.isClubRoomRequest(connection.getGameServerType(),
                    roomType);
            if (packet.getMode() == -1) {
                packet.setMode(isClubRoomRequest ? (byte) GameMode.BASIC : (byte) new Random().nextInt(2));
            }

            byte playerSize = packet.getMode() == GameMode.GUARDIAN
                    ? (byte) 4
                    : packet.getPlayers() == 0 ? (byte) 2 : packet.getPlayers();
            int validationResult = ClubMatchRules.validateCreation(connection.getGameServerType(),
                    roomType, packet.getMode(), playerSize, player.getGuild());
            if (validationResult != ClubMatchRules.SUCCESS) {
                connection.sendTCP(new S2CRoomCreateAnswerPacket((char) validationResult,
                        (byte) 0, 0, (byte) 0));
                return;
            }

            Room room = new Room();
            room.setRoomId(GameManager.getInstance().getRoomId());
            room.setGameServerType(connection.getGameServerType());
            room.setRoomName(String.format("%s's room", player.getName()));
            room.setRoomType(isClubRoomRequest
                    ? ClubMatchRules.roomTypeForWireMode(packet.getMode())
                    : roomType);
            room.setAllowBattlemon(room.getRoomType() == 2 ? (byte) 1 : (byte) 0);

            room.setMode(packet.getMode());
            room.setRule((byte) 0);

            room.setPlayers(playerSize);

            if (room.getRoomType() == RoomType.BATTLEMON)
                room.setPlayers((byte) 4);

            room.setPrivate(false);
            room.setSkillFree(false);
            room.setQuickSlot(false);
            room.setLevel((byte) player.getLevel());
            room.setLevelRange((byte) -1);
            room.setBettingType('0');
            room.setBettingAmount(0);
            room.setBall(1);
            room.setMap((byte) 0);
            if (isClubRoomRequest) {
                room.setClubMatchMaxPlayTimeMinutes(Math.max(1,
                        ConfigService.getInstance().getValue("club.match.max-play-time.minutes", 5)));
            }

            GameManager.getInstance().internalHandleRoomCreate(client.getConnection(), room);
        } finally {
            client.getIsJoiningOrLeavingRoom().set(false);
        }
    }
}
