package com.jftse.emulator.server.core.tournament;

import com.jftse.server.core.shared.packets.tournament.CMSGTournamentApply;
import com.jftse.server.core.shared.packets.tournament.CMSGTournamentCancel;
import com.jftse.server.core.shared.packets.tournament.CMSGTournamentInfo;
import com.jftse.server.core.shared.packets.tournament.CMSGTournamentJoin;
import com.jftse.server.core.shared.packets.tournament.CMSGTournamentList;
import com.jftse.server.core.shared.packets.tournament.SMSGTournamentApply;
import com.jftse.server.core.shared.packets.tournament.SMSGTournamentCancel;
import com.jftse.server.core.shared.packets.tournament.SMSGTournamentList;
import com.jftse.server.core.shared.packets.tournament.Tournament;
import com.jftse.server.core.shared.packets.tournament.TournamentPair;
import com.jftse.server.core.shared.packets.tournament.TournamentReward;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TournamentPacketProtocolTest {
    private static final int TOURNAMENT_ID = 0x01020304;

    @Test
    void clientRequestsDecodeTheirSingleTournamentIdOrPageField() {
        assertEquals(-1, CMSGTournamentList.fromBytes(packet(0x26AF, intPayload(-1))).getPage());
        assertEquals(TOURNAMENT_ID, CMSGTournamentApply.fromBytes(packet(0x26B1, intPayload(TOURNAMENT_ID))).getTournamentId());
        assertEquals(TOURNAMENT_ID, CMSGTournamentCancel.fromBytes(packet(0x26B3, intPayload(TOURNAMENT_ID))).getTournamentId());
        assertEquals(TOURNAMENT_ID, CMSGTournamentJoin.fromBytes(packet(0x26AD, intPayload(TOURNAMENT_ID))).getTournamentId());
        assertEquals(TOURNAMENT_ID, CMSGTournamentInfo.fromBytes(packet(0x26BE, intPayload(TOURNAMENT_ID))).getTournamentId());
    }

    @Test
    void emptyListAndApplyCancelAnswersMatchGoldenBytes() {
        assertArrayEquals(packet(0x26B0, new byte[]{0}),
                SMSGTournamentList.builder().tournaments(List.of()).build().toBytes());
        assertArrayEquals(packet(0x26B2, new byte[]{0}),
                SMSGTournamentApply.builder().status((byte) 0).build().toBytes());
        assertArrayEquals(packet(0x26B4, new byte[]{-1}),
                SMSGTournamentCancel.builder().status((byte) -1).build().toBytes());
    }

    @Test
    void oneTournamentSerializesInClientParserOrder() {
        Date applicationStart = Date.from(Instant.parse("2026-08-01T10:00:00Z"));
        Date applicationEnd = Date.from(Instant.parse("2026-08-08T10:00:00Z"));
        Date tournamentStart = Date.from(Instant.parse("2026-08-09T10:00:00Z"));
        Date tournamentEnd = Date.from(Instant.parse("2026-08-10T10:00:00Z"));
        Date unknownTime = Date.from(Instant.parse("2026-07-01T00:00:00Z"));

        List<TournamentReward> rewards = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            rewards.add(TournamentReward.builder()
                    .productIndex(1000 + i)
                    .useType(2000 + i)
                    .build());
        }

        List<TournamentPair> bracketA = pairs(3000);
        List<TournamentPair> bracketB = pairs(4000);
        Tournament tournament = Tournament.builder()
                .tournamentId(TOURNAMENT_ID)
                .entryType((byte) 1)
                .gameMode((byte) 2)
                .title("JFTSE Cup")
                .applicationStart(applicationStart)
                .applicationEnd(applicationEnd)
                .tournamentStart(tournamentStart)
                .tournamentEnd(tournamentEnd)
                .unknown0(0x11121314)
                .unknown1(0x21222324)
                .unknown2((byte) 0x31)
                .unknown3(0x41424344)
                .unknownTime(unknownTime)
                .status((byte) 1)
                .unknownFlag(true)
                .unknown4(0x51525354)
                .bracketSize(16)
                .rewards(rewards)
                .bracketA(bracketA)
                .bracketB(bracketB)
                .build();

        byte[] actual = SMSGTournamentList.builder().tournaments(List.of(tournament)).build().toBytes();
        byte[] expectedPayload = expectedTournamentPayload(
                applicationStart, applicationEnd, tournamentStart, tournamentEnd, unknownTime,
                rewards, bracketA, bracketB);

        assertArrayEquals(packet(0x26B0, expectedPayload), actual);
        assertEquals(1 + 6 + ("JFTSE Cup".length() + 1) * 2 + 359, expectedPayload.length);
    }

    @Test
    void fixedTournamentArraysRejectWrongCardinality() {
        Tournament.Builder builder = tournamentBuilder()
                .rewards(rewards(4))
                .bracketA(pairs(3000))
                .bracketB(pairs(4000));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> SMSGTournamentList.builder().tournaments(List.of(builder.build())).build());

        assertEquals("rewards must contain exactly 5 elements", error.getMessage());
    }

    @Test
    void fixedTournamentBracketsRejectWrongCardinality() {
        Tournament.Builder builder = tournamentBuilder()
                .rewards(rewards(5))
                .bracketA(pairs(3000).subList(0, 15))
                .bracketB(pairs(4000));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> SMSGTournamentList.builder().tournaments(List.of(builder.build())).build());

        assertEquals("bracketA must contain exactly 16 elements", error.getMessage());
    }

    private static Tournament.Builder tournamentBuilder() {
        Date time = Date.from(Instant.parse("2026-08-01T10:00:00Z"));
        return Tournament.builder()
                .tournamentId(TOURNAMENT_ID)
                .entryType((byte) 1)
                .gameMode((byte) 2)
                .title("JFTSE Cup")
                .applicationStart(time)
                .applicationEnd(time)
                .tournamentStart(time)
                .tournamentEnd(time)
                .unknownTime(time);
    }

    private static List<TournamentReward> rewards(int count) {
        List<TournamentReward> rewards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rewards.add(TournamentReward.builder().productIndex(i).useType(i).build());
        }
        return rewards;
    }

    private static List<TournamentPair> pairs(int base) {
        List<TournamentPair> pairs = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            pairs.add(TournamentPair.builder().first(base + i).second(base + 100 + i).build());
        }
        return pairs;
    }

    private static byte[] expectedTournamentPayload(
            Date applicationStart,
            Date applicationEnd,
            Date tournamentStart,
            Date tournamentEnd,
            Date unknownTime,
            List<TournamentReward> rewards,
            List<TournamentPair> bracketA,
            List<TournamentPair> bracketB) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(1);
        writeInt(output, TOURNAMENT_ID);
        output.write(1);
        output.write(2);
        output.writeBytes("JFTSE Cup".getBytes(StandardCharsets.UTF_16LE));
        output.writeBytes(new byte[]{0, 0});
        writeFiletime(output, applicationStart);
        writeFiletime(output, applicationEnd);
        writeFiletime(output, tournamentStart);
        writeFiletime(output, tournamentEnd);
        writeInt(output, 0x11121314);
        writeInt(output, 0x21222324);
        output.write(0x31);
        writeInt(output, 0x41424344);
        writeFiletime(output, unknownTime);
        output.write(1);
        output.write(1);
        writeInt(output, 0x51525354);
        writeInt(output, 16);
        rewards.forEach(reward -> {
            writeInt(output, reward.getProductIndex());
            writeInt(output, reward.getUseType());
        });
        bracketA.forEach(pair -> writePair(output, pair));
        bracketB.forEach(pair -> writePair(output, pair));
        return output.toByteArray();
    }

    private static void writePair(ByteArrayOutputStream output, TournamentPair pair) {
        writeInt(output, pair.getFirst());
        writeInt(output, pair.getSecond());
    }

    private static void writeFiletime(ByteArrayOutputStream output, Date date) {
        writeLong(output, (date.getTime() + 11644473600000L) * 10000L);
    }

    private static void writeInt(ByteArrayOutputStream output, int value) {
        output.writeBytes(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array());
    }

    private static void writeLong(ByteArrayOutputStream output, long value) {
        output.writeBytes(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array());
    }

    private static byte[] intPayload(int value) {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array();
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
