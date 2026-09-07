package com.jftse.entities.database.model.tournament;

import com.jftse.entities.database.model.AbstractBaseModel;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

@Getter
@Setter
@Entity
@Table(name = "TournamentSettlement", uniqueConstraints = {
        @UniqueConstraint(name = "uk_tournament_settlement_prize", columnNames = {
                "tournamentId", "playerId", "placeNumber", "productIndex"
        })
})
public class TournamentSettlement extends AbstractBaseModel {
    @Column(nullable = false)
    private Long tournamentId;
    @Column(nullable = false)
    private Long playerId;
    @Column(nullable = false)
    private Integer placeNumber;
    @Column(nullable = false)
    private Integer productIndex;
    @Column(nullable = false)
    private Integer quantity;
}
