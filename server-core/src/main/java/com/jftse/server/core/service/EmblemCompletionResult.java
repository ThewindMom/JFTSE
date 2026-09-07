package com.jftse.server.core.service;

import java.util.List;

public record EmblemCompletionResult(EmblemQuestStatus status, byte level, int exp, int gold,
                                     List<EmblemRewardItem> rewards) {
    public EmblemCompletionResult { rewards = List.copyOf(rewards); }
    public static EmblemCompletionResult failure(EmblemQuestStatus status) {
        return new EmblemCompletionResult(status, (byte) 0, 0, 0, List.of());
    }
}
