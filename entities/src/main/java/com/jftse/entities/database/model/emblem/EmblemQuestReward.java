package com.jftse.entities.database.model.emblem;

import com.jftse.entities.database.model.AbstractIdBaseModel;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

@Getter
@Setter
@Entity
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_emblem_quest_reward",
        columnNames = {"definition_id", "playerType", "rewardSlot"}
))
public class EmblemQuestReward extends AbstractIdBaseModel {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private EmblemQuestDefinition definition;

    private Byte playerType;
    private Byte rewardSlot;
    private Integer productIndex;
    private Integer quantityMin;
    private Integer quantityMax;
}
