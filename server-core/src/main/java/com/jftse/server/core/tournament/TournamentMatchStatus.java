package com.jftse.server.core.tournament;

public final class TournamentMatchStatus {
    public static final byte WAITING = 0;
    public static final byte READY = 1;
    public static final byte ACTIVE = 2;
    public static final byte COMPLETED = 3;
    public static final byte ABORTED = 4;

    private TournamentMatchStatus() {
    }
}
