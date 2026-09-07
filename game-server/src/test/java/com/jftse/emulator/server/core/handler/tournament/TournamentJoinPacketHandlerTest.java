package com.jftse.emulator.server.core.handler.tournament;

import com.jftse.emulator.server.core.tournament.TournamentManager;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TournamentJoinPacketHandlerTest {
    private static final int TOURNAMENT_ID = 0x01020304;

    @Test
    void successIncludesTournamentMetadataAfterId() {
        byte[] response = TournamentJoinPacketHandler
                .response(TournamentManager.SUCCESS, 1)
                .toBytes();

        assertEquals(108, response.length);
        assertArrayEquals(new byte[]{0, 1, 0, 0, 0, 1, 0},
                java.util.Arrays.copyOfRange(response, 8, 15));
    }

    @Test
    void failureContainsOnlyStatus() {
        assertArrayEquals(
                packet(new byte[]{TournamentManager.NOT_APPLIED}),
                TournamentJoinPacketHandler.response(TournamentManager.NOT_APPLIED, TOURNAMENT_ID).toBytes());
    }

    private static byte[] packet(byte[] payload) {
        return ByteBuffer.allocate(8 + payload.length)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort((short) 0)
                .putShort((short) 0)
                .putShort((short) 0x26AE)
                .putShort((short) payload.length)
                .put(payload)
                .array();
    }
}
