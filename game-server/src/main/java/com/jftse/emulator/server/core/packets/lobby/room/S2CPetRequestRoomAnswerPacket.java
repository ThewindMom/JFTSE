package com.jftse.emulator.server.core.packets.lobby.room;

import com.jftse.emulator.server.core.client.PetView;
import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.protocol.PacketOperations;

public class S2CPetRequestRoomAnswerPacket extends Packet {
    public static final byte SUCCESS = 0;
    public static final byte NO_PET_SELECTED = 1;
    public static final byte NO_PERMISSION = 2;
    public static final byte NO_FREE_SLOT = 3;
    public static final byte PET_NOT_ALLOWED = 4;
    public static final byte CAN_NOT_ADD_PET = 5;

    public S2CPetRequestRoomAnswerPacket(byte result, boolean isAdd, byte slot, PetView pet) {
        super(PacketOperations.S2CPetRequestRoomAnswer);

        this.write(result);
        this.write(isAdd);
        this.write(slot);

        if (pet != null) {
            this.write(pet.name());
            this.write((byte) pet.level());
            this.write((byte) pet.type());
            this.write(pet.hp());
            this.write((byte) pet.strength());
            this.write((byte) pet.stamina());
            this.write((byte) pet.dexterity());
            this.write((byte) pet.willpower());
            this.write(pet.hunger());
            this.write(pet.energy());
        }
    }
}
