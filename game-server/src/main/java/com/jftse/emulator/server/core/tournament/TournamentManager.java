package com.jftse.emulator.server.core.tournament;

import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.server.core.tournament.TournamentService;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class TournamentManager {
    public static final byte SUCCESS = 0;
    public static final byte NOT_FOUND = -8;
    public static final byte ALREADY_APPLIED = -3;
    public static final byte NOT_APPLIED = -1;
    public static final byte NOT_OPEN = -2;
    public static final byte REGISTRATION_FULL = -4;

    public static final byte QUALIFYING = 0;
    public static final byte FINAL = 1;
    private static final int CAPACITY = 64;

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
        return getTournaments(0);
    }

    public List<TournamentDefinition> getTournaments(int page) {
        TournamentService service = service();
        if (service == null) {
            return page == 0 ? tournaments : List.of();
        }
        return service.findPage(page).stream().map(TournamentManager::toDefinition).toList();
    }

    public List<BracketEntry> getBracketEntries(int tournamentId) {
        return getBracketEntries(tournamentId, FINAL, 0);
    }

    public List<BracketEntry> getBracketEntries(int tournamentId, byte bracketType, int page) {
        TournamentService service = service();
        if (service == null) {
            return hasTournament(tournamentId) && bracketType == FINAL && page == 0
                    ? BRACKET_ENTRIES
                    : List.of();
        }
        return service.bracketEntries(tournamentId, bracketType, page).stream()
                .map(entry -> new BracketEntry(entry.first(), entry.second(), entry.third()))
                .toList();
    }

    public List<BracketMatch> getBracketMatches(int tournamentId) {
        return getBracketMatches(tournamentId, FINAL, 0);
    }

    public List<BracketMatch> getBracketMatches(int tournamentId, byte bracketType, int page) {
        TournamentService service = service();
        if (service == null) {
            return hasTournament(tournamentId) && bracketType == FINAL && page == 0
                    ? BRACKET_MATCHES
                    : List.of();
        }
        return service.bracketMatches(tournamentId, bracketType, page).stream()
                .map(match -> new BracketMatch(match.result(), match.state()))
                .toList();
    }

    public byte apply(int tournamentId, long playerId) {
        return apply(tournamentId, playerId, "P" + playerId);
    }

    public byte apply(int tournamentId, long playerId, String playerName) {
        TournamentService service = service();
        if (service != null) {
            return service.apply(tournamentId, playerId, playerName);
        }
        if (!hasTournament(tournamentId)) {
            return NOT_FOUND;
        }

        Set<Long> players = applications.computeIfAbsent(tournamentId, ignored -> ConcurrentHashMap.newKeySet());
        synchronized (players) {
            if (players.contains(playerId)) {
                return ALREADY_APPLIED;
            }
            if (players.size() >= CAPACITY) {
                return REGISTRATION_FULL;
            }
            players.add(playerId);
            return SUCCESS;
        }
    }

    public byte cancel(int tournamentId, long playerId) {
        TournamentService service = service();
        if (service != null) {
            return service.cancel(tournamentId, playerId);
        }
        if (!hasTournament(tournamentId)) {
            return NOT_FOUND;
        }

        Set<Long> players = applications.get(tournamentId);
        return players != null && players.remove(playerId) ? SUCCESS : NOT_APPLIED;
    }

    public boolean isApplied(int tournamentId, long playerId) {
        return getPlayerState(tournamentId, playerId) != 0;
    }

    public byte getPlayerState(int tournamentId, long playerId) {
        TournamentService service = service();
        if (service != null) {
            return service.playerState(tournamentId, playerId);
        }
        Set<Long> players = applications.get(tournamentId);
        return (byte) (players != null && players.contains(playerId) ? 1 : 0);
    }

    public boolean hasTournament(int tournamentId) {
        return getTournament(tournamentId).isPresent();
    }

    public Optional<TournamentDefinition> getTournament(int tournamentId) {
        TournamentService service = service();
        if (service != null) {
            return service.find(tournamentId).map(TournamentManager::toDefinition);
        }
        return tournaments.stream().filter(tournament -> tournament.tournamentId() == tournamentId).findFirst();
    }

    public List<TournamentDefinition> getArchives(int page) {
        TournamentService service = service();
        return service == null
                ? List.of()
                : service.findArchives(page).stream().map(TournamentManager::toDefinition).toList();
    }

    private TournamentService service() {
        ServiceManager serviceManager = ServiceManager.getInstance();
        return serviceManager == null ? null : serviceManager.getTournamentService();
    }

    private static TournamentDefinition toDefinition(
            com.jftse.entities.database.model.tournament.TournamentDefinition tournament
    ) {
        return new TournamentDefinition(
                Math.toIntExact(tournament.getId()),
                tournament.getTitle(),
                tournament.getApplicationStart().toInstant(),
                tournament.getApplicationEnd().toInstant(),
                tournament.getQualifyingStart().toInstant(),
                tournament.getTournamentEnd().toInstant(),
                tournament.getEntryType(),
                tournament.getGameMode(),
                tournament.getStatus(),
                tournament.getRewardProductIndex(),
                tournament.getRewardQuantity());
    }

    public record TournamentDefinition(
            int tournamentId,
            String title,
            Instant applicationStart,
            Instant applicationEnd,
            Instant tournamentStart,
            Instant tournamentEnd,
            byte entryType,
            byte gameMode,
            byte status,
            int rewardProductIndex,
            int rewardQuantity
    ) {
        public TournamentDefinition(
                int tournamentId,
                String title,
                Instant applicationStart,
                Instant applicationEnd,
                Instant tournamentStart,
                Instant tournamentEnd
        ) {
            this(
                    tournamentId,
                    title,
                    applicationStart,
                    applicationEnd,
                    tournamentStart,
                    tournamentEnd,
                    (byte) 1,
                    (byte) 0,
                    (byte) 1,
                    287,
                    1);
        }
    }

    public record BracketEntry(String first, String second, String third) {
    }

    public record BracketMatch(byte result, byte state) {
    }
}
