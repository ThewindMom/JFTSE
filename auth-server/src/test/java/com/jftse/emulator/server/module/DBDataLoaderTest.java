package com.jftse.emulator.server.module;

import com.jftse.entities.database.model.emblem.EmblemQuestDefinition;
import com.jftse.entities.database.repository.ImportLogRepository;
import com.jftse.entities.database.repository.emblem.EmblemQuestDefinitionRepository;
import com.jftse.entities.database.repository.emblem.EmblemQuestRewardRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DBDataLoaderTest {
    @Mock
    private ImportLogRepository importLogRepository;
    @Mock
    private EmblemQuestDefinitionRepository definitionRepository;
    @Mock
    private EmblemQuestRewardRepository rewardRepository;
    @InjectMocks
    private DBDataLoader loader;

    @Test
    void repeatedImportPreservesExternallyManagedQuestAndRewards() {
        EmblemQuestDefinition managedDefinition = new EmblemQuestDefinition();
        managedDefinition.setQuestIndex(1);
        managedDefinition.setName("Operator managed quest");
        managedDefinition.setEnabled(false);

        when(definitionRepository.findByQuestIndex(anyInt()))
                .thenReturn(Optional.of(managedDefinition));

        assertTrue(loader.loadEmblemQuest());
        assertEquals("Operator managed quest", managedDefinition.getName());
        assertFalse(managedDefinition.getEnabled());
        verify(definitionRepository, never()).save(any());
        verify(rewardRepository, never()).deleteAllByDefinition(any());
        verify(rewardRepository, never()).save(any());
    }
}
