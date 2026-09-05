package com.jftse.emulator.server.core.task;

import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.matchplay.event.EventHandler;
import com.jftse.emulator.server.core.matchplay.event.RunnableEvent;
import com.jftse.emulator.server.core.matchplay.game.MatchplayGuardianGame;
import com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayDealDamage;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.battle.Skill;
import com.jftse.server.core.matchplay.battle.PlayerBattleState;
import com.jftse.server.core.service.SkillService;
import com.jftse.server.core.thread.AbstractTask;
import com.jftse.server.core.thread.ThreadManager;
import lombok.extern.log4j.Log4j2;

import java.util.concurrent.atomic.AtomicInteger;

@Log4j2
public class ApplyDoTTask extends AbstractTask {
    private final FTConnection connection;
    private final PlayerBattleState player;
    private final GameSession gameSession;
    private final MatchplayGuardianGame game;
    private final boolean bossStage;

    private final EventHandler eventHandler;
    private final SkillService skillService;

    private final int ticks;
    private final int interval;
    private final int damagePerTick;

    private static final long SKILL_ID = 3L;

    public ApplyDoTTask(FTConnection connection, PlayerBattleState player, int ticks, int interval, int damagePerTick) {
        this.connection = connection;
        this.player = player;
        this.ticks = ticks;
        this.interval = interval;
        this.damagePerTick = damagePerTick;
        this.gameSession = connection.getClient() == null ? null : connection.getClient().getActiveGameSession();
        this.game = gameSession != null && gameSession.getMatchplayGame() instanceof MatchplayGuardianGame guardian ? guardian : null;
        this.bossStage = game != null && game.getBossBattleActive().get();

        this.skillService = ServiceManager.getInstance().getSkillService();
        this.eventHandler = GameManager.getInstance().getEventHandler();
    }


    @Override
    public void run() {
        if (game != null && ticks > 0) {
            synchronized (game) {
                if (isCurrentStage()) {
                    scheduleTick(new AtomicInteger());
                }
            }
        }
    }

    private boolean isCurrentStage() {
        final FTClient client = connection.getClient();
        return client != null && client.getActiveGameSession() == gameSession &&
                gameSession.getMatchplayGame() == game && !game.getFinished().get() &&
                game.getBossBattleActive().get() == bossStage && !game.getStageChangingToBoss().get() &&
                game.getPlayerBattleStates().contains(player);
    }

    private void scheduleTick(AtomicInteger tickCounter) {
        RunnableEvent event = eventHandler.createRunnableEvent(() -> applyDoT(tickCounter), interval);
        gameSession.getFireables().push(event);
        eventHandler.offer(event);
    }

    private void applyDoT(AtomicInteger tickCounter) {
        synchronized (game) {
            if (!isCurrentStage() || player.getCurrentHealth().get() <= 0) return;

            Skill skill = skillService.findSkillById(SKILL_ID);
            if (skill != null) {
                short newHealth = game.getGuardianCombatSystem().updateHealthByDamage(player, -damagePerTick);
                S2CMatchplayDealDamage packet = new S2CMatchplayDealDamage((short) player.getPosition(), newHealth, (short) 4, skill.getId().byteValue(), 0.0f, 0.0f);
                GameManager.getInstance().sendPacketToAllClientsInSameGameSession(packet, connection);
                if (game.getPlayerBattleStates().stream().allMatch(state -> state.getCurrentHealth().get() <= 0)) {
                    ThreadManager.getInstance().newTask(new FinishGameTask(connection));
                }
            }

            if (player.getCurrentHealth().get() > 0 && tickCounter.incrementAndGet() < ticks) {
                scheduleTick(tickCounter);
            }
        }
    }
}
