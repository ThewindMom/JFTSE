package com.jftse.emulator.server.core.handler.lobby.room;

import com.jftse.emulator.server.core.constants.RoomType;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.tournament.TournamentRoomCoordinator;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomCreate;

import java.util.Optional;

@PacketId(CMSGRoomCreate.PACKET_ID)
public class RoomCreateRequestPacketHandler implements PacketHandler<FTConnection, CMSGRoomCreate> {
    @Override
    public void handle(FTConnection connection, CMSGRoomCreate packet) {
        handle(
                connection,
                packet,
                GameManager.getInstance(),
                TournamentRoomCoordinator.getInstance());
    }

    void handle(
            FTConnection connection,
            CMSGRoomCreate packet,
            GameManager gameManager,
            TournamentRoomCoordinator tournamentCoordinator
    ) {
        FTClient client = connection.getClient();
        // prevent multiple room creations, this might have to be adjusted into a "room join answer"
        if (client.getActiveRoom() != null || !client.hasPlayer())
            return;

        if (packet.getRoomType() == RoomType.BATTLEMON) {
            //GameManager.getInstance().handleChatLobbyJoin(client);
            return;
        }

        if (!client.getIsJoiningOrLeavingRoom().compareAndSet(false, true)) {
            return;
        }

        Optional<com.jftse.server.core.tournament.TournamentService.AssignedMatch> tournamentMatch =
                tournamentCoordinator.requestedMatch(packet.getRoomName(), client.getPlayer().getId());
        if (tournamentCoordinator.isTournamentRoomRequest(packet.getRoomName()) && tournamentMatch.isEmpty()) {
            client.getIsJoiningOrLeavingRoom().set(false);
            return;
        }

        Room room = new Room();
        room.setRoomName(packet.getRoomName());
        room.setRoomType(packet.getRoomType());
        room.setAllowBattlemon(room.getRoomType() == 2 ? (byte) 1 : (byte) 0);

        room.setMode(packet.getMode());
        room.setRule(packet.getRule());
        room.setPlayers(packet.getPlayers());
        room.setPrivate(packet.getIsPrivate());
        room.setPassword(packet.getPassword());
        room.setSkillFree(packet.getSkillFree());
        room.setQuickSlot(packet.getQuickSlot());
        room.setLevel((byte) client.getPlayer().getLevel());
        room.setLevelRange(packet.getLevelRange());
        room.setBettingType(packet.getBettingType());
        room.setBettingAmount(packet.getBettingAmount());
        room.setBall(packet.getBall());
        room.setMap(packet.getMapId());

        boolean tournamentRoomBound = false;
        try {
            synchronized (gameManager) {
                room.setRoomId(gameManager.getRoomId());
                if (tournamentMatch.isPresent()) {
                    tournamentCoordinator.configureRoom(room, tournamentMatch.get(), client.getPlayer().getLevel());
                    if (!tournamentCoordinator.bindRoom(room, tournamentMatch.get(), client.getPlayer().getId())) {
                        return;
                    }
                    tournamentRoomBound = true;
                }

                gameManager.internalHandleRoomCreate(client.getConnection(), room);
            }
            tournamentRoomBound = false;
        } finally {
            if (tournamentRoomBound) {
                tournamentCoordinator.release(room);
            }
            client.getIsJoiningOrLeavingRoom().set(false);
        }
    }
}
