package com.jftse.emulator.server.core.relay;

import com.jftse.emulator.common.utilities.BitKit;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnedPetRelay3332LayoutTest {
    @Test
    void parsesDocumentedTwentyFiveByteWidthsAndRejectsWrongLength() {
        byte[] inner = new byte[OwnedPetRelay3332Layout.INNER_LENGTH];
        inner[4] = 0x32;
        inner[5] = 0x33;
        inner[8] = 0x01;
        inner[9] = 0x02;
        inner[10] = 0x03;
        inner[11] = 0x04;
        System.arraycopy(BitKit.getBytes(1.5f), 0, inner, 12, 4);
        System.arraycopy(BitKit.getBytes((char) 0x1111), 0, inner, 16, 2);
        inner[18] = 0x05;
        System.arraycopy(BitKit.getBytes((char) 0x2222), 0, inner, 19, 2);
        System.arraycopy(BitKit.getBytes((char) 0x3333), 0, inner, 21, 2);
        System.arraycopy(BitKit.getBytes((char) 0x4444), 0, inner, 23, 2);

        Optional<OwnedPetRelay3332Layout.Widths> parsed = OwnedPetRelay3332Layout.parse(inner);

        assertTrue(parsed.isPresent());
        OwnedPetRelay3332Layout.Widths widths = parsed.get();
        assertEquals(1, widths.byte0());
        assertEquals(2, widths.byte1());
        assertEquals(3, widths.byte2());
        assertEquals(4, widths.byte3());
        assertEquals(1.5f, widths.float4());
        assertEquals(0x1111, widths.u16At8());
        assertEquals(5, widths.byte10());
        assertEquals(0x2222, widths.u16At11());
        assertEquals(0x3333, widths.u16At13());
        assertEquals(0x4444, widths.u16At15());

        assertFalse(OwnedPetRelay3332Layout.parse(new byte[8]).isPresent());
        byte[] tooLong = new byte[26];
        tooLong[4] = 0x32;
        tooLong[5] = 0x33;
        assertFalse(OwnedPetRelay3332Layout.parse(tooLong).isPresent());
    }

    @Test
    void doesNotTreatOtherPacketIdsAs3332() {
        byte[] inner = new byte[OwnedPetRelay3332Layout.INNER_LENGTH];
        inner[4] = (byte) 0xC9;
        inner[5] = 0x32;
        assertFalse(OwnedPetRelay3332Layout.isPacket(inner));
        assertFalse(OwnedPetRelay3332Layout.parse(inner).isPresent());
    }
}
