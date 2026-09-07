package com.jftse.entities.database.model.emblem;

import com.jftse.entities.database.model.AbstractIdBaseModel;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;

@Getter
@Setter
@Entity
public class EmblemQuestDefinition extends AbstractIdBaseModel {
    @Column(unique = true, nullable = false)
    private Integer questIndex;

    private Boolean enabled;
    private Integer event;
    private String name;
    private String icon;
    private Integer emblemGrade;
    private String questNameLabel;
    private String successConditionLabel;
    private String gameMode;
    private Integer levelRestriction;
    private String prerequisites;
    private Boolean questRepeat;
    private Boolean itemRewardRepeat;
    private Integer rewardExp;
    private Integer rewardGold;

    private String conditionType1;
    private String conditionType2;
    private String conditionType3;
    private String conditionType4;
    private String conditionTarget1;
    private String conditionTarget2;
    private String conditionTarget3;
    private String conditionTarget4;

    private Integer requiredItem1;
    private Integer requiredItem2;
    private Integer requiredItem3;
    private Integer requiredItem4;
    private Integer requiredQuantity1;
    private Integer requiredQuantity2;
    private Integer requiredQuantity3;
    private Integer requiredQuantity4;
}
