package com.jftse.emulator.server.core.matchplay;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.client.GuildView;
import com.jftse.emulator.server.core.life.room.ClubMatchState;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoomGameLauncherTest {
    private static final GuildView RED_GUILD = guild(10, "Red Club");
    private static final GuildView BLUE_GUILD = guild(20, "Blue Club");

    @Test
    void ordinaryLaunchPreservesPlayersSpectatorsAndInvisibleGameMasters() {
        FTClient player = client(1, 0, RED_GUILD);
        FTClient spectator = client(2, 5, BLUE_GUILD);
        FTClient invisibleGameMaster = client(3, 9, null);
        List<FTClient> roomClients = List.of(player, spectator, invisibleGameMaster);

        List<FTClient> selected = RoomGameLauncher.selectSessionClients(
                false, List.of(), roomClients);

        assertSame(roomClients, selected);
        assertEquals(3, selected.size());
    }

    @Test
    void clubLaunchUsesOnlyTheClaimedParticipantsInSlotOrder() {
        FTClient red = client(1, 0, RED_GUILD);
        FTClient blue = client(2, 1, BLUE_GUILD);
        FTClient spectator = client(3, 5, RED_GUILD);
        List<ClubMatchState.Participant> participants = List.of(
                participant(1, 0, RED_GUILD),
                participant(2, 1, BLUE_GUILD));

        List<FTClient> selected = RoomGameLauncher.selectSessionClients(
                true, participants, List.of(spectator, blue, red));

        assertEquals(List.of(red, blue), selected);
    }

    @Test
    void staleClubPositionOrGuildCannotJoinTheClaimedSession() {
        FTClient movedPlayer = client(1, 2, RED_GUILD);
        FTClient changedGuild = client(2, 1, RED_GUILD);
        List<ClubMatchState.Participant> participants = List.of(
                participant(1, 0, RED_GUILD),
                participant(2, 1, BLUE_GUILD));

        List<FTClient> selected = RoomGameLauncher.selectSessionClients(
                true, participants, List.of(movedPlayer, changedGuild));

        assertEquals(List.of(), selected);
    }

    @Test
    void staleCleanupCannotClearAReplacementGameSession() {
        FTClient client = new FTClient();
        client.setActiveGameSession(12345);

        assertFalse(client.clearActiveGameSession(54321));
        assertEquals(12345, client.getGameSessionId());
        assertTrue(client.clearActiveGameSession(12345));
        assertEquals(null, client.getGameSessionId());
    }

    private static FTClient client(long playerId, int position, GuildView guild) {
        FTClient client = mock(FTClient.class);
        FTPlayer player = mock(FTPlayer.class);
        FTConnection connection = mock(FTConnection.class);
        RoomPlayer roomPlayer = new RoomPlayer(null) {
            @Override
            public long getPlayerId() {
                return playerId;
            }

            @Override
            public GuildView getGuild() {
                return guild;
            }
        };
        roomPlayer.setPosition((short) position);
        when(client.hasPlayer()).thenReturn(true);
        when(client.getPlayer()).thenReturn(player);
        when(player.getId()).thenReturn(playerId);
        when(client.getConnection()).thenReturn(connection);
        when(client.getRoomPlayer()).thenReturn(roomPlayer);
        return client;
    }

    private static ClubMatchState.Participant participant(long playerId, int position,
                                                           GuildView guild) {
        return new ClubMatchState.Participant(playerId, (short) position, guild.id());
    }

    private static GuildView guild(long id, String name) {
        return new GuildView(id, name, 0, 0, 0, 0, 0, 0);
    }
}
