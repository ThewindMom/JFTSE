package com.jftse.emulator.server.core.manager;

import com.jftse.emulator.server.net.FTClient;
import com.jftse.server.core.shared.rabbit.messages.RelaySessionAuthorizationMessage;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RelaySessionAuthorizationStore {
    private static RelaySessionAuthorizationStore instance;

    private final ConcurrentHashMap<Integer, SessionAuthorization> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> revokedGenerations = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        instance = this;
    }

    public static RelaySessionAuthorizationStore getInstance() {
        return instance;
    }

    public void put(RelaySessionAuthorizationMessage message) {
        if (message == null || message.getGameSessionId() == null || message.getGeneration() == null ||
                message.getGeneration().isBlank() || message.getBattlemon() == null || message.getRevoked() == null ||
                message.getExpiresAt() == null ||
                !message.getExpiresAt().isAfter(Instant.now())) {
            throw new IllegalArgumentException("Relay session authorization is incomplete or expired");
        }

        if (Boolean.TRUE.equals(message.getRevoked())) {
            revokedGenerations.put(message.getGeneration(), message.getExpiresAt());
            sessions.computeIfPresent(message.getGameSessionId(), (sessionId, authorization) ->
                    authorization.generation().equals(message.getGeneration()) ? null : authorization);
            return;
        }
        if (revokedGenerations.containsKey(message.getGeneration())) {
            return;
        }
        if (message.getActorPositionsByPlayerId() == null || message.getPlayerAddresses() == null ||
                !message.getPlayerAddresses().keySet().equals(message.getActorPositionsByPlayerId().keySet())) {
            throw new IllegalArgumentException("Relay session authorization has incomplete endpoint data");
        }

        Map<Integer, Set<Short>> actorsByPlayerId = new HashMap<>();
        Set<Short> claimedActors = new HashSet<>();
        for (Map.Entry<Integer, List<Short>> entry : message.getActorPositionsByPlayerId().entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException("Relay session authorization contains a null player or actor list");
            }
            Set<Short> actorPositions = new HashSet<>(entry.getValue());
            if (actorPositions.stream().anyMatch(position -> position == null || position < 0 || position > 3)) {
                throw new IllegalArgumentException("Relay actor position is outside the gameplay range");
            }
            if (actorPositions.size() != entry.getValue().size() ||
                    actorPositions.stream().anyMatch(position -> !claimedActors.add(position))) {
                throw new IllegalArgumentException("Relay actor ownership overlaps");
            }
            actorsByPlayerId.put(entry.getKey(), Set.copyOf(actorPositions));
        }

        boolean battlemon = Boolean.TRUE.equals(message.getBattlemon());
        if (battlemon && (actorsByPlayerId.size() != 2 ||
                !new HashSet<>(actorsByPlayerId.values()).equals(Set.of(
                        Set.of((short) 0, (short) 2),
                        Set.of((short) 1, (short) 3))))) {
            throw new IllegalArgumentException("Battlemon relay authorization has an invalid actor layout");
        }

        Map<Integer, String> playerAddresses = new HashMap<>();
        message.getPlayerAddresses().forEach((playerId, address) -> {
            if (address == null || address.isBlank()) {
                throw new IllegalArgumentException("Relay endpoint address is missing");
            }
            playerAddresses.put(playerId, address);
        });

        SessionAuthorization newAuthorization = new SessionAuthorization(
                message.getGeneration(),
                battlemon,
                Map.copyOf(actorsByPlayerId),
                Map.copyOf(playerAddresses),
                message.getExpiresAt()
        );
        sessions.compute(message.getGameSessionId(), (sessionId, existing) -> {
            if (existing == null || !existing.expiresAt().isAfter(Instant.now())) {
                return newAuthorization;
            }
            if (existing.generation().equals(newAuthorization.generation()) && existing.equals(newAuthorization)) {
                return existing;
            }
            throw new IllegalStateException("Relay session already has a different live authorization");
        });
    }

    public Optional<SessionAuthorization> find(int gameSessionId) {
        SessionAuthorization authorization = sessions.get(gameSessionId);
        if (authorization != null && !authorization.expiresAt().isAfter(Instant.now())) {
            authorization = null;
        }
        return Optional.ofNullable(authorization);
    }

    public boolean canRegister(int gameSessionId, int playerId, boolean spectator, String remoteAddress) {
        return find(gameSessionId)
                .map(authorization -> {
                    Set<Short> actorPositions = authorization.actorPositionsByPlayerId().get(playerId);
                    String expectedAddress = authorization.playerAddresses().get(playerId);
                    return actorPositions != null && spectator == actorPositions.isEmpty() &&
                            expectedAddress != null && expectedAddress.equals(remoteAddress);
                })
                .orElse(false);
    }

    public boolean canAct(FTClient client, int actorPosition) {
        FTClient.RelayRegistration registration = client == null ? null : client.getRelayRegistration();
        if (registration == null || registration.spectator()) {
            return false;
        }
        return findForClient(registration)
                .map(authorization -> authorization.actorPositionsByPlayerId()
                        .getOrDefault(registration.playerId(), Set.of())
                        .contains((short) actorPosition))
                .orElse(false);
    }

    public boolean canParticipate(FTClient client) {
        FTClient.RelayRegistration registration = client == null ? null : client.getRelayRegistration();
        if (registration == null || registration.spectator()) {
            return false;
        }
        return findForClient(registration)
                .map(authorization -> !authorization.actorPositionsByPlayerId()
                        .getOrDefault(registration.playerId(), Set.of())
                        .isEmpty())
                .orElse(false);
    }

    private Optional<SessionAuthorization> findForClient(FTClient.RelayRegistration registration) {
        return find(registration.gameSessionId())
                .filter(authorization -> authorization.generation()
                        .equals(registration.generation()))
                .filter(authorization -> authorization.battlemon() == registration.battlemonSession());
    }

    public boolean isAuthorizedActor(int gameSessionId, int actorPosition) {
        return find(gameSessionId)
                .map(authorization -> authorization.actorPositionsByPlayerId().values().stream()
                        .anyMatch(positions -> positions.contains((short) actorPosition)))
                .orElse(false);
    }

    public boolean isBattlemon(int gameSessionId) {
        return find(gameSessionId).map(SessionAuthorization::battlemon).orElse(false);
    }

    public void remove(int gameSessionId) {
        sessions.remove(gameSessionId);
    }

    public List<ExpiredAuthorization> removeExpired() {
        return removeExpired(Instant.now());
    }

    List<ExpiredAuthorization> removeExpired(Instant now) {
        List<ExpiredAuthorization> expiredAuthorizations = new ArrayList<>();
        sessions.forEach((sessionId, authorization) -> {
            if (!authorization.expiresAt().isAfter(now) && sessions.remove(sessionId, authorization)) {
                expiredAuthorizations.add(new ExpiredAuthorization(sessionId, authorization.generation()));
            }
        });
        revokedGenerations.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
        return List.copyOf(expiredAuthorizations);
    }

    public record ExpiredAuthorization(int gameSessionId, String generation) {
    }

    public record SessionAuthorization(String generation,
                                       boolean battlemon,
                                       Map<Integer, Set<Short>> actorPositionsByPlayerId,
                                       Map<Integer, String> playerAddresses,
                                       Instant expiresAt) {
    }
}
