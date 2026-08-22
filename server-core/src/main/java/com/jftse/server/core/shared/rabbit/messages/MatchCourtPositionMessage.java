package com.jftse.server.core.shared.rabbit.messages;

import com.jftse.server.core.rabbit.AbstractBaseMessage;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Relay → game-server court feet from CMSG_PlayerAnimation.
 * absoluteXPositionOnMap → x, absoluteYPositionOnMap → z (court Z).
 */
@Getter
@Setter
@NoArgsConstructor
public class MatchCourtPositionMessage extends AbstractBaseMessage {
    public static final String ROUTING_KEY = "game.stats.match.court";
    public static final String TYPE = "MATCH_COURT_POSITION";

    private Integer gameSessionId;
    private Integer playerId;
    private Integer playerPosition;
    private Integer x;
    private Integer z;

    @Builder
    public MatchCourtPositionMessage(Integer gameSessionId, Integer playerId, Integer playerPosition, Integer x, Integer z) {
        this.gameSessionId = gameSessionId;
        this.playerId = playerId;
        this.playerPosition = playerPosition;
        this.x = x;
        this.z = z;
    }

    /**
     * Map CMSG_PlayerAnimation fields onto court X/Z. absoluteY is court Z.
     * Native animation shorts are millicourt (100 = 1 court unit). Pad circles
     * and spawn Points use court units, so always scale.
     */
    public static MatchCourtPositionMessage fromAnimation(Integer gameSessionId, Integer playerId,
                                                          int playerPosition, short absoluteXPositionOnMap,
                                                          short absoluteYPositionOnMap) {
        return MatchCourtPositionMessage.builder()
                .gameSessionId(gameSessionId)
                .playerId(playerId)
                .playerPosition(playerPosition)
                .x(toCourtUnits(absoluteXPositionOnMap))
                .z(toCourtUnits(absoluteYPositionOnMap))
                .build();
    }

    static int toCourtUnits(short raw) {
        return Math.round(raw / 100.0f);
    }

    @Override
    public String getMessageType() {
        return TYPE;
    }
}
