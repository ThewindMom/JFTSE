package com.jftse.emulator.server.core.packets.matchplay;

import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.server.core.matchplay.battle.PlayerBattleState;
import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.protocol.PacketOperations;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

public class S2CGameSetNameColorAndRemoveBlackBar extends Packet {
    public S2CGameSetNameColorAndRemoveBlackBar(Room room) {
        super(PacketOperations.S2CGameSetNameColorAndRemoveBlackBar);

        if (room == null) {
            this.write((char) 0);
        } else {
            final ConcurrentLinkedDeque<RoomPlayer> roomPlayerList = room.getRoomPlayerList();
            List<RoomPlayer> activePlayers = roomPlayerList.stream()
                    .filter(x -> x.getPosition() < 4)
                    .collect(Collectors.toList());

            this.write((char) activePlayers.size());
            for (RoomPlayer roomPlayer : activePlayers) {
                this.write(roomPlayer.getPosition());
                this.write(roomPlayer.getPosition());
            }
        }
    }

    public S2CGameSetNameColorAndRemoveBlackBar(List<PlayerBattleState> livingPlayers) {
        super(PacketOperations.S2CGameSetNameColorAndRemoveBlackBar);

        if (livingPlayers == null || livingPlayers.isEmpty()) {
            this.write((char) 0);
            return;
        }

        this.write((char) livingPlayers.size());
        for (PlayerBattleState playerBattleState : livingPlayers) {
            short position = (short) playerBattleState.getPosition();
            this.write(position);
            this.write(position);
        }
    }
}
