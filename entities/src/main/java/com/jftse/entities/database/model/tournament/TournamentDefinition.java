package com.jftse.entities.database.model.tournament;

import com.jftse.entities.database.model.AbstractBaseModel;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import java.util.Date;

@Getter
@Setter
@Entity
@Table(name = "TournamentDefinition", uniqueConstraints = {
        @UniqueConstraint(name = "uk_tournament_title", columnNames = "title")
})
public class TournamentDefinition extends AbstractBaseModel {
    @Column(nullable = false, length = 100)
    private String title;
    @Column(nullable = false)
    private Byte entryType;
    @Column(nullable = false)
    private Byte gameMode;
    @Column(nullable = false)
    private Byte status;
    @Column(nullable = false)
    private Integer capacity;
    @Column(nullable = false)
    private Integer finalSize;
    @Column(nullable = false)
    private Integer rewardProductIndex;
    @Column(nullable = false)
    private Integer rewardQuantity;
    @Column(nullable = false)
    private Date applicationStart;
    @Column(nullable = false)
    private Date applicationEnd;
    @Column(nullable = false)
    private Date qualifyingStart;
    @Column(nullable = false)
    private Date finalStart;
    @Column(nullable = false)
    private Date tournamentEnd;
}
