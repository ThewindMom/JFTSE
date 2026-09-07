package com.jftse.server.core.service;

public record EmblemQuestState(
        short questIndex, boolean inProgress, short completionCount,
        boolean condition1Present, short progress1,
        boolean condition2Present, short progress2,
        boolean condition3Present, short progress3,
        boolean condition4Present, short progress4
) {
}
