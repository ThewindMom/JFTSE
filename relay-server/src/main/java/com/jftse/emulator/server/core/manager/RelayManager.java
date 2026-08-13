package com.jftse.emulator.server.core.manager;

import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.ServerType;
import com.jftse.entities.database.model.Uptime;
import com.jftse.server.core.BuildInfoProperties;
import com.jftse.server.core.ServerLoop;
import com.jftse.server.core.ServerLoopHandler;
import com.jftse.server.core.service.BlockedIPService;
import com.jftse.server.core.service.ServerLoopMetricsService;
import com.jftse.server.core.service.UptimeService;
import com.jftse.server.core.shared.MetricsService;
import com.jftse.server.core.shared.ServerConfService;
import com.jftse.server.core.shared.ServerMetricsContext;
import com.jftse.server.core.protocol.IPacket;
import com.jftse.server.core.shared.packets.SMSGInitHandshake;
import com.jftse.server.core.util.GameTime;
import com.jftse.server.core.util.IntervalTimer;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Getter
@Setter
@Log4j2
public class RelayManager implements ServerLoopHandler {
    private static RelayManager instance;

    @Autowired
    private BlockedIPService blockedIPService;
    @Autowired
    private UptimeService uptimeService;
    @Autowired
    private BuildInfoProperties revisionInfo;
    @Autowired
    private ServerConfService serverConfService;
    @Autowired
    private MetricsService metricsService;
    @Autowired
    private ServerLoopMetricsService serverLoopMetrics;
    @Autowired
    private RelaySessionAuthorizationStore relaySessionAuthorizationStore;

    private ConcurrentLinkedQueue<FTConnection> addConnectionQueue;
    private ConcurrentLinkedDeque<FTClient> clients;
    private ConcurrentHashMap<Integer, ConcurrentLinkedDeque<FTClient>> sessionMap;
    private AtomicInteger playerCount = new AtomicInteger(0);
    private int maxPlayerCount = 0;

    private final Object lock = new Object();

    private IntervalTimer[] timers = new IntervalTimer[ServerTimers.COUNT];

    @PostConstruct
    public void init() {
        instance = this;

        addConnectionQueue = new ConcurrentLinkedQueue<>();
        clients = new ConcurrentLinkedDeque<>();
        sessionMap = new ConcurrentHashMap<>();

        GameTime.updateGameTimers();
        initTimers();

        Uptime uptime = new Uptime();
        uptime.setServerType(ServerType.RELAY_SERVER);
        uptime.setStartTime(GameTime.getStartTime().getEpochSecond());
        uptime.setUptime(0L);
        uptime.setRevision(revisionInfo.getFullVersion());
        uptimeService.save(uptime);

        log.info(this.getClass().getSimpleName() + " initialized");
    }

    public static RelayManager getInstance() {
        return instance;
    }

    public void onExit() {
        uptimeService.updateUptimeAndMaxPlayers(GameTime.getUptimeSeconds(), getMaxPlayerCount(), ServerType.RELAY_SERVER, GameTime.getStartTime().getEpochSecond());

        log.info("RelayManager stopped");
    }

    public void addClient(FTClient client) {
        clients.add(client);
    }

    public void removeClient(FTClient client) {
        if (!clients.remove(client)) {
            log.warn("({}) Client could not be removed from the client list", client.getConnection().getIPString());
        }
    }

