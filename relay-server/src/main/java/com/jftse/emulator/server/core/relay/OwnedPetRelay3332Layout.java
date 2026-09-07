package com.jftse.emulator.server.core.relay;

import com.jftse.emulator.common.utilities.BitKit;

import java.util.Optional;

/**
 * Width-only view of inner relay packet 0x3332.
 *
 * Native builder 0x52be79 / parser 0x5319de prove packet identity and the
 * 8-byte header plus 17-byte body widths. Field meanings, sender authority,
 * and actor ownership are unresolved. This type must never be queued as a
 * server-authoritative combat handler; opaque same-session relay remains
 * development-compatible transport.
 */
public final class OwnedPetRelay3332Layout {
    public static final int PACKET_ID = 0x3332;
    public static final int HEADER_LENGTH = 8;
    public static final int BODY_LENGTH = 17;
    public static final int INNER_LENGTH = HEADER_LENGTH + BODY_LENGTH;

    private OwnedPetRelay3332Layout() {
    }

    public static boolean isPacket(byte[] innerPacket) {
        return innerPacket != null && innerPacket.length >= HEADER_LENGTH
                && (BitKit.bytesToShort(innerPacket, 4) & 0xFFFF) == PACKET_ID;
    }

    /**
     * Parses documented widths only. Names are offsets, not semantics.
     */
    public static Optional<Widths> parse(byte[] innerPacket) {
        if (innerPacket == null || innerPacket.length != INNER_LENGTH || !isPacket(innerPacket)) {
            return Optional.empty();
        }
        int body = HEADER_LENGTH;
        return Optional.of(new Widths(
                innerPacket[body] & 0xFF,
                innerPacket[body + 1] & 0xFF,
                innerPacket[body + 2] & 0xFF,
                innerPacket[body + 3] & 0xFF,
                BitKit.bytesToFloat(innerPacket, body + 4),
                BitKit.bytesToShort(innerPacket, body + 8) & 0xFFFF,
                innerPacket[body + 10] & 0xFF,
                BitKit.bytesToShort(innerPacket, body + 11) & 0xFFFF,
                BitKit.bytesToShort(innerPacket, body + 13) & 0xFFFF,
                BitKit.bytesToShort(innerPacket, body + 15) & 0xFFFF
        ));
    }

    public record Widths(
            int byte0,
            int byte1,
            int byte2,
            int byte3,
            float float4,
            int u16At8,
            int byte10,
            int u16At11,
            int u16At13,
            int u16At15) {
        public String toLogString() {
            return String.format(
                    "byte0=0x%02x byte1=0x%02x byte2=0x%02x byte3=0x%02x float4=%s u16@8=0x%04x byte10=0x%02x u16@11=0x%04x u16@13=0x%04x u16@15=0x%04x",
                    byte0, byte1, byte2, byte3, Float.toString(float4), u16At8, byte10, u16At11, u16At13, u16At15);
        }
    }
}
