package com.jftse.server.core.service.impl;

import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.entities.database.model.pocket.Pocket;
import com.jftse.entities.database.repository.pocket.PlayerPocketRepository;
import com.jftse.entities.database.repository.pocket.PocketRepository;
import com.jftse.server.core.item.EItemCategory;
import com.jftse.server.core.item.EItemUseType;
import com.jftse.server.core.service.PersonalBoardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PersonalBoardServiceImpl implements PersonalBoardService {
    private static final int PERSONAL_BOARD_INDEX = 19;

    private final PlayerPocketRepository playerPocketRepository;
    private final PocketRepository pocketRepository;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UseResult use(Long pocketId, Long playerPocketId) {
        PlayerPocket item = playerPocketRepository.findLockedById(playerPocketId).orElse(null);
        if (item == null)
            return new UseResult(UseStatus.ITEM_NOT_FOUND, null, false);

        if (!isPersonalBoard(item))
            return new UseResult(UseStatus.INVALID_ITEM, item, false);

        if (item.getPocket() == null || !pocketId.equals(item.getPocket().getId()))
            return new UseResult(UseStatus.NOT_OWNED, item, false);

        boolean itemRemoved = item.getItemCount() == 1;
        if (itemRemoved) {
            Pocket pocket = pocketRepository.findLockedById(pocketId).orElseThrow();
            playerPocketRepository.delete(item);
            pocket.setBelongings(pocket.getBelongings() - 1);
            pocketRepository.save(pocket);
        } else {
            item.setItemCount(item.getItemCount() - 1);
            playerPocketRepository.save(item);
        }

        return new UseResult(UseStatus.SUCCESS, item, itemRemoved);
    }

    private boolean isPersonalBoard(PlayerPocket item) {
        return item.getItemIndex() != null
                && item.getItemIndex() == PERSONAL_BOARD_INDEX
                && EItemCategory.SPECIAL.getName().equals(item.getCategory())
                && EItemUseType.COUNT.getName().equals(item.getUseType())
                && item.getItemCount() != null
                && item.getItemCount() > 0;
    }
}
