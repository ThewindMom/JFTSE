package com.jftse.emulator.server.core.handler.tournament;

import com.jftse.emulator.server.core.tournament.TournamentManager;
import com.jftse.server.core.shared.packets.tournament.CMSGTournamentBracket;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TournamentBracketPacketHandlerTest {
    private static final int TOURNAMENT_ID = 0x01020304;

    @Test
    void requestDecodesTournamentIdAndTwoByteSelectors() {
        CMSGTournamentBracket request = CMSGTournamentBracket.fromBytes(packet(
                0x26C0,
                new byte[]{4, 3, 2, 1, 1, 7}
        ));

        assertEquals(TOURNAMENT_ID, request.getTournamentId());
        assertEquals(1, request.getBracketType());
        assertEquals(7, request.getPage());
    }

    @Test
    void onlyRecoveredFinalBracketSelectorIsSupported() {
        assertTrue(TournamentBracketPacketHandler.supports((byte) 1, (byte) 0));
        assertFalse(TournamentBracketPacketHandler.supports((byte) 0, (byte) 0));
        assertFalse(TournamentBracketPacketHandler.supports((byte) 1, (byte) 1));
    }

    @Test
    void successFollowsClientBracketParserOrder() {
        List<TournamentManager.BracketEntry> entries = List.of(
                new TournamentManager.BracketEntry("test", "BOT01", "BOT02"),
                new TournamentManager.BracketEntry("BOT03", "BOT04", "BOT05")
        );

        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write(TournamentManager.SUCCESS);
        writeInt(payload, TOURNAMENT_ID);
        payload.write(1);
        payload.write(0);
        payload.write(0); // three fixed UTF-16LE names follow for each row
        writeShort(payload, entries.size());
        entries.forEach(entry -> {
            writeFixedUtf16(payload, entry.first());
            writeFixedUtf16(payload, entry.second());
            writeFixedUtf16(payload, entry.third());
        });

        assertArrayEquals(
                packet(0x26C1, payload.toByteArray()),
                TournamentBracketPacketHandler.response(
                        TournamentManager.SUCCESS,
                        TOURNAMENT_ID,
                        (byte) 1,
                        (byte) 0,
                        entries
                ).toBytes()
        );
    }

    @Test
    void failureContainsOnlyStatus() {
        assertArrayEquals(
                packet(0x26C1, new byte[]{TournamentManager.NOT_FOUND}),
                TournamentBracketPacketHandler.response(
                        TournamentManager.NOT_FOUND,
                        TOURNAMENT_ID,
                        (byte) 1,
                        (byte) 0,
                        List.of()
                ).toBytes()
        );
    }

    private static void writeFixedUtf16(ByteArrayOutputStream output, String value) {
        byte[] fixed = new byte[12];
        byte[] encoded = value.getBytes(StandardCharsets.UTF_16LE);
        System.arraycopy(encoded, 0, fixed, 0, Math.min(encoded.length, fixed.length - 2));
        output.writeBytes(fixed);
    }

    private static void writeInt(ByteArrayOutputStream output, int value) {
        output.writeBytes(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array());
    }

    private static void writeShort(ByteArrayOutputStream output, int value) {
        output.writeBytes(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort((short) value).array());
    }

    private static byte[] packet(int id, byte[] payload) {
        return ByteBuffer.allocate(8 + payload.length)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort((short) 0)
                .putShort((short) 0)
                .putShort((short) id)
                .putShort((short) payload.length)
                .put(payload)
                .array();
    }
}
