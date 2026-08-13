package com.jftse.emulator.server.core.handler.matchplay;

import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.matchplay.MatchplayGame;
import com.jftse.emulator.server.core.matchplay.MatchplayHandleable;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.shared.packets.matchplay.CMSGPoint;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchplayPointPacketHandlerTest {
    @Test
    void gameplayEndpointMayReportPointScoredByAnyBattlemonActor() {
        FTConnection connection = mock(FTConnection.class);
        FTClient client = mock(FTClient.class);
        GameSession gameSession = mock(GameSession.class);
        MatchplayGame game = mock(MatchplayGame.class);
        MatchplayHandleable handleable = mock(MatchplayHandleable.class);
        CMSGPoint point = mock(CMSGPoint.class);
        when(point.getPlayerPosition()).thenReturn((byte) 3);
        when(connection.getClient()).thenReturn(client);
        when(client.getActiveGameSession()).thenReturn(gameSession);
        when(gameSession.isGameplayEndpoint(client)).thenReturn(true);
        when(gameSession.getMatchplayGame()).thenReturn(game);
        when(game.getHandleable()).thenReturn(handleable);

        new MatchplayPointPacketHandler().handle(connection, point);

        verify(handleable).onPoint(client, point);
    }

    @Test
    void nonGameplayEndpointCannotReportPoint() {
        FTConnection connection = mock(FTConnection.class);
        FTClient client = mock(FTClient.class);
        GameSession gameSession = mock(GameSession.class);
        MatchplayGame game = mock(MatchplayGame.class);
        MatchplayHandleable handleable = mock(MatchplayHandleable.class);
        CMSGPoint point = mock(CMSGPoint.class);
        when(connection.getClient()).thenReturn(client);
        when(client.getActiveGameSession()).thenReturn(gameSession);
        when(gameSession.isGameplayEndpoint(client)).thenReturn(false);
        when(gameSession.getMatchplayGame()).thenReturn(game);
        when(game.getHandleable()).thenReturn(handleable);

        new MatchplayPointPacketHandler().handle(connection, point);

        verify(handleable, never()).onPoint(client, point);
    }
}
