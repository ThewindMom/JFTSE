package com.jftse.emulator.server.core.packets.matchplay;

import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.protocol.PacketOperations;

import java.util.ArrayList;
import java.util.List;

public class S2CGameNetworkSettingsPacket extends Packet {
    public S2CGameNetworkSettingsPacket(String host, int port, int gameSessionId, Room room,
                                        List<FTClient> clientsInRoom) {
        super(PacketOperations.S2CGameNetworkSettings);

        this.write(host);
        this.write((short) port);
        this.write(gameSessionId);

        int missingClientsCount = 4 - clientsInRoom.size();
        clientsInRoom.forEach(client -> {
            if (client.hasPlayer())
                this.write(Math.toIntExact(client.getPlayer().getId()));
        });
        for (int i = 1; i <= missingClientsCount; i++)
            this.write(0);
    }

    public S2CGameNetworkSettingsPacket(String host, int port, int gameSessionId, GameSession gameSession,
                                        List<FTClient> clientsInRoom) {
        super(PacketOperations.S2CGameNetworkSettings);

        this.write(host);
        this.write((short) port);

        this.write(gameSessionId);

        int maxClientsInRoom = 4;
        List<FTClient> endpointClients = clientsInRoom.stream()
                .filter(FTClient::hasPlayer)
                .limit(4)
                .toList();

        List<Integer> relayEndpointIds = new ArrayList<>(maxClientsInRoom);
        endpointClients.stream()
                .map(FTClient::getPlayer)
                .map(player -> Math.toIntExact(player.getId()))
                .forEach(relayEndpointIds::add);
        if (gameSession.isDedicatedBattlemonRoom()) {
            endpointClients.stream()
                    .filter(client -> gameSession.getOwnedPetSeat(client.getPlayer().getId()) != null)
                    .map(client -> Math.toIntExact(client.getPlayer().getId()))
                    .limit(maxClientsInRoom - relayEndpointIds.size())
                    .forEach(relayEndpointIds::add);
        }
        relayEndpointIds.forEach(this::write);
        for (int i = relayEndpointIds.size(); i < maxClientsInRoom; i++) {
            this.write(0);
        }
    }
}
