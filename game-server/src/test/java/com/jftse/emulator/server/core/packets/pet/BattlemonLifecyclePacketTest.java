package com.jftse.emulator.server.core.packets.pet;

import com.jftse.server.core.shared.packets.pet.CMSGPetNameCheck;
import com.jftse.server.core.shared.packets.pet.CMSGRevivePet;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BattlemonLifecyclePacketTest {
    @Test
    void decodesNativeRenameRequestSchema() {
        byte[] name = "Renamed".getBytes(StandardCharsets.UTF_16LE);
        ByteBuffer raw = packet(0x1524, 4 + 1 + name.length + 2);
        raw.putInt(22).put((byte) 1).put(name).putShort((short) 0);

        CMSGPetNameCheck packet = CMSGPetNameCheck.fromBytes(raw.array());

        assertEquals(22, packet.getItemId());
        assertEquals(1, packet.getPetType());
        assertEquals("Renamed", packet.getNewPetName());
    }

    @Test
    void decodesNativeReviveRequestSchema() {
        ByteBuffer raw = packet(0x1526, 5);
        raw.putInt(21).put((byte) 1);

        CMSGRevivePet packet = CMSGRevivePet.fromBytes(raw.array());

        assertEquals(21, packet.getItemId());
        assertEquals(1, packet.getPetType());
    }

    @Test
    void encodesNativeTwoByteLifecycleResults() {
        assertArrayEquals(new byte[]{0, 0}, payload(new S2CPetNameChangeAnswerPacket((short) 0)));
        assertEquals(0x1525, new S2CPetNameChangeAnswerPacket((short) 0).getPacketId());
        assertArrayEquals(new byte[]{1, 0}, payload(new S2CPetReviveAnswerPacket((short) 1)));
        assertEquals(0x1527, new S2CPetReviveAnswerPacket((short) 1).getPacketId());
    }

    private static ByteBuffer packet(int id, int payloadLength) {
        return ByteBuffer.allocate(8 + payloadLength)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort((short) 0)
                .putShort((short) 0)
                .putShort((short) id)
                .putShort((short) payloadLength);
    }

    private static byte[] payload(com.jftse.server.core.protocol.Packet packet) {
        byte[] raw = packet.toBytes();
        byte[] payload = new byte[raw.length - 8];
        System.arraycopy(raw, 8, payload, 0, payload.length);
        return payload;
    }
}
