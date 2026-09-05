package com.jftse.emulator.server.core.matchplay.event;

import com.jftse.emulator.server.core.constants.PacketEventType;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.protocol.Packet;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PacketEvent extends AbstractFireableEvent {
    private FTConnection sender;
    private FTClient client;
    private Packet packet;
    private PacketEventType packetEventType; // unused as of current state, might be useful later on
    private final Long matchGeneration;
    private final com.jftse.emulator.server.core.life.room.Room room;

    @Builder
    public PacketEvent(FTConnection sender, FTClient client, Packet packet, PacketEventType packetEventType, long currentTime, long delayMS) {
        super(currentTime, delayMS);

        this.sender = sender;
        this.client = client;
        this.packet = packet;
        this.packetEventType = packetEventType;
        synchronized (client) {
            this.matchGeneration = client.getGameSessionId() == null ? null : client.getGameSessionGeneration();
            this.room = client.getActiveRoom();
        }
    }

    @Override
    protected void execute() {
        synchronized (client) {
            if (matchGeneration == null ||
                    client.getGameSessionGeneration() == matchGeneration && client.getActiveRoom() == room) {
                sender.sendTCP(packet);
            }
        }
    }
}
