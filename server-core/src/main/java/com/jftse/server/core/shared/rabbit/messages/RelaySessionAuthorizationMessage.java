package com.jftse.server.core.shared.rabbit.messages;

import com.jftse.server.core.rabbit.AbstractBaseMessage;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class RelaySessionAuthorizationMessage extends AbstractBaseMessage {
    public static final String ROUTING_KEY = "game.relay.session.authorization";

    private Integer gameSessionId;
    private Boolean battlemon;
    private Boolean ownedPetSession;
    private Map<Integer, List<Short>> actorPositionsByPlayerId;
    private Map<Integer, Boolean> battlemonControllerByPlayerId;
    private Boolean remove;

    @Builder
    public RelaySessionAuthorizationMessage(Integer gameSessionId, Boolean battlemon, Boolean ownedPetSession,
                                            Map<Integer, List<Short>> actorPositionsByPlayerId,
                                            Map<Integer, Boolean> battlemonControllerByPlayerId,
                                            Boolean remove) {
        this.gameSessionId = gameSessionId;
        this.battlemon = battlemon;
        this.ownedPetSession = ownedPetSession;
        this.actorPositionsByPlayerId = actorPositionsByPlayerId;
        this.battlemonControllerByPlayerId = battlemonControllerByPlayerId;
        this.remove = remove;
    }

    @Override
    public String getMessageType() {
        return "RELAY_SESSION_AUTHORIZATION";
    }
}
