package com.jftse.emulator.server.core.emblem;

import com.jftse.entities.database.model.player.PlayerStatistic;
import com.jftse.entities.database.repository.player.PlayerStatisticRepository;
import com.jftse.server.core.service.impl.PlayerStatisticServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerStatisticServiceTest {
    private final PlayerStatisticRepository repository = mock(PlayerStatisticRepository.class);
    private final PlayerStatisticServiceImpl service = new PlayerStatisticServiceImpl(repository);

    @Test
    void persistsFishAndFruitCollectionTotals() {
        PlayerStatistic statistic = new PlayerStatistic();
        statistic.setFishesCaught(4);
        statistic.setFruitsCollected(7);
        when(repository.findByIdForUpdate(12L)).thenReturn(Optional.of(statistic));
        when(repository.save(statistic)).thenReturn(statistic);

        PlayerStatistic updated = service.incrementHousingCollections(12L, 2, 3);

        assertEquals(6, updated.getFishesCaught());
        assertEquals(10, updated.getFruitsCollected());
        verify(repository).save(statistic);
    }

    @Test
    void leavesStateUnchangedWhenStatisticDoesNotExist() {
        when(repository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertNull(service.incrementHousingCollections(99L, 1, 1));

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
