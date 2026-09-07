package com.jftse.server.core.service.impl;

import com.jftse.entities.database.model.messenger.Proposal;
import com.jftse.entities.database.model.player.Player;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.entities.database.model.pocket.Pocket;
import com.jftse.entities.database.repository.messenger.ProposalRepository;
import com.jftse.entities.database.repository.pocket.PlayerPocketRepository;
import com.jftse.entities.database.repository.pocket.PocketRepository;
import com.jftse.server.core.item.EItemCategory;
import com.jftse.server.core.service.ProposalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProposalServiceImpl implements ProposalService {
    private final ProposalRepository proposalRepository;
    private final PlayerPocketRepository playerPocketRepository;
    private final PocketRepository pocketRepository;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Proposal save(Proposal proposal) {
        return proposalRepository.save(proposal);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ProposalCreationResult createWithItem(Player sender, Player receiver, String message, Long playerPocketId) {
        PlayerPocket item = playerPocketRepository.findLockedById(playerPocketId).orElse(null);
        Integer itemIndex = item == null ? null : item.getItemIndex();
        if (item == null
                || !EItemCategory.SPECIAL.getName().equals(item.getCategory())
                || item.getItemCount() == null
                || item.getItemCount() < 1
                || itemIndex == null
                || itemIndex < 23
                || itemIndex > 25) {
            return new ProposalCreationResult(ProposalCreationStatus.INVALID_ITEM, null, item, false);
        }

        if (!item.getPocket().getId().equals(sender.getPocket().getId()))
            return new ProposalCreationResult(ProposalCreationStatus.NOT_OWNED, null, item, false);

        boolean itemRemoved = item.getItemCount() == 1;
        if (itemRemoved) {
            playerPocketRepository.delete(item);
            long pocketId = item.getPocket().getId();
            Pocket pocket = pocketRepository.findLockedById(pocketId).orElseThrow();
            pocket.setBelongings(pocket.getBelongings() - 1);
            pocketRepository.save(pocket);
        } else {
            item.setItemCount(item.getItemCount() - 1);
            playerPocketRepository.save(item);
        }

        Proposal proposal = new Proposal();
        proposal.setReceiver(receiver);
        proposal.setSender(sender);
        proposal.setMessage(message);
        proposal.setSeen(false);
        proposal.setCategory(item.getCategory());
        proposal.setItemIndex(item.getItemIndex());
        proposal = proposalRepository.save(proposal);

        return new ProposalCreationResult(ProposalCreationStatus.SUCCESS, proposal, item, itemRemoved);
    }

    @Override
    @Transactional
    public void remove(Long proposalId) {
        proposalRepository.deleteById(proposalId);
    }

    @Override
    @Transactional(readOnly = true)
    public Proposal findById(Long id) {
        return proposalRepository.findById(id).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Proposal> findBySender(Player sender) {
        return proposalRepository.findBySender(sender);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Proposal> findWithPlayerBySender(Long playerId) {
        return proposalRepository.findWithPlayerBySender(playerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Proposal> findByReceiver(Player receiver) {
        return proposalRepository.findByReceiver(receiver);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Proposal> findWithPlayerByReceiver(Long playerId) {
        return proposalRepository.findWithPlayerByReceiver(playerId);
    }

    @Override
    @Transactional
    public long deleteBySender(Player sender) {
        return proposalRepository.deleteBySender(sender);
    }

    @Override
    @Transactional
    public long deleteByReceiver(Player receiver) {
        return proposalRepository.deleteByReceiver(receiver);
    }
}
