package com.jftse.emulator.server.core.packets.pet;

import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.protocol.PacketOperations;


public class S2CPetReviveAnswerPacket extends Packet {
    public S2CPetReviveAnswerPacket(short result) {
        super(PacketOperations.S2CPetReviveAnswer);

        this.write(result);
    }
}
