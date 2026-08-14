package com.jftse.emulator.server.core.life.room;

import com.jftse.emulator.server.core.client.PetView;

/**
 * One court seat. Matchplay, serve, skills, HP, and results talk only to seats.
 * Ordinary Basic/Battle/Guardian is the 1:1 case: the owner's human seat.
 * Battlemon and Guardian+pets add an owned pet seat at ownerPosition + 2.
 */
public record GameplayActor(
        short position,
        short ownerPosition,
        long ownerPlayerId,
        PetView pet,
        int basicWins,
        int basicLosses,
        int battleWins,
        int battleLosses,
        int consecutiveWins) {

    public boolean isHuman() {
        return pet == null;
    }

    public boolean receivesHumanRewards() {
        return isHuman();
    }
}
