package com.jftse.entities.database.model.tournament;

import com.jftse.entities.database.model.AbstractBaseModel;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import java.util.Date;

@Getter
@Setter
@Entity
@Table(name = "TournamentEnrollment", indexes = {
        @Index(name = "idx_tournament_enrollment_seed", columnList = "tournamentId, seed")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_tournament_enrollment_player", columnNames = {"tournamentId", "playerId"}),
        @UniqueConstraint(name = "uk_tournament_enrollment_seed", columnNames = {"tournamentId", "seed"})
})
public class TournamentEnrollment extends AbstractBaseModel {
    @Column(nullable = false)
    private Long tournamentId;
    @Column(nullable = false)
    private Long playerId;
    @Column(nullable = false, length = 30)
    private String playerName;
    @Column(nullable = false)
    private Integer seed;
    @Column(nullable = false)
    private Byte state;
    private Date qualifiedAt;
    private Date eliminatedAt;
}
