package com.jftse.entities.database.repository.emblem;

import com.jftse.entities.database.model.emblem.EmblemQuestDefinition;
import com.jftse.entities.database.model.emblem.EmblemQuestReward;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmblemQuestRewardRepository extends JpaRepository<EmblemQuestReward, Long> {
    List<EmblemQuestReward> findAllByDefinitionAndPlayerTypeOrderByRewardSlot(
            EmblemQuestDefinition definition,
            Byte playerType
    );

    void deleteAllByDefinition(EmblemQuestDefinition definition);
}
