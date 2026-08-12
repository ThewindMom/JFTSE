package com.jftse.emulator.server.core.life.room;

import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Getter
public class ClubMatchState {
    private Instant countdownStartedAt;
    private Instant countdownEndsAt;
    private long designatedPlayerId;
    private boolean countdownActive;
    private boolean startTriggered;
    private boolean resultRecorded;
    private boolean timerExpired;
    private Integer gameSessionId;
    private Instant gameEndsAt;
    private long generation;
    private List<Participant> participants = List.of();

    public synchronized boolean startCountdown(Instant now, Duration duration, long designatedPlayerId,
                                               List<Participant> participants) {
        if (countdownActive) {
            return false;
        }
        countdownStartedAt = now;
        countdownEndsAt = now.plus(duration);
        this.designatedPlayerId = designatedPlayerId;
        countdownActive = true;
        startTriggered = false;
        resultRecorded = false;
        timerExpired = false;
        gameSessionId = null;
        gameEndsAt = null;
        this.participants = List.copyOf(participants);
        generation++;
        return true;
    }

    public synchronized boolean cancelCountdown() {
        boolean wasActive = countdownActive || startTriggered;
        countdownStartedAt = null;
        countdownEndsAt = null;
        designatedPlayerId = 0;
        countdownActive = false;
        startTriggered = false;
        timerExpired = false;
        gameSessionId = null;
        gameEndsAt = null;
        participants = List.of();
        generation++;
        return wasActive;
    }

    public synchronized boolean tryStart(long playerId, Instant now,
                                         List<Participant> currentParticipants) {
        if (!countdownActive || startTriggered || playerId != designatedPlayerId
                || now.isBefore(countdownEndsAt) || !matchesParticipants(currentParticipants)) {
            return false;
        }
        countdownActive = false;
        startTriggered = true;
        return true;
    }

    public synchronized boolean matchesParticipants(List<Participant> currentParticipants) {
        return participants.equals(currentParticipants);
    }

    public synchronized boolean markGameStarted(int gameSessionId, Instant now, Duration duration,
                                                long expectedGeneration) {
        if (generation != expectedGeneration || !startTriggered || resultRecorded || this.gameSessionId != null) {
            return false;
        }
        this.gameSessionId = gameSessionId;
        gameEndsAt = now.plus(duration);
        return true;
    }

    public synchronized boolean ownsGeneration(long expectedGeneration) {
        return generation == expectedGeneration;
    }

    public synchronized boolean ownsGameSession(int expectedGameSessionId) {
        return gameSessionId != null && gameSessionId == expectedGameSessionId;
    }

    public synchronized boolean hasGameSession() {
        return gameSessionId != null;
    }

    public synchronized boolean isTerminal() {
        return resultRecorded;
    }

    public synchronized long millisUntilExpiry(int expectedGameSessionId, Instant now) {
        if (resultRecorded || timerExpired || !ownsGameSession(expectedGameSessionId) || gameEndsAt == null) {
            return -1;
        }
        return Math.max(0, Duration.between(now, gameEndsAt).toMillis());
    }

    public synchronized PointAction beforePoint(int expectedGameSessionId, Instant now) {
        if (!startTriggered || resultRecorded || timerExpired || gameEndsAt == null
                || !ownsGameSession(expectedGameSessionId)) {
            return PointAction.REJECT;
        }
        if (!now.isBefore(gameEndsAt)) {
            timerExpired = true;
            return PointAction.EXPIRE;
        }
        return PointAction.APPLY;
    }

    public synchronized boolean canSendContinuation(int expectedGameSessionId, long expectedGeneration,
                                                    Instant now) {
        return generation == expectedGeneration && startTriggered && !resultRecorded && !timerExpired
                && gameEndsAt != null && now.isBefore(gameEndsAt)
                && ownsGameSession(expectedGameSessionId);
    }

    public synchronized boolean tryRecordResult(int expectedGameSessionId) {
        if (!startTriggered || resultRecorded || timerExpired || !ownsGameSession(expectedGameSessionId)) {
            return false;
        }
        resultRecorded = true;
        return true;
    }

    public synchronized boolean tryRecordExpiredResult(int expectedGameSessionId) {
        if (!startTriggered || resultRecorded || !timerExpired || !ownsGameSession(expectedGameSessionId)) {
            return false;
        }
        resultRecorded = true;
        return true;
    }

    public synchronized boolean tryAbort(int expectedGameSessionId) {
        if (!startTriggered || resultRecorded || timerExpired
                || gameSessionId != null && gameSessionId != expectedGameSessionId) {
            return false;
        }
        gameSessionId = expectedGameSessionId;
        resultRecorded = true;
        return true;
    }

    public synchronized boolean tryExpire(int gameSessionId, Instant now) {
        if (!startTriggered || resultRecorded || timerExpired || this.gameSessionId == null
                || this.gameSessionId != gameSessionId || gameEndsAt == null || now.isBefore(gameEndsAt)) {
            return false;
        }
        timerExpired = true;
        return true;
    }

    public record Participant(long playerId, short position, Long guildId) {
    }

    public enum PointAction {
        APPLY,
        EXPIRE,
        REJECT
    }
}
