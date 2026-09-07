package com.jftse.server.core.service;

import com.jftse.entities.database.model.player.Player;

import java.util.List;

public interface EmblemQuestService {
    String TOTAL_SMASH = "TotalSmash";
    String TOTAL_SLICE = "TotalSlice";
    String TOTAL_CHARGE_SHOT = "TotalChargeShot";
    String TOTAL_LOB = "TotalLob";
    String TOTAL_SERVICE_ACE = "TotalServiceAce";
    String TOTAL_RETURN_ACE = "TotalReturnAce";
    String TOTAL_SKILL_SHOT = "TotalSkillShot";
    String TOTAL_WIN_COUNT = "TotalWinCount";
    String TOTAL_LOSE_COUNT = "TotalLoseCount";
    String TOTAL_GUARD_BREAK = "TotalGuardBreak";
    String TOTAL_FISHES = "TotalFishes";
    String TOTAL_FRUITS = "TotalFruits";
    String TUTORIAL = "Tutorial";
    String CHARACTER_LEVEL = "CharacterLevel";

    List<EmblemQuestState> list(long playerId);
    EmblemQuestStatus accept(Player player, int questIndex);
    EmblemQuestStatus abandon(long playerId, int questIndex);
    EmblemCompletionResult complete(Player player, int questIndex);
    EmblemQuestStatus equip(Player player, List<Integer> emblemIds);
    void increment(long playerId, String conditionType, int amount);
    void setBaseline(long playerId, String conditionType, int value);

    void updateMatchTotals(long playerId, int gameMode, boolean won, int serviceAces, int returnAces,
                           int slices, int lobs, int smashes, int guardBreakShots,
                           int chargeShots, int skillShots);
}
