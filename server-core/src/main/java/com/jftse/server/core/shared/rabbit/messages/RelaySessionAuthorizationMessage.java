package com.jftse.server.core.shared.rabbit.messages;

import com.jftse.server.core.rabbit.AbstractBaseMessage;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class RelaySessionAuthorizationMessage extends AbstractBaseMessage {
    public static final String ROUTING_KEY = "game.relay.session.authorization";

    private Integer gameSessionId;
    private String generation;
    private Boolean battlemon;
    private Boolean revoked;
    private Map<Integer, List<Short>> actorPositionsByPlayerId;
    private Map<Integer, String> playerAddresses;
    private Instant expiresAt;

    @Builder
    public RelaySessionAuthorizationMessage(Integer gameSessionId, String generation, Boolean battlemon,
                                            Boolean revoked,
                                            Map<Integer, List<Short>> actorPositionsByPlayerId,
                                            Map<Integer, String> playerAddresses,
                                            Instant expiresAt) {
        this.gameSessionId = gameSessionId;
        this.generation = generation;
        this.battlemon = battlemon;
        this.revoked = revoked;
        this.actorPositionsByPlayerId = actorPositionsByPlayerId;
        this.playerAddresses = playerAddresses;
        this.expiresAt = expiresAt;
    }

    @Override
    public String getMessageType() {
        return "RELAY_SESSION_AUTHORIZATION";
    }
}
