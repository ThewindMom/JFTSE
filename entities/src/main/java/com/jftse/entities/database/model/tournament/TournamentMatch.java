package com.jftse.entities.database.model.tournament;

import com.jftse.entities.database.model.AbstractBaseModel;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.persistence.Version;
import java.util.Date;

@Getter
@Setter
@Entity
@Table(name = "TournamentMatch", indexes = {
        @Index(name = "idx_tournament_match_player_one", columnList = "tournamentId, playerOneId, status"),
        @Index(name = "idx_tournament_match_player_two", columnList = "tournamentId, playerTwoId, status")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_tournament_match_slot", columnNames = {"tournamentId", "stage", "roundNumber", "slotNumber"}),
        @UniqueConstraint(name = "uk_tournament_match_room", columnNames = "roomId"),
        @UniqueConstraint(name = "uk_tournament_match_session", columnNames = "gameSessionId")
})
public class TournamentMatch extends AbstractBaseModel {
    @Column(nullable = false)
    private Long tournamentId;
    @Column(nullable = false)
    private Byte stage;
    @Column(nullable = false)
    private Integer roundNumber;
    @Column(nullable = false)
    private Integer slotNumber;
    private Long playerOneId;
    private Long playerTwoId;
    private Long winnerPlayerId;
    @Column(nullable = false)
    private Byte status;
    private Short roomId;
    private Integer gameSessionId;
    private Date startedAt;
    private Date completedAt;
    @Version
    private Long version;
}
