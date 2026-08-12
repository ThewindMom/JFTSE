package com.jftse.server.core.tournament;

public final class TournamentStatus {
    public static final byte PREPARE = 0;
    public static final byte APPLY = 1;
    public static final byte PREPARE_QUALIFYING = 2;
    public static final byte QUALIFYING = 3;
    public static final byte PREPARE_FINAL = 4;
    public static final byte FINAL = 5;
    public static final byte FINISHED = 6;
    public static final byte SUSPENDED = 7;
    public static final byte CANCELED = 8;

    private TournamentStatus() {
    }
}
