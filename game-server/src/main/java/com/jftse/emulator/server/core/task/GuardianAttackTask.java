package com.jftse.emulator.server.core.task;

import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.matchplay.event.EventHandler;
import com.jftse.emulator.server.core.matchplay.event.RunnableEvent;
import com.jftse.emulator.server.core.matchplay.game.MatchplayGuardianGame;
import com.jftse.emulator.server.core.matchplay.guardian.AdvancedGuardianState;
import com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayGiveSpecificSkill;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.battle.Skill;
import com.jftse.server.core.matchplay.battle.GuardianBattleState;
import com.jftse.server.core.service.GuardianSkillsService;
import com.jftse.server.core.thread.AbstractTask;
import lombok.extern.log4j.Log4j2;

import java.util.List;

@Log4j2
public class GuardianAttackTask extends AbstractTask {
    private final FTConnection connection;
    private final GameSession gameSession;

    private final GuardianSkillsService guardianSkillsService;

    private final List<GuardianBattleState> guardians;

    private final EventHandler eventHandler;

    public GuardianAttackTask(FTConnection connection) {
        this(connection, null);
    }

    public GuardianAttackTask(FTConnection connection, GuardianBattleState guardianBattleState) {
        this.connection = connection;
        this.gameSession = connection.getClient().getActiveGameSession();

        this.guardianSkillsService = ServiceManager.getInstance().getGuardianSkillsService();
        this.guardians = guardianBattleState != null ? List.of(guardianBattleState)
                : gameSession == null ? List.of()
                : List.copyOf(((MatchplayGuardianGame) gameSession.getMatchplayGame()).getGuardianBattleStates());

        eventHandler = GameManager.getInstance().getEventHandler();
    }

    @Override
    public void run() {
        if (connection.getClient() == null) return;
        if (gameSession == null || connection.getClient().getActiveGameSession() != gameSession) return;
        MatchplayGuardianGame game = (MatchplayGuardianGame) gameSession.getMatchplayGame();
        if (game.getFinished().get()) return;

        final boolean hasPhaseEnded = game.isAdvancedBossGuardianMode() && !game.getPhaseManager().getIsRunning().get();
        for (GuardianBattleState guardian : guardians) {
            if (guardian.getCurrentHealth().get() > 0 && game.getGuardianBattleStates().contains(guardian)) {
                pickAttack(gameSession, game, hasPhaseEnded, guardian);
            }
        }
    }

    private void pickAttack(GameSession gameSession, MatchplayGuardianGame game, boolean hasPhaseEnded, GuardianBattleState guardianBattleState) {
        final long loopTime = hasPhaseEnded || !game.isAdvancedBossGuardianMode() ? MatchplayGuardianGame.guardianAttackLoopTime : game.getPhaseManager().getGuardianAttackLoopTime((AdvancedGuardianState) guardianBattleState);
        if (loopTime != -1) {
            final Skill skill = hasPhaseEnded || !game.isAdvancedBossGuardianMode() ?
                    guardianSkillsService.getRandomGuardianSkillBasedOnProbability(guardianBattleState.getBtItemId(), guardianBattleState.getId(), guardianBattleState.isBoss(), game.getScenario(), game.getMap())
                    : guardianBattleState.getRandomGuardianSkillBasedOnProbability();
            final int skillIndex = skill.getId().intValue() - 1;

            synchronized (game) {
                synchronized (connection.getClient()) {
                    if (connection.getClient().getActiveGameSession() != gameSession || game.getFinished().get() ||
                            guardianBattleState.getCurrentHealth().get() < 1 ||
                            !game.getGuardianBattleStates().contains(guardianBattleState)) return;
                    gameSession.authorizeSkillCast(guardianBattleState.getPosition(), skillIndex, System.nanoTime());
                    S2CMatchplayGiveSpecificSkill packet = new S2CMatchplayGiveSpecificSkill((short) 0, (short) guardianBattleState.getPosition(), skillIndex);
                    GameManager.getInstance().sendPacketToAllClientsInSameGameSession(packet, connection);

                    RunnableEvent runnableEvent = eventHandler.createRunnableEvent(new GuardianAttackTask(connection, guardianBattleState), loopTime);
                    gameSession.getFireables().push(runnableEvent);
                    eventHandler.offer(runnableEvent);
                }
            }
        }
    }
}
