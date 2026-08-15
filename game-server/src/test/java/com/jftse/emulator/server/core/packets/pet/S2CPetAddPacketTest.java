package com.jftse.emulator.server.core.packets.pet;

import com.jftse.entities.database.model.pet.Pet;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

class S2CPetAddPacketTest {
    @Test
    void writesDisplayedLevelTwoFiftyAsUnsignedByteWithoutChangingType() {
        Pet pet = new Pet();
        pet.setType((byte) 0);
        pet.setName("Pikaro");
        pet.setLevel(250);
        pet.setExpPoints(1_408_515);
        pet.setHp(180);
        pet.setStrength((byte) 0);
        pet.setStamina((byte) 0);
        pet.setDexterity((byte) 0);
        pet.setWillpower((byte) 0);
        pet.setHunger(100);
        pet.setEnergy(50);
        pet.setLifeMax(60);
        pet.setValidUntil(new Date(0L));

        byte[] raw = new S2CPetAddPacket(pet).toBytes();
        ByteBuffer payload = ByteBuffer.wrap(raw, 8, raw.length - 8).order(ByteOrder.LITTLE_ENDIAN);

        assertEquals(0, payload.get());
        byte[] name = "Pikaro".getBytes(StandardCharsets.UTF_16LE);
        byte[] actualName = new byte[name.length];
        payload.get(actualName);
        assertEquals("Pikaro", new String(actualName, StandardCharsets.UTF_16LE));
        assertEquals(0, payload.getShort());
        assertEquals((byte) 0xFA, payload.get());
        assertEquals(1_408_515, payload.getInt());
        assertEquals(180, payload.getInt());
    }
}
