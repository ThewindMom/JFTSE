package com.jftse.entities.database.model.emblem;

import com.jftse.entities.database.model.AbstractBaseModel;
import com.jftse.entities.database.model.player.Player;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;

@Getter
@Setter
@Audited
@Entity
public class PlayerEmblemEquipment extends AbstractBaseModel {
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", referencedColumnName = "id", unique = true)
    private Player player;

    private Short slot1 = 0;
    private Short slot2 = 0;
    private Short slot3 = 0;
    private Short slot4 = 0;
}
