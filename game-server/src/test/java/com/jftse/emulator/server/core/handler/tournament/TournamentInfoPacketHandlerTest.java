package com.jftse.emulator.server.core.handler.tournament;

import com.jftse.emulator.server.core.tournament.TournamentManager;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class TournamentInfoPacketHandlerTest {
    private static final int TOURNAMENT_ID = 0x01020304;

    @Test
    void successIncludesClientParsedParticipationInfo() {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write(TournamentManager.SUCCESS);
        writeInt(payload, TOURNAMENT_ID);
        payload.write(0); // MY_TOURNEY_STATE_NOT_APPLIED
        payload.write(0);
        payload.writeBytes(new byte[6]); // three empty UTF-16LE strings
        payload.writeBytes(new byte[2]);
        payload.writeBytes(new byte[16]); // two empty FILETIMEs

        assertArrayEquals(
                packet(payload.toByteArray()),
                TournamentInfoPacketHandler.response(TournamentManager.SUCCESS, TOURNAMENT_ID, false).toBytes());
    }

    @Test
    void appliedPlayerUsesClientAppliedState() {
        byte[] packet = TournamentInfoPacketHandler
                .response(TournamentManager.SUCCESS, TOURNAMENT_ID, true)
                .toBytes();

        assertArrayEquals(new byte[]{1}, new byte[]{packet[13]});
    }

    @Test
    void failureContainsOnlyStatus() {
        assertArrayEquals(
                packet(new byte[]{TournamentInfoPacketHandler.INFO_FAILED}),
                TournamentInfoPacketHandler.response(
                        TournamentInfoPacketHandler.INFO_FAILED,
                        TOURNAMENT_ID,
                        false
                ).toBytes());
    }

    private static void writeInt(ByteArrayOutputStream output, int value) {
        output.writeBytes(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array());
    }

    private static byte[] packet(byte[] payload) {
        return ByteBuffer.allocate(8 + payload.length)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort((short) 0)
                .putShort((short) 0)
                .putShort((short) 0x26BF)
                .putShort((short) payload.length)
                .put(payload)
                .array();
    }
}
