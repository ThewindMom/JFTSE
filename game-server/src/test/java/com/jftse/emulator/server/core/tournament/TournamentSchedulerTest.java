package com.jftse.emulator.server.core.tournament;

import com.jftse.entities.database.model.tournament.TournamentDefinition;
import com.jftse.server.core.tournament.TournamentService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TournamentSchedulerTest {
    @Test
    void startupContinuesWhenAnotherSchedulerCreatesTheDefaultTournament() {
        TournamentService tournamentService = mock(TournamentService.class);
        when(tournamentService.ensureDefaultTournament(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate title"))
                .thenReturn(mock(TournamentDefinition.class));
        TournamentScheduler scheduler = new TournamentScheduler(tournamentService);

        assertDoesNotThrow(scheduler::recover);

        verify(tournamentService).recoverRuntimeBindings();
        verify(tournamentService).advanceDueTournaments(any());
        verify(tournamentService, times(2)).ensureDefaultTournament(any());
    }

    @Test
    void startupDoesNotSuppressAnUnrelatedPersistentIntegrityFailure() {
        TournamentService tournamentService = mock(TournamentService.class);
        when(tournamentService.ensureDefaultTournament(any()))
                .thenThrow(
                        new DataIntegrityViolationException("invalid reward product"),
                        new DataIntegrityViolationException("invalid reward product"));
        TournamentScheduler scheduler = new TournamentScheduler(tournamentService);

        assertThrows(DataIntegrityViolationException.class, scheduler::recover);

        verify(tournamentService, times(2)).ensureDefaultTournament(any());
    }
}
