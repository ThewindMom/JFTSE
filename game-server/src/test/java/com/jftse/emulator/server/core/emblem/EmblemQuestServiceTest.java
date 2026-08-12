package com.jftse.emulator.server.core.emblem;

import com.jftse.entities.database.model.emblem.EmblemQuestDefinition;
import com.jftse.entities.database.model.emblem.EmblemQuestReward;
import com.jftse.entities.database.model.emblem.PlayerEmblemEquipment;
import com.jftse.entities.database.model.emblem.PlayerEmblemQuest;
import com.jftse.entities.database.model.emblem.PlayerEmblemQuestStatus;
import com.jftse.entities.database.model.item.Product;
import com.jftse.entities.database.model.player.Player;
import com.jftse.entities.database.model.player.PlayerStatistic;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.entities.database.repository.emblem.EmblemQuestDefinitionRepository;
import com.jftse.entities.database.repository.emblem.EmblemQuestRewardRepository;
import com.jftse.entities.database.repository.emblem.PlayerEmblemEquipmentRepository;
import com.jftse.entities.database.repository.emblem.PlayerEmblemQuestRepository;
import com.jftse.entities.database.repository.player.PlayerRepository;
import com.jftse.entities.database.repository.pocket.PlayerPocketRepository;
import com.jftse.server.core.constants.GameMode;
import com.jftse.server.core.service.EmblemCompletionResult;
import com.jftse.server.core.service.EmblemQuestStatus;
import com.jftse.server.core.service.InventoryService;
import com.jftse.server.core.service.LevelService;
import com.jftse.server.core.service.ProductService;
import com.jftse.server.core.service.impl.EmblemQuestServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmblemQuestServiceTest {
    @Mock
    private EmblemQuestDefinitionRepository definitionRepository;
    @Mock
    private EmblemQuestRewardRepository rewardRepository;
    @Mock
    private PlayerEmblemQuestRepository questRepository;
    @Mock
    private PlayerEmblemEquipmentRepository equipmentRepository;
    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private PlayerPocketRepository playerPocketRepository;
    @Mock
    private InventoryService inventoryService;
    @Mock
    private LevelService levelService;
    @Mock
    private ProductService productService;
    @InjectMocks
    private EmblemQuestServiceImpl service;

    private Player player;

    @BeforeEach
    void setUp() {
        PlayerStatistic statistic = new PlayerStatistic();
        player = new Player();
        player.setId(7L);
        player.setLevel((byte) 12);
        player.setExpPoints(1_000);
        player.setGold(2_000);
        player.setPlayerType((byte) 0);
        player.setPlayerStatistic(statistic);
        lenient().when(playerRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(player));
    }

    @Test
    void enforcesEnabledLevelPrerequisiteAndThreeActiveQuestLimit() {
        EmblemQuestDefinition disabled = definition(100);
        disabled.setEnabled(false);
        when(definitionRepository.findByQuestIndex(100)).thenReturn(Optional.of(disabled));
        assertEquals(EmblemQuestStatus.NOT_ALLOWED, service.accept(player, 100));

        EmblemQuestDefinition restricted = definition(101);
        restricted.setLevelRestriction(13);
        when(definitionRepository.findByQuestIndex(101)).thenReturn(Optional.of(restricted));
        assertEquals(EmblemQuestStatus.LEVEL_RESTRICTED, service.accept(player, 101));

        EmblemQuestDefinition limited = definition(102);
        when(definitionRepository.findByQuestIndex(102)).thenReturn(Optional.of(limited));
        when(questRepository.findByPlayerIdAndDefinition(7L, limited)).thenReturn(Optional.empty());
        when(questRepository.countActiveManualQuests(7L, PlayerEmblemQuestStatus.ACTIVE)).thenReturn(3L);
        assertEquals(EmblemQuestStatus.LIMIT_REACHED, service.accept(player, 102));

        EmblemQuestDefinition prerequisite = definition(103);
        prerequisite.setPrerequisites("98, 99");
        when(definitionRepository.findByQuestIndex(103)).thenReturn(Optional.of(prerequisite));
        when(questRepository.findByPlayerIdAndDefinition(7L, prerequisite)).thenReturn(Optional.empty());
        when(questRepository.countActiveManualQuests(7L, PlayerEmblemQuestStatus.ACTIVE)).thenReturn(0L);
        when(questRepository.findAllByPlayerId(7L)).thenReturn(List.of(completedQuest(definition(98))));
        assertEquals(EmblemQuestStatus.PREREQUISITE_MISSING, service.accept(player, 103));
    }

    @Test
    void initializesTotalConditionsFromPersistedPlayerStatistics() {
        player.getPlayerStatistic().setSmash(87);
        EmblemQuestDefinition definition = definition(100);
        definition.setConditionType1("TotalSmash");
        definition.setConditionTarget1("100");
        stubAccept(definition, Optional.empty());

        assertEquals(EmblemQuestStatus.SUCCESS, service.accept(player, 100));

        ArgumentCaptor<PlayerEmblemQuest> saved = ArgumentCaptor.forClass(PlayerEmblemQuest.class);
        verify(questRepository).save(saved.capture());
        assertEquals(87, saved.getValue().getBaseline1());
        assertEquals(87, saved.getValue().getProgress1());
        assertEquals(PlayerEmblemQuestStatus.ACTIVE, saved.getValue().getStatus());
    }

    @Test
    void initializesAutomaticEmblemsFromPersistedStatisticsWhenTheClientListsThem() {
        player.getPlayerStatistic().setSmash(87);
        EmblemQuestDefinition definition = definition(1000);
        definition.setConditionType1("TotalSmash");
        definition.setConditionTarget1("100");
        when(questRepository.findAllByPlayerId(7L)).thenReturn(new ArrayList<>());
        when(definitionRepository.findAllByEnabledTrueAndQuestIndexBetweenOrderByQuestIndex(1000, 1999))
                .thenReturn(List.of(definition));

        var states = service.list(7L);

        assertEquals(1, states.size());
        assertEquals(1000, Short.toUnsignedInt(states.get(0).questIndex()));
        assertTrue(states.get(0).inProgress());
        assertEquals(0, states.get(0).completionCount());
        assertEquals(87, states.get(0).progress1());
        ArgumentCaptor<PlayerEmblemQuest> saved = ArgumentCaptor.forClass(PlayerEmblemQuest.class);
        verify(questRepository).save(saved.capture());
        assertEquals(87, saved.getValue().getBaseline1());
        assertEquals(PlayerEmblemQuestStatus.ACTIVE, saved.getValue().getStatus());
    }

    @Test
    void listsThePersistedLifetimeBaselineForClientSideDeltaConditions() {
        EmblemQuestDefinition definition = definition(1000);
        definition.setConditionType1("TotalSmash");
        definition.setConditionTarget1("100");
        PlayerEmblemQuest active = quest(definition, PlayerEmblemQuestStatus.ACTIVE);
        active.setBaseline1(87);
        active.setProgress1(187);
        when(questRepository.findAllByPlayerId(7L)).thenReturn(new ArrayList<>(List.of(active)));
        when(definitionRepository.findAllByEnabledTrueAndQuestIndexBetweenOrderByQuestIndex(1000, 1999))
                .thenReturn(List.of(definition));

        var states = service.list(7L);

        assertEquals(87, states.get(0).progress1());
    }

    @Test
    void initializesNonTotalConditionsWithALifetimeBaselineAndZeroSessionProgress() {
        player.getPlayerStatistic().setSmash(87);
        EmblemQuestDefinition definition = definition(100);
        definition.setConditionType1("Smash");
        definition.setConditionTarget1("10");
        stubAccept(definition, Optional.empty());

        assertEquals(EmblemQuestStatus.SUCCESS, service.accept(player, 100));

        ArgumentCaptor<PlayerEmblemQuest> saved = ArgumentCaptor.forClass(PlayerEmblemQuest.class);
        verify(questRepository).save(saved.capture());
        assertEquals(87, saved.getValue().getBaseline1());
        assertEquals(0, saved.getValue().getProgress1());
    }

    @Test
    void rejectsManualLifecycleRequestsForAutomaticallyTrackedEmblems() {
        assertEquals(EmblemQuestStatus.NOT_ALLOWED, service.accept(player, 1000));
        assertEquals(EmblemQuestStatus.NOT_ALLOWED, service.abandon(7L, 1000));
        verify(definitionRepository, never()).findByQuestIndex(1000);
    }

    @Test
    void supportsAbandonAndReacceptButRejectsCompletedNonrepeatableQuest() {
        EmblemQuestDefinition repeatable = definition(100);
        repeatable.setQuestRepeat(true);
        PlayerEmblemQuest abandoned = quest(repeatable, PlayerEmblemQuestStatus.ABANDONED);
        stubAccept(repeatable, Optional.of(abandoned));

        assertEquals(EmblemQuestStatus.SUCCESS, service.accept(player, 100));
        assertEquals(PlayerEmblemQuestStatus.ACTIVE, abandoned.getStatus());

        EmblemQuestDefinition nonrepeatable = definition(101);
        PlayerEmblemQuest completed = completedQuest(nonrepeatable);
        when(definitionRepository.findByQuestIndex(101)).thenReturn(Optional.of(nonrepeatable));
        when(questRepository.findByPlayerIdAndDefinition(7L, nonrepeatable)).thenReturn(Optional.of(completed));
        assertEquals(EmblemQuestStatus.DUPLICATE, service.accept(player, 101));
    }

    @Test
    void updatesAnAbsoluteBaselineAndAnIncrementalCondition() {
        EmblemQuestDefinition definition = definition(1000);
        definition.setConditionType1("TotalFishes");
        definition.setConditionType2("Smash");
        PlayerEmblemQuest active = quest(definition, PlayerEmblemQuestStatus.ACTIVE);
        active.setProgress2(4);
        when(questRepository.findAllByPlayerId(7L)).thenReturn(List.of(active));

        service.setBaseline(7L, "TotalFishes", 120);
        service.increment(7L, "Smash", 3);

        assertEquals(120, active.getBaseline1());
        assertEquals(120, active.getProgress1());
        assertEquals(7, active.getProgress2());
        verify(questRepository, org.mockito.Mockito.times(2)).save(active);
    }

    @Test
    void updatesIncrementalAndTotalMatchConditionsOnlyForCompatibleMode() {
        EmblemQuestDefinition basicDefinition = definition(1000);
        basicDefinition.setGameMode("BASIC");
        basicDefinition.setConditionType1("Smash");
        basicDefinition.setConditionType2("TotalSlice");
        basicDefinition.setConditionType3("WinCount");
        PlayerEmblemQuest basicQuest = quest(basicDefinition, PlayerEmblemQuestStatus.ACTIVE);

        EmblemQuestDefinition battleDefinition = definition(1001);
        battleDefinition.setGameMode("BATTLE");
        battleDefinition.setConditionType1("Smash");
        PlayerEmblemQuest battleQuest = quest(battleDefinition, PlayerEmblemQuestStatus.ACTIVE);
        when(questRepository.findAllByPlayerId(7L)).thenReturn(List.of(basicQuest, battleQuest));

        service.updateMatchTotals(7L, GameMode.BASIC, true, 1, 2, 3, 4, 5, 6, 7, 8);

        assertEquals(5, basicQuest.getProgress1());
        assertEquals(3, basicQuest.getProgress2());
        assertEquals(1, basicQuest.getProgress3());
        assertEquals(0, battleQuest.getProgress1());
        verify(questRepository).save(basicQuest);
        verify(questRepository, never()).save(battleQuest);
    }

    @Test
    void rejectsIncompleteAndAlreadyCompletedQuestsWithoutGrantingRewards() {
        EmblemQuestDefinition definition = definition(1000);
        definition.setConditionType1("Smash");
        definition.setConditionTarget1("10");
        PlayerEmblemQuest active = quest(definition, PlayerEmblemQuestStatus.ACTIVE);
        active.setProgress1(9);
        stubFind(definition, active);

        EmblemCompletionResult incomplete = service.complete(player, 1000);
        assertEquals(EmblemQuestStatus.INCOMPLETE, incomplete.status());
        verify(inventoryService, never()).addItem(anyLong(), any(Integer.class), any(Integer.class), any());
        verify(questRepository, never()).save(any());

        active.setStatus(PlayerEmblemQuestStatus.COMPLETED);
        EmblemCompletionResult duplicate = service.complete(player, 1000);
        assertEquals(EmblemQuestStatus.NOT_ACTIVE, duplicate.status());
        verify(questRepository, never()).save(any());
    }

    @Test
    void validatesTotalConditionsAgainstAuthoritativePlayerStatistics() {
        EmblemQuestDefinition definition = definition(1000);
        definition.setConditionType1("TotalSmash");
        definition.setConditionTarget1("100");
        PlayerEmblemQuest active = quest(definition, PlayerEmblemQuestStatus.ACTIVE);
        active.setBaseline1(87);
        active.setProgress1(187);
        stubFind(definition, active);

        player.getPlayerStatistic().setSmash(99);
        assertEquals(EmblemQuestStatus.INCOMPLETE, service.complete(player, 1000).status());

        player.getPlayerStatistic().setSmash(100);
        when(rewardRepository.findAllByDefinitionAndPlayerTypeOrderByRewardSlot(definition, (byte) 0))
                .thenReturn(List.of());
        when(levelService.getLevel(0, 1_000, (byte) 12)).thenReturn((byte) 12);
        assertEquals(EmblemQuestStatus.SUCCESS, service.complete(player, 1000).status());
    }

    @Test
    void atomicallyCompletesAndReturnsAuthoritativePersistedRewardRecords() {
        EmblemQuestDefinition definition = definition(1000);
        definition.setRewardExp(25);
        definition.setRewardGold(40);
        PlayerEmblemQuest active = quest(definition, PlayerEmblemQuestStatus.ACTIVE);
        stubFind(definition, active);

        EmblemQuestReward reward = reward(definition, 5015, 2);
        PlayerPocket granted = grantedPocket();
        when(rewardRepository.findAllByDefinitionAndPlayerTypeOrderByRewardSlot(definition, (byte) 0))
                .thenReturn(List.of(reward));
        when(inventoryService.addItem(7L, 5015, 2, List.of())).thenReturn(List.of(granted));
        when(levelService.getLevel(25, 1_000, (byte) 12)).thenReturn((byte) 13);

        EmblemCompletionResult result = service.complete(player, 1000);

        assertEquals(EmblemQuestStatus.SUCCESS, result.status());
        assertEquals(1_025, result.exp());
        assertEquals(2_040, result.gold());
        assertEquals(13, result.level());
        assertEquals(1, result.rewards().size());
        assertEquals(44, result.rewards().get(0).pocketId());
        assertEquals(1_025, player.getExpPoints());
        assertEquals(2_040, player.getGold());
        assertEquals(PlayerEmblemQuestStatus.COMPLETED, active.getStatus());
        assertEquals(1, active.getCompletionCount());
        verify(questRepository).save(active);
        verify(levelService).setNewLevelStatusPoints((byte) 13, player);
    }

    @Test
    void completesAgainstTheLockedManagedPlayerRatherThanTheDetachedSessionReference() {
        Player detached = new Player();
        detached.setId(7L);
        detached.setLevel((byte) 1);
        detached.setExpPoints(10);
        detached.setGold(20);
        EmblemQuestDefinition definition = definition(1000);
        definition.setRewardExp(25);
        definition.setRewardGold(40);
        PlayerEmblemQuest active = quest(definition, PlayerEmblemQuestStatus.ACTIVE);
        stubFind(definition, active);
        when(rewardRepository.findAllByDefinitionAndPlayerTypeOrderByRewardSlot(definition, (byte) 0))
                .thenReturn(List.of());
        when(levelService.getLevel(25, 1_000, (byte) 12)).thenReturn((byte) 12);

        EmblemCompletionResult result = service.complete(detached, 1000);

        assertEquals(EmblemQuestStatus.SUCCESS, result.status());
        assertEquals(1_025, player.getExpPoints());
        assertEquals(2_040, player.getGold());
        assertEquals(10, detached.getExpPoints());
        assertEquals(20, detached.getGold());
        verify(playerRepository).findByIdForUpdate(7L);
        verify(levelService).setNewLevelStatusPoints((byte) 12, player);
    }

    @Test
    void grantsACompletionOnlyOnceWhenTheSameRequestIsRetried() {
        EmblemQuestDefinition definition = definition(1000);
        definition.setRewardExp(25);
        PlayerEmblemQuest active = quest(definition, PlayerEmblemQuestStatus.ACTIVE);
        stubFind(definition, active);
        when(rewardRepository.findAllByDefinitionAndPlayerTypeOrderByRewardSlot(definition, (byte) 0))
                .thenReturn(List.of());
        when(levelService.getLevel(25, 1_000, (byte) 12)).thenReturn((byte) 12);

        EmblemCompletionResult first = service.complete(player, 1000);
        EmblemCompletionResult retry = service.complete(player, 1000);

        assertEquals(EmblemQuestStatus.SUCCESS, first.status());
        assertEquals(EmblemQuestStatus.NOT_ACTIVE, retry.status());
        assertEquals(1, active.getCompletionCount());
        assertEquals(1_025, player.getExpPoints());
        verify(questRepository, times(2)).findByPlayerIdAndDefinitionForUpdate(7L, definition);
        verify(levelService, times(1)).setNewLevelStatusPoints((byte) 12, player);
    }

    @Test
    void requiresConfiguredProductsButDoesNotConsumeThemWithoutNativeEvidence() {
        EmblemQuestDefinition definition = definition(6);
        definition.setRequiredItem1(4014);
        definition.setRequiredQuantity1(10);
        PlayerEmblemQuest active = quest(definition, PlayerEmblemQuestStatus.ACTIVE);
        stubFind(definition, active);
        Product product = new Product();
        product.setProductIndex(4014);
        product.setCategory("MATERIAL");
        product.setItem0(3);
        PlayerPocket required = new PlayerPocket();
        required.setItemCount(10);
        when(productService.findProductByProductItemIndex(4014)).thenReturn(product);
        when(playerPocketRepository.findAllRequiredForUpdate(player.getPocket(), "MATERIAL", 3))
                .thenReturn(List.of(required));
        when(rewardRepository.findAllByDefinitionAndPlayerTypeOrderByRewardSlot(definition, (byte) 0))
                .thenReturn(List.of());
        when(levelService.getLevel(0, 1_000, (byte) 12)).thenReturn((byte) 12);

        assertEquals(EmblemQuestStatus.SUCCESS, service.complete(player, 6).status());
        assertEquals(10, required.getItemCount());
        verify(inventoryService, never()).removeItem(7L, 3, "MATERIAL", 10);
    }

    @Test
    void abortsCompletionBeforeStateOrCurrencyMutationWhenARewardCannotBeGranted() {
        EmblemQuestDefinition definition = definition(1000);
        definition.setRewardExp(25);
        definition.setRewardGold(40);
        PlayerEmblemQuest active = quest(definition, PlayerEmblemQuestStatus.ACTIVE);
        stubFind(definition, active);
        EmblemQuestReward reward = reward(definition, 9999, 1);
        when(rewardRepository.findAllByDefinitionAndPlayerTypeOrderByRewardSlot(definition, (byte) 0))
                .thenReturn(List.of(reward));
        when(inventoryService.addItem(7L, 9999, 1, List.of())).thenReturn(List.of());
        when(levelService.getLevel(25, 1_000, (byte) 12)).thenReturn((byte) 13);

        assertThrows(IllegalStateException.class, () -> service.complete(player, 1000));

        assertEquals(1_000, player.getExpPoints());
        assertEquals(2_000, player.getGold());
        assertEquals(PlayerEmblemQuestStatus.ACTIVE, active.getStatus());
        assertEquals(0, active.getCompletionCount());
        verify(questRepository, never()).save(any());
        verify(levelService, never()).setNewLevelStatusPoints(org.mockito.ArgumentMatchers.anyByte(), any());
    }

    @Test
    void equipsOnlyFourUniqueOwnedEmblems() {
        EmblemQuestDefinition ownedDefinition = definition(1000);
        when(questRepository.findAllByPlayerId(7L)).thenReturn(List.of(completedQuest(ownedDefinition)));

        assertEquals(EmblemQuestStatus.INVALID_EQUIPMENT,
                service.equip(player, List.of(1000, 1000, 0, 0)));
        assertEquals(EmblemQuestStatus.INVALID_EQUIPMENT,
                service.equip(player, List.of(1000, 1001, 0, 0)));

        when(equipmentRepository.findByPlayerId(7L)).thenReturn(Optional.empty());
        assertEquals(EmblemQuestStatus.SUCCESS, service.equip(player, List.of(1000, 0, 0, 0)));
        ArgumentCaptor<PlayerEmblemEquipment> saved = ArgumentCaptor.forClass(PlayerEmblemEquipment.class);
        verify(equipmentRepository).save(saved.capture());
        assertEquals(1000, Short.toUnsignedInt(saved.getValue().getSlot1()));
        assertTrue(saved.getValue().getPlayer() == player);
    }

    private void stubAccept(EmblemQuestDefinition definition, Optional<PlayerEmblemQuest> existing) {
        when(definitionRepository.findByQuestIndex(definition.getQuestIndex())).thenReturn(Optional.of(definition));
        when(questRepository.findByPlayerIdAndDefinition(7L, definition)).thenReturn(existing);
        when(questRepository.countActiveManualQuests(7L, PlayerEmblemQuestStatus.ACTIVE)).thenReturn(0L);
    }

    private void stubFind(EmblemQuestDefinition definition, PlayerEmblemQuest quest) {
        when(definitionRepository.findByQuestIndex(definition.getQuestIndex())).thenReturn(Optional.of(definition));
        when(questRepository.findByPlayerIdAndDefinitionForUpdate(7L, definition)).thenReturn(Optional.of(quest));
    }

    private EmblemQuestDefinition definition(int index) {
        EmblemQuestDefinition definition = new EmblemQuestDefinition();
        definition.setId((long) index);
        definition.setQuestIndex(index);
        definition.setEnabled(true);
        definition.setLevelRestriction(1);
        definition.setQuestRepeat(false);
        definition.setItemRewardRepeat(false);
        definition.setRewardExp(0);
        definition.setRewardGold(0);
        return definition;
    }

    private PlayerEmblemQuest quest(EmblemQuestDefinition definition, PlayerEmblemQuestStatus status) {
        PlayerEmblemQuest quest = new PlayerEmblemQuest();
        quest.setPlayer(player);
        quest.setDefinition(definition);
        quest.setStatus(status);
        return quest;
    }

    private PlayerEmblemQuest completedQuest(EmblemQuestDefinition definition) {
        PlayerEmblemQuest quest = quest(definition, PlayerEmblemQuestStatus.COMPLETED);
        quest.setCompletionCount(1);
        return quest;
    }

    private EmblemQuestReward reward(EmblemQuestDefinition definition, int productIndex, int quantity) {
        EmblemQuestReward reward = new EmblemQuestReward();
        reward.setDefinition(definition);
        reward.setPlayerType((byte) 0);
        reward.setRewardSlot((byte) 1);
        reward.setProductIndex(productIndex);
        reward.setQuantityMin(quantity);
        reward.setQuantityMax(quantity);
        return reward;
    }

    private PlayerPocket grantedPocket() {
        PlayerPocket pocket = new PlayerPocket();
        pocket.setId(44L);
        pocket.setCategory("MATERIAL");
        pocket.setItemIndex(15);
        pocket.setUseType("Count");
        pocket.setItemCount(2);
        pocket.setCreated(new Date(1_000));
        pocket.setEnchantStr(1);
        pocket.setEnchantSta(2);
        pocket.setEnchantDex(3);
        pocket.setEnchantWil(4);
        pocket.setEnchantElement(5);
        pocket.setEnchantLevel(6);
        return pocket;
    }
}
