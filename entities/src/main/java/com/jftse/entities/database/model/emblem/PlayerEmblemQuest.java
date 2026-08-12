package com.jftse.entities.database.model.emblem;

import com.jftse.entities.database.model.AbstractBaseModel;
import com.jftse.entities.database.model.player.Player;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

@Getter
@Setter
@Audited
@Entity
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_player_emblem_quest",
        columnNames = {"player_id", "definition_id"}
))
public class PlayerEmblemQuest extends AbstractBaseModel {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Player player;

    @NotAudited
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private EmblemQuestDefinition definition;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PlayerEmblemQuestStatus status = PlayerEmblemQuestStatus.ACTIVE;

    private Integer completionCount = 0;
    private Integer progress1 = 0;
    private Integer progress2 = 0;
    private Integer progress3 = 0;
    private Integer progress4 = 0;
    private Integer baseline1 = 0;
    private Integer baseline2 = 0;
    private Integer baseline3 = 0;
    private Integer baseline4 = 0;
}
