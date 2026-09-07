package com.jftse.emulator.server.core.handler.item;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.packets.inventory.S2CInventoryItemCountPacket;
import com.jftse.emulator.server.core.packets.item.S2CPersonalBoardPacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.server.core.protocol.IPacket;
import com.jftse.server.core.service.PersonalBoardService;
import com.jftse.server.core.service.ProfaneWordsService;
import com.jftse.server.core.shared.packets.inventory.S2CInventoryItemRemoveAnswerPacket;
import com.jftse.server.core.shared.packets.item.CMSGPersonalBoard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonalBoardRequestPacketHandlerTest {
    @Mock private PersonalBoardService personalBoardService;
    @Mock private ProfaneWordsService profaneWordsService;
    @Mock private GameManager gameManager;
    @Mock private FTConnection connection;
    @Mock private FTConnection recipientConnection;
    @Mock private FTClient client;
    @Mock private FTClient recipient;
    @Mock private FTPlayer player;
    @Mock private RoomPlayer roomPlayer;

    private PersonalBoardRequestPacketHandler handler;
    private Room room;
    private PlayerPocket board;

    @BeforeEach
    void setUp() {
        handler = new PersonalBoardRequestPacketHandler(
                personalBoardService,
                profaneWordsService,
                gameManager
        );
        room = new Room();
        room.setRoomId((short) 9);
        board = new PlayerPocket();
        board.setId(501L);
        board.setItemCount(1);
    }

    @Test
    void consumesAStackUnitAndBroadcastsTheBoardInsideOnlyItsRoom() {
        arrangeAuthenticatedRoomPlayer();
        when(personalBoardService.use(71L, 501L)).thenReturn(success(false));

        handler.handle(connection, packet(501, "NATIVE BOARD"));

        assertEquals("NATIVE BOARD", room.getPersonalBoardMessages().get(11L));
        assertSentPacket(connection, S2CInventoryItemCountPacket.class);
        assertSentPacket(recipientConnection, S2CPersonalBoardPacket.class);
        verify(gameManager).getClientsInRoom((short) 9);
    }

    @Test
    void finalUnitUsesTheInventoryRemovalPacket() {
        arrangeAuthenticatedRoomPlayer();
        when(personalBoardService.use(71L, 501L)).thenReturn(success(true));

        handler.handle(connection, packet(501, "LAST BOARD"));

        assertSentPacket(connection, S2CInventoryItemRemoveAnswerPacket.class);
        assertEquals("LAST BOARD", room.getPersonalBoardMessages().get(11L));
    }

    @Test
    void replacingABoardConsumesAnotherItemAndReplacesRoomState() {
        arrangeAuthenticatedRoomPlayer();
        when(personalBoardService.use(71L, 501L)).thenReturn(success(false));

        handler.handle(connection, packet(501, "FIRST BOARD"));
        handler.handle(connection, packet(501, "SECOND BOARD"));

        assertEquals("SECOND BOARD", room.getPersonalBoardMessages().get(11L));
        verify(personalBoardService, times(2)).use(71L, 501L);
        verify(recipientConnection, times(2)).sendTCP(any(IPacket.class));
    }

    @Test
    void rejectedConsumptionDoesNotPublishOrChangeRoomState() {
        arrangeAuthenticatedRoomPlayer();
        when(personalBoardService.use(71L, 999L)).thenReturn(
                new PersonalBoardService.UseResult(
                        PersonalBoardService.UseStatus.NOT_OWNED,
                        board,
                        false
                )
        );

        handler.handle(connection, packet(999, "FORGED BOARD"));

        assertTrue(room.getPersonalBoardMessages().isEmpty());
        verify(connection, never()).sendTCP(any(IPacket.class));
        verify(gameManager, never()).getClientsInRoom(anyShort());
    }

    @Test
    void requiresAnAuthenticatedPlayerWhoIsInARoom() {
        when(connection.getClient()).thenReturn(client);
        when(client.hasPlayer()).thenReturn(true);
        when(client.getActiveRoom()).thenReturn(room);
        when(client.getRoomPlayer()).thenReturn(null);

        handler.handle(connection, packet(501, "NO ROOM MEMBER"));

        verify(personalBoardService, never()).use(any(), any());
    }

    @Test
    void enforcesTheNativeTwoToEightyCharacterBoundaryAndProfanityCheck() {
        arrangeAuthenticatedRoomPlayer();

        handler.handle(connection, packet(501, "X"));
        handler.handle(connection, packet(501, "X".repeat(81)));
        when(profaneWordsService.textContainsProfaneWord("BLOCKED BOARD")).thenReturn(true);
        handler.handle(connection, packet(501, "BLOCKED BOARD"));

        verify(personalBoardService, never()).use(any(), any());
    }

    private void arrangeAuthenticatedRoomPlayer() {
        lenient().when(connection.getClient()).thenReturn(client);
        lenient().when(client.hasPlayer()).thenReturn(true);
        lenient().when(client.getActiveRoom()).thenReturn(room);
        lenient().when(client.getRoomPlayer()).thenReturn(roomPlayer);
        lenient().when(client.getPlayer()).thenReturn(player);
        lenient().when(player.getId()).thenReturn(11L);
        lenient().when(player.getPocketId()).thenReturn(71L);
        lenient().when(player.getName()).thenReturn("SpecialLab");
        lenient().when(gameManager.getClientsInRoom((short) 9)).thenReturn(List.of(recipient));
        lenient().when(recipient.getConnection()).thenReturn(recipientConnection);
    }

    private PersonalBoardService.UseResult success(boolean removed) {
        return new PersonalBoardService.UseResult(PersonalBoardService.UseStatus.SUCCESS, board, removed);
    }

    private CMSGPersonalBoard packet(int playerPocketId, String message) {
        return CMSGPersonalBoard.builder()
                .playerPocketId(playerPocketId)
                .message(message)
                .build();
    }

    private void assertSentPacket(FTConnection target, Class<? extends IPacket> packetType) {
        ArgumentCaptor<IPacket> captor = ArgumentCaptor.forClass(IPacket.class);
        verify(target).sendTCP(captor.capture());
        assertInstanceOf(packetType, captor.getValue());
    }
}
