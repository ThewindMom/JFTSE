package com.jftse.emulator.server.core.handler.lobby.room;

import com.jftse.emulator.server.core.constants.RoomStatus;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomChangeSkillFree;
import com.jftse.server.core.shared.packets.lobby.room.SMSGRoomChangeSkillFree;

@PacketId(CMSGRoomChangeSkillFree.PACKET_ID)
public class RoomSkillFreeChangePacketHandler implements PacketHandler<FTConnection, CMSGRoomChangeSkillFree> {
    @Override
    public void handle(FTConnection connection, CMSGRoomChangeSkillFree packet) {
        FTClient ftClient = connection.getClient();
        Room room = ftClient.getActiveRoom();
        if (room != null) {
            RoomPlayer roomPlayer = ftClient.getRoomPlayer();
            if (roomPlayer == null || !roomPlayer.isMaster()) {
                return;
            }
            synchronized (room) {
                if (room.getStatus() != RoomStatus.NotRunning) {
                    return;
                }
                room.setSkillFree(packet.getEnable());
            }

            SMSGRoomChangeSkillFree answer = SMSGRoomChangeSkillFree.builder().enable(room.isSkillFree()).build();
            GameManager.getInstance().sendPacketToAllClientsInSameRoom(answer, connection);
        }
    }
}
