package com.jftse.emulator.server.core.packets.pet;

import com.jftse.server.core.shared.packets.pet.CMSGRequestPet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CMSGRequestPetTest {
    @Test
    void readsTheNativeClientsOneByteSlotValue() {
        byte[] rawPacket = new byte[]{0, 0, 0, 0, 0x56, 0x1D, 0x01, 0x00, 0x03};

        CMSGRequestPet packet = CMSGRequestPet.fromBytes(rawPacket);

        assertEquals((byte) 3, packet.getSlot());
        assertEquals(0, packet.remaining());
    }
}
