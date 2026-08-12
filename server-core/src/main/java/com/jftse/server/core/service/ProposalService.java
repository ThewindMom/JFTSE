package com.jftse.server.core.service;

import com.jftse.entities.database.model.messenger.Proposal;
import com.jftse.entities.database.model.player.Player;
import com.jftse.entities.database.model.pocket.PlayerPocket;

import java.util.List;

public interface ProposalService {
    enum ProposalCreationStatus {
        SUCCESS,
        INVALID_ITEM,
        NOT_OWNED
    }

    record ProposalCreationResult(ProposalCreationStatus status, Proposal proposal, PlayerPocket item,
                                  boolean itemRemoved) {
    }

    Proposal save(Proposal proposal);

    ProposalCreationResult createWithItem(Player sender, Player receiver, String message, Long playerPocketId);

    void remove(Long proposalId);

    Proposal findById(Long id);

    List<Proposal> findBySender(Player sender);

    List<Proposal> findWithPlayerBySender(Long playerId);

    List<Proposal> findByReceiver(Player receiver);

    List<Proposal> findWithPlayerByReceiver(Long playerId);

    long deleteBySender(Player sender);

    long deleteByReceiver(Player receiver);
}
