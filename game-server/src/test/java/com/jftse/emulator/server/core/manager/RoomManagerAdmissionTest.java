package com.jftse.emulator.server.core.manager;

import com.jftse.emulator.common.service.ConfigService;
import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.client.GuildView;
import com.jftse.emulator.server.core.constants.RoomPositionState;
import com.jftse.emulator.server.core.constants.RoomType;
import com.jftse.emulator.server.core.life.room.ClubMatchRules;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomCreateResult;
import com.jftse.emulator.server.core.life.room.RoomJoinResult;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.constants.GameMode;
import com.jftse.server.core.service.SocialService;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomCreate;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomCreateQuick;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoomManagerAdmissionTest {
    private static final GuildView RED = new GuildView(10, "Red Club", 0, 0, 0, 0, 0, 0);
    private static final GuildView BLUE = new GuildView(20, "Blue Club", 0, 0, 0, 0, 0, 0);
    private static final GuildView THIRD = new GuildView(30, "Third Club", 0, 0, 0, 0, 0, 0);
    private static final byte ORDINARY_SERVER = 1;

    private RoomManager roomManager;

    @BeforeEach
    void setUp() {
        ConfigService configService = mock(ConfigService.class);
        when(configService.getValue(anyString(), any())).thenAnswer(invocation -> invocation.getArgument(1));

        roomManager = new RoomManager();
        roomManager.init();
        ReflectionTestUtils.setField(roomManager, "socialService", mock(SocialService.class));
        ReflectionTestUtils.setField(roomManager, "configService", configService);
    }

    @Test
    void clubListenerCreateRemapsMatchRequestToClubRoomType() {
        RoomCreateResult created = roomManager.createRoom(
                createPacket(ClubMatchRules.CLUB_ROOM_REQUEST_TYPE, GameMode.BASIC, 2),
                client(1, RED, ClubMatchRules.CLUB_SERVER_TYPE));

        assertEquals((char) ClubMatchRules.SUCCESS, created.result());
        assertEquals(ClubMatchRules.CLUB_ROOM_TYPE, created.room().getRoomType());
        assertEquals(ClubMatchRules.CLUB_SERVER_TYPE, created.room().getGameServerType());
        assertEquals(5, created.room().getClubMatchMaxPlayTimeMinutes());
        assertEquals(RoomPositionState.Locked, (short) created.room().getPositions().get(2));
    }

    @Test
    void ordinaryListenerKeepsRequestedRoomTypeAndServerType() {
        RoomCreateResult created = roomManager.createRoom(
                createPacket(RoomType.MATCH, GameMode.BASIC, 2),
                client(1, RED, ORDINARY_SERVER));

        assertEquals((char) 0, created.result());
        assertEquals(RoomType.MATCH, created.room().getRoomType());
        assertEquals(ORDINARY_SERVER, created.room().getGameServerType());
        assertEquals(0, created.room().getClubMatchMaxPlayTimeMinutes());
    }

    @Test
    void clubListenerRejectsCreateWithoutGuild() {
        RoomCreateResult created = roomManager.createRoom(
                createPacket(ClubMatchRules.CLUB_ROOM_REQUEST_TYPE, GameMode.BASIC, 2),
                client(1, null, ClubMatchRules.CLUB_SERVER_TYPE));

        assertEquals((char) ClubMatchRules.NOT_GUILD_MEMBER, created.result());
        assertNull(created.room());
        assertEquals(0, roomManager.getRooms().size());
    }

    @Test
    void clubListenerRejectsUnimplementedPetMode() {
        RoomCreateResult created = roomManager.createRoom(
                createPacket(ClubMatchRules.CLUB_ROOM_REQUEST_TYPE, GameMode.BATTLE, 2),
                client(1, RED, ClubMatchRules.CLUB_SERVER_TYPE));

        assertEquals((char) ClubMatchRules.UNSUPPORTED_MODE, created.result());
        assertNull(created.room());
    }

    @Test
    void clubListenerRejectsUnsupportedCapacity() {
        RoomCreateResult created = roomManager.createRoom(
                createPacket(ClubMatchRules.CLUB_ROOM_REQUEST_TYPE, GameMode.BASIC, 3),
                client(1, RED, ClubMatchRules.CLUB_SERVER_TYPE));

        assertEquals((char) ClubMatchRules.UNSUPPORTED_CAPACITY, created.result());
        assertNull(created.room());
    }

    @Test
    void clubListenerQuickCreateRemapsAndValidatesLikeNormalCreate() {
        CMSGRoomCreateQuick packet = CMSGRoomCreateQuick.builder()
                .roomType((byte) ClubMatchRules.CLUB_ROOM_REQUEST_TYPE)
                .mode((byte) GameMode.BASIC)
                .players((byte) 2)
                .build();

        RoomCreateResult created = roomManager.createRoom(packet, client(1, RED, ClubMatchRules.CLUB_SERVER_TYPE));
        RoomCreateResult rejected = roomManager.createRoom(packet, client(2, null, ClubMatchRules.CLUB_SERVER_TYPE));

        assertEquals(ClubMatchRules.CLUB_ROOM_TYPE, created.room().getRoomType());
        assertEquals(5, created.room().getClubMatchMaxPlayTimeMinutes());
        assertEquals((char) ClubMatchRules.NOT_GUILD_MEMBER, rejected.result());
    }

    @Test
    void clubJoinSeatsSameGuildOnTheSameSideAndRejectsAThirdGuild() {
        FTClient master = client(1, RED, ClubMatchRules.CLUB_SERVER_TYPE);
        Room room = roomManager.createRoom(
                createPacket(ClubMatchRules.CLUB_ROOM_REQUEST_TYPE, GameMode.BASIC, 4), master).room();
        assertNotNull(room);

        FTClient blueClient = client(2, BLUE, ClubMatchRules.CLUB_SERVER_TYPE);
        RoomJoinResult blue = roomManager.joinRoom(blueClient, room.getRoomId(), (byte) 0, null);
        RoomJoinResult redMate = roomManager.joinRoom(client(3, RED, ClubMatchRules.CLUB_SERVER_TYPE),
                room.getRoomId(), (byte) 0, null);
        RoomJoinResult third = roomManager.joinRoom(client(4, THIRD, ClubMatchRules.CLUB_SERVER_TYPE),
                room.getRoomId(), (byte) 0, null);

        assertEquals((char) 0, blue.result());
        assertEquals(1, blue.player().getPosition());
        assertSame(room, blueClient.getActiveRoom());
        assertEquals((char) 0, redMate.result());
        assertEquals(2, redMate.player().getPosition());
        assertEquals((char) ClubMatchRules.GUILD_COUNT_LIMIT, third.result());
        assertNull(third.player());
        assertEquals(RoomPositionState.Free, (short) room.getPositions().get(3));
    }

    @Test
    void clubJoinRejectsPlayerWithoutGuild() {
        Room room = roomManager.createRoom(
                createPacket(ClubMatchRules.CLUB_ROOM_REQUEST_TYPE, GameMode.BASIC, 2),
                client(1, RED, ClubMatchRules.CLUB_SERVER_TYPE)).room();

        RoomJoinResult joined = roomManager.joinRoom(client(2, null, ClubMatchRules.CLUB_SERVER_TYPE),
                room.getRoomId(), (byte) 0, null);

        assertEquals((char) ClubMatchRules.NOT_GUILD_MEMBER, joined.result());
        assertEquals(1, room.getRoomPlayerList().size());
    }

    @Test
    void battlemonCreateOpensAFourPlayerPetRoom() {
        RoomCreateResult created = roomManager.createRoom(
                createPacket(RoomType.BATTLEMON, GameMode.BASIC, 2),
                client(1, null, ORDINARY_SERVER));

        assertEquals((char) 0, created.result());
        assertEquals(RoomType.BATTLEMON, created.room().getRoomType());
        assertEquals(4, created.room().getPlayers());
        assertEquals(1, created.room().getAllowBattlemon());
        assertEquals(RoomPositionState.Free, (short) created.room().getPositions().get(2));
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

        FTConnection connection = mock(FTConnection.class);
        when(connection.getGameServerType()).thenReturn(gameServerType);

        FTClient client = new FTClient();
        client.setConnection(connection);
        client.refreshPlayer(player);
        return client;
    }
}
