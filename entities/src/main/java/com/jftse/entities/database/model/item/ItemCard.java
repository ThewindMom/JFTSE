package com.jftse.entities.database.model.item;

import com.jftse.entities.database.model.AbstractIdBaseModel;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;

@Getter
@Setter
@Entity
public class ItemCard extends AbstractIdBaseModel {
    @Column(unique = true, nullable = false)
    private Integer itemIndex;

    private String name;
    private String itemType;
    private Integer abilityGrade;
    private Integer abilityPower;
}
