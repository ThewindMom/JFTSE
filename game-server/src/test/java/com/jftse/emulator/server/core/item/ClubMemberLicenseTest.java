package com.jftse.emulator.server.core.item;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.life.item.BaseItem;
import com.jftse.emulator.server.core.life.item.ItemFactory;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.entities.database.model.guild.Guild;
import com.jftse.entities.database.model.guild.GuildMember;
import com.jftse.entities.database.model.item.ItemSpecial;
import com.jftse.entities.database.model.player.Player;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.entities.database.model.pocket.Pocket;
import com.jftse.entities.database.repository.guild.GuildMemberRepository;
import com.jftse.entities.database.repository.guild.GuildRepository;
import com.jftse.entities.database.repository.pocket.PlayerPocketRepository;
import com.jftse.entities.database.repository.pocket.PocketRepository;
import com.jftse.server.core.item.EItemCategory;
import com.jftse.server.core.item.EItemUseType;
import com.jftse.server.core.protocol.IPacket;
import com.jftse.server.core.service.ClubMemberLicenseService;
import com.jftse.server.core.service.ItemSpecialService;
import com.jftse.server.core.service.PlayerPocketService;
import com.jftse.server.core.service.impl.ClubMemberLicenseServiceImpl;
import com.jftse.server.core.shared.packets.inventory.S2CInventoryItemRemoveAnswerPacket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.LockModeType;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClubMemberLicenseTest {
    @Mock private PlayerPocketService playerPocketService;
    @Mock private ItemSpecialService itemSpecialService;
    @Mock private ClubMemberLicenseService clubMemberLicenseService;
    @Mock private PlayerPocketRepository playerPocketRepository;
    @Mock private PocketRepository pocketRepository;
    @Mock private GuildMemberRepository guildMemberRepository;
    @Mock private GuildRepository guildRepository;
    @Mock private FTPlayer ftPlayer;

    private ClubMemberLicenseServiceImpl service;
    private PlayerPocket license;
    private Pocket pocket;
    private Guild guild;
    private GuildMember clubMaster;

    @BeforeEach
    void setUp() {
        ServiceManager manager = new ServiceManager();
        ReflectionTestUtils.setField(manager, "playerPocketService", playerPocketService);
        ReflectionTestUtils.setField(manager, "itemSpecialService", itemSpecialService);
        ReflectionTestUtils.setField(manager, "clubMemberLicenseService", clubMemberLicenseService);
        manager.init();

        service = new ClubMemberLicenseServiceImpl(
                playerPocketRepository,
                pocketRepository,
                guildMemberRepository,
                guildRepository
        );
        pocket = pocket(71L, 4);
        license = license(501L, pocket, 2);
        guild = guild(81L, 10, 25);
        clubMaster = member(91L, player(11L, pocket), guild, 3, false);
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(ServiceManager.class, "instance", null);
    }

    @Test
    void factoryRecognizesClubMemberLicenseFromTheNativePocketRequest() {
        BaseItem item = factoryItem();

        assertNotNull(item);
        assertEquals(18, item.getItemIndex());
    }

    @Test
    void itemAdapterSendsTheExistingInventoryRemovalPacketAfterAtomicSuccess() {
        BaseItem item = factoryItem();
        when(ftPlayer.getId()).thenReturn(11L);
        when(clubMemberLicenseService.use(11L, 71L, 501L)).thenReturn(
                new ClubMemberLicenseService.UseResult(
                        ClubMemberLicenseService.UseStatus.SUCCESS,
                        license,
                        true,
                        (byte) 35
                )
        );

        assertTrue(item.processPlayer(ftPlayer));
        assertTrue(item.processPocket(71L));

        IPacket response = item.getPacketsToSend().get(11L).getFirst();
        assertTrue(response instanceof S2CInventoryItemRemoveAnswerPacket);
        byte[] bytes = response.toBytes();
        assertArrayEquals(new byte[]{(byte) 0xF5, 0x01, 0x00, 0x00},
                Arrays.copyOfRange(bytes, bytes.length - 4, bytes.length));
    }

    @Test
    void itemAdapterDoesNotRemoveTheItemWhenTheAuthoritativeServiceRejectsUse() {
        BaseItem item = factoryItem();
        when(ftPlayer.getId()).thenReturn(11L);
        when(clubMemberLicenseService.use(11L, 71L, 501L)).thenReturn(
                new ClubMemberLicenseService.UseResult(
                        ClubMemberLicenseService.UseStatus.NOT_CLUB_MASTER,
                        license,
                        false,
                        (byte) 25
                )
        );

        assertTrue(item.processPlayer(ftPlayer));
        assertFalse(item.processPocket(71L));
        assertTrue(item.getPacketsToSend().isEmpty());
    }

    @Test
    void increasesCapacityByTenAndConsumesOneStackUnitAtomically() {
        arrangeOwnedLicense();

        ClubMemberLicenseService.UseResult result = service.use(11L, 71L, 501L);

        assertEquals(ClubMemberLicenseService.UseStatus.SUCCESS, result.status());
        assertEquals(35, result.maxMemberCount());
        assertEquals(35, guild.getMaxMemberCount().intValue());
        assertEquals(1, license.getItemCount().intValue());
        assertFalse(result.itemRemoved());
        verify(guildRepository).save(guild);
        verify(playerPocketRepository).save(license);
        verify(playerPocketRepository, never()).delete(any());
        verify(pocketRepository, never()).save(any());
    }

    @Test
    void removesTheFinalUnitAndDecrementsPocketBelongingsInTheSameTransaction() {
        license.setItemCount(1);
        arrangeOwnedLicense();
        when(pocketRepository.findLockedById(71L)).thenReturn(Optional.of(pocket));

        ClubMemberLicenseService.UseResult result = service.use(11L, 71L, 501L);

        assertEquals(ClubMemberLicenseService.UseStatus.SUCCESS, result.status());
        assertTrue(result.itemRemoved());
        assertEquals(35, guild.getMaxMemberCount().intValue());
        assertEquals(3, pocket.getBelongings().intValue());
        verify(playerPocketRepository).delete(license);
        verify(pocketRepository).save(pocket);
    }

    @Test
    void rejectsPlayersWithoutAClubMembership() {
        when(playerPocketRepository.findLockedById(501L)).thenReturn(Optional.of(license));
        when(guildMemberRepository.findLockedByPlayerId(11L)).thenReturn(Optional.empty());

        assertRejected(ClubMemberLicenseService.UseStatus.NOT_CLUB_MEMBER);
    }

    @Test
    void rejectsNonMastersWithoutMutatingGuildOrItem() {
        clubMaster.setMemberRank((byte) 2);
        arrangeOwnedLicenseAndMembership();

        assertRejected(ClubMemberLicenseService.UseStatus.NOT_CLUB_MASTER);
    }

    @Test
    void rejectsPendingMembershipEvenIfItsRankValueIsThree() {
        clubMaster.setWaitingForApproval(true);
        arrangeOwnedLicenseAndMembership();

        assertRejected(ClubMemberLicenseService.UseStatus.NOT_CLUB_MEMBER);
    }

    @Test
    void requiresGuildLevelTen() {
        guild.setLevel((byte) 9);
        arrangeOwnedLicense();

        assertRejected(ClubMemberLicenseService.UseStatus.GUILD_LEVEL_TOO_LOW);
    }

    @Test
    void rejectsTheDocumentedMaximumCapacity() {
        guild.setMaxMemberCount((byte) 80);
        arrangeOwnedLicense();

        assertRejected(ClubMemberLicenseService.UseStatus.CAPACITY_LIMIT_REACHED);
    }

    @Test
    void rejectsAnOverflowingIncreaseRatherThanClampingASeventyFiveMemberClub() {
        guild.setMaxMemberCount((byte) 75);
        arrangeOwnedLicense();

        assertRejected(ClubMemberLicenseService.UseStatus.CAPACITY_LIMIT_REACHED);
        assertEquals(75, guild.getMaxMemberCount().intValue());
    }

    @Test
    void acceptsTheHighestFullTenMemberIncrease() {
        guild.setMaxMemberCount((byte) 70);
        arrangeOwnedLicense();

        ClubMemberLicenseService.UseResult result = service.use(11L, 71L, 501L);

        assertEquals(ClubMemberLicenseService.UseStatus.SUCCESS, result.status());
        assertEquals(80, result.maxMemberCount());
        assertEquals(80, guild.getMaxMemberCount().intValue());
    }

    @Test
    void rejectsAPlayerPocketRowOwnedByAnotherPocket() {
        license.setPocket(pocket(72L, 1));
        when(playerPocketRepository.findLockedById(501L)).thenReturn(Optional.of(license));

        assertRejected(ClubMemberLicenseService.UseStatus.NOT_OWNED);
        verify(guildMemberRepository, never()).findLockedByPlayerId(any());
    }

    @Test
    void rejectsWrongIndexCategoryUseTypeAndEmptyStacks() {
        PlayerPocket wrongIndex = license(501L, pocket, 1);
        wrongIndex.setItemIndex(19);
        PlayerPocket wrongCategory = license(502L, pocket, 1);
        wrongCategory.setCategory(EItemCategory.QUICK.getName());
        PlayerPocket wrongUseType = license(503L, pocket, 1);
        wrongUseType.setUseType(EItemUseType.TIME.getName());
        PlayerPocket empty = license(504L, pocket, 0);
        when(playerPocketRepository.findLockedById(501L)).thenReturn(Optional.of(wrongIndex));
        when(playerPocketRepository.findLockedById(502L)).thenReturn(Optional.of(wrongCategory));
        when(playerPocketRepository.findLockedById(503L)).thenReturn(Optional.of(wrongUseType));
        when(playerPocketRepository.findLockedById(504L)).thenReturn(Optional.of(empty));

        assertEquals(ClubMemberLicenseService.UseStatus.INVALID_ITEM, service.use(11L, 71L, 501L).status());
        assertEquals(ClubMemberLicenseService.UseStatus.INVALID_ITEM, service.use(11L, 71L, 502L).status());
        assertEquals(ClubMemberLicenseService.UseStatus.INVALID_ITEM, service.use(11L, 71L, 503L).status());
        assertEquals(ClubMemberLicenseService.UseStatus.INVALID_ITEM, service.use(11L, 71L, 504L).status());
        verify(guildRepository, never()).save(any());
        verify(playerPocketRepository, never()).save(any());
        verify(playerPocketRepository, never()).delete(any());
    }

    @Test
    void aReplayAfterFinalConsumptionCannotIncreaseCapacityTwice() {
        license.setItemCount(1);
        when(playerPocketRepository.findLockedById(501L))
                .thenReturn(Optional.of(license), Optional.empty());
        when(guildMemberRepository.findLockedByPlayerId(11L)).thenReturn(Optional.of(clubMaster));
        when(guildRepository.findLockedById(81L)).thenReturn(Optional.of(guild));
        when(pocketRepository.findLockedById(71L)).thenReturn(Optional.of(pocket));

        ClubMemberLicenseService.UseResult first = service.use(11L, 71L, 501L);
        ClubMemberLicenseService.UseResult replay = service.use(11L, 71L, 501L);

        assertEquals(ClubMemberLicenseService.UseStatus.SUCCESS, first.status());
        assertEquals(ClubMemberLicenseService.UseStatus.ITEM_NOT_FOUND, replay.status());
        assertEquals(35, guild.getMaxMemberCount().intValue());
        verify(guildRepository).save(guild);
    }

    @Test
    void mutationUsesOneTransactionAndPessimisticItemMembershipGuildAndPocketLocks() throws Exception {
        Method serviceMethod = ClubMemberLicenseServiceImpl.class.getMethod(
                "use", Long.class, Long.class, Long.class);
        Transactional transactional = serviceMethod.getAnnotation(Transactional.class);
        Lock itemLock = PlayerPocketRepository.class.getMethod("findLockedById", Long.class)
                .getAnnotation(Lock.class);
        Lock membershipLock = GuildMemberRepository.class.getMethod("findLockedByPlayerId", Long.class)
                .getAnnotation(Lock.class);
        Lock guildLock = GuildRepository.class.getMethod("findLockedById", Long.class)
                .getAnnotation(Lock.class);
        Lock pocketLock = PocketRepository.class.getMethod("findLockedById", Long.class)
                .getAnnotation(Lock.class);

        assertEquals(Isolation.READ_COMMITTED, transactional.isolation());
        assertEquals(LockModeType.PESSIMISTIC_WRITE, itemLock.value());
        assertEquals(LockModeType.PESSIMISTIC_WRITE, membershipLock.value());
        assertEquals(LockModeType.PESSIMISTIC_WRITE, guildLock.value());
        assertEquals(LockModeType.PESSIMISTIC_WRITE, pocketLock.value());
    }

    private void arrangeOwnedLicense() {
        arrangeOwnedLicenseAndMembership();
        when(guildRepository.findLockedById(81L)).thenReturn(Optional.of(guild));
    }

    private void arrangeOwnedLicenseAndMembership() {
        when(playerPocketRepository.findLockedById(501L)).thenReturn(Optional.of(license));
        when(guildMemberRepository.findLockedByPlayerId(11L)).thenReturn(Optional.of(clubMaster));
    }

    private void assertRejected(ClubMemberLicenseService.UseStatus expected) {
        ClubMemberLicenseService.UseResult result = service.use(11L, 71L, 501L);

        assertEquals(expected, result.status());
        verify(guildRepository, never()).save(any());
        verify(playerPocketRepository, never()).save(any());
        verify(playerPocketRepository, never()).delete(any());
        verify(pocketRepository, never()).save(any());
    }

    private BaseItem factoryItem() {
        ItemSpecial catalogItem = new ItemSpecial();
        catalogItem.setItemIndex(18);
        catalogItem.setName("Club Member License");
        when(playerPocketService.getItemAsPocket(501L, pocket)).thenReturn(license);
        when(itemSpecialService.findByItemIndex(18)).thenReturn(catalogItem);
        return ItemFactory.getItem(501L, pocket);
    }

    private PlayerPocket license(long id, Pocket pocket, int count) {
        PlayerPocket item = new PlayerPocket();
        item.setId(id);
        item.setPocket(pocket);
        item.setCategory(EItemCategory.SPECIAL.getName());
        item.setItemIndex(18);
        item.setItemCount(count);
        item.setUseType(EItemUseType.INSTANT.getName());
        return item;
    }

    private Pocket pocket(long id, int belongings) {
        Pocket result = new Pocket();
        result.setId(id);
        result.setBelongings(belongings);
        return result;
    }

    private Player player(long id, Pocket pocket) {
        Player result = new Player();
        result.setId(id);
        result.setPocket(pocket);
        return result;
    }

    private Guild guild(long id, int level, int maxMemberCount) {
        Guild result = new Guild();
        result.setId(id);
        result.setLevel((byte) level);
        result.setMaxMemberCount((byte) maxMemberCount);
        return result;
    }

    private GuildMember member(long id, Player player, Guild guild, int rank, boolean waitingForApproval) {
        GuildMember result = new GuildMember();
        result.setId(id);
        result.setPlayer(player);
        result.setGuild(guild);
        result.setMemberRank((byte) rank);
        result.setWaitingForApproval(waitingForApproval);
        return result;
    }
}