    public boolean registerClient(final int sessionId, final int playerId, final boolean spectator,
                                  final boolean battlemon, final String authorizationGeneration,
                                  final FTClient client) {
        AtomicBoolean registered = new AtomicBoolean(false);
        sessionMap.compute(sessionId, (ignored, existingClients) -> {
            ConcurrentLinkedDeque<FTClient> clientList = existingClients == null
                    ? new ConcurrentLinkedDeque<>()
                    : existingClients;
            RelaySessionAuthorizationStore.SessionAuthorization currentAuthorization =
                    relaySessionAuthorizationStore.find(sessionId).orElse(null);
            if (currentAuthorization == null ||
                    !currentAuthorization.generation().equals(authorizationGeneration) ||
                    client.getGameSessionId().isPresent() || clientList.stream()
                    .anyMatch(registeredClient -> authorizationGeneration.equals(
                            registeredClient.getRelayAuthorizationGeneration()) &&
                            registeredClient.getPlayerId() == playerId)) {
                return existingClients;
            }
            client.setGameSessionId(sessionId);
            client.setPlayerId(playerId);
            client.setSpectator(spectator);
            client.setBattlemonSession(battlemon);
            client.setRelayAuthorizationGeneration(authorizationGeneration);
            clientList.add(client);
            registered.set(true);
            return clientList;
        });

        if (!registered.get()) {
            return false;
        }
        playerCount.getAndIncrement();
        maxPlayerCount = Math.max(maxPlayerCount, playerCount.get());
        return true;
    }

    public void removeClient(final int sessionId, final FTClient client) {
        AtomicBoolean foundSession = new AtomicBoolean(false);
        sessionMap.computeIfPresent(sessionId, (ignored, clientList) -> {
            foundSession.set(true);
            if (clientList.remove(client)) {
                playerCount.getAndDecrement();
            }
            return clientList.isEmpty() ? null : clientList;
        });
        if (!foundSession.get()) {
            log.warn("({}) Client not found in session ({})", client.getConnection().getIPString(), sessionId);
        }
    }

    public final List<FTClient> getClientsInSession(final int sessionId, final String authorizationGeneration) {
        if (authorizationGeneration == null) {
            return List.of();
        }
        ConcurrentLinkedDeque<FTClient> clientsInSession = sessionMap.get(sessionId);
        return clientsInSession == null ? List.of() : clientsInSession.stream()
                .filter(client -> authorizationGeneration.equals(client.getRelayAuthorizationGeneration()))
                .toList();
    }

    public void broadcastToSessionGeneration(final int sessionId, final String authorizationGeneration,
                                             final IPacket packet) {
        if (authorizationGeneration == null || packet == null) {
            return;
        }
        sessionMap.computeIfPresent(sessionId, (ignored, clientList) -> {
            RelaySessionAuthorizationStore.SessionAuthorization currentAuthorization =
                    relaySessionAuthorizationStore.find(sessionId).orElse(null);
            if (currentAuthorization == null ||
                    !currentAuthorization.generation().equals(authorizationGeneration)) {
                return clientList;
            }
            clientList.stream()
                    .filter(client -> authorizationGeneration.equals(client.getRelayAuthorizationGeneration()))
                    .map(FTClient::getConnection)
                    .filter(java.util.Objects::nonNull)
                    .forEach(connection -> connection.sendTCP(packet));
            return clientList;
        });
    }

    public void revokeSessionGeneration(final int sessionId, final String authorizationGeneration) {
        AtomicReference<List<FTClient>> revokedClients = new AtomicReference<>(List.of());
        sessionMap.computeIfPresent(sessionId, (ignored, clientList) -> {
            List<FTClient> matchingClients = clientList.stream()
                    .filter(client -> authorizationGeneration.equals(client.getRelayAuthorizationGeneration()))
                    .toList();
            matchingClients.forEach(client -> {
                if (clientList.remove(client)) {
                    playerCount.getAndDecrement();
                }
                client.clearGameSessionId();
            });
            revokedClients.set(matchingClients);
            return clientList.isEmpty() ? null : clientList;
        });
        revokedClients.get().forEach(client -> {
            if (client.getConnection() != null) {
                client.getConnection().close();
            }
        });
    }

    public void revokeOtherSessionGenerations(final int sessionId, final String authorizationGeneration) {
        AtomicReference<List<FTClient>> revokedClients = new AtomicReference<>(List.of());
        sessionMap.computeIfPresent(sessionId, (ignored, clientList) -> {
            List<FTClient> matchingClients = clientList.stream()
                    .filter(client -> !authorizationGeneration.equals(client.getRelayAuthorizationGeneration()))
                    .toList();
            matchingClients.forEach(client -> {
                if (clientList.remove(client)) {
                    playerCount.getAndDecrement();
                }
                client.clearGameSessionId();
            });
            revokedClients.set(matchingClients);
            return clientList.isEmpty() ? null : clientList;
        });
        revokedClients.get().forEach(client -> {
            if (client.getConnection() != null) {
                client.getConnection().close();
            }
        });
    }

