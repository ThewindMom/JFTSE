package com.jftse.emulator.server.core.handler.lobby.room;

import com.jftse.emulator.server.core.client.PetView;
import com.jftse.emulator.server.core.constants.RoomPositionState;
import com.jftse.emulator.server.core.constants.RoomStatus;
import com.jftse.emulator.server.core.constants.RoomType;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.packets.lobby.room.S2CPetRequestRoomAnswerPacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomChangeAllowBattlemon;
import com.jftse.server.core.shared.packets.lobby.room.SMSGRoomChangeAllowBattlemon;

import java.util.ArrayList;
import java.util.List;

@PacketId(CMSGRoomChangeAllowBattlemon.PACKET_ID)
public class RoomAllowBattlemonChangePacketHandler implements PacketHandler<FTConnection, CMSGRoomChangeAllowBattlemon> {
    @Override
    public void handle(FTConnection connection, CMSGRoomChangeAllowBattlemon packet) {
        FTClient client = connection.getClient();
        Room room = client.getActiveRoom();
        if (room != null) {
            RoomPlayer roomPlayer = client.getRoomPlayer();
            if (roomPlayer == null || !roomPlayer.isMaster()) {
                return;
            }
            byte allowBattlemon = room.getRoomType() == RoomType.BATTLEMON
                    ? (byte) 1
                    : packet.getAllowBattlemon() == 0 ? (byte) 0 : (byte) 1;
            List<S2CPetRequestRoomAnswerPacket> removedPets = new ArrayList<>();
            synchronized (room) {
                if (room.getStatus() != RoomStatus.NotRunning) {
                    return;
                }
                room.setAllowBattlemon(allowBattlemon);
                if (allowBattlemon == 0) {
                    for (RoomPlayer player : room.getRoomPlayerList()) {
                        PetView pet = player.getPet();
                        if (pet == null) {
                            continue;
                        }
                        player.setPet(null);
                        int petPosition = player.getPosition() + 2;
                        if (petPosition >= 0 && petPosition < room.getPositions().size() &&
                                room.getPositions().get(petPosition) == RoomPositionState.InUse &&
                                room.getRoomPlayerList().stream()
                                        .noneMatch(roomPlayerInSlot -> roomPlayerInSlot.getPosition() == petPosition)) {
                            room.getPositions().set(petPosition, RoomPositionState.Free);
                        }
                        removedPets.add(new S2CPetRequestRoomAnswerPacket(
                                S2CPetRequestRoomAnswerPacket.SUCCESS,
                                false,
                                (byte) player.getPosition(),
                                pet));
                    }
                }
            }

            SMSGRoomChangeAllowBattlemon response = SMSGRoomChangeAllowBattlemon.builder().allowBattlemon(allowBattlemon).build();
            GameManager.getInstance().sendPacketToAllClientsInSameRoom(response, connection);
            removedPets.forEach(packetToSend ->
                    GameManager.getInstance().sendPacketToAllClientsInSameRoom(packetToSend, connection));
        }
    }
}
