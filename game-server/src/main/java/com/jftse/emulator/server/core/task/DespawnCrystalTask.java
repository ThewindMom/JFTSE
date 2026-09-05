package com.jftse.emulator.server.core.task;

import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.matchplay.MatchplayGame;
import com.jftse.emulator.server.core.matchplay.event.EventHandler;
import com.jftse.emulator.server.core.matchplay.event.RunnableEvent;
import com.jftse.emulator.server.core.matchplay.game.MatchplayBattleGame;
import com.jftse.emulator.server.core.matchplay.game.MatchplayGuardianGame;
import com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayLetCrystalDisappear;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.matchplay.battle.SkillCrystal;
import com.jftse.server.core.thread.AbstractTask;

import java.util.concurrent.ConcurrentLinkedDeque;

public class DespawnCrystalTask extends AbstractTask {
    private final FTConnection connection;
    private final SkillCrystal skillCrystal;
    private final short gameFieldSide;
    private final GameSession gameSession;

    private final EventHandler eventHandler;

    public DespawnCrystalTask(FTConnection connection, SkillCrystal skillCrystal, short gameFieldSide) {
        this.connection = connection;
        this.skillCrystal = skillCrystal;
        this.gameFieldSide = gameFieldSide;
        this.gameSession = connection.getClient() == null ? null : connection.getClient().getActiveGameSession();

        eventHandler = GameManager.getInstance().getEventHandler();
    }

    public DespawnCrystalTask(FTConnection connection, SkillCrystal skillCrystal) {
        this(connection, skillCrystal, (short) -1);
    }

    @Override
    public void run() {
        if (connection.getClient() == null) return;

        if (gameSession == null || connection.getClient().getActiveGameSession() != gameSession) return;

        MatchplayGame game = gameSession.getMatchplayGame();
        synchronized (game) {
            synchronized (connection.getClient()) {
                if (connection.getClient().getActiveGameSession() != gameSession || game.getFinished().get()) return;
                boolean isBattleGame = game instanceof MatchplayBattleGame;

                ConcurrentLinkedDeque<SkillCrystal> skillCrystals = isBattleGame ? ((MatchplayBattleGame) game).getSkillCrystals() : ((MatchplayGuardianGame) game).getSkillCrystals();

                if (skillCrystals.remove(skillCrystal)) {
                    S2CMatchplayLetCrystalDisappear letCrystalDisappearPacket = new S2CMatchplayLetCrystalDisappear((short) skillCrystal.getId());
                    GameManager.getInstance().sendPacketToAllClientsInSameGameSession(letCrystalDisappearPacket, connection);

                    RunnableEvent runnableEvent;
                    if (isBattleGame)
                        runnableEvent = eventHandler.createRunnableEvent(new PlaceCrystalRandomlyTask(connection, gameFieldSide), ((MatchplayBattleGame) game).getCrystalSpawnInterval().get());
                    else
                        runnableEvent = eventHandler.createRunnableEvent(new PlaceCrystalRandomlyTask(connection), ((MatchplayGuardianGame) game).getCrystalSpawnInterval().get());

                    gameSession.getFireables().push(runnableEvent);
                    eventHandler.offer(runnableEvent);
                }
            }
        }
    }
}
