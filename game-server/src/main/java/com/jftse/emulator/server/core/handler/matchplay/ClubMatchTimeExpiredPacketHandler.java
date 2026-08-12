package com.jftse.emulator.server.core.handler.matchplay;

import com.jftse.emulator.server.core.life.room.ClubMatchRules;
import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.matchplay.GameSessionManager;
import com.jftse.emulator.server.core.matchplay.handler.MatchplayBasicModeHandler;
import com.jftse.emulator.server.core.matchplay.game.MatchplayBasicGame;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.shared.packets.matchplay.CMSGClubMatchTimeExpired;
import lombok.extern.log4j.Log4j2;

@Log4j2
@PacketId(CMSGClubMatchTimeExpired.PACKET_ID)
public class ClubMatchTimeExpiredPacketHandler implements PacketHandler<FTConnection, CMSGClubMatchTimeExpired> {
    @Override
    public void handle(FTConnection connection, CMSGClubMatchTimeExpired packet) {
        FTClient client = connection.getClient();
        if (client == null) {
            return;
        }
        Integer gameSessionId = client.getGameSessionId();
        if (gameSessionId == null) {
            return;
        }
        Room room = client.getActiveRoom();
        GameSession gameSession = GameSessionManager.getInstance().getGameSessionBySessionId(gameSessionId);
        if (!client.hasPlayer() || !ClubMatchRules.isClubMatch(room) || gameSession == null) {
            return;
        }

        long playerId = client.getPlayer().getId();
        boolean sessionParticipant = gameSession.getClients().stream()
                .anyMatch(sessionClient -> sessionClient == client);
        if (!sessionParticipant) {
            return;
        }

        log.debug("Club Match timer elapsed for player {} in game session {}",
                playerId, gameSessionId);
        if (gameSession.getMatchplayGame() instanceof MatchplayBasicGame
                && gameSession.getMatchplayGame().getHandleable() instanceof MatchplayBasicModeHandler handler) {
            handler.onClubMatchTimerExpired(gameSession, gameSessionId, room);
        }
    }
}
