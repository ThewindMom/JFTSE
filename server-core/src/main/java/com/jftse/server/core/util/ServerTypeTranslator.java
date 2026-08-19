package com.jftse.server.core.util;

import com.jftse.entities.database.model.ServerType;
import com.jftse.server.core.constants.FTChannelType;

public final class ServerTypeTranslator {
    public static FTChannelType toChannelType(ServerType serverType) {
        if (serverType == null) {
            return FTChannelType.NONE;
        }

        return switch (serverType) {
            case GAME_SERVER -> FTChannelType.FREE;
            case CHAT_SERVER -> FTChannelType.CHAT;
            default -> FTChannelType.NONE;
        };
    }

    public static ServerType toServerType(FTChannelType channelType) {
        if (channelType == null) {
            return ServerType.NONE;
        }

        return switch (channelType) {
            case FREE -> ServerType.GAME_SERVER;
            case CHAT -> ServerType.CHAT_SERVER;
            default -> ServerType.NONE;
        };
    }
}
