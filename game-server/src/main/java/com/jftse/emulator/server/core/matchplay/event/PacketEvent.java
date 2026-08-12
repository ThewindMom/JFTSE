package com.jftse.emulator.server.core.matchplay.event;

import com.jftse.emulator.server.core.constants.PacketEventType;
import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.life.room.Room;
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
    private PacketEventType packetEventType;
    private GameSession expectedGameSession;
    private Room expectedRoom;
    private boolean allowDetachedSession;

    @Builder
    public PacketEvent(FTConnection sender, FTClient client, Packet packet, PacketEventType packetEventType,
                       GameSession expectedGameSession, boolean allowDetachedSession,
                       long currentTime, long delayMS) {
        super(currentTime, delayMS);

        this.sender = sender;
        this.client = client;
        this.packet = packet;
        this.packetEventType = packetEventType;
        this.expectedGameSession = expectedGameSession;
        this.expectedRoom = client.getActiveRoom();
        this.allowDetachedSession = allowDetachedSession;
    }

    @Override
    protected void execute() {
        GameSession activeGameSession = client.getActiveGameSession();
        if (packetEventType == PacketEventType.FIRE_DELAYED &&
                activeGameSession != expectedGameSession &&
                !(allowDetachedSession && activeGameSession == null && client.getActiveRoom() == expectedRoom)) {
            return;
        }
        sender.sendTCP(packet);
    }
}
