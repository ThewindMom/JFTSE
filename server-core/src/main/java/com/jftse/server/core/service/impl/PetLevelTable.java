package com.jftse.server.core.service.impl;

import com.jftse.emulator.common.utilities.ResourceUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cumulative Battlemon EXP thresholds from the validated client's
 * {@code Res/Script/PubETC/%s/LevelExp_Pet.xml}.
 *
 * <p>Index {@code 0} is displayed level 1 ({@code Value=0}). Reaching
 * {@code thresholds[n]} produces displayed level {@code n + 1}. The table
 * has 250 rows, so the maximum displayed level is 250. Emblem quests
 * {@code BattleMonLevel} 10 / 30 / 50 require this full ladder.
 *
 * <p>This file contains no STR/STA/DEX/WIL/HP grants. Client
 * {@code AI_PetA.ini}…{@code AI_PetK.ini} are a separate 13-band AI lookup
 * keyed as {@code Level%d}; they are not a reason to truncate the EXP table.
 */
final class PetLevelTable {
    static final int MAX_LEVEL = 250;
    private static final Pattern VALUE = Pattern.compile("Value=\"(\\d+)\"");
    private static final int[] THRESHOLDS = load();

    private PetLevelTable() {
    }

    static int levelForExperience(int expPoints) {
        int experience = Math.max(0, expPoints);
        int level = 1;
        for (int index = 1; index < THRESHOLDS.length; index++) {
            if (experience < THRESHOLDS[index]) {
                break;
            }
            level = index + 1;
        }
        return Math.min(level, MAX_LEVEL);
    }

    static byte toStoredLevel(int displayedLevel) {
        return (byte) Math.min(Math.max(displayedLevel, 1), MAX_LEVEL);
    }

    static int experienceBeforeLevel(int displayedLevel) {
        if (displayedLevel <= 1) {
            return 0;
        }
        int thresholdIndex = Math.min(displayedLevel - 1, THRESHOLDS.length - 1);
        return THRESHOLDS[thresholdIndex] - 1;
    }

    private static int[] load() {
        try (InputStream inputStream = ResourceUtil.getResource("res/LevelExp_Pet.xml")) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing res/LevelExp_Pet.xml");
            }
            String xml = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            Matcher matcher = VALUE.matcher(xml);
            List<Integer> values = new ArrayList<>();
            while (matcher.find()) {
                values.add(Integer.valueOf(matcher.group(1)));
            }
            if (values.size() != MAX_LEVEL || values.getFirst() != 0) {
                throw new IllegalStateException("Unexpected LevelExp_Pet table size " + values.size());
            }
            return values.stream().mapToInt(Integer::intValue).toArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load res/LevelExp_Pet.xml", exception);
        }
    }
}
