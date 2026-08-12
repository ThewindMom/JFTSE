package com.jftse.entities.database.repository.emblem;

import com.jftse.entities.database.model.emblem.EmblemQuestDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmblemQuestDefinitionRepository extends JpaRepository<EmblemQuestDefinition, Long> {
    Optional<EmblemQuestDefinition> findByQuestIndex(Integer questIndex);
    List<EmblemQuestDefinition> findAllByEnabledTrueAndQuestIndexBetweenOrderByQuestIndex(Integer minimum, Integer maximum);
}
