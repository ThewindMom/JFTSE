package com.jftse.emulator.server.core.card;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.handler.inventory.InventoryWearCardPacketHandler;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.player.Player;
import com.jftse.server.core.protocol.IPacket;
import com.jftse.server.core.protocol.PacketOperations;
import com.jftse.server.core.service.CardSlotEquipmentService;
import com.jftse.server.core.shared.packets.inventory.CMSGInventoryWearCard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryWearCardPacketHandlerTest {
    private ServiceManager previousServices;
    private CardSlotEquipmentService equipmentService;
    private FTConnection connection;
    private FTPlayer player;
    private Player playerEntity;

    @BeforeEach
    void setUp() throws ReflectiveOperationException {
        Field instance = ServiceManager.class.getDeclaredField("instance");
        instance.setAccessible(true);
        previousServices = (ServiceManager) instance.get(null);

        ServiceManager services = new ServiceManager();
        equipmentService = mock(CardSlotEquipmentService.class);
        Field service = ServiceManager.class.getDeclaredField("cardSlotEquipmentService");
        service.setAccessible(true);
        service.set(services, equipmentService);
        instance.set(null, services);

        connection = mock(FTConnection.class);
        FTClient client = mock(FTClient.class);
        player = mock(FTPlayer.class);
        playerEntity = new Player();
        when(connection.getClient()).thenReturn(client);
        when(client.hasPlayer()).thenReturn(true);
        when(client.getPlayer()).thenReturn(player);
        when(player.getPlayer()).thenReturn(playerEntity);
        when(equipmentService.getEquippedCardSlots(playerEntity)).thenReturn(List.of(0, 0, 0, 0));
    }

    @AfterEach
    void restoreServices() throws ReflectiveOperationException {
        Field instance = ServiceManager.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, previousServices);
    }

    @Test
    void acceptsTheExactSixteenByteNativePayload() {
        CMSGInventoryWearCard packet = packet(16);
        when(equipmentService.tryUpdateCardSlots(playerEntity, List.of(11, 12, 13, 14))).thenReturn(true);

        new InventoryWearCardPacketHandler().handle(connection, packet);

        verify(equipmentService).tryUpdateCardSlots(playerEntity, List.of(11, 12, 13, 14));
        verify(player).loadCardSlots();
        verify(connection).sendTCP(any());
    }

    @Test
    void rejectsAParseablePayloadWithTrailingBytesWithoutPersistingIt() {
        CMSGInventoryWearCard packet = packet(17);
        assertEquals(17, packet.getDataLength());
        assertEquals(List.of(11, 12, 13, 14), packet.getCardSlotList());
        when(equipmentService.getEquippedCardSlots(playerEntity)).thenReturn(List.of(21, 22, 23, 24));

        new InventoryWearCardPacketHandler().handle(connection, packet);

        verify(equipmentService, never()).tryUpdateCardSlots(any(), any());
        verify(player, never()).loadCardSlots();
        verify(equipmentService).getEquippedCardSlots(playerEntity);
        ArgumentCaptor<IPacket> response = ArgumentCaptor.forClass(IPacket.class);
        verify(connection).sendTCP(response.capture());
        assertEquals(PacketOperations.S2CInventoryWearCardAnswer.getValue(), response.getValue().getPacketId());
        assertArrayEquals(new byte[]{
                21, 0, 0, 0,
                22, 0, 0, 0,
                23, 0, 0, 0,
                24, 0, 0, 0
        }, Arrays.copyOfRange(response.getValue().toBytes(), 8, 24));
    }

    private static CMSGInventoryWearCard packet(int payloadLength) {
        ByteBuffer buffer = ByteBuffer.allocate(8 + payloadLength).order(ByteOrder.nativeOrder());
        buffer.putChar((char) 0);
        buffer.putChar((char) 0);
        buffer.putChar((char) CMSGInventoryWearCard.PACKET_ID);
        buffer.putChar((char) payloadLength);
        buffer.putInt(11);
        buffer.putInt(12);
        buffer.putInt(13);
        buffer.putInt(14);
        if (payloadLength > 16) buffer.put((byte) 0x7f);
        return CMSGInventoryWearCard.fromBytes(buffer.array());
    }
}
