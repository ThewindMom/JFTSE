package com.jftse.server.core.service.impl;

import com.jftse.entities.database.model.guild.Guild;
import com.jftse.entities.database.model.guild.GuildMember;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.entities.database.model.pocket.Pocket;
import com.jftse.entities.database.repository.guild.GuildMemberRepository;
import com.jftse.entities.database.repository.guild.GuildRepository;
import com.jftse.entities.database.repository.pocket.PlayerPocketRepository;
import com.jftse.entities.database.repository.pocket.PocketRepository;
import com.jftse.server.core.item.EItemCategory;
import com.jftse.server.core.item.EItemUseType;
import com.jftse.server.core.service.ClubMemberLicenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClubMemberLicenseServiceImpl implements ClubMemberLicenseService {
    private static final int CLUB_MEMBER_LICENSE_INDEX = 18;
    private static final int CLUB_MASTER_RANK = 3;
    private static final int REQUIRED_GUILD_LEVEL = 10;
    private static final int CAPACITY_INCREASE = 10;
    private static final int MAX_MEMBER_CAPACITY = 80;

    private final PlayerPocketRepository playerPocketRepository;
    private final PocketRepository pocketRepository;
    private final GuildMemberRepository guildMemberRepository;
    private final GuildRepository guildRepository;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UseResult use(Long playerId, Long pocketId, Long playerPocketId) {
        PlayerPocket item = playerPocketRepository.findLockedById(playerPocketId).orElse(null);
        if (item == null)
            return result(UseStatus.ITEM_NOT_FOUND, null);

        if (!isClubMemberLicense(item))
            return result(UseStatus.INVALID_ITEM, item);

        if (item.getPocket() == null || !pocketId.equals(item.getPocket().getId()))
            return result(UseStatus.NOT_OWNED, item);

        GuildMember membership = guildMemberRepository.findLockedByPlayerId(playerId).orElse(null);
        if (membership == null || !Boolean.FALSE.equals(membership.getWaitingForApproval()))
            return result(UseStatus.NOT_CLUB_MEMBER, item);

        if (membership.getMemberRank() == null || membership.getMemberRank() != CLUB_MASTER_RANK)
            return result(UseStatus.NOT_CLUB_MASTER, item);

        Guild membershipGuild = membership.getGuild();
        Guild guild = membershipGuild == null
                ? null
                : guildRepository.findLockedById(membershipGuild.getId()).orElse(null);
        if (guild == null)
            return result(UseStatus.NOT_CLUB_MEMBER, item);

        if (guild.getLevel() == null || guild.getLevel() < REQUIRED_GUILD_LEVEL)
            return result(UseStatus.GUILD_LEVEL_TOO_LOW, item, guild.getMaxMemberCount());

        Byte currentCapacity = guild.getMaxMemberCount();
        if (currentCapacity == null || currentCapacity + CAPACITY_INCREASE > MAX_MEMBER_CAPACITY)
            return result(UseStatus.CAPACITY_LIMIT_REACHED, item, currentCapacity);

        byte newCapacity = (byte) (currentCapacity + CAPACITY_INCREASE);
        guild.setMaxMemberCount(newCapacity);
        guildRepository.save(guild);

        boolean itemRemoved = item.getItemCount() == 1;
        if (itemRemoved) {
            Pocket pocket = pocketRepository.findLockedById(pocketId).orElseThrow();
            playerPocketRepository.delete(item);
            pocket.setBelongings(pocket.getBelongings() - 1);
            pocketRepository.save(pocket);
        } else {
            item.setItemCount(item.getItemCount() - 1);
            playerPocketRepository.save(item);
        }

        return new UseResult(UseStatus.SUCCESS, item, itemRemoved, newCapacity);
    }

    private boolean isClubMemberLicense(PlayerPocket item) {
        return item.getItemIndex() != null
                && item.getItemIndex() == CLUB_MEMBER_LICENSE_INDEX
                && EItemCategory.SPECIAL.getName().equals(item.getCategory())
                && EItemUseType.INSTANT.getName().equals(item.getUseType())
                && item.getItemCount() != null
                && item.getItemCount() > 0;
    }

    private UseResult result(UseStatus status, PlayerPocket item) {
        return result(status, item, null);
    }

    private UseResult result(UseStatus status, PlayerPocket item, Byte maxMemberCount) {
        return new UseResult(status, item, false, maxMemberCount == null ? 0 : maxMemberCount);
    }
}
