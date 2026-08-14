package com.jftse.emulator.server.core.handler.lobby.room;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.constants.RoomPositionState;
import com.jftse.emulator.server.core.constants.RoomType;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.packets.lobby.room.S2CPetRequestRoomAnswerPacket;
import com.jftse.emulator.server.core.packets.lobby.room.S2CRoomInformationPacket;
import com.jftse.emulator.server.core.packets.lobby.room.S2CRoomListAnswerPacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.constants.GameMode;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomChangeGameMode;
import com.jftse.server.core.shared.packets.lobby.room.SMSGRoomChangeReady;

@PacketId(CMSGRoomChangeGameMode.PACKET_ID)
public class GameModeChangePacketHandler implements PacketHandler<FTConnection, CMSGRoomChangeGameMode> {
    @Override
    public void handle(FTConnection connection, CMSGRoomChangeGameMode packet) {
        FTClient client = connection.getClient();
        if (!client.hasPlayer()) {
            return;
        }

        Room room = client.getActiveRoom();

        if (room != null) {
            synchronized (room) {
                boolean dedicatedBattlemon = room.getRoomType() == RoomType.BATTLEMON;
                boolean leavingEnhancedGuardian = room.getMode() == GameMode.GUARDIAN &&
                        room.getAllowBattlemon() != 0 && packet.getMode() != GameMode.GUARDIAN;
                if (dedicatedBattlemon &&
                                packet.getMode() != GameMode.BASIC && packet.getMode() != GameMode.BATTLE) {
                    return;
                }
                room.setMode(packet.getMode());
                if (dedicatedBattlemon) {
                    room.getRoomPlayerList().forEach(player -> {
                        player.setReady(false);
                        SMSGRoomChangeReady changeReady = SMSGRoomChangeReady.builder()
                                .position(player.getPosition())
                                .ready(false)
                                .build();
                        GameManager.getInstance().sendPacketToAllClientsInSameRoom(changeReady, connection);
                    });
                }
                if (leavingEnhancedGuardian) {
                    room.getRoomPlayerList().forEach(player -> {
                        if (player.getPet() == null) {
                            return;
                        }
                        int petPosition = player.getPosition() + 2;
                        if (petPosition >= 0 && petPosition < room.getPositions().size() &&
                                room.getPositions().get(petPosition) == RoomPositionState.InUse &&
                                room.getRoomPlayerList().stream()
                                        .noneMatch(other -> other != player && other.getPosition() == petPosition)) {
                            room.getPositions().set(petPosition, RoomPositionState.Free);
                        }
                        S2CPetRequestRoomAnswerPacket response = new S2CPetRequestRoomAnswerPacket(
                                S2CPetRequestRoomAnswerPacket.SUCCESS,
                                false,
                                (byte) player.getPosition(),
                                player.getPet());
                        player.setPet(null);
                        GameManager.getInstance().sendPacketToAllClientsInSameRoom(response, connection);
                    });
                }
            }

            S2CRoomInformationPacket roomInformationPacket = new S2CRoomInformationPacket(room);
            GameManager.getInstance().getClientsInRoom(room.getRoomId()).forEach(c -> {
                if (c.getConnection() != null) {
                    c.getConnection().sendTCP(roomInformationPacket);
                }
            });

            FTPlayer player = client.getPlayer();
            GameManager.getInstance().getClientsInLobby().forEach(c -> {
                boolean isActivePlayer = c.hasPlayer() && c.getPlayer().getId() == player.getId();
                if (isActivePlayer)
                    return;

                if (c.getConnection() != null) {
                    S2CRoomListAnswerPacket roomListAnswerPacket = new S2CRoomListAnswerPacket(GameManager.getInstance().getFilteredRoomsForClient(c));
                    c.getConnection().sendTCP(roomListAnswerPacket);
                }
            });
        }
    }
}
