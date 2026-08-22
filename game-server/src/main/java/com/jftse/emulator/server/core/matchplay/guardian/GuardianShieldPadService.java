package com.jftse.emulator.server.core.matchplay.guardian;

import com.jftse.emulator.server.core.life.event.GameEventBus;
import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.matchplay.GameSessionManager;
import com.jftse.emulator.server.core.matchplay.game.MatchplayGuardianGame;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.core.packets.chat.S2CChatRoomAnswerPacket;
import com.jftse.server.core.matchplay.battle.PlayerBattleState;
import com.jftse.server.core.shared.packets.matchplay.SMSGPlayerUseSkill;
import com.jftse.server.core.thread.ThreadManager;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Spring wrapper around {@link GuardianShieldPads}.
 * Atlantis map 10 opts out; other Guardian maps keep the shared pad mechanic.
 */
@Service
@Log4j2
public class GuardianShieldPadService {
    /**
     * Official Shield skill index used by SMSGPlayerUseSkill / SkillUse.isShield (DB id 10 → index 9).
     * FieldItem_Skills_Ini3.xml {@code <ID>9</ID><Name>Shield</Name>}.
     */
    public static final byte SHIELD_SKILL_INDEX = 9;

    @Getter
    private static GuardianShieldPadService instance;

    @Getter
    private final GuardianShieldPads pads;

    @Getter
    private final int delaySeconds;

    private final byte visualSkillIndex;
    private final GameEventBus gameEventBus;

    @Autowired
    public GuardianShieldPadService(
            GameEventBus gameEventBus,
            @Value("${jftse.guardian.shield-pads.enabled:true}") boolean enabled,
            @Value("${jftse.guardian.shield-pads.delay-seconds:10}") int delaySeconds,
            @Value("${jftse.guardian.shield-pads.left-x:-40}") int leftX,
            @Value("${jftse.guardian.shield-pads.left-z:-40}") int leftZ,
            @Value("${jftse.guardian.shield-pads.right-x:40}") int rightX,
            @Value("${jftse.guardian.shield-pads.right-z:-40}") int rightZ,
            @Value("${jftse.guardian.shield-pads.radius:15}") int radius,
            @Value("${jftse.guardian.shield-pads.zone-file:}") String zoneFile,
            @Value("${jftse.guardian.shield-pads.visual-skill-index:9}") int visualSkillIndex) {
        this.gameEventBus = gameEventBus;
        this.delaySeconds = delaySeconds;
        this.visualSkillIndex = (byte) visualSkillIndex;
        Path zonePath = (zoneFile == null || zoneFile.isBlank()) ? null : Path.of(zoneFile);
        GuardianShieldPads.Config config = new GuardianShieldPads.Config(
                enabled, delaySeconds, leftX, leftZ, rightX, rightZ, radius, zonePath);
        this.pads = new GuardianShieldPads(config, this::grantShield);
        log.info("Guardian shield pads config enabled={} delay={}s pads=({},{}) ({},{}) r={} zoneFile={} visualSkillIndex={}",
                enabled, delaySeconds, leftX, leftZ, rightX, rightZ, radius, zonePath, visualSkillIndex);
    }

    @PostConstruct
    public void init() {
        instance = this;
        registerMatchEvents();
    }

    /**
     * MP_MATCH_START is fired immediately after handleable.onStart (stage start).
     * MP_MATCH_END covers the normal Guardian finish path; session removal covers banable/cleanup.
     */
    private void registerMatchEvents() {
        if (gameEventBus == null) {
            log.warn("GameEventBus not injected; shield pads will rely on MatchplayGuardianModeHandler hooks");
            return;
        }
        gameEventBus.on("MP_MATCH_START", this::onMatchStartEvent);
        gameEventBus.on("MP_MATCH_END", this::onMatchEndEvent);
    }

    private void onMatchStartEvent(Object... args) {
        MatchplayGuardianGame game = null;
        FTClient client = null;
        for (Object arg : args) {
            if (arg instanceof MatchplayGuardianGame guardianGame) {
                game = guardianGame;
            } else if (arg instanceof FTClient ftClient) {
                client = ftClient;
            }
        }
        if (game == null || client == null || client.getGameSessionId() == null) {
            return;
        }
        schedulePads(game, client.getGameSessionId());
    }

    /** Schedule pads for supported Guardian maps; Atlantis map 10 is excluded. */
    public void schedulePads(MatchplayGuardianGame game, int sessionId) {
        if (game == null) {
            return;
        }
        Integer mapId = game.getMap() != null ? game.getMap().getMap() : null;
        if (!AtlantisV2Rules.shouldSchedulePads(mapId)) {
            pads.trackSession(sessionId);
            log.info("Guardian pads skipped session={} map={} bossActive={}",
                    sessionId, mapId, game.getBossBattleActive().get());
            return;
        }
        onMatchStart(sessionId);
        ScheduledFuture<?> future = ThreadManager.getInstance().schedule(
                () -> activate(sessionId),
                delaySeconds,
                TimeUnit.SECONDS);
        game.getScheduledFutures().add(future);
    }

    private void onMatchEndEvent(Object... args) {
        Integer sessionId = sessionIdFromEvent(args);
        if (sessionId != null) {
            onMatchEnd(sessionId);
        }
    }

