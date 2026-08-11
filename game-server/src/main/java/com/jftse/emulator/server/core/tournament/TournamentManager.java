package com.jftse.emulator.server.core.tournament;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class TournamentManager {
    public static final byte SUCCESS = 0;
    public static final byte NOT_FOUND = -8;
    public static final byte ALREADY_APPLIED = -3;
    public static final byte NOT_APPLIED = -1;

    private static final List<BracketEntry> BRACKET_ENTRIES = List.of(
            new BracketEntry("test", "BOT01", "BOT02"),
            new BracketEntry("BOT03", "BOT04", "BOT05"),
            new BracketEntry("BOT06", "BOT07", "BOT08"),
            new BracketEntry("BOT09", "BOT10", "BOT11"),
            new BracketEntry("BOT12", "BOT13", "BOT14"),
            new BracketEntry("BOT15", "BOT16", "BOT17"),
            new BracketEntry("BOT18", "BOT19", "BOT20"),
            new BracketEntry("BOT21", "BOT22", "BOT23"),
            new BracketEntry("BOT24", "BOT25", "BOT26"),
            new BracketEntry("BOT27", "BOT28", "BOT29"),
            new BracketEntry("BOT30", "BOT31", "BOT32"),
            new BracketEntry("BOT33", "BOT34", "BOT35"),
            new BracketEntry("BOT36", "BOT37", "BOT38"),
            new BracketEntry("BOT39", "BOT40", "BOT41"),
            new BracketEntry("BOT42", "BOT43", "BOT44"),
            new BracketEntry("BOT45", "BOT46", "BOT47")
    );
    private static final List<BracketMatch> BRACKET_MATCHES = BRACKET_ENTRIES.stream()
            .skip(1)
            .map(ignored -> new BracketMatch((byte) -1, (byte) 0))
            .toList();

    private static final TournamentManager INSTANCE = new TournamentManager(Clock.systemUTC());

    private final List<TournamentDefinition> tournaments;
    private final ConcurrentMap<Integer, Set<Long>> applications = new ConcurrentHashMap<>();

    public TournamentManager(Clock clock) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        tournaments = List.of(new TournamentDefinition(
                1,
                "JFTSE Open Cup",
                now.minus(1, ChronoUnit.DAYS),
                now.plus(7, ChronoUnit.DAYS),
                now.plus(8, ChronoUnit.DAYS),
                now.plus(9, ChronoUnit.DAYS)
        ));
    }

    public static TournamentManager getInstance() {
        return INSTANCE;
    }

    public List<TournamentDefinition> getTournaments() {
        return tournaments;
    }

    public List<TournamentDefinition> getTournaments(int page) {
        return page == 0 ? tournaments : List.of();
    }

    public List<BracketEntry> getBracketEntries(int tournamentId) {
        return hasTournament(tournamentId) ? BRACKET_ENTRIES : List.of();
    }

    public List<BracketMatch> getBracketMatches(int tournamentId) {
        return hasTournament(tournamentId) ? BRACKET_MATCHES : List.of();
    }

    public byte apply(int tournamentId, long playerId) {
        if (!hasTournament(tournamentId)) {
            return NOT_FOUND;
        }

        return applications
                .computeIfAbsent(tournamentId, ignored -> ConcurrentHashMap.newKeySet())
                .add(playerId) ? SUCCESS : ALREADY_APPLIED;
    }

    public byte cancel(int tournamentId, long playerId) {
        if (!hasTournament(tournamentId)) {
            return NOT_FOUND;
        }

        Set<Long> players = applications.get(tournamentId);
        return players != null && players.remove(playerId) ? SUCCESS : NOT_APPLIED;
    }

    public boolean isApplied(int tournamentId, long playerId) {
        Set<Long> players = applications.get(tournamentId);
        return players != null && players.contains(playerId);
    }

    public boolean hasTournament(int tournamentId) {
        return tournaments.stream().anyMatch(tournament -> tournament.tournamentId() == tournamentId);
    }

    public record TournamentDefinition(
            int tournamentId,
            String title,
            Instant applicationStart,
            Instant applicationEnd,
            Instant tournamentStart,
            Instant tournamentEnd
    ) {
    }

    public record BracketEntry(String first, String second, String third) {
    }

    public record BracketMatch(byte result, byte state) {
    }
}
