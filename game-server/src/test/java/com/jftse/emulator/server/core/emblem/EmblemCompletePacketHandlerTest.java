package com.jftse.emulator.server.core.emblem;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.handler.emblem.EmblemCompletePacketHandler;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.player.Player;
import com.jftse.server.core.service.EmblemCompletionResult;
import com.jftse.server.core.service.EmblemQuestService;
import com.jftse.server.core.service.EmblemQuestState;
import com.jftse.server.core.service.EmblemQuestStatus;
import com.jftse.server.core.service.PlayerService;
import com.jftse.server.core.shared.packets.emblem.CMSGEmblemComplete;
import com.jftse.server.core.shared.packets.emblem.S2CEmblemCompletionPacket;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmblemCompletePacketHandlerTest {
    private ServiceManager previousServices;
    private EmblemQuestService emblemQuestService;
    private PlayerService playerService;
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
        playerService = mock(PlayerService.class);
        setField(services, "emblemQuestService", emblemQuestService);
        setField(services, "playerService", playerService);
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
        when(playerService.findById(7L)).thenReturn(playerEntity);
    }

    @AfterEach
    void restoreServices() throws ReflectiveOperationException {
        Field instance = ServiceManager.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, previousServices);
    }

    @Test
    void sendsCompletionBeforeTheAuthoritativeListRefreshExpectedByTheNativeClient() {
        EmblemQuestState completed = new EmblemQuestState(
                (short) 1000, false, (short) 1,
                true, (short) 99,
                false, (short) 0,
                false, (short) 0,
                false, (short) 0);
        when(emblemQuestService.complete(playerEntity, 1000)).thenReturn(new EmblemCompletionResult(
                EmblemQuestStatus.SUCCESS, (byte) 2, 100, 10_100, List.of()));
        when(emblemQuestService.list(7L)).thenReturn(List.of(completed));

        new EmblemCompletePacketHandler().handle(connection, packet(1000));

        InOrder sent = inOrder(connection);
        sent.verify(connection).sendTCP(isA(S2CEmblemCompletionPacket.class));
        sent.verify(connection).sendTCP(isA(S2CEmblemListPacket.class));
        verify(emblemQuestService).list(7L);
        verify(player).sync(playerEntity);
    }

    private static CMSGEmblemComplete packet(int questIndex) {
        ByteBuffer buffer = ByteBuffer.allocate(10).order(ByteOrder.nativeOrder());
        buffer.putChar((char) 0);
        buffer.putChar((char) 0);
        buffer.putChar((char) CMSGEmblemComplete.PACKET_ID);
        buffer.putChar((char) 2);
        buffer.putChar((char) questIndex);
        return CMSGEmblemComplete.fromBytes(buffer.array());
    }

    private static void setField(ServiceManager services, String name, Object value)
            throws ReflectiveOperationException {
        Field field = ServiceManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(services, value);
    }
}
