package com.jftse.emulator.server.core.item;

import com.jftse.emulator.server.core.packets.item.S2CPersonalBoardMessageListPacket;
import com.jftse.emulator.server.core.packets.item.S2CPersonalBoardPacket;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PersonalBoardPacketTest {
    @Test
    void personalBoardBroadcastUsesNullTerminatedWideStrings() {
        S2CPersonalBoardPacket packet = new S2CPersonalBoardPacket("SpecialLab", "BOARD");

        assertArrayEquals(new byte[]{
                'S', 0, 'p', 0, 'e', 0, 'c', 0, 'i', 0, 'a', 0, 'l', 0, 'L', 0, 'a', 0, 'b', 0, 0, 0,
                'B', 0, 'O', 0, 'A', 0, 'R', 0, 'D', 0, 0, 0
        }, payload(packet.toBytes()));
    }

    @Test
    void personalBoardListUsesRoomPositionsAndNullTerminatedWideMessages() {
        Map<Short, String> messages = new LinkedHashMap<>();
        messages.put((short) 3, "THREE");
        messages.put((short) 7, "SEVEN");

        S2CPersonalBoardMessageListPacket packet = new S2CPersonalBoardMessageListPacket(messages);

        byte[] payload = payload(packet.toBytes());
        assertEquals(30, payload.length);
        assertArrayEquals(new byte[]{2, 0, 3, 0}, slice(payload, 0, 4));
        assertArrayEquals(new byte[]{'T', 0, 'H', 0, 'R', 0, 'E', 0, 'E', 0, 0, 0}, slice(payload, 4, 16));
        assertArrayEquals(new byte[]{7, 0}, slice(payload, 16, 18));
        assertArrayEquals(new byte[]{'S', 0, 'E', 0, 'V', 0, 'E', 0, 'N', 0, 0, 0}, slice(payload, 18, 30));
    }

    private byte[] payload(byte[] packet) {
        return slice(packet, 8, packet.length);
    }

    private byte[] slice(byte[] bytes, int from, int to) {
        byte[] result = new byte[to - from];
        System.arraycopy(bytes, from, result, 0, result.length);
        return result;
    }
}
