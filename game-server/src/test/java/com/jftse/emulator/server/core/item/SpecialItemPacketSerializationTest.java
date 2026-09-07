package com.jftse.emulator.server.core.item;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.packets.player.S2CPlayerStatusPointChangePacket;
import com.jftse.entities.database.model.player.EquippedItemStats;
import com.jftse.server.core.protocol.PacketOperations;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpecialItemPacketSerializationTest {
    @Test
    void genericStatusPacketDoesNotApplyBattleOnlyHpButKeepsEarringStats() {
        FTPlayer player = mock(FTPlayer.class);
        EquippedItemStats stats = new EquippedItemStats();
        stats.setAddHp(20);
        stats.setSpecialAddHp(200);
        stats.setSpecialStrength(3);
        stats.setSpecialStamina(5);
        stats.setSpecialDexterity(3);
        stats.setSpecialWillpower(5);

        when(player.getLevel()).thenReturn(1);
        when(player.getStrength()).thenReturn(10);
        when(player.getStamina()).thenReturn(11);
        when(player.getDexterity()).thenReturn(12);
        when(player.getWillpower()).thenReturn(13);
        when(player.getStatusPoints()).thenReturn(7);
        when(player.getItemStats()).thenReturn(stats);

        byte[] packet = new S2CPlayerStatusPointChangePacket(player).toBytes();
        ByteBuffer bytes = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN);

        assertEquals(63, packet.length);
        assertEquals(PacketOperations.S2CPlayerStatusPointChange.getValue(), Short.toUnsignedInt(bytes.getShort(4)));
        assertEquals(55, Short.toUnsignedInt(bytes.getShort(6)));
        assertEquals(220, bytes.getInt(8));
        assertEquals(0, bytes.getInt(30));
        assertEquals(3, Byte.toUnsignedInt(packet[34]));
        assertEquals(5, Byte.toUnsignedInt(packet[35]));
        assertEquals(3, Byte.toUnsignedInt(packet[36]));
        assertEquals(5, Byte.toUnsignedInt(packet[37]));
        assertEquals(7, Byte.toUnsignedInt(packet[62]));
    }
}