    private Integer sessionIdFromEvent(Object... args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof FTClient client && client.getGameSessionId() != null) {
                return client.getGameSessionId();
            }
            if (arg instanceof ConcurrentLinkedDeque<?> deque) {
                for (Object item : deque) {
                    if (item instanceof FTClient client && client.getGameSessionId() != null) {
                        return client.getGameSessionId();
                    }
                }
            }
        }
        return null;
    }

    public void onMatchStart(int sessionId) {
        pads.onMatchStart(sessionId);
    }

    public void activate(int sessionId) {
        pads.activate(sessionId);
        if (pads.isVisible(sessionId)) {
            announce(sessionId, "Safe zones are up. Stand in the green pads at back-left or back-right.");
        }
    }

    public void onMatchEnd(int sessionId) {
        clearPadRings(sessionId);
        pads.onMatchEnd(sessionId);
    }

    public void onCourtPosition(int sessionId, int playerId, int playerPosition, int x, int z) {
        pads.onCourtPosition(sessionId, playerId, playerPosition, x, z);
    }

    /**
     * True when the player's last court X/Z is inside a visible pad circle.
     * Used to drop SeaWave hits without consuming the one-shot shield.
     */
    public boolean isInsideVisiblePad(int sessionId, int playerId, int playerPosition) {
        return pads.isInsideVisiblePad(sessionId, playerId, playerPosition);
    }

    /** One current court centroid per clustered group of living Atlantis players. */
    public GuardianShieldPads.ClusterCenter[] clusteredAlivePlayerCenters(int sessionId, int radius) {
        GameSession session = sessionOf(sessionId);
        if (session == null || !(session.getMatchplayGame() instanceof MatchplayGuardianGame game)) {
            return new GuardianShieldPads.ClusterCenter[0];
        }
        Set<Integer> activePositions = game.getPlayerBattleStates().stream()
                .filter(player -> player != null
                        && AtlantisV2Rules.isPlayerSlot((short) player.getPosition())
                        && player.getCurrentHealth().get() > 0)
                .map(PlayerBattleState::getPosition)
                .collect(java.util.stream.Collectors.toSet());
        return pads.clusteredPlayerCenters(sessionId, radius, activePositions);
    }

    void grantShield(int sessionId, int playerId, int playerPosition) {
        GameSessionManager manager = GameSessionManager.getInstance();
        if (manager == null) {
            return;
        }
        GameSession session = manager.getGameSessionBySessionId(sessionId);
        if (session == null || !session.isGuardianMode() || session.getMatchplayGame() == null) {
            return;
        }
        if (!(session.getMatchplayGame() instanceof MatchplayGuardianGame game)) {
            return;
        }

        PlayerBattleState battleState = game.getPlayerBattleStates().stream()
                .filter(p -> p.getId() == playerId || p.getPosition() == playerPosition)
                .findFirst()
                .orElse(null);
        if (battleState == null) {
            log.warn("Shield grant skipped: no PlayerBattleState session={} playerId={} pos={}",
                    sessionId, playerId, playerPosition);
            return;
        }
        battleState.setShieldActive(true);

        byte pos = (byte) battleState.getPosition();
        SMSGPlayerUseSkill visual = SMSGPlayerUseSkill.builder()
                .attacker(pos)
                .target(pos)
                .skillId(visualSkillIndex)
                .seed((byte) 0)
                .xTarget(0f)
                .zTarget(0f)
                .yTarget(0f)
                .build();
        ConcurrentLinkedDeque<FTClient> clients = session.getClients();
        if (clients != null) {
            clients.forEach(c -> {
                if (c != null && c.getConnection() != null) {
                    c.getConnection().sendTCP(visual);
                }
            });
        }
        log.info("BattleState.shieldActive set and Shield skill index {} broadcast session={} playerId={} pos={}",
                visualSkillIndex, sessionId, playerId, battleState.getPosition());
    }

    private void placePadRings(int sessionId) {
        GameSession session = sessionOf(sessionId);
        if (session == null || !(session.getMatchplayGame() instanceof MatchplayGuardianGame game)) {
            return;
        }
        FTClient client = session.getClients() != null ? session.getClients().peek() : null;
        if (client == null || client.getConnection() == null) {
            return;
        }
        game.placeAtlantisPadRings(client.getConnection());
    }

    private void clearPadRings(int sessionId) {
        GameSession session = sessionOf(sessionId);
        if (session == null || !(session.getMatchplayGame() instanceof MatchplayGuardianGame game)) {
            return;
        }
        FTClient client = session.getClients() != null ? session.getClients().peek() : null;
        if (client == null || client.getConnection() == null) {
            return;
        }
        game.clearMarkerCrystals(client.getConnection());
    }

    private GameSession sessionOf(int sessionId) {
        GameSessionManager manager = GameSessionManager.getInstance();
        return manager == null ? null : manager.getGameSessionBySessionId(sessionId);
    }

    private void announce(int sessionId, String text) {
        GameSessionManager manager = GameSessionManager.getInstance();
        if (manager == null) {
            return;
        }
        GameSession session = manager.getGameSessionBySessionId(sessionId);
        if (session == null || session.getClients() == null) {
            return;
        }
        S2CChatRoomAnswerPacket packet = new S2CChatRoomAnswerPacket((byte) 2, "Server", text);
        session.getClients().forEach(c -> {
            if (c != null && c.getConnection() != null) {
                c.getConnection().sendTCP(packet);
            }
        });
    }
}
