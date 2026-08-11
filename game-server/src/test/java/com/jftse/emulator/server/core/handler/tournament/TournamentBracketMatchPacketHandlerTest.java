package com.jftse.emulator.server.core.handler.tournament;

import com.jftse.emulator.server.core.tournament.TournamentManager;
import com.jftse.server.core.shared.packets.tournament.CMSGTournamentBracketMatch;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TournamentBracketMatchPacketHandlerTest {
    private static final int TOURNAMENT_ID = 0x01020304;

    @Test
    void requestDecodesTournamentIdAndTwoByteSelectors() {
        CMSGTournamentBracketMatch request = CMSGTournamentBracketMatch.fromBytes(packet(
                0x26C2,
                new byte[]{4, 3, 2, 1, 1, 7}
        ));

        assertEquals(TOURNAMENT_ID, request.getTournamentId());
        assertEquals(1, request.getBracketType());
        assertEquals(7, request.getMatchIndex());
    }

    @Test
    void onlyRecoveredFinalBracketSelectorIsSupported() {
        assertTrue(TournamentBracketMatchPacketHandler.supports((byte) 1, (byte) 0));
        assertFalse(TournamentBracketMatchPacketHandler.supports((byte) 0, (byte) 0));
        assertFalse(TournamentBracketMatchPacketHandler.supports((byte) 1, (byte) 1));
    }

    @Test
    void successFollowsClientMatchStateParserOrder() {
        List<TournamentManager.BracketMatch> matches = TournamentManager.getInstance().getBracketMatches(1);

        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write(TournamentManager.SUCCESS);
        writeInt(payload, TOURNAMENT_ID);
        payload.write(1);
        payload.write(0);
        writeShort(payload, matches.size());
        matches.forEach(match -> {
            payload.write(match.result());
            payload.write(match.state());
        });

        assertArrayEquals(
                packet(0x26C3, payload.toByteArray()),
                TournamentBracketMatchPacketHandler.response(
                        TournamentManager.SUCCESS,
                        TOURNAMENT_ID,
                        (byte) 1,
                        (byte) 0,
                        matches
                ).toBytes()
        );
        assertEquals(39, payload.size());
        assertEquals(15, matches.size());
        assertEquals(
                List.of(new TournamentManager.BracketMatch((byte) -1, (byte) 0)),
                matches.stream().distinct().toList()
        );
    }

    @Test
    void failureContainsOnlyStatus() {
        assertArrayEquals(
                packet(0x26C3, new byte[]{TournamentManager.NOT_FOUND}),
                TournamentBracketMatchPacketHandler.response(
                        TournamentManager.NOT_FOUND,
                        TOURNAMENT_ID,
                        (byte) 1,
                        (byte) 0,
                        List.of()
                ).toBytes()
        );
    }

    @Test
    void tournamentMetadataAndMatchCardinalityDescribeTheSameBracket() {
        TournamentManager.TournamentDefinition definition = new TournamentManager.TournamentDefinition(
                1,
                "JFTSE Open Cup",
                Instant.EPOCH,
                Instant.EPOCH,
                Instant.EPOCH,
                Instant.EPOCH
        );

        assertEquals(16, TournamentListPacketHandler.toPacket(definition).getUnknown4());
        List<TournamentManager.BracketMatch> matches = TournamentManager.getInstance().getBracketMatches(1);
        assertEquals(15, matches.size());
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
