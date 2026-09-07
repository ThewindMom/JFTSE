package com.jftse.server.core.service.impl;

import com.jftse.entities.database.model.guild.Guild;
import com.jftse.entities.database.model.guild.GuildMember;
import com.jftse.entities.database.model.player.Player;
import com.jftse.entities.database.repository.guild.GuildMemberRepository;
import com.jftse.entities.database.repository.guild.GuildRepository;
import com.jftse.entities.database.repository.player.PlayerRepository;
import com.jftse.server.core.service.GuildCastleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GuildCastleServiceImpl implements GuildCastleService {
    private final GuildRepository guildRepository;
    private final GuildMemberRepository guildMemberRepository;
    private final PlayerRepository playerRepository;

    @Override
    @Transactional(readOnly = true)
    public Guild findById(Long guildId) {
        if (guildId == null) {
            return null;
        }
        return guildRepository.findById(guildId)
                .filter(guild -> Boolean.TRUE.equals(guild.getCastleOwner()))
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public Guild findForPlayer(Long playerId) {
        if (playerId == null) {
            return null;
        }
        GuildMember member = guildMemberRepository.findByPlayerId(playerId).orElse(null);
        if (!isApproved(member)) {
            return null;
        }
        Guild guild = member.getGuild();
        return Boolean.TRUE.equals(guild.getCastleOwner()) ? guild : null;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Guild> findAll() {
        return guildRepository.findAllByCastleOwnerTrueOrderByIdAsc();
    }

    @Override
    @Transactional
    public byte changeInformation(Long playerId, byte accessLimit, int admissionFee) {
        if (playerId == null) {
            return CHANGE_GUILD_NOT_FOUND;
        }
        if (accessLimit < ACCESS_MASTER || accessLimit > ACCESS_ALL
                || admissionFee < MIN_ADMISSION_FEE || admissionFee > MAX_ADMISSION_FEE) {
            return CHANGE_REJECTED;
        }

        Long guildId = guildMemberRepository.findGuildIdByPlayerId(playerId).orElse(null);
        if (guildId == null) {
            return CHANGE_GUILD_NOT_FOUND;
        }
        Guild guild = guildRepository.findByIdForUpdate(guildId).orElse(null);
        if (guild == null || !Boolean.TRUE.equals(guild.getCastleOwner())) {
            return CHANGE_GUILD_NOT_FOUND;
        }
        GuildMember member = guildMemberRepository.findByPlayerIdForUpdate(playerId).orElse(null);
        if (!isApproved(member) || !guild.getId().equals(member.getGuild().getId())) {
            return CHANGE_GUILD_NOT_FOUND;
        }
        if (member.getMemberRank() != 3) {
            return CHANGE_REJECTED;
        }

        guild.setCastleAccessLimit(accessLimit);
        guild.setCastleAdmissionFee(admissionFee);
        guildRepository.save(guild);
        return CHANGE_SUCCESS;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canEnter(Long playerId, Long guildId) {
        if (playerId == null || guildId == null) {
            return false;
        }
        Guild guild = findById(guildId);
        return guild != null && hasAccess(guildMemberRepository.findByPlayerId(playerId).orElse(null), guild);
    }

    @Override
    @Transactional
    public Player chargeAdmission(Long playerId, Long guildId) {
        if (playerId == null || guildId == null) {
            return null;
        }
        Guild guild = guildRepository.findByIdForUpdate(guildId).orElse(null);
        GuildMember member = guildMemberRepository.findByPlayerIdForUpdate(playerId).orElse(null);
        if (guild == null || !Boolean.TRUE.equals(guild.getCastleOwner())
                || !hasAccess(member, guild)) {
            return null;
        }

        Integer configuredFee = guild.getCastleAdmissionFee();
        if (configuredFee == null) {
            return null;
        }
        int fee = configuredFee;
        if (fee < MIN_ADMISSION_FEE || fee > MAX_ADMISSION_FEE) {
            return null;
        }

        Player player = playerRepository.findByIdForUpdate(playerId).orElse(null);
        if (player == null || player.getGold() == null || player.getGold() < fee) {
            return null;
        }
        if (fee == 0) {
            return player;
        }

        int guildGold = guild.getGold() == null ? 0 : guild.getGold();
        if (guildGold > Integer.MAX_VALUE - fee) {
            return null;
        }

        player.setGold(player.getGold() - fee);
        guild.setGold(guildGold + fee);
        playerRepository.save(player);
        guildRepository.save(guild);
        return player;
    }

    private boolean hasAccess(GuildMember member, Guild guild) {
        Byte accessLimit = guild.getCastleAccessLimit();
        if (accessLimit == null) {
            return false;
        }
        if (accessLimit == ACCESS_ALL) {
            return true;
        }
        if (!isApproved(member) || !member.getGuild().getId().equals(guild.getId())) {
            return false;
        }
        return switch (accessLimit) {
            case ACCESS_MASTER -> member.getMemberRank() == 3;
            case ACCESS_SUBMASTER -> member.getMemberRank() >= 2;
            case ACCESS_MEMBER -> member.getMemberRank() >= 1;
            default -> false;
        };
    }

    private boolean isApproved(GuildMember member) {
        return member != null && !Boolean.TRUE.equals(member.getWaitingForApproval());
    }
}
