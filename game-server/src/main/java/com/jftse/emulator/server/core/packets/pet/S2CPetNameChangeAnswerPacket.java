package com.jftse.emulator.server.core.packets.pet;

import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.protocol.PacketOperations;


public class S2CPetNameChangeAnswerPacket extends Packet {
    public S2CPetNameChangeAnswerPacket(short result) {
        super(PacketOperations.S2CPetNameChangeAnswer);

        this.write(result);
    }
}
