package com.jftse.emulator.server.core.handler.lobby.room;

import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomChangeAllowBattlemon;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomChangeGameMode;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomChangeIsPrivate;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomChangeLevelRange;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomChangeMap;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomChangeName;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomChangeQuickSlot;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomChangeSkillFree;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomKickPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClubHouseMutationGuardTest {
    private FTConnection connection;
    private Room room;

    @BeforeEach
    void setUp() {
        room = new Room();
        room.setCastleGuildId(11L);
        room.setCastleGuildName("CastleClub");
        room.setRoomName("Club House");
        room.setRoomType((byte) 1);
        room.setMode((byte) 3);
        room.setMap((byte) 5);
        room.setAllowBattlemon((byte) 0);
        room.setPrivate(false);
        room.setLevelRange((byte) 0);
        room.setQuickSlot(true);
        room.setSkillFree(false);

        RoomPlayer roomPlayer = mock(RoomPlayer.class);
        when(roomPlayer.isMaster()).thenReturn(true);

        FTClient client = mock(FTClient.class);
        when(client.hasPlayer()).thenReturn(true);
        when(client.getActiveRoom()).thenReturn(room);
        when(client.getRoomPlayer()).thenReturn(roomPlayer);

        connection = mock(FTConnection.class);
        when(connection.getClient()).thenReturn(client);
    }

    @Test
    void clientRoomMutationPacketsCannotAlterServerOwnedClubHouse() {
        new RoomAllowBattlemonChangePacketHandler().handle(
                connection, mock(CMSGRoomChangeAllowBattlemon.class));
        new RoomIsPrivateChangePacketHandler().handle(
                connection, mock(CMSGRoomChangeIsPrivate.class));
        new RoomLevelRangeChangePacketHandler().handle(
                connection, mock(CMSGRoomChangeLevelRange.class));
        new RoomMapChangeRequestPacketHandler().handle(
                connection, mock(CMSGRoomChangeMap.class));
        new RoomNameChangePacketHandler().handle(
                connection, mock(CMSGRoomChangeName.class));
        new RoomQuickSlotChangePacketHandler().handle(
                connection, mock(CMSGRoomChangeQuickSlot.class));
        new RoomSkillFreeChangePacketHandler().handle(
                connection, mock(CMSGRoomChangeSkillFree.class));
        new RoomKickPlayerRequestPacketHandler().handle(
                connection, mock(CMSGRoomKickPlayer.class));
        new GameModeChangePacketHandler().handle(
                connection, mock(CMSGRoomChangeGameMode.class));

        assertAll(
                () -> assertEquals(11L, room.getCastleGuildId()),
                () -> assertEquals("CastleClub", room.getCastleGuildName()),
                () -> assertEquals("Club House", room.getRoomName()),
                () -> assertEquals(1, room.getRoomType()),
                () -> assertEquals(3, room.getMode()),
                () -> assertEquals(5, room.getMap()),
                () -> assertEquals(0, room.getAllowBattlemon()),
                () -> assertFalse(room.isPrivate()),
                () -> assertEquals(0, room.getLevelRange()),
                () -> assertTrue(room.isQuickSlot()),
                () -> assertFalse(room.isSkillFree()),
                () -> assertTrue(room.getBannedPlayers().isEmpty())
        );
    }
}
