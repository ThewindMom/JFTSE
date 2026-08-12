package com.jftse.server.core.service.impl;

import com.jftse.entities.database.model.emblem.EmblemQuestDefinition;
import com.jftse.entities.database.model.emblem.EmblemQuestReward;
import com.jftse.entities.database.model.emblem.PlayerEmblemEquipment;
import com.jftse.entities.database.model.emblem.PlayerEmblemQuest;
import com.jftse.entities.database.model.emblem.PlayerEmblemQuestStatus;
import com.jftse.entities.database.model.item.Product;
import com.jftse.entities.database.model.player.Player;
import com.jftse.entities.database.model.player.PlayerStatistic;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.entities.database.repository.player.PlayerRepository;
import com.jftse.entities.database.repository.pocket.PlayerPocketRepository;
import com.jftse.entities.database.repository.emblem.EmblemQuestDefinitionRepository;
import com.jftse.entities.database.repository.emblem.EmblemQuestRewardRepository;
import com.jftse.entities.database.repository.emblem.PlayerEmblemEquipmentRepository;
import com.jftse.entities.database.repository.emblem.PlayerEmblemQuestRepository;
import com.jftse.server.core.constants.GameMode;
import com.jftse.server.core.item.EItemCategory;
import com.jftse.server.core.item.EItemUseType;
import com.jftse.server.core.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmblemQuestServiceImpl implements EmblemQuestService {
    private static final int AUTOMATIC_EMBLEM_MIN = 1000;
    private static final int AUTOMATIC_EMBLEM_MAX = 1999;

    private final EmblemQuestDefinitionRepository definitionRepository;
    private final EmblemQuestRewardRepository rewardRepository;
    private final PlayerEmblemQuestRepository questRepository;
    private final PlayerEmblemEquipmentRepository equipmentRepository;
    private final PlayerRepository playerRepository;
    private final PlayerPocketRepository playerPocketRepository;
    private final InventoryService inventoryService;
    private final LevelService levelService;
    private final ProductService productService;

    @Override
    @Transactional
    public List<EmblemQuestState> list(long playerId) {
        Player player = playerRepository.findByIdForUpdate(playerId).orElse(null);
        if (player == null) return List.of();
        List<PlayerEmblemQuest> quests = new ArrayList<>(questRepository.findAllByPlayerId(playerId));
        Map<Integer, PlayerEmblemQuest> questsByIndex = quests.stream().collect(Collectors.toMap(
                q -> q.getDefinition().getQuestIndex(), q -> q));
        for (EmblemQuestDefinition definition : definitionRepository
                .findAllByEnabledTrueAndQuestIndexBetweenOrderByQuestIndex(AUTOMATIC_EMBLEM_MIN, AUTOMATIC_EMBLEM_MAX)) {
            if (!questsByIndex.containsKey(definition.getQuestIndex())) {
                PlayerEmblemQuest quest = new PlayerEmblemQuest();
                quest.setPlayer(player);
                quest.setDefinition(definition);
                quest.setStatus(PlayerEmblemQuestStatus.ACTIVE);
                initializeProgress(quest, player);
                questRepository.save(quest);
                quests.add(quest);
                questsByIndex.put(definition.getQuestIndex(), quest);
            }
        }
        return quests.stream()
                .filter(q -> Boolean.TRUE.equals(q.getDefinition().getEnabled()))
                .filter(q -> q.getStatus() != PlayerEmblemQuestStatus.ABANDONED)
                .sorted(Comparator.comparing(q -> q.getDefinition().getQuestIndex()))
                .map(EmblemQuestServiceImpl::state).toList();
    }

    @Override
    @Transactional
    public EmblemQuestStatus accept(Player player, int questIndex) {
        if (player == null || automaticEmblem(questIndex)) return EmblemQuestStatus.NOT_ALLOWED;
        player = playerRepository.findByIdForUpdate(player.getId()).orElse(null);
        if (player == null) return EmblemQuestStatus.NOT_FOUND;
        EmblemQuestDefinition definition = definitionRepository.findByQuestIndex(questIndex).orElse(null);
        if (definition == null) return EmblemQuestStatus.NOT_FOUND;
        if (!Boolean.TRUE.equals(definition.getEnabled())) return EmblemQuestStatus.NOT_ALLOWED;
        if (definition.getLevelRestriction() != null && player.getLevel() < definition.getLevelRestriction())
            return EmblemQuestStatus.LEVEL_RESTRICTED;
        Optional<PlayerEmblemQuest> existing = questRepository.findByPlayerIdAndDefinition(player.getId(), definition);
        if (existing.isPresent() && existing.get().getStatus() == PlayerEmblemQuestStatus.ACTIVE)
            return EmblemQuestStatus.DUPLICATE;
        if (existing.isPresent() && existing.get().getCompletionCount() > 0 && !Boolean.TRUE.equals(definition.getQuestRepeat()))
            return EmblemQuestStatus.DUPLICATE;
        if (questRepository.countActiveManualQuests(player.getId(), PlayerEmblemQuestStatus.ACTIVE) >= 3)
            return EmblemQuestStatus.LIMIT_REACHED;
        if (!prerequisitesMet(player.getId(), definition.getPrerequisites())) return EmblemQuestStatus.PREREQUISITE_MISSING;
        PlayerEmblemQuest quest = existing.orElseGet(PlayerEmblemQuest::new);
        quest.setPlayer(player);
        quest.setDefinition(definition);
        quest.setStatus(PlayerEmblemQuestStatus.ACTIVE);
        initializeProgress(quest, player);
        questRepository.save(quest);
        return EmblemQuestStatus.SUCCESS;
    }

    @Override
    @Transactional
    public EmblemQuestStatus abandon(long playerId, int questIndex) {
        if (automaticEmblem(questIndex)) return EmblemQuestStatus.NOT_ALLOWED;
        if (playerRepository.findByIdForUpdate(playerId).isEmpty()) return EmblemQuestStatus.NOT_FOUND;
        PlayerEmblemQuest quest = findForUpdate(playerId, questIndex);
        if (quest == null) return EmblemQuestStatus.NOT_FOUND;
        if (quest.getStatus() != PlayerEmblemQuestStatus.ACTIVE) return EmblemQuestStatus.NOT_ACTIVE;
        quest.setStatus(PlayerEmblemQuestStatus.ABANDONED);
        questRepository.save(quest);
        return EmblemQuestStatus.SUCCESS;
    }

    @Override
    @Transactional
    public EmblemCompletionResult complete(Player player, int questIndex) {
        if (player == null) return EmblemCompletionResult.failure(EmblemQuestStatus.NOT_ALLOWED);
        player = playerRepository.findByIdForUpdate(player.getId()).orElse(null);
        if (player == null) return EmblemCompletionResult.failure(EmblemQuestStatus.NOT_FOUND);
        PlayerEmblemQuest quest = findForUpdate(player.getId(), questIndex);
        if (quest == null || quest.getStatus() != PlayerEmblemQuestStatus.ACTIVE)
            return EmblemCompletionResult.failure(EmblemQuestStatus.NOT_ACTIVE);
        EmblemQuestDefinition definition = quest.getDefinition();
        if (!Boolean.TRUE.equals(definition.getEnabled()))
            return EmblemCompletionResult.failure(EmblemQuestStatus.NOT_ALLOWED);
        if (definition.getLevelRestriction() != null && player.getLevel() < definition.getLevelRestriction())
            return EmblemCompletionResult.failure(EmblemQuestStatus.LEVEL_RESTRICTED);
        if (!complete(quest, player)) return EmblemCompletionResult.failure(EmblemQuestStatus.INCOMPLETE);
        if (!requiredItemsPresent(player, definition))
            return EmblemCompletionResult.failure(EmblemQuestStatus.INCOMPLETE);
        int exp = value(definition.getRewardExp());
        int gold = value(definition.getRewardGold());
        int newExp = Math.addExact(player.getExpPoints(), exp);
        int newGold = Math.addExact(player.getGold(), gold);
        byte newLevel = levelService.getLevel(exp, player.getExpPoints(), player.getLevel());
        int priorCompletions = value(quest.getCompletionCount());

        List<EmblemRewardItem> rewards = new ArrayList<>();
        if (priorCompletions == 0 || Boolean.TRUE.equals(definition.getItemRewardRepeat())) {
            for (EmblemQuestReward reward : rewardRepository.findAllByDefinitionAndPlayerTypeOrderByRewardSlot(
                    definition, player.getPlayerType())) {
                int quantity = rewardQuantity(reward);
                List<PlayerPocket> granted = inventoryService.addItem(
                        player.getId(), reward.getProductIndex(), quantity, List.of());
                if (granted.isEmpty()) {
                    throw new IllegalStateException("Unable to grant emblem quest reward product "
                            + reward.getProductIndex() + " to player " + player.getId());
                }
                granted.stream().map(EmblemQuestServiceImpl::rewardItem).forEach(rewards::add);
            }
        }

        quest.setStatus(PlayerEmblemQuestStatus.COMPLETED);
        quest.setCompletionCount(priorCompletions + 1);
        player.setExpPoints(newExp);
        player.setGold(newGold);
        levelService.setNewLevelStatusPoints(newLevel, player);
        questRepository.save(quest);
        return new EmblemCompletionResult(EmblemQuestStatus.SUCCESS, newLevel, newExp, newGold, rewards);
    }

    @Override
    @Transactional
    public EmblemQuestStatus equip(Player player, List<Integer> ids) {
        if (player == null || ids == null || ids.size() != 4
                || ids.stream().anyMatch(i -> i == null || i < 0 || i > 0xffff))
            return EmblemQuestStatus.INVALID_EQUIPMENT;
        player = playerRepository.findByIdForUpdate(player.getId()).orElse(null);
        if (player == null) return EmblemQuestStatus.NOT_FOUND;
        List<Integer> nonzero = ids.stream().filter(i -> i != 0).toList();
        if (new HashSet<>(nonzero).size() != nonzero.size()) return EmblemQuestStatus.INVALID_EQUIPMENT;
        Set<Integer> owned = questRepository.findAllByPlayerId(player.getId()).stream()
                .filter(q -> q.getStatus() == PlayerEmblemQuestStatus.COMPLETED || value(q.getCompletionCount()) > 0)
                .map(q -> q.getDefinition().getQuestIndex()).collect(Collectors.toSet());
        if (!owned.containsAll(nonzero)) return EmblemQuestStatus.INVALID_EQUIPMENT;
        PlayerEmblemEquipment equipment = equipmentRepository.findByPlayerId(player.getId()).orElseGet(PlayerEmblemEquipment::new);
        equipment.setPlayer(player); equipment.setSlot1((short) (int) ids.get(0)); equipment.setSlot2((short) (int) ids.get(1));
        equipment.setSlot3((short) (int) ids.get(2)); equipment.setSlot4((short) (int) ids.get(3));
        equipmentRepository.save(equipment);
        return EmblemQuestStatus.SUCCESS;
    }

    @Override @Transactional public void increment(long playerId, String type, int amount) { update(playerId, type, amount, false); }
    @Override @Transactional public void setBaseline(long playerId, String type, int value) { update(playerId, type, value, true); }

    @Override
    @Transactional
    public void updateMatchTotals(long playerId, int gameMode, boolean won, int serviceAces, int returnAces,
                                  int slices, int lobs, int smashes, int guardBreakShots,
                                  int chargeShots, int skillShots) {
        if (playerRepository.findByIdForUpdate(playerId).isEmpty()) return;
        Map<String, Integer> increments = new HashMap<>();
        addMatchIncrement(increments, TOTAL_SMASH, "Smash", smashes);
        addMatchIncrement(increments, TOTAL_SLICE, "Slice", slices);
        addMatchIncrement(increments, TOTAL_CHARGE_SHOT, "ChargeShot", chargeShots);
        addMatchIncrement(increments, TOTAL_LOB, "Lob", lobs);
        addMatchIncrement(increments, TOTAL_SERVICE_ACE, "ServiceAce", serviceAces);
        addMatchIncrement(increments, TOTAL_RETURN_ACE, "ReturnAce", returnAces);
        addMatchIncrement(increments, TOTAL_SKILL_SHOT, "SkillShot", skillShots);
        addMatchIncrement(increments, TOTAL_GUARD_BREAK, "GuardBreak", guardBreakShots);
        if (gameMode == GameMode.BASIC || gameMode == GameMode.BATTLE) {
            increments.put(won ? TOTAL_WIN_COUNT : TOTAL_LOSE_COUNT, 1);
            increments.put(won ? "WinCount" : "LoseCount", 1);
        }

        String mode = gameMode == GameMode.BATTLE ? "BATTLE" : gameMode == GameMode.BASIC ? "BASIC" : null;
        for (PlayerEmblemQuest quest : questRepository.findAllByPlayerId(playerId)) {
            if (quest.getStatus() != PlayerEmblemQuestStatus.ACTIVE)
                continue;
            String requiredMode = quest.getDefinition().getGameMode();
            if (("BASIC".equals(requiredMode) || "BATTLE".equals(requiredMode)) && !requiredMode.equals(mode))
                continue;

            boolean changed = false;
            for (int i = 1; i <= 4; i++) {
                int amount = increments.getOrDefault(condition(quest.getDefinition(), i), 0);
                if (amount > 0) {
                    setProgress(quest, i, progress(quest, i) + amount);
                    changed = true;
                }
            }
            if (changed)
                questRepository.save(quest);
        }
    }

    private static void addMatchIncrement(Map<String, Integer> increments, String total, String active, int amount) {
        if (amount > 0) {
            increments.put(total, amount);
            increments.put(active, amount);
        }
    }

    private void update(long playerId, String type, int amount, boolean baseline) {
        if (playerRepository.findByIdForUpdate(playerId).isEmpty()) return;
        for (PlayerEmblemQuest q : questRepository.findAllByPlayerId(playerId))
            if (q.getStatus() == PlayerEmblemQuestStatus.ACTIVE) {
            EmblemQuestDefinition d = q.getDefinition();
            for (int i = 1; i <= 4; i++) if (Objects.equals(condition(d, i), type)) {
                if (baseline) {
                    setBaseline(q, i, Math.max(0, amount));
                    setProgress(q, i, Math.max(0, amount));
                } else {
                    setProgress(q, i, Math.max(0, progress(q, i) + amount));
                }
                questRepository.save(q);
            }
        }
    }

    private boolean prerequisitesMet(long playerId, String prerequisites) {
        if (prerequisites == null || prerequisites.isBlank()) return true;
        Set<Integer> completed = questRepository.findAllByPlayerId(playerId).stream()
                .filter(q -> value(q.getCompletionCount()) > 0).map(q -> q.getDefinition().getQuestIndex()).collect(Collectors.toSet());
        try { return Arrays.stream(prerequisites.split("[,;\\s]+")).filter(s -> !s.isBlank()).map(Integer::valueOf).allMatch(completed::contains); }
        catch (NumberFormatException e) { return false; }
    }
    private PlayerEmblemQuest findForUpdate(long playerId, int index) {
        EmblemQuestDefinition d = definitionRepository.findByQuestIndex(index).orElse(null);
        return d == null ? null : questRepository.findByPlayerIdAndDefinitionForUpdate(playerId, d).orElse(null);
    }
    private static boolean complete(PlayerEmblemQuest q, Player player) {
        for (int i = 1; i <= 4; i++) {
            String condition = condition(q.getDefinition(), i);
            int achieved = totalCondition(condition)
                    ? initialProgress(player, q.getDefinition(), condition)
                    : progress(q, i);
            if (condition != null && achieved < target(q.getDefinition(), i)) return false;
        }
        return true;
    }
    private static EmblemQuestState state(PlayerEmblemQuest q) {
        EmblemQuestDefinition d = q.getDefinition();
        return new EmblemQuestState(cap(d.getQuestIndex()), q.getStatus() == PlayerEmblemQuestStatus.ACTIVE,
                cap(value(q.getCompletionCount())),
                condition(d,1)!=null, cap(wireProgress(q,1)), condition(d,2)!=null, cap(wireProgress(q,2)),
                condition(d,3)!=null, cap(wireProgress(q,3)), condition(d,4)!=null, cap(wireProgress(q,4)));
    }

    private static void initializeProgress(PlayerEmblemQuest quest, Player player) {
        for (int i = 1; i <= 4; i++) {
            String condition = condition(quest.getDefinition(), i);
            int initial = initialProgress(player, quest.getDefinition(), condition);
            setBaseline(quest, i, initial);
            setProgress(quest, i, incrementalCondition(condition) ? 0 : initial);
        }
    }

    private static int initialProgress(Player player, EmblemQuestDefinition definition, String condition) {
        if (condition == null) return 0;
        if (condition.equals("CharacterLevel")) return player.getLevel();
        PlayerStatistic statistic = player.getPlayerStatistic();
        if (statistic == null) return 0;
        return switch (condition) {
            case "Smash", "TotalSmash" -> value(statistic.getSmash());
            case "Slice", "TotalSlice" -> value(statistic.getSlice());
            case "ChargeShot", "TotalChargeShot" -> value(statistic.getChargeShot());
            case "Lob", "TotalLob" -> value(statistic.getLob());
            case "ServiceAce", "TotalServiceAce" -> value(statistic.getServiceAce());
            case "ReturnAce", "TotalReturnAce" -> value(statistic.getReturnAce());
            case "SkillShot", "TotalSkillShot" -> value(statistic.getSkillShot());
            case "GuardBreak", "TotalGuardBreak" -> value(statistic.getGuardBreakShot());
            case "PerfectGame", "TotalPerfectGame" -> value(statistic.getPerfectGames());
            case "Fishes", "TotalFishes" -> value(statistic.getFishesCaught());
            case "Fruits", "TotalFruits" -> value(statistic.getFruitsCollected());
            case "WinCount", "TotalWinCount" -> "BATTLE".equals(definition.getGameMode())
                    ? value(statistic.getBattleRecordWin()) : value(statistic.getBasicRecordWin());
            case "LoseCount", "TotalLoseCount" -> "BATTLE".equals(definition.getGameMode())
                    ? value(statistic.getBattleRecordLoss()) : value(statistic.getBasicRecordLoss());
            default -> 0;
        };
    }

    private static int rewardQuantity(EmblemQuestReward reward) {
        int minimum = Math.max(1, value(reward.getQuantityMin()));
        int maximum = Math.max(minimum, value(reward.getQuantityMax()));
        return minimum == maximum ? minimum : ThreadLocalRandom.current().nextInt(minimum, maximum + 1);
    }

    private static EmblemRewardItem rewardItem(PlayerPocket pocket) {
        EItemCategory category = EItemCategory.valueOf(pocket.getCategory());
        byte useType = "N/A".equals(pocket.getUseType())
                ? 0 : EItemUseType.valueOf(pocket.getUseType().toUpperCase(Locale.ROOT)).getValue();
        return new EmblemRewardItem(
                Math.toIntExact(pocket.getId()), category.getValue(), pocket.getItemIndex(), useType,
                pocket.getItemCount(), pocket.getCreated(),
                byteValue(pocket.getEnchantStr()), byteValue(pocket.getEnchantSta()),
                byteValue(pocket.getEnchantDex()), byteValue(pocket.getEnchantWil()),
                byteValue(pocket.getEnchantElement()), byteValue(pocket.getEnchantLevel())
        );
    }

    private static byte byteValue(Integer value) {
        return (byte) (value == null ? 0 : value);
    }
    private static short cap(int value) { return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value)); }
    private static int value(Integer v) { return v == null ? 0 : v; }
    private static int target(EmblemQuestDefinition d, int i) { try { String s = switch(i){case 1->d.getConditionTarget1();case 2->d.getConditionTarget2();case 3->d.getConditionTarget3();default->d.getConditionTarget4();}; return s == null ? 0 : Integer.parseInt(s); } catch(NumberFormatException e){ return Integer.MAX_VALUE; } }
    private static String condition(EmblemQuestDefinition d,int i){ String value = switch(i){case 1->d.getConditionType1();case 2->d.getConditionType2();case 3->d.getConditionType3();default->d.getConditionType4();}; return value == null || value.isBlank() ? null : value; }
    private static int progress(PlayerEmblemQuest q,int i){ return value(switch(i){case 1->q.getProgress1();case 2->q.getProgress2();case 3->q.getProgress3();default->q.getProgress4();}); }
    private static int baseline(PlayerEmblemQuest q,int i){ return value(switch(i){case 1->q.getBaseline1();case 2->q.getBaseline2();case 3->q.getBaseline3();default->q.getBaseline4();}); }
    private static int wireProgress(PlayerEmblemQuest q,int i){ return baseline(q, i); }
    private static boolean incrementalCondition(String condition){ return switch(condition == null ? "" : condition){case "WinCount","LoseCount","GuardBreak","PerfectGame","Smash","Slice","ChargeShot","Lob","SkillShot","ServiceAce","ReturnAce","Fishes","Fruits","Transmutes","Furniture"->true;default->false;}; }
    private static boolean totalCondition(String condition){ return condition != null && condition.startsWith("Total"); }
    private static void setProgress(PlayerEmblemQuest q,int i,int v){ switch(i){case 1->q.setProgress1(v);case 2->q.setProgress2(v);case 3->q.setProgress3(v);default->q.setProgress4(v);} }
    private static void setBaseline(PlayerEmblemQuest q,int i,int v){ switch(i){case 1->q.setBaseline1(v);case 2->q.setBaseline2(v);case 3->q.setBaseline3(v);default->q.setBaseline4(v);} }
    private static boolean automaticEmblem(int questIndex) {
        return questIndex >= AUTOMATIC_EMBLEM_MIN && questIndex <= AUTOMATIC_EMBLEM_MAX;
    }

    private boolean requiredItemsPresent(Player player, EmblemQuestDefinition definition) {
        for (int slot = 1; slot <= 4; slot++) {
            int productIndex = requiredItem(definition, slot);
            int quantity = requiredQuantity(definition, slot);
            if (productIndex == 0 || quantity <= 0) continue;
            Product product = productService.findProductByProductItemIndex(productIndex);
            if (product == null || product.getCategory() == null || product.getItem0() == null)
                return false;
            long owned = playerPocketRepository.findAllRequiredForUpdate(
                            player.getPocket(), product.getCategory(), product.getItem0()).stream()
                    .map(PlayerPocket::getItemCount)
                    .filter(Objects::nonNull)
                    .mapToLong(count -> Math.max(0, count))
                    .sum();
            if (owned < quantity) return false;
        }
        return true;
    }

    private static int requiredItem(EmblemQuestDefinition definition, int slot) {
        return value(switch (slot) {
            case 1 -> definition.getRequiredItem1();
            case 2 -> definition.getRequiredItem2();
            case 3 -> definition.getRequiredItem3();
            default -> definition.getRequiredItem4();
        });
    }

    private static int requiredQuantity(EmblemQuestDefinition definition, int slot) {
        return value(switch (slot) {
            case 1 -> definition.getRequiredQuantity1();
            case 2 -> definition.getRequiredQuantity2();
            case 3 -> definition.getRequiredQuantity3();
            default -> definition.getRequiredQuantity4();
        });
    }
}
