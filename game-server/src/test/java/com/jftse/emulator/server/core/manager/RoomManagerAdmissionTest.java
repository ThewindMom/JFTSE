package com.jftse.emulator.server.core.manager;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.client.GuildView;
import com.jftse.emulator.server.core.constants.RoomType;
import com.jftse.emulator.server.core.life.room.ClubMatchRules;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomCreateResult;
import com.jftse.emulator.server.core.life.room.RoomJoinResult;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.ServerType;
import com.jftse.server.core.constants.GameMode;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomCreate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoomManagerAdmissionTest {
    private static final GuildView RED = new GuildView(10, "Red Club", 0, 0, 0, 0, 0, 0);
    private static final GuildView BLUE = new GuildView(20, "Blue Club", 0, 0, 0, 0, 0, 0);
    private static final GuildView THIRD = new GuildView(30, "Third Club", 0, 0, 0, 0, 0, 0);

    private RoomManager roomManager;

    @BeforeEach
    void setUp() {
        roomManager = new RoomManager();
        roomManager.init();
    }

    @Test
    void clubListenerCreateRemapsMatchRequestToClubRoomType() {
        FTClient client = client(1, RED, ClubMatchRules.CLUB_SERVER_TYPE);
        CMSGRoomCreate packet = createPacket(ClubMatchRules.CLUB_ROOM_REQUEST_TYPE, GameMode.BASIC, 2);

        RoomCreateResult created = roomManager.createRoom(packet, client);

        assertEquals(ClubMatchRules.SUCCESS, created.result());
        assertEquals(ClubMatchRules.CLUB_ROOM_TYPE, created.room().getRoomType());
        assertEquals(ClubMatchRules.CLUB_SERVER_TYPE, created.room().getGameServerType());
        assertEquals(5, created.room().getClubMatchMaxPlayTimeMinutes());
    }

    @Test
    void clubListenerRejectsCreateWithoutGuild() {
        FTClient client = client(1, null, ClubMatchRules.CLUB_SERVER_TYPE);
        CMSGRoomCreate packet = createPacket(ClubMatchRules.CLUB_ROOM_REQUEST_TYPE, GameMode.BASIC, 2);

        RoomCreateResult created = roomManager.createRoom(packet, client);

        assertEquals(ClubMatchRules.NOT_GUILD_MEMBER, created.result());
        assertNull(created.room());
    }

    @Test
    void clubListenerRejectsUnimplementedPetMode() {
        FTClient client = client(1, RED, ClubMatchRules.CLUB_SERVER_TYPE);
        CMSGRoomCreate packet = createPacket(ClubMatchRules.CLUB_ROOM_REQUEST_TYPE, GameMode.BATTLE, 2);

        RoomCreateResult created = roomManager.createRoom(packet, client);

        assertEquals(ClubMatchRules.UNSUPPORTED_MODE, created.result());
        assertNull(created.room());
    }

    @Test
    void clubJoinSeatsSameGuildOnTheSameSideAndRejectsAThirdGuild() {
        Room room = roomManager.createRoom(
                createPacket(ClubMatchRules.CLUB_ROOM_REQUEST_TYPE, GameMode.BASIC, 4),
                client(1, RED, ClubMatchRules.CLUB_SERVER_TYPE)).room();
        assertNotNull(room);

        RoomJoinResult blue = roomManager.joinRoom(client(2, BLUE, ClubMatchRules.CLUB_SERVER_TYPE),
                room.getRoomId(), (byte) 0, null);
        RoomJoinResult redMate = roomManager.joinRoom(client(3, RED, ClubMatchRules.CLUB_SERVER_TYPE),
                room.getRoomId(), (byte) 0, null);
        RoomJoinResult third = roomManager.joinRoom(client(4, THIRD, ClubMatchRules.CLUB_SERVER_TYPE),
                room.getRoomId(), (byte) 0, null);

        assertEquals(0, blue.result());
        assertEquals(1, positionOf(room, 2) % 2);
        assertEquals(0, redMate.result());
        assertEquals(0, positionOf(room, 3) % 2);
        assertEquals(ClubMatchRules.GUILD_COUNT_LIMIT, third.result());
    }

    @Test
    void battlemonCreateOpensAFourPlayerPetRoom() {
        FTClient client = client(1, null, (byte) 1);
        CMSGRoomCreate packet = createPacket((byte) RoomType.BATTLEMON, GameMode.BASIC, 2);

        RoomCreateResult created = roomManager.createRoom(packet, client);

        assertEquals(0, created.result());
        assertEquals(RoomType.BATTLEMON, created.room().getRoomType());
        assertEquals(4, created.room().getPlayers());
        assertEquals(1, created.room().getAllowBattlemon());
    }

    private static int positionOf(Room room, long playerId) {
        return room.getRoomPlayerList().stream()
                .filter(player -> player.getPlayerId() == playerId)
                .findFirst()
                .orElseThrow()
                .getPosition();
    }

    private static CMSGRoomCreate createPacket(int roomType, int mode, int players) {
        CMSGRoomCreate packet = mock(CMSGRoomCreate.class);
        when(packet.getRoomName()).thenReturn("Club #1");
        when(packet.getRoomType()).thenReturn((byte) roomType);
        when(packet.getMode()).thenReturn((byte) mode);
        when(packet.getPlayers()).thenReturn((byte) players);
        when(packet.getIsPrivate()).thenReturn(false);
        when(packet.getSkillFree()).thenReturn(false);
        when(packet.getQuickSlot()).thenReturn(false);
        when(packet.getLevelRange()).thenReturn((byte) 0);
        when(packet.getBettingType()).thenReturn('0');
        when(packet.getBettingAmount()).thenReturn(0);
        when(packet.getBall()).thenReturn(1);
        when(packet.getMapId()).thenReturn((byte) 0);
        return packet;
    }

    private static FTClient client(long playerId, GuildView guild, byte gameServerType) {
        FTPlayer player = mock(FTPlayer.class);
        when(player.getId()).thenReturn(playerId);
        when(player.getLevel()).thenReturn(10);
        when(player.getGuild()).thenReturn(guild);
        when(player.getName()).thenReturn("p" + playerId);

        FTConnection connection = new FTConnection(0, 0, ServerType.GAME_SERVER, gameServerType);
        FTClient client = mock(FTClient.class);
        when(client.getConnection()).thenReturn(connection);
        when(client.getPlayer()).thenReturn(player);
        when(client.hasPlayer()).thenReturn(true);
        when(client.getIsJoiningOrLeavingRoom()).thenReturn(new AtomicBoolean(false));
        return client;
    }
}