    public void queueConnection(FTConnection connection) {
        addConnectionQueue.offer(connection);
    }

    @Override
    public void update(long diff) {
        GameTime.updateGameTimers();

        // update different timers
        for (IntervalTimer timer : timers) {
            if (timer.getCurrent() >= 0)
                timer.update(diff);
            else
                timer.setCurrent(0);
        }

        updateSessions(diff);

        if (timers[ServerTimers.SUPDATE_RELAY_AUTHORIZATIONS.value()].passed()) {
            timers[ServerTimers.SUPDATE_RELAY_AUTHORIZATIONS.value()].reset();
            relaySessionAuthorizationStore.removeExpired().forEach(expiredAuthorization ->
                    revokeSessionGeneration(expiredAuthorization.gameSessionId(),
                            expiredAuthorization.generation()));
        }

        if (timers[ServerTimers.SUPDATE_METRICS.value()].passed()) {
            timers[ServerTimers.SUPDATE_METRICS.value()].reset();

            ServerMetricsContext ctx = ServerMetricsContext
                    .of(ServerType.RELAY_SERVER, revisionInfo, ServerLoop.getInstance())
                    .attr("connections", clients.size());
            serverLoopMetrics.publishMetrics(ctx);
        }

        if (timers[ServerTimers.SUPDATE_UPTIME.value()].passed()) {
            long uptimeSeconds = GameTime.getUptimeSeconds();
            int maxOnlinePlayers = getMaxPlayerCount();
            timers[ServerTimers.SUPDATE_UPTIME.value()].reset();

            uptimeService.updateUptimeAndMaxPlayers(uptimeSeconds, maxOnlinePlayers, ServerType.RELAY_SERVER, GameTime.getStartTime().getEpochSecond());
        }
    }

    private void updateSessions(long diff) {
        FTConnection conn;
        while ((conn = addConnectionQueue.poll()) != null) {
            initializeConnection(conn);
        }

        for (FTClient client : clients) {
            FTConnection connection = client.getConnection();
            if (connection == null || !connection.update(diff)) {
                removeClient(client);
            }
        }
    }

    private void initializeConnection(FTConnection conn) {
        if (conn.getClient() != null) {
            log.warn("({}) Connection already has a client assigned", conn.getIPString());
            return;
        }

        FTClient client = new FTClient();
        client.setConnection(conn);
        conn.setClient(client);
        addClient(client);

        SMSGInitHandshake initHandshakePacket = SMSGInitHandshake.builder()
                .decKey(conn.getDecryptionKey())
                .encKey(conn.getEncryptionKey())
                .decTblIdx(0)
                .encTblIdx(0)
                .build();
        conn.sendTCP(initHandshakePacket);
    }

    private void initTimers() {
        for (int i = 0; i < ServerTimers.COUNT; i++) {
            timers[i] = new IntervalTimer();
        }
        timers[ServerTimers.SUPDATE_UPTIME.value()].setInterval(TimeUnit.MINUTES.toMillis(serverConfService.get("UpdateUptimeInterval", Integer.class)));
        timers[ServerTimers.SUPDATE_METRICS.value()].setInterval(TimeUnit.SECONDS.toMillis(serverConfService.get("UpdateMetricsInterval", Integer.class)));
        timers[ServerTimers.SUPDATE_RELAY_AUTHORIZATIONS.value()].setInterval(TimeUnit.MINUTES.toMillis(1));
    }

    public enum ServerTimers {
        SUPDATE_METRICS,
        SUPDATE_UPTIME,
        SUPDATE_RELAY_AUTHORIZATIONS;

        public static final int COUNT = values().length;

        public int value() {
            return this.ordinal();
        }
    }
}
