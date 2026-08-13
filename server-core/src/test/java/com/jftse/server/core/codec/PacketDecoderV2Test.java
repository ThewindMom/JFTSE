package com.jftse.server.core.codec;

import com.jftse.server.core.protocol.IPacket;
import com.jftse.server.core.shared.packets.relay.CMSGPlayerJoinSession;
import com.jftse.server.core.shared.packets.relay.CMSGRelay;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;

class PacketDecoderV2Test {
    @Test
    void relayRegistrationCanAdvancePastTheClientConsumedSerial() throws Exception {
        PacketDecoderV2 decoder = new PacketDecoderV2(0, mock(org.apache.logging.log4j.Logger.class, RETURNS_DEFAULTS), false, false);

        IPacket registration = decoder.decode(Unpooled.wrappedBuffer(bytes(
                "F2 30 47 08 ED 03 15 00 60 1C 01 00 00 01 00 00 " +
                        "00 02 00 00 00 00 00 00 00 00 00 00 00")));
        assertNotNull(registration);
        assertEquals(CMSGPlayerJoinSession.PACKET_ID, registration.getPacketId());

        decoder.advanceReceiveIndicator();

        IPacket gameplay = decoder.decode(Unpooled.wrappedBuffer(bytes(
                "D4 7D AF 07 14 04 13 00 00 00 00 00 C9 32 0B 00 " +
                        "00 00 D0 07 2C CF 00 00 00 00 11")));
        assertNotNull(gameplay);
        assertEquals(CMSGRelay.PACKET_ID, gameplay.getPacketId());
    }

    private static byte[] bytes(String hex) {
        String[] values = hex.split(" ");
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) Integer.parseInt(values[i], 16);
        }
        return bytes;
    }
}
