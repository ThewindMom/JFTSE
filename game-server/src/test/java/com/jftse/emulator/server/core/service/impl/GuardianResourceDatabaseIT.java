package com.jftse.emulator.server.core.service.impl;

import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.matchplay.combat.GuardianCombatSystem;
import com.jftse.emulator.server.core.matchplay.game.MatchplayGuardianGame;
import com.jftse.entities.database.model.battle.BossGuardian;
import com.jftse.entities.database.model.battle.Guardian;
import com.jftse.entities.database.model.battle.GuardianBase;
import com.jftse.entities.database.model.item.ItemEnchantLevel;
import com.jftse.entities.database.model.map.SMaps;
import com.jftse.entities.database.model.scenario.MScenarios;
import com.jftse.server.core.service.EnchantService;
import com.jftse.server.core.service.GuardianSkillsService;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.test.util.ReflectionTestUtils;

import javax.persistence.Entity;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GuardianResourceDatabaseIT {
    @Test
    void importedGuardianAndEnchantInputsKeepActualCreationHealAndDamageWithinSignedHpWidth() throws Exception {
        String url = System.getenv("JFTSE_AUDIT_JDBC_URL");
        assertNotNull(url);
        assertTrue(url.matches("jdbc:mysql://[^/]+/jftse_server_audit_resources(?:\\?.*)?"));
        Configuration configuration = new Configuration()
                .setProperty("hibernate.connection.url", url)
                .setProperty("hibernate.connection.username", System.getenv("JFTSE_AUDIT_JDBC_USER"))
                .setProperty("hibernate.connection.password", System.getenv("JFTSE_AUDIT_JDBC_PASSWORD"))
                .setProperty("hibernate.dialect", "org.hibernate.dialect.MySQL8Dialect");
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        for (var candidate : scanner.findCandidateComponents("com.jftse.entities.database.model"))
            configuration.addAnnotatedClass(Class.forName(candidate.getBeanClassName()));
        Object previousServices = ReflectionTestUtils.getField(ServiceManager.class, "instance");
        Object previousManager = ReflectionTestUtils.getField(com.jftse.emulator.server.core.manager.GameManager.class, "instance");
        Object previousConfig = ((java.util.concurrent.atomic.AtomicReference<?>) ReflectionTestUtils.getField(
                com.jftse.emulator.server.core.utils.BattleUtils.class, "statConfig")).get();
        try (var factory = configuration.buildSessionFactory(); var session = factory.openSession()) {
            session.setDefaultReadOnly(true);
            List<GuardianBase> guardians = new ArrayList<>(session.createQuery("from Guardian", Guardian.class).list());
            guardians.addAll(session.createQuery("from BossGuardian", BossGuardian.class).list());
            List<ItemEnchantLevel> enchants = session.createQuery("from ItemEnchantLevel", ItemEnchantLevel.class).list();
            var skills = session.createQuery("from Skill", com.jftse.entities.database.model.battle.Skill.class).list();
            assertEquals(65, skills.size());
            var manager = mock(com.jftse.emulator.server.core.manager.GameManager.class);
            var config = mock(com.jftse.server.core.shared.ServerConfService.class);
            when(manager.getServerConfService()).thenReturn(config);
            when(config.get("StrengthDamageScale", Double.class)).thenReturn(0.35);
            when(config.get("StaminaDamageReductionScale", Double.class)).thenReturn(0.30);
            when(config.get("WillpowerBallDamageScale", Double.class)).thenReturn(0.52);
            when(config.get("BallBaseDamage", Integer.class)).thenReturn(10);
            when(config.get("BallMinDamage", Integer.class)).thenReturn(20);
            ReflectionTestUtils.setField(com.jftse.emulator.server.core.manager.GameManager.class, "instance", manager);
            com.jftse.emulator.server.core.utils.BattleUtils.reloadStatConfig();
            assertEquals(79, guardians.size());
            assertEquals(40, enchants.size());
            ServiceManager services = mock(ServiceManager.class);
            EnchantService enchantService = mock(EnchantService.class);
            when(services.getEnchantService()).thenReturn(enchantService);
            when(enchantService.getItemEnchantLevel(anyString(), anyInt())).thenAnswer(invocation -> enchants.stream()
                    .filter(row -> row.getElementalKind().equals(invocation.getArgument(0)) &&
                            row.getGrade().equals(invocation.getArgument(1))).findFirst().orElseThrow());
            when(services.getGuardianSkillsService()).thenReturn(mock(GuardianSkillsService.class));
            ReflectionTestUtils.setField(ServiceManager.class, "instance", services);
            MatchplayGuardianGame game = mock(MatchplayGuardianGame.class);
            when(game.createGuardianBattleState(anyBoolean(), any(), anyShort(), anyInt())).thenCallRealMethod();
            when(game.getGuardianHealPercentage()).thenReturn((short) 5);
            SMaps map = new SMaps();
            map.setId(11L);
            MScenarios scenario = new MScenarios();
            scenario.setId(3L);
            ReflectionTestUtils.setField(game, "map", map);
            ReflectionTestUtils.setField(game, "scenario", scenario);
            GuardianCombatSystem combat = new GuardianCombatSystem(game);
            int maximumHp = 0;
            for (GuardianBase guardian : guardians) {
                for (boolean advanced : List.of(false, true)) {
                    ReflectionTestUtils.setField(game, "isAdvancedBossGuardianMode", advanced);
                    for (boolean hard : List.of(false, true)) {
                        for (int players = 1; players <= 4; players++) {
                            var state = game.createGuardianBattleState(hard, guardian, (short) 10, players);
                            long expected = hard ? 8000L : guardian.getHpBase().longValue() + guardian.getHpPer().longValue() * players;
                            if (advanced && players == 4) expected = expected * 3 / 2;
                            assertEquals(expected, state.getMaxHealth());
                            assertTrue(expected > 0 && expected <= Short.MAX_VALUE);
                            maximumHp = Math.max(maximumHp, state.getMaxHealth());
                            assertEquals(hard || advanced ? 110 : guardian.getBaseStr() + guardian.getAddStr() * players, state.getStr());
                            assertEquals(hard || advanced ? 45 : guardian.getBaseSta() + guardian.getAddSta() * players, state.getSta());
                            when(game.getGuardianBattleStates()).thenReturn(new ConcurrentLinkedDeque<>(List.of(state)));
                            assertEquals(state.getMaxHealth(), combat.heal(10, (short) 100));
                            state.getCurrentHealth().set(1);
                            assertEquals(0, combat.updateHealthByDamage(state, -1));
                            assertEquals((int) (state.getMaxHealth() * 0.05f), combat.heal(10, (short) 100));
                            if (players == 4 && !hard && !advanced) {
                                var participant = new com.jftse.server.core.matchplay.battle.PlayerBattleState(
                                        (short) 0, 1L, 2131, 110, 45, 0, 0);
                                when(game.getPlayerBattleStates()).thenReturn(new ConcurrentLinkedDeque<>(List.of(participant)));
                                for (var skill : skills) {
                                    assertTrue(skill.getDamage() >= Short.MIN_VALUE && skill.getDamage() <= Short.MAX_VALUE);
                                    if (skill.getDamage() >= 0) continue;
                                    for (boolean buff : List.of(false, true)) {
                                        state.getCurrentHealth().set(state.getMaxHealth());
                                        participant.getCurrentHealth().set(participant.getMaxHealth());
                                        short enemyHp = combat.dealDamage(0, 10, skill.getDamage().shortValue(), buff, buff, skill);
                                        short playerHp = combat.dealDamageToPlayer(10, 0, skill.getDamage().shortValue(), buff, buff, skill);
                                        assertTrue(enemyHp >= 0 && enemyHp <= state.getMaxHealth(), "resource negative skill cannot heal guardian");
                                        assertTrue(playerHp >= 0 && playerHp <= participant.getMaxHealth(), "resource negative skill cannot heal player");
                                        assertEquals(enemyHp, state.getCurrentHealth().get());
                                        assertEquals(playerHp, participant.getCurrentHealth().get());
                                    }
                                }
                            }
                        }
                    }
                }
            }
            assertEquals(24900, maximumHp, "Conservative79-resource ×4-player ×normal/hard ×ordinary/advanced envelope");
            var thresholds = session.createQuery("from LevelExp order by level", com.jftse.entities.database.model.level.LevelExp.class).list();
            assertEquals(60, thresholds.size());
            var levels = mock(com.jftse.entities.database.repository.level.LevelExpRepository.class);
            when(levels.findAllByLevel(anyByte())).thenAnswer(invocation -> thresholds.stream()
                    .filter(row -> row.getLevel().equals(invocation.getArgument(0))).toList());
            when(levels.findAllByExpValueIsLessThanEqualOrderByExpValueDesc(anyInt())).thenAnswer(invocation -> thresholds.stream()
                    .filter(row -> row.getExpValue() <= (Integer) invocation.getArgument(0))
                    .sorted(java.util.Comparator.comparing(com.jftse.entities.database.model.level.LevelExp::getExpValue).reversed()).toList());
            var levelConfig = mock(com.jftse.emulator.common.service.ConfigService.class);
            when(levelConfig.getValue("player.level.max", 60)).thenReturn(60);
            var levelService = new com.jftse.server.core.service.impl.LevelServiceImpl(levels, null, levelConfig);
            for (var threshold : thresholds) {
                if (threshold.getLevel() < 1) continue;
                byte level = threshold.getLevel();
                assertEquals(level, levelService.getLevel(0, threshold.getExpValue() - 1, level));
                assertEquals(level + 1, levelService.getLevel(1, threshold.getExpValue() - 1, level));
                assertEquals(level + 1, levelService.getLevel(2, threshold.getExpValue() - 1, level));
            }
            assertEquals(60, levelService.getLevel(259937, 0, (byte) 1));
            assertEquals(60, levelService.getLevel(100, 259937, (byte) 60));
            int maxItemHp = session.createQuery("select max(i.addHp) from ItemPart i", Byte.class).uniqueResult();
            assertEquals(100, maxItemHp);
            var slots = new com.jftse.emulator.server.core.client.EquippedItemParts(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            assertEquals(12, slots.toList().size());
            var stats = new com.jftse.entities.database.model.player.EquippedItemStats();
            stats.setAddHp(maxItemHp * slots.toList().size());
            var player = mock(com.jftse.emulator.server.core.life.room.RoomPlayer.class);
            var partner = mock(com.jftse.emulator.server.core.life.room.RoomPlayer.class);
            when(player.getEquippedItemStats()).thenReturn(stats);
            when(player.getEquippedItemParts()).thenReturn(slots);
            when(player.getCoupleId()).thenReturn(2L);
            when(partner.getPlayerId()).thenReturn(2L);
            when(services.getPocketService()).thenReturn(mock(com.jftse.server.core.service.PocketService.class));
            when(services.getPlayerPocketService()).thenReturn(mock(com.jftse.server.core.service.PlayerPocketService.class));
            when(game.createPlayerBattleState(any(), any())).thenCallRealMethod();
            var battle = mock(com.jftse.emulator.server.core.matchplay.game.MatchplayBattleGame.class);
            when(battle.createPlayerBattleState(any())).thenCallRealMethod();
            for (int level : List.of(1, 60, 127)) {
                when(player.getLevel()).thenReturn(level);
                int expected = 200 + 5 * (level - 1) + 1200;
                assertEquals(expected, battle.createPlayerBattleState(player).getMaxHealth());
                var guardianPlayer = game.createPlayerBattleState(player, List.of(player, partner));
                assertEquals(expected + expected / 20, guardianPlayer.getMaxHealth());
                assertTrue(guardianPlayer.getMaxHealth() <= 2131, "Includes signed player-level storage envelope and couple bonus");
                when(game.getPlayerBattleStates()).thenReturn(new ConcurrentLinkedDeque<>(List.of(guardianPlayer)));
                assertEquals(guardianPlayer.getMaxHealth(), new com.jftse.emulator.server.core.matchplay.combat.PlayerCombatSystem(game)
                        .heal(guardianPlayer.getPosition(), (short) 100));
            }
            var pets = mock(com.jftse.entities.database.repository.pet.PetRepository.class);
            var petStats = mock(com.jftse.entities.database.repository.pet.PetStatisticRepository.class);
            when(petStats.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(pets.save(any())).thenAnswer(invocation -> {
                com.jftse.entities.database.model.pet.Pet pet = invocation.getArgument(0);
                pet.setId(1L);
                return pet;
            });
            var petService = new com.jftse.server.core.service.impl.PetServiceImpl(pets, petStats);
            var owner = new com.jftse.entities.database.model.player.Player();
            owner.setId(1L);
            for (int item = 1; item <= 9; item++) {
                var pet = petService.createPet(item, owner);
                int hp = pet.getHp();
                assertTrue(hp >= 180 && hp <= 280);
                when(pets.findByIdAndPlayerIdForUpdate(1L, 1L)).thenReturn(java.util.Optional.of(pet));
                petService.awardExperience(1L, 1L, Integer.MAX_VALUE);
                assertEquals(250, pet.getLevel());
                assertEquals(hp, pet.getHp(), "Current pet EXP ladder does not grant HP");
                var actor = new com.jftse.emulator.server.core.life.room.GameplayActor((short) 2, (short) 0, 1L,
                        com.jftse.emulator.server.core.client.PetView.of(pet), 0, 0, 0, 0, 0);
                when(game.createOwnedPetBattleState(actor)).thenCallRealMethod();
                when(battle.createOwnedPetBattleState(actor)).thenCallRealMethod();
                assertEquals(hp, game.createOwnedPetBattleState(actor).getMaxHealth());
                assertEquals(hp, battle.createOwnedPetBattleState(actor).getMaxHealth());
            }
        } finally {
            ReflectionTestUtils.setField(ServiceManager.class, "instance", previousServices);
            ReflectionTestUtils.setField(com.jftse.emulator.server.core.manager.GameManager.class, "instance", previousManager);
            ReflectionTestUtils.invokeMethod(ReflectionTestUtils.getField(
                    com.jftse.emulator.server.core.utils.BattleUtils.class, "statConfig"), "set", previousConfig);
        }
    }
}
