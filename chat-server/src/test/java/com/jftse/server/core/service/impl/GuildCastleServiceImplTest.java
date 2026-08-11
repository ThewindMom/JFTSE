package com.jftse.server.core.service.impl;

import com.jftse.entities.database.model.guild.Guild;
import com.jftse.entities.database.model.guild.GuildMember;
import com.jftse.entities.database.model.player.Player;
import com.jftse.entities.database.repository.guild.GuildMemberRepository;
import com.jftse.entities.database.repository.guild.GuildRepository;
import com.jftse.entities.database.repository.player.PlayerRepository;
import com.jftse.server.core.service.GuildCastleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GuildCastleServiceImplTest {
    private static final long GUILD_ID = 11L;
    private static final long PLAYER_ID = 22L;

    private GuildRepository guildRepository;
    private GuildMemberRepository memberRepository;
    private PlayerRepository playerRepository;
    private GuildCastleServiceImpl service;
    private Guild guild;

    @BeforeEach
    void setUp() {
        guildRepository = mock(GuildRepository.class);
        memberRepository = mock(GuildMemberRepository.class);
        playerRepository = mock(PlayerRepository.class);
        service = new GuildCastleServiceImpl(guildRepository, memberRepository, playerRepository);

        guild = new Guild();
        guild.setId(GUILD_ID);
        guild.setCastleOwner(true);
        guild.setCastleAccessLimit(GuildCastleService.ACCESS_MEMBER);
        guild.setCastleAdmissionFee(0);
        guild.setGold(0);
    }

    @Test
    void onlyCastleMasterCanChangeSettingsWithinClientBounds() {
        GuildMember master = member((byte) 3, guild, false);
        when(memberRepository.findGuildIdByPlayerId(PLAYER_ID)).thenReturn(Optional.of(GUILD_ID));
        when(guildRepository.findByIdForUpdate(GUILD_ID)).thenReturn(Optional.of(guild));
        when(memberRepository.findByPlayerIdForUpdate(PLAYER_ID)).thenReturn(Optional.of(master));

        assertEquals(GuildCastleService.CHANGE_SUCCESS,
                service.changeInformation(PLAYER_ID, GuildCastleService.ACCESS_ALL, 1000));
        assertEquals(GuildCastleService.ACCESS_ALL, guild.getCastleAccessLimit());
        assertEquals(1000, guild.getCastleAdmissionFee());
        verify(guildRepository).save(guild);
    }

    @Test
    void rejectsNonMasterNonCastleAndOutOfRangeSettings() {
        GuildMember member = member((byte) 2, guild, false);
        when(memberRepository.findGuildIdByPlayerId(PLAYER_ID)).thenReturn(Optional.of(GUILD_ID));
        when(guildRepository.findByIdForUpdate(GUILD_ID)).thenReturn(Optional.of(guild));
        when(memberRepository.findByPlayerIdForUpdate(PLAYER_ID)).thenReturn(Optional.of(member));

        assertEquals(GuildCastleService.CHANGE_REJECTED,
                service.changeInformation(PLAYER_ID, GuildCastleService.ACCESS_ALL, 100));

        member.setMemberRank((byte) 3);
        guild.setCastleOwner(false);
        assertEquals(GuildCastleService.CHANGE_GUILD_NOT_FOUND,
                service.changeInformation(PLAYER_ID, GuildCastleService.ACCESS_ALL, 100));

        guild.setCastleOwner(true);
        assertEquals(GuildCastleService.CHANGE_REJECTED,
                service.changeInformation(PLAYER_ID, (byte) -1, 0));
        assertEquals(GuildCastleService.CHANGE_REJECTED,
                service.changeInformation(PLAYER_ID, (byte) 4, 0));
        assertEquals(GuildCastleService.CHANGE_REJECTED,
                service.changeInformation(PLAYER_ID, GuildCastleService.ACCESS_ALL, -1));
        assertEquals(GuildCastleService.CHANGE_REJECTED,
                service.changeInformation(PLAYER_ID, GuildCastleService.ACCESS_ALL, 1001));
        verify(guildRepository, never()).save(any());
    }

    @Test
    void accessModesFollowMasterSubmasterMemberAllOrdering() {
        when(guildRepository.findById(GUILD_ID)).thenReturn(Optional.of(guild));

        for (byte rank = 1; rank <= 3; rank++) {
            GuildMember member = member(rank, guild, false);
            when(memberRepository.findByPlayerId(PLAYER_ID)).thenReturn(Optional.of(member));

            guild.setCastleAccessLimit(GuildCastleService.ACCESS_MASTER);
            assertEquals(rank == 3, service.canEnter(PLAYER_ID, GUILD_ID));
            guild.setCastleAccessLimit(GuildCastleService.ACCESS_SUBMASTER);
            assertEquals(rank >= 2, service.canEnter(PLAYER_ID, GUILD_ID));
            guild.setCastleAccessLimit(GuildCastleService.ACCESS_MEMBER);
            assertTrue(service.canEnter(PLAYER_ID, GUILD_ID));
        }

        when(memberRepository.findByPlayerId(PLAYER_ID)).thenReturn(Optional.empty());
        guild.setCastleAccessLimit(GuildCastleService.ACCESS_MEMBER);
        assertFalse(service.canEnter(PLAYER_ID, GUILD_ID));
        guild.setCastleAccessLimit(GuildCastleService.ACCESS_ALL);
        assertTrue(service.canEnter(PLAYER_ID, GUILD_ID));
    }

    @Test
    void pendingApplicantIsNotAClubMemberForCastleAccess() {
        when(guildRepository.findById(GUILD_ID)).thenReturn(Optional.of(guild));
        when(memberRepository.findByPlayerId(PLAYER_ID))
                .thenReturn(Optional.of(member((byte) 3, guild, true)));

        assertFalse(service.canEnter(PLAYER_ID, GUILD_ID));
    }

    @Test
    void successfulAdmissionTransfersFee() {
        Player player = player(100);
        guild.setCastleAccessLimit(GuildCastleService.ACCESS_ALL);
        guild.setCastleAdmissionFee(60);
        when(guildRepository.findByIdForUpdate(GUILD_ID)).thenReturn(Optional.of(guild));
        when(memberRepository.findByPlayerIdForUpdate(PLAYER_ID)).thenReturn(Optional.empty());
        when(playerRepository.findByIdForUpdate(PLAYER_ID)).thenReturn(Optional.of(player));

        assertSame(player, service.chargeAdmission(PLAYER_ID, GUILD_ID));
        assertEquals(40, player.getGold());
        assertEquals(60, guild.getGold());
        verify(playerRepository, times(1)).save(player);
        verify(guildRepository, times(1)).save(guild);
    }

    @Test
    void insufficientFundsNeverMutatePlayerOrGuild() {
        Player player = player(59);
        guild.setCastleAccessLimit(GuildCastleService.ACCESS_ALL);
        guild.setCastleAdmissionFee(60);
        guild.setGold(10);
        when(guildRepository.findByIdForUpdate(GUILD_ID)).thenReturn(Optional.of(guild));
        when(memberRepository.findByPlayerIdForUpdate(PLAYER_ID)).thenReturn(Optional.empty());
        when(playerRepository.findByIdForUpdate(PLAYER_ID)).thenReturn(Optional.of(player));

        assertNull(service.chargeAdmission(PLAYER_ID, GUILD_ID));
        assertEquals(59, player.getGold());
        assertEquals(10, guild.getGold());
        verify(playerRepository, never()).save(any());
        verify(guildRepository, never()).save(any());
    }

    @Test
    void zeroFeeAdmissionChecksAccessWithoutWritingBalances() {
        Player player = player(59);
        guild.setCastleAccessLimit(GuildCastleService.ACCESS_ALL);
        guild.setCastleAdmissionFee(0);
        when(guildRepository.findByIdForUpdate(GUILD_ID)).thenReturn(Optional.of(guild));
        when(memberRepository.findByPlayerIdForUpdate(PLAYER_ID)).thenReturn(Optional.empty());
        when(playerRepository.findByIdForUpdate(PLAYER_ID)).thenReturn(Optional.of(player));

        assertSame(player, service.chargeAdmission(PLAYER_ID, GUILD_ID));
        assertEquals(59, player.getGold());
        assertEquals(0, guild.getGold());
        verify(playerRepository, never()).save(any());
        verify(guildRepository, never()).save(any());
    }

    private GuildMember member(byte rank, Guild memberGuild, boolean waiting) {
        GuildMember member = new GuildMember();
        member.setGuild(memberGuild);
        member.setMemberRank(rank);
        member.setWaitingForApproval(waiting);
        return member;
    }

    private Player player(int gold) {
        Player player = new Player();
        player.setId(PLAYER_ID);
        player.setGold(gold);
        return player;
    }
}
