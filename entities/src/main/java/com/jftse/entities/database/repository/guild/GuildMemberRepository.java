package com.jftse.entities.database.repository.guild;

import com.jftse.entities.database.model.guild.GuildMember;
import com.jftse.entities.database.model.player.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import javax.persistence.LockModeType;
import java.util.Optional;

public interface GuildMemberRepository extends JpaRepository<GuildMember, Long> {
    Optional<GuildMember> findByPlayer(Player player);

    @Query(value = "SELECT gm FROM GuildMember gm JOIN FETCH gm.guild g WHERE gm.player.id = :playerId")
    Optional<GuildMember> findByPlayerId(Long playerId);

    @Query("SELECT gm.guild.id FROM GuildMember gm WHERE gm.player.id = :playerId")
    Optional<Long> findGuildIdByPlayerId(Long playerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT gm FROM GuildMember gm JOIN FETCH gm.guild g WHERE gm.player.id = :playerId")
    Optional<GuildMember> findByPlayerIdForUpdate(Long playerId);

    Optional<GuildMember> findByPlayerIdAndWaitingForApprovalTrue(Long playerId);
}
