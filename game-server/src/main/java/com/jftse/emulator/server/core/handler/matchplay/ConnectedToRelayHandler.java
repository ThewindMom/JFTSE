package com.jftse.emulator.server.core.handler.matchplay;

import com.jftse.emulator.server.core.constants.RoomStatus;
import com.jftse.emulator.server.core.life.room.ClubMatchRules;
import com.jftse.emulator.server.core.life.room.ClubMatchState;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.matchplay.CMSGConnectedToRelay;
import com.jftse.server.core.shared.packets.matchplay.SMSGConnectedToRelay;

import java.util.concurrent.ConcurrentLinkedDeque;

@PacketId(CMSGConnectedToRelay.PACKET_ID)
public class ConnectedToRelayHandler implements PacketHandler<FTConnection, CMSGConnectedToRelay> {
    @Override
    public void handle(FTConnection connection, CMSGConnectedToRelay packet) {
        FTClient ftClient = connection.getClient();
        if (ftClient == null) {
            return;
        }

        Room room = ftClient.getActiveRoom();
        RoomPlayer roomPlayer = ftClient.getRoomPlayer();
        if (room == null || ftClient.getActiveGameSession() == null || roomPlayer == null) {
            return;
        }

        synchronized (room) {
            if (room.getStatus() != RoomStatus.StartingGame
                    && room.getStatus() != RoomStatus.RelayConnectionSuccess) {
                return;
            }
            if (ClubMatchRules.isClubMatch(room)
                    && room.getClubMatchState().getParticipants().stream()
                    .noneMatch(participant -> matches(roomPlayer, participant))) {
                return;
            }
        }

        if (!roomPlayer.getConnectedToRelay().compareAndSet(false, true)) {
            SMSGConnectedToRelay answer = SMSGConnectedToRelay.builder().result((byte) 1).build();
            connection.sendTCP(answer);
            synchronized (room) {
                if (room.getStatus() == RoomStatus.StartingGame
                        || room.getStatus() == RoomStatus.RelayConnectionSuccess) {
                    room.setStatus(RoomStatus.RelayConnectionFailed);
                }
            }
            return;
        }

        ConcurrentLinkedDeque<RoomPlayer> roomPlayerList = room.getRoomPlayerList();
        boolean allConnected = ClubMatchRules.isClubMatch(room)
                ? room.getClubMatchState().getParticipants().stream()
                .allMatch(participant -> roomPlayerList.stream()
                        .anyMatch(player -> matches(player, participant)
                                && player.getConnectedToRelay().get()))
                : roomPlayerList.stream().allMatch(player -> player.getConnectedToRelay().get());
        if (allConnected) {
            synchronized (room) {
                if (room.getStatus() == RoomStatus.StartingGame) {
                    room.setStatus(RoomStatus.RelayConnectionSuccess);
                }
            }
        }
    }

    private boolean matches(RoomPlayer player, ClubMatchState.Participant participant) {
        return player.getPlayerId() == participant.playerId()
                && player.getPosition() == participant.position()
                && player.getGuild() != null
                && participant.guildId() != null
                && player.getGuild().id() == participant.guildId();
    }
}
