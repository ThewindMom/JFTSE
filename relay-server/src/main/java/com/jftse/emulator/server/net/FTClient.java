package com.jftse.emulator.server.net;

import com.jftse.server.core.net.Client;
import lombok.Getter;
import lombok.Setter;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Getter
@Setter
public class FTClient extends Client<FTConnection> {
    private volatile Optional<Integer> gameSessionId = Optional.empty();
    private volatile int playerId;
    private volatile boolean spectator;
    private volatile boolean battlemonSession;
    private volatile String relayAuthorizationGeneration;
    private final AtomicBoolean registrationPending = new AtomicBoolean(false);

    public void setGameSessionId(Integer gameSessionId) {
        this.gameSessionId = Optional.of(gameSessionId);
    }

    public void clearGameSessionId() {
        this.gameSessionId = Optional.empty();
        this.relayAuthorizationGeneration = null;
    }

    public RelayRegistration getRelayRegistration() {
        Optional<Integer> currentSessionId = gameSessionId;
        String currentGeneration = relayAuthorizationGeneration;
        if (currentSessionId.isEmpty() || currentGeneration == null) {
            return null;
        }
        return new RelayRegistration(currentSessionId.get(), playerId, spectator,
                battlemonSession, currentGeneration);
    }

    public record RelayRegistration(int gameSessionId, int playerId, boolean spectator,
                                    boolean battlemonSession, String generation) {
    }
}
