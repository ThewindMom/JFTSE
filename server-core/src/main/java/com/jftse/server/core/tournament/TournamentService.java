package com.jftse.server.core.tournament;

import com.jftse.entities.database.model.tournament.TournamentDefinition;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TournamentService {
    byte SUCCESS = 0;
    byte NOT_FOUND = -8;
    byte ALREADY_APPLIED = -3;
    byte REGISTRATION_FULL = -4;
    byte NOT_APPLIED = -1;
    byte NOT_OPEN = -2;

    byte STAGE_QUALIFYING = 0;
    byte STAGE_FINAL = 1;

    TournamentDefinition create(CreateTournament command);

    TournamentDefinition ensureDefaultTournament(Instant now);

    List<TournamentDefinition> findPage(int page);

    List<TournamentDefinition> findArchives(int page);

    Optional<TournamentDefinition> find(int tournamentId);

    byte apply(int tournamentId, long playerId, String playerName);

    byte cancel(int tournamentId, long playerId);

    byte playerState(int tournamentId, long playerId);

    List<BracketEntry> bracketEntries(int tournamentId, byte bracketType, int page);

    List<BracketMatch> bracketMatches(int tournamentId, byte bracketType, int page);

    Optional<AssignedMatch> assignedMatch(int tournamentId, long playerId);

    Optional<AssignedMatch> matchForRoom(short roomId);

    boolean bindRoom(long matchId, short roomId, long playerId);

    boolean activateMatch(long matchId, short roomId, int gameSessionId, List<Long> activePlayerIds);

    boolean deactivateMatch(short roomId, int gameSessionId);

    CompletionResult completeMatch(long matchId, short roomId, int gameSessionId, long reporterPlayerId, long winnerPlayerId);

    void releaseRoom(short roomId);

    void recoverRuntimeBindings();

    void advanceDueTournaments(Instant now);

    record CreateTournament(
            String title,
            byte entryType,
            byte gameMode,
            int capacity,
            int finalSize,
            int rewardProductIndex,
            int rewardQuantity,
            Instant applicationStart,
            Instant applicationEnd,
            Instant qualifyingStart,
            Instant finalStart,
            Instant tournamentEnd
    ) {
    }

    record BracketEntry(String first, String second, String third) {
    }

    record BracketMatch(byte result, byte state) {
    }

    record AssignedMatch(
            long matchId,
            int tournamentId,
            byte stage,
            int round,
            int slot,
            long playerOneId,
            long playerTwoId,
            Short roomId,
            Integer gameSessionId,
            byte status
    ) {
        public boolean contains(long playerId) {
            return playerOneId == playerId || playerTwoId == playerId;
        }
    }

    enum CompletionResult {
        COMPLETED,
        ALREADY_COMPLETED,
        UNAUTHORIZED,
        NOT_FOUND
    }
}
