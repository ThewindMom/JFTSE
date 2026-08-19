package com.jftse.server.core.client;

import lombok.Builder;
import lombok.Getter;

@Getter
public class FTFriend {
    private long serverId;

    private final long playerId;
    private final String name;
    private final byte playerType;

    @Builder
    public FTFriend(long serverId, long playerId, String name, byte playerType) {
        this.serverId = serverId;
        this.playerId = playerId;
        this.name = name;
        this.playerType = playerType;
    }
}
