package com.jftse.server.core.service.impl;

import com.jftse.entities.database.model.player.PlayerStatistic;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.entities.database.model.pocket.Pocket;
import com.jftse.entities.database.repository.player.PlayerStatisticRepository;
import com.jftse.entities.database.repository.pocket.PlayerPocketRepository;
import com.jftse.entities.database.repository.pocket.PocketRepository;
import com.jftse.server.core.item.EItemCategory;
import com.jftse.server.core.item.EItemUseType;
import com.jftse.server.core.service.ContractWithGuardianService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContractWithGuardianServiceImpl implements ContractWithGuardianService {
    private static final int CONTRACT_WITH_GUARDIAN_INDEX = 7;

    private final PlayerPocketRepository playerPocketRepository;
    private final PocketRepository pocketRepository;
    private final PlayerStatisticRepository playerStatisticRepository;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UseResult use(Long playerStatisticId, Long pocketId, Long playerPocketId) {
        PlayerPocket item = playerPocketRepository.findLockedById(playerPocketId).orElse(null);
        if (item == null)
            return result(UseStatus.ITEM_NOT_FOUND, null);

        if (!isContractWithGuardian(item))
            return result(UseStatus.INVALID_ITEM, item);

        if (item.getPocket() == null || !pocketId.equals(item.getPocket().getId()))
            return result(UseStatus.NOT_OWNED, item);

        PlayerStatistic statistic = playerStatisticRepository.findLockedById(playerStatisticId).orElse(null);
        if (statistic == null)
            return result(UseStatus.STATISTIC_NOT_FOUND, item);

        boolean itemRemoved = item.getItemCount() == 1;
        Pocket pocket = null;
        if (itemRemoved) {
            pocket = pocketRepository.findLockedById(pocketId).orElse(null);
            if (pocket == null)
                return result(UseStatus.POCKET_NOT_FOUND, item);
        }

        statistic.setBasicRecordWin(0);
        statistic.setBasicRecordLoss(0);
        statistic.setBattleRecordWin(0);
        statistic.setBattleRecordLoss(0);
        statistic.setGuardianRecordWin(0);
        statistic.setGuardianRecordLoss(0);
        statistic.setTotalGames(0);
        playerStatisticRepository.save(statistic);

        if (itemRemoved) {
            playerPocketRepository.delete(item);
            pocket.setBelongings(pocket.getBelongings() - 1);
            pocketRepository.save(pocket);
        } else {
            item.setItemCount(item.getItemCount() - 1);
            playerPocketRepository.save(item);
        }

        return new UseResult(UseStatus.SUCCESS, item, itemRemoved, statistic);
    }

    private boolean isContractWithGuardian(PlayerPocket item) {
        return item.getItemIndex() != null
                && item.getItemIndex() == CONTRACT_WITH_GUARDIAN_INDEX
                && EItemCategory.SPECIAL.getName().equals(item.getCategory())
                && EItemUseType.INSTANT.getName().equals(item.getUseType())
                && item.getItemCount() != null
                && item.getItemCount() > 0;
    }

    private UseResult result(UseStatus status, PlayerPocket item) {
        return new UseResult(status, item, false, null);
    }
}
