package com.jftse.emulator.server.core.manager;

import com.jftse.emulator.server.net.FTClient;
import com.jftse.server.core.item.BattlemonController;
import com.jftse.server.core.shared.rabbit.messages.RelaySessionAuthorizationMessage;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Optional per-session actor policy. Absence deliberately preserves the development relay behavior. */
@Service
public class RelaySessionAuthorizationStore {
    private static RelaySessionAuthorizationStore instance;
    private final ConcurrentHashMap<Integer, SessionAuthorization> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Set<Integer>> registeredParticipants = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        instance = this;
    }

    public static RelaySessionAuthorizationStore getInstance() {
        return instance;
    }

    public void put(RelaySessionAuthorizationMessage message) {
        if (message == null || message.getGameSessionId() == null) {
            throw new IllegalArgumentException("Relay actor policy has no session id");
        }
        if (Boolean.TRUE.equals(message.getRemove())) {
            remove(message.getGameSessionId());
            return;
        }
        if (message.getBattlemon() == null || message.getActorPositionsByPlayerId() == null) {
            throw new IllegalArgumentException("Relay actor policy is incomplete");
        }
        Map<Integer, Boolean> controllers = message.getBattlemonControllerByPlayerId() == null
                ? Map.of() : message.getBattlemonControllerByPlayerId();
        if (!message.getActorPositionsByPlayerId().keySet().containsAll(controllers.keySet()) ||
                controllers.values().stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("Relay actor policy has invalid controller data");
        }

        Map<Integer, Set<Short>> actors = new HashMap<>();
        Set<Short> claimed = new HashSet<>();
        for (Map.Entry<Integer, List<Short>> entry : message.getActorPositionsByPlayerId().entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException("Relay actor policy contains null data");
            }
            Set<Short> positions = new HashSet<>(entry.getValue());
            if (positions.size() != entry.getValue().size() || positions.stream().anyMatch(position ->
                    position == null || position < 0 || position > 3 || !claimed.add(position))) {
                throw new IllegalArgumentException("Relay actor policy has invalid or overlapping actors");
            }
            actors.put(entry.getKey(), Set.copyOf(positions));
        }
        boolean battlemon = Boolean.TRUE.equals(message.getBattlemon());
        if (battlemon && (actors.size() != 2 || !new HashSet<>(actors.values()).equals(Set.of(
                Set.of((short) 0, (short) 2), Set.of((short) 1, (short) 3))))) {
            throw new IllegalArgumentException("Battlemon relay actor policy has an invalid layout");
        }
        sessions.put(message.getGameSessionId(), new SessionAuthorization(
                battlemon, Map.copyOf(actors), Map.copyOf(controllers)));
        registeredParticipants.put(message.getGameSessionId(), ConcurrentHashMap.newKeySet());
    }

    public Optional<SessionAuthorization> find(int gameSessionId) {
        return Optional.ofNullable(sessions.get(gameSessionId));
    }

    public boolean canAct(FTClient client, int actorPosition) {
        return canAct(client, actorPosition, false);
    }

    public boolean canAct(FTClient client, int actorPosition, boolean controllerCommand) {
        if (client == null || client.getGameSessionId().isEmpty()) return false;
        SessionAuthorization policy = sessions.get(client.getGameSessionId().get());
        if (policy == null) return true;
        if (client.isSpectator() || !policy.actorPositionsByPlayerId()
                .getOrDefault(client.getPlayerId(), Set.of()).contains((short) actorPosition)) return false;
        return !controllerCommand || !BattlemonController.isPetActor(actorPosition) ||
                Boolean.TRUE.equals(policy.battlemonControllerByPlayerId().get(client.getPlayerId()));
    }

    public boolean canParticipate(FTClient client) {
        if (client == null || client.getGameSessionId().isEmpty()) return false;
        SessionAuthorization policy = sessions.get(client.getGameSessionId().get());
        return policy == null || !client.isSpectator() &&
                !policy.actorPositionsByPlayerId().getOrDefault(client.getPlayerId(), Set.of()).isEmpty();
    }

    public boolean isAuthorizedActor(int gameSessionId, int actorPosition) {
        SessionAuthorization policy = sessions.get(gameSessionId);
        return policy == null || policy.actorPositionsByPlayerId().values().stream()
                .anyMatch(positions -> positions.contains((short) actorPosition));
    }

    public boolean isBattlemon(int gameSessionId) {
        return find(gameSessionId).map(SessionAuthorization::battlemon).orElse(false);
    }

    public void markParticipantRegistered(int gameSessionId, int playerId) {
        SessionAuthorization policy = sessions.get(gameSessionId);
        Set<Integer> participants = registeredParticipants.get(gameSessionId);
        if (policy != null && participants != null &&
                policy.actorPositionsByPlayerId().containsKey(playerId)) {
            participants.add(playerId);
        }
    }

    public boolean canRemoveAfterSessionEmpties(int gameSessionId) {
        SessionAuthorization policy = sessions.get(gameSessionId);
        if (policy == null) {
            return true;
        }
        Set<Integer> participants = registeredParticipants.get(gameSessionId);
        return participants != null &&
                participants.containsAll(policy.actorPositionsByPlayerId().keySet());
    }

    public void remove(int gameSessionId) {
        sessions.remove(gameSessionId);
        registeredParticipants.remove(gameSessionId);
    }

    public record SessionAuthorization(boolean battlemon,
                                       Map<Integer, Set<Short>> actorPositionsByPlayerId,
                                       Map<Integer, Boolean> battlemonControllerByPlayerId) {}
}
