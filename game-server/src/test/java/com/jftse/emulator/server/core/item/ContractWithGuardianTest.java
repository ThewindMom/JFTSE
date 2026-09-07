package com.jftse.emulator.server.core.item;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.client.PlayerStatisticView;
import com.jftse.emulator.server.core.life.item.BaseItem;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.packets.player.S2CPlayerInfoPlayStatsPacket;
import com.jftse.entities.database.model.item.ItemSpecial;
import com.jftse.entities.database.model.player.PlayerStatistic;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.entities.database.model.pocket.Pocket;
import com.jftse.entities.database.repository.player.PlayerStatisticRepository;
import com.jftse.entities.database.repository.pocket.PlayerPocketRepository;
import com.jftse.entities.database.repository.pocket.PocketRepository;
import com.jftse.server.core.item.EItemCategory;
import com.jftse.server.core.item.EItemUseType;
import com.jftse.server.core.protocol.IPacket;
import com.jftse.server.core.service.ContractWithGuardianService;
import com.jftse.server.core.service.ItemSpecialService;
import com.jftse.server.core.service.PlayerPocketService;
import com.jftse.server.core.service.impl.ContractWithGuardianServiceImpl;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractWithGuardianTest {
    @Mock private PlayerPocketService playerPocketService;
    @Mock private ItemSpecialService itemSpecialService;
    @Mock private ContractWithGuardianService contractWithGuardianService;
    @Mock private PlayerPocketRepository playerPocketRepository;
    @Mock private PocketRepository pocketRepository;
    @Mock private PlayerStatisticRepository playerStatisticRepository;
    @Mock private FTPlayer ftPlayer;

    private ContractWithGuardianServiceImpl service;
    private PlayerPocket contract;
    private Pocket pocket;
    private PlayerStatistic statistic;

    @BeforeEach
    void setUp() {
        ServiceManager manager = new ServiceManager();
        ReflectionTestUtils.setField(manager, "playerPocketService", playerPocketService);
        ReflectionTestUtils.setField(manager, "itemSpecialService", itemSpecialService);
        ReflectionTestUtils.setField(manager, "contractWithGuardianService", contractWithGuardianService);
        manager.init();

        service = new ContractWithGuardianServiceImpl(
                playerPocketRepository,
                pocketRepository,
                playerStatisticRepository
        );
        pocket = pocket(71L, 4);
        contract = contract(501L, pocket, 2);
        statistic = statistic(61L);
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(ServiceManager.class, "instance", null);
    }

    @Test
    void factoryRecognizesTheNativeResetRecordsItem() {
        BaseItem item = factoryItem();

        assertNotNull(item);
        assertEquals(7, item.getItemIndex());
    }

    @Test
    void itemAdapterRefreshesTheRecordDisplayAndRemovesTheFinalItem() {
        contract.setItemCount(1);
        BaseItem item = factoryItem();
        when(ftPlayer.getId()).thenReturn(11L);
        when(ftPlayer.getPlayerStatisticId()).thenReturn(61L);
        when(contractWithGuardianService.use(61L, 71L, 501L)).thenReturn(
                new ContractWithGuardianService.UseResult(
                        ContractWithGuardianService.UseStatus.SUCCESS,
                        contract,
                        true,
                        statistic
                )
        );

        assertTrue(item.processPlayer(ftPlayer));
        assertTrue(item.processPocket(71L));

        List<IPacket> packets = item.getPacketsToSend().get(11L);
        assertEquals(2, packets.size());
        assertTrue(packets.get(0) instanceof S2CPlayerInfoPlayStatsPacket);
        assertTrue(packets.get(1) instanceof S2CInventoryItemRemoveAnswerPacket);
        verify(ftPlayer).setPlayerStatistic(any(PlayerStatisticView.class));
    }

    @Test
    void itemAdapterDoesNotChangeCachedStateOrInventoryAfterRejection() {
        BaseItem item = factoryItem();
        when(ftPlayer.getId()).thenReturn(11L);
        when(ftPlayer.getPlayerStatisticId()).thenReturn(61L);
        when(contractWithGuardianService.use(61L, 71L, 501L)).thenReturn(
                new ContractWithGuardianService.UseResult(
                        ContractWithGuardianService.UseStatus.NOT_OWNED,
                        contract,
                        false,
                        null
                )
        );

        assertTrue(item.processPlayer(ftPlayer));
        assertFalse(item.processPocket(71L));
        assertTrue(item.getPacketsToSend().isEmpty());
        verify(ftPlayer, never()).setPlayerStatistic(any());
    }

    @Test
    void resetsOnlyBasicBattleAndGuardianWinLossRecords() {
        arrangeOwnedContract();

        ContractWithGuardianService.UseResult result = service.use(61L, 71L, 501L);

        assertEquals(ContractWithGuardianService.UseStatus.SUCCESS, result.status());
        assertEquals(0, statistic.getBasicRecordWin());
        assertEquals(0, statistic.getBasicRecordLoss());
        assertEquals(0, statistic.getBattleRecordWin());
        assertEquals(0, statistic.getBattleRecordLoss());
        assertEquals(0, statistic.getGuardianRecordWin());
        assertEquals(0, statistic.getGuardianRecordLoss());
        assertEquals(0, statistic.getTotalGames());
        assertEquals(101, statistic.getBasicRP());
        assertEquals(202, statistic.getBattleRP());
        assertEquals(303, statistic.getGuardianRP());
        assertEquals(4, statistic.getConsecutiveWins());
        assertEquals(9, statistic.getMaxConsecutiveWins());
        assertEquals(7, statistic.getNumberOfDisconnects());
        assertEquals(41, statistic.getServiceAce());
        assertEquals(53, statistic.getSkillShot());
        verify(playerStatisticRepository).save(statistic);
    }

    @Test
    void consumesOneUnitInTheSameTransactionAsTheRecordReset() {
        arrangeOwnedContract();

        ContractWithGuardianService.UseResult result = service.use(61L, 71L, 501L);

        assertEquals(ContractWithGuardianService.UseStatus.SUCCESS, result.status());
        assertFalse(result.itemRemoved());
        assertEquals(1, contract.getItemCount());
        verify(playerPocketRepository).save(contract);
        verify(playerPocketRepository, never()).delete(any());
        verify(pocketRepository, never()).save(any());
    }

    @Test
    void finalUnitRemovalAlsoDecrementsBelongings() {
        contract.setItemCount(1);
        arrangeOwnedContract();
        when(pocketRepository.findLockedById(71L)).thenReturn(Optional.of(pocket));

        ContractWithGuardianService.UseResult result = service.use(61L, 71L, 501L);

        assertTrue(result.itemRemoved());
        assertEquals(3, pocket.getBelongings());
        verify(playerPocketRepository).delete(contract);
        verify(pocketRepository).save(pocket);
    }

    @Test
    void rejectsAPlayerPocketRowOwnedByAnotherPocketWithoutMutation() {
        contract.setPocket(pocket(72L, 1));
        when(playerPocketRepository.findLockedById(501L)).thenReturn(Optional.of(contract));

        assertRejected(ContractWithGuardianService.UseStatus.NOT_OWNED);
        verify(playerStatisticRepository, never()).findLockedById(any());
    }

    @Test
    void rejectsWrongIndexCategoryUseTypeAndEmptyStacks() {
        PlayerPocket wrongIndex = contract(501L, pocket, 1);
        wrongIndex.setItemIndex(8);
        PlayerPocket wrongCategory = contract(502L, pocket, 1);
        wrongCategory.setCategory(EItemCategory.QUICK.getName());
        PlayerPocket wrongUseType = contract(503L, pocket, 1);
        wrongUseType.setUseType(EItemUseType.TIME.getName());
        PlayerPocket empty = contract(504L, pocket, 0);
        when(playerPocketRepository.findLockedById(501L)).thenReturn(Optional.of(wrongIndex));
        when(playerPocketRepository.findLockedById(502L)).thenReturn(Optional.of(wrongCategory));
        when(playerPocketRepository.findLockedById(503L)).thenReturn(Optional.of(wrongUseType));
        when(playerPocketRepository.findLockedById(504L)).thenReturn(Optional.of(empty));

        assertEquals(ContractWithGuardianService.UseStatus.INVALID_ITEM, service.use(61L, 71L, 501L).status());
        assertEquals(ContractWithGuardianService.UseStatus.INVALID_ITEM, service.use(61L, 71L, 502L).status());
        assertEquals(ContractWithGuardianService.UseStatus.INVALID_ITEM, service.use(61L, 71L, 503L).status());
        assertEquals(ContractWithGuardianService.UseStatus.INVALID_ITEM, service.use(61L, 71L, 504L).status());
        verify(playerStatisticRepository, never()).save(any());
        verify(playerPocketRepository, never()).save(any());
        verify(playerPocketRepository, never()).delete(any());
    }

    @Test
    void rejectsMissingStatisticsWithoutConsumingTheItem() {
        when(playerPocketRepository.findLockedById(501L)).thenReturn(Optional.of(contract));
        when(playerStatisticRepository.findLockedById(61L)).thenReturn(Optional.empty());

        assertRejected(ContractWithGuardianService.UseStatus.STATISTIC_NOT_FOUND);
    }

    @Test
    void rejectsMissingPocketBeforeResettingRecords() {
        contract.setItemCount(1);
        arrangeOwnedContract();
        when(pocketRepository.findLockedById(71L)).thenReturn(Optional.empty());

        assertRejected(ContractWithGuardianService.UseStatus.POCKET_NOT_FOUND);
    }

    @Test
    void replayAfterFinalConsumptionCannotResetOrConsumeTwice() {
        contract.setItemCount(1);
        when(playerPocketRepository.findLockedById(501L))
                .thenReturn(Optional.of(contract), Optional.empty());
        when(playerStatisticRepository.findLockedById(61L)).thenReturn(Optional.of(statistic));
        when(pocketRepository.findLockedById(71L)).thenReturn(Optional.of(pocket));

        ContractWithGuardianService.UseResult first = service.use(61L, 71L, 501L);
        ContractWithGuardianService.UseResult replay = service.use(61L, 71L, 501L);

        assertEquals(ContractWithGuardianService.UseStatus.SUCCESS, first.status());
        assertEquals(ContractWithGuardianService.UseStatus.ITEM_NOT_FOUND, replay.status());
        verify(playerStatisticRepository).save(statistic);
        verify(playerPocketRepository).delete(contract);
    }

    @Test
    void mutationUsesOneTransactionAndPessimisticItemStatisticAndPocketLocks() throws Exception {
        Method serviceMethod = ContractWithGuardianServiceImpl.class.getMethod(
                "use", Long.class, Long.class, Long.class);
        Transactional transactional = serviceMethod.getAnnotation(Transactional.class);
        Lock itemLock = PlayerPocketRepository.class.getMethod("findLockedById", Long.class)
                .getAnnotation(Lock.class);
        Lock statisticLock = PlayerStatisticRepository.class.getMethod("findLockedById", Long.class)
                .getAnnotation(Lock.class);
        Lock pocketLock = PocketRepository.class.getMethod("findLockedById", Long.class)
                .getAnnotation(Lock.class);

        assertEquals(Isolation.READ_COMMITTED, transactional.isolation());
        assertEquals(LockModeType.PESSIMISTIC_WRITE, itemLock.value());
        assertEquals(LockModeType.PESSIMISTIC_WRITE, statisticLock.value());
        assertEquals(LockModeType.PESSIMISTIC_WRITE, pocketLock.value());
    }

    private void arrangeOwnedContract() {
        when(playerPocketRepository.findLockedById(501L)).thenReturn(Optional.of(contract));
        when(playerStatisticRepository.findLockedById(61L)).thenReturn(Optional.of(statistic));
    }

    private void assertRejected(ContractWithGuardianService.UseStatus expected) {
        ContractWithGuardianService.UseResult result = service.use(61L, 71L, 501L);

        assertEquals(expected, result.status());
        verify(playerStatisticRepository, never()).save(any());
        verify(playerPocketRepository, never()).save(any());
        verify(playerPocketRepository, never()).delete(any());
        verify(pocketRepository, never()).save(any());
    }

    private BaseItem factoryItem() {
        ItemSpecial catalogItem = new ItemSpecial();
        catalogItem.setItemIndex(7);
        catalogItem.setName("Contract with Guardian");
        when(playerPocketService.getItemAsPocket(501L, pocket)).thenReturn(contract);
        when(itemSpecialService.findByItemIndex(7)).thenReturn(catalogItem);
        return com.jftse.emulator.server.core.life.item.ItemFactory.getItem(501L, pocket);
    }

    private PlayerPocket contract(long id, Pocket pocket, int count) {
        PlayerPocket item = new PlayerPocket();
        item.setId(id);
        item.setPocket(pocket);
        item.setCategory(EItemCategory.SPECIAL.getName());
        item.setItemIndex(7);
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

    private PlayerStatistic statistic(long id) {
        PlayerStatistic result = new PlayerStatistic();
        result.setId(id);
        result.setBasicRecordWin(11);
        result.setBasicRecordLoss(12);
        result.setBattleRecordWin(21);
        result.setBattleRecordLoss(22);
        result.setGuardianRecordWin(31);
        result.setGuardianRecordLoss(32);
        result.setBasicRP(101);
        result.setBattleRP(202);
        result.setGuardianRP(303);
        result.setConsecutiveWins(4);
        result.setMaxConsecutiveWins(9);
        result.setNumberOfDisconnects(7);
        result.setServiceAce(41);
        result.setReturnAce(42);
        result.setStroke(43);
        result.setSlice(44);
        result.setLob(45);
        result.setSmash(46);
        result.setVolley(47);
        result.setTopSpin(48);
        result.setRising(49);
        result.setServe(50);
        result.setGuardBreakShot(51);
        result.setChargeShot(52);
        result.setSkillShot(53);
        result.setTotalGames(66);
        return result;
    }
}
