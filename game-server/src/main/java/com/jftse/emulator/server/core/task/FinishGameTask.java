package com.jftse.emulator.server.core.task;

import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.matchplay.MatchplayGame;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.thread.AbstractTask;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class FinishGameTask extends AbstractTask {
    private final FTConnection connection;
    private final GameSession gameSession;

    public FinishGameTask(FTConnection connection) {
        this.connection = connection;
        this.gameSession = connection.getClient() == null ? null : connection.getClient().getActiveGameSession();
    }

    @Override
    public void run() {
        if (connection.getClient() == null) return;

        if (gameSession == null || connection.getClient().getActiveGameSession() != gameSession) return;

        MatchplayGame game = gameSession.getMatchplayGame();

        if (game != null && !game.getFinished().get()) {
            synchronized (game) {
                if (game instanceof com.jftse.emulator.server.core.matchplay.game.MatchplayGuardianGame guardian &&
                        guardian.deferUntilLootComplete(this)) return;
            }
            game.getScheduledFutures().forEach(sf -> sf.cancel(false));
            game.getScheduledFutures().clear();
            game.getHandleable().onEnd(connection.getClient());
        }
    }
}
