package com.jftse.emulator.server.core.tournament;

import com.jftse.server.core.tournament.TournamentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Log4j2
public class TournamentScheduler {
    private final TournamentService tournamentService;

    public void recover() {
        Instant now = Instant.now();
        tournamentService.recoverRuntimeBindings();
        tournamentService.advanceDueTournaments(now);
        ensureDefaultTournament(now);
        log.info("Tournament schedule initialized and stale runtime bindings recovered");
    }

    @Scheduled(fixedDelayString = "${tournament.scheduler.delay-ms:10000}")
    public void advance() {
        Instant now = Instant.now();
        tournamentService.advanceDueTournaments(now);
        ensureDefaultTournament(now);
    }

    private void ensureDefaultTournament(Instant now) {
        try {
            tournamentService.ensureDefaultTournament(now);
        } catch (ConcurrencyFailureException | DataIntegrityViolationException exception) {
            try {
                tournamentService.ensureDefaultTournament(now);
                log.info("Another scheduler created or advanced the default tournament concurrently");
            } catch (ConcurrencyFailureException | DataIntegrityViolationException verificationFailure) {
                if (verificationFailure != exception) {
                    exception.addSuppressed(verificationFailure);
                }
                throw exception;
            }
        }
    }
}
