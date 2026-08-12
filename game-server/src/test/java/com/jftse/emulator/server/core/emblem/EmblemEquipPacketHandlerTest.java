package com.jftse.emulator.server.core.emblem;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.handler.emblem.EmblemEquipPacketHandler;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.emblem.PlayerEmblemEquipment;
import com.jftse.entities.database.model.player.Player;
import com.jftse.server.core.service.EmblemQuestService;
import com.jftse.server.core.service.EmblemQuestStatus;
import com.jftse.server.core.shared.packets.emblem.CMSGEmblemEquip;
import com.jftse.server.core.shared.packets.emblem.S2CEmblemEquipmentPacket;
import com.jftse.server.core.shared.packets.emblem.S2CEmblemListPacket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmblemEquipPacketHandlerTest {
    private ServiceManager previousServices;
    private EmblemQuestService emblemQuestService;
    private FTConnection connection;
    private FTPlayer player;
    private Player playerEntity;

    @BeforeEach
    void setUp() throws ReflectiveOperationException {
        Field instance = ServiceManager.class.getDeclaredField("instance");
        instance.setAccessible(true);
        previousServices = (ServiceManager) instance.get(null);

        ServiceManager services = new ServiceManager();
        emblemQuestService = mock(EmblemQuestService.class);
        setField(services, "emblemQuestService", emblemQuestService);
        instance.set(null, services);

        connection = mock(FTConnection.class);
        FTClient client = mock(FTClient.class);
        player = mock(FTPlayer.class);
        playerEntity = new Player();
        playerEntity.setId(7L);
        when(connection.getClient()).thenReturn(client);
        when(client.hasPlayer()).thenReturn(true);
        when(client.getPlayer()).thenReturn(player);
        when(player.getId()).thenReturn(7L);
        when(player.getPlayerRef()).thenReturn(playerEntity);
        when(emblemQuestService.list(7L)).thenReturn(List.of());
    }

    @AfterEach
    void restoreServices() throws ReflectiveOperationException {
        Field instance = ServiceManager.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, previousServices);
    }

    @Test
    void acknowledgesSuccessfulPersistenceWithTheAuthoritativeEquipmentSnapshot() {
        PlayerEmblemEquipment equipment = new PlayerEmblemEquipment();
        equipment.setSlot1((short) 1000);
        when(emblemQuestService.equip(playerEntity, List.of(1000, 0, 0, 0)))
                .thenReturn(EmblemQuestStatus.SUCCESS);
        when(player.getEmblemEquipment()).thenReturn(equipment);

        new EmblemEquipPacketHandler().handle(connection, packet(1000, 0, 0, 0));

        verify(player).loadEmblemEquipment();
        InOrder sent = inOrder(connection);
        sent.verify(connection).sendTCP(isA(S2CEmblemEquipmentPacket.class));
        sent.verify(connection).sendTCP(isA(S2CEmblemListPacket.class));
    }

    @Test
    void doesNotAcknowledgeRejectedEquipmentAsAuthoritative() {
        when(emblemQuestService.equip(playerEntity, List.of(1000, 1001, 0, 0)))
                .thenReturn(EmblemQuestStatus.INVALID_EQUIPMENT);

        new EmblemEquipPacketHandler().handle(connection, packet(1000, 1001, 0, 0));

        verify(player, never()).loadEmblemEquipment();
        verify(connection, never()).sendTCP(isA(S2CEmblemEquipmentPacket.class));
        verify(connection).sendTCP(isA(S2CEmblemListPacket.class));
    }

    private static CMSGEmblemEquip packet(int slot1, int slot2, int slot3, int slot4) {
        ByteBuffer buffer = ByteBuffer.allocate(16).order(ByteOrder.nativeOrder());
        buffer.putChar((char) 0);
        buffer.putChar((char) 0);
        buffer.putChar((char) CMSGEmblemEquip.PACKET_ID);
        buffer.putChar((char) 8);
        buffer.putChar((char) slot1);
        buffer.putChar((char) slot2);
        buffer.putChar((char) slot3);
        buffer.putChar((char) slot4);
        return CMSGEmblemEquip.fromBytes(buffer.array());
    }

    private static void setField(ServiceManager services, String name, Object value)
            throws ReflectiveOperationException {
        Field field = ServiceManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(services, value);
    }
}
