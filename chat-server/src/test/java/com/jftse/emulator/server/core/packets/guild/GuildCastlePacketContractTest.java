package com.jftse.emulator.server.core.packets.guild;

import com.jftse.server.core.protocol.IPacket;
import com.jftse.server.core.shared.packets.guild.SMSGGuildCastleChangeInformation;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GuildCastlePacketContractTest {
    @Test
    void castleChangeResultIsOneSignedByte() {
        SMSGGuildCastleChangeInformation packet = SMSGGuildCastleChangeInformation.builder()
                .result((byte) -2)
                .build();

        assertEquals(1, packet.getDataLength());
        assertArrayEquals(new byte[]{(byte) -2}, payload(packet));
    }

    @Test
    void castleInfoSuccessUsesClientFieldWidthsAndOrder() {
        S2CGuildCastleInfoAnswerPacket packet = new S2CGuildCastleInfoAnswerPacket(
                (byte) 0, 0x11223344, 0x55667788, (byte) 3, 1000);

        assertEquals(14, packet.getDataLength());
        assertArrayEquals(new byte[]{
                0,
                0x44, 0x33, 0x22, 0x11,
                (byte) 0x88, 0x77, 0x66, 0x55,
                3,
                (byte) 0xe8, 0x03, 0, 0
        }, payload(packet));
    }

    @Test
    void castleInfoFailureContainsOnlyTheSignedResultByte() {
        S2CGuildCastleInfoAnswerPacket packet = new S2CGuildCastleInfoAnswerPacket(
                (byte) -1, 0x11223344, 0x55667788, (byte) 3, 1000);

        assertEquals(1, packet.getDataLength());
        assertArrayEquals(new byte[]{(byte) -1}, payload(packet));
    }

    private byte[] payload(IPacket packet) {
        return Arrays.copyOfRange(packet.toBytes(), 8, 8 + packet.getDataLength());
    }
}
