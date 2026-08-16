package com.jftse.emulator.server.core.handler.matchplay;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.matchplay.combat.GuardianCombatSystem;
import com.jftse.emulator.server.core.matchplay.combat.PlayerCombatSystem;
import com.jftse.emulator.server.core.matchplay.event.EventHandler;
import com.jftse.emulator.server.core.matchplay.event.RunnableEvent;
import com.jftse.emulator.server.core.matchplay.game.MatchplayBattleGame;
import com.jftse.emulator.server.core.matchplay.game.MatchplayGuardianGame;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.battle.Skill;
import com.jftse.entities.database.model.map.SMaps;
import com.jftse.server.core.matchplay.battle.GuardianBattleState;
import com.jftse.server.core.matchplay.battle.PlayerBattleState;
import com.jftse.server.core.matchplay.battle.SkillCrystal;
import com.jftse.server.core.service.SkillDropRateService;
import com.jftse.server.core.service.SkillService;
import com.jftse.server.core.shared.packets.matchplay.CMSGPlayerPickupCrystal;
import com.jftse.server.core.shared.packets.matchplay.CMSGPlayerUseSkill;
import com.jftse.server.core.shared.packets.matchplay.CMSGSpellHitsTarget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;

class BattlemonActorPolicyTest {
    private Object previousGameManager;
    private Object previousServiceManager;
    private SkillService skillService;

    @BeforeEach
    void setUpManagers() {
        previousGameManager = ReflectionTestUtils.getField(GameManager.class, "instance");
        previousServiceManager = ReflectionTestUtils.getField(ServiceManager.class, "instance");

        GameManager gameManager = mock(GameManager.class);
        ServiceManager serviceManager = mock(ServiceManager.class);
        skillService = mock(SkillService.class);
        EventHandler eventHandler = mock(EventHandler.class);
        when(gameManager.getEventHandler()).thenReturn(eventHandler);
        when(eventHandler.createRunnableEvent(any(), anyLong())).thenReturn(mock(RunnableEvent.class));
        when(serviceManager.getSkillService()).thenReturn(skillService);
        when(serviceManager.getSkillDropRateService()).thenReturn(mock(SkillDropRateService.class));

        ReflectionTestUtils.setField(GameManager.class, "instance", gameManager);
        ReflectionTestUtils.setField(ServiceManager.class, "instance", serviceManager);
    }

    @AfterEach
    void restoreManagers() {
        ReflectionTestUtils.setField(GameManager.class, "instance", previousGameManager);
        ReflectionTestUtils.setField(ServiceManager.class, "instance", previousServiceManager);
    }

    @Test
    void crystalPickupUsesAuthenticatedRoomPositionInsteadOfUnreliablePacketPosition() {
        BattleContext context = battleContext((short) 2, true);
        SkillCrystal crystal = new SkillCrystal(17);
        context.game().getSkillCrystals().add(crystal);
        Queue<SkillCrystal> ownerCrystals = new ConcurrentLinkedDeque<>();
        when(context.roomPlayer().getPickedUpSkillCrystals()).thenReturn(ownerCrystals);

        CMSGPlayerPickupCrystal packet = CMSGPlayerPickupCrystal.builder()
                .playerPosition((byte) 2)
                .crystalId((short) 17)
                .build();
        new PlayerPickingUpCrystalHandler().handle(context.connection(), packet);

        assertTrue(context.game().getSkillCrystals().isEmpty());
        assertTrue(ownerCrystals.contains(crystal));
    }

    @Test
    void petQuickSlotAndCrystalSkillUseFailClosedBeforeInventoryLookup() {
        BattleContext context = battleContext((short) 2, true);
        CMSGPlayerUseSkill quickSlotPacket = CMSGPlayerUseSkill.builder()
                .attackerPosition((byte) 2)
                .targetPosition((byte) 1)
                .isQuickSlot(true)
                .skillIndex((byte) 1)
                .build();
        CMSGPlayerUseSkill crystalPacket = CMSGPlayerUseSkill.builder()
                .attackerPosition((byte) 2)
                .targetPosition((byte) 1)
                .isQuickSlot(false)
                .skillIndex((byte) 1)
                .build();

        PlayerUseSkillHandler handler = new PlayerUseSkillHandler();
        handler.handle(context.connection(), quickSlotPacket);
        handler.handle(context.connection(), crystalPacket);

        verify(skillService, never()).findSkillByIndex(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void ownerCannotUseOpponentActor() {
        BattleContext context = battleContext((short) 1, false);
        CMSGPlayerUseSkill packet = CMSGPlayerUseSkill.builder()
                .attackerPosition((byte) 1)
                .targetPosition((byte) 0)
                .isQuickSlot(true)
                .skillIndex((byte) 1)
                .build();

        new PlayerUseSkillHandler().handle(context.connection(), packet);

        verify(skillService, never()).findSkillByIndex(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void ownerCannotApplySpellDamageAsOpponentActor() {
        BattleContext context = battleContext((short) 1, false);
        CMSGSpellHitsTarget packet = CMSGSpellHitsTarget.builder()
                .attackerPosition((short) 1)
                .targetPosition((short) 0)
                .skillId((byte) 6)
                .build();

        new SpellHitsTargetHandler().handle(context.connection(), packet);

        verify(skillService, never()).findSkillById(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void battlemonReviveRestoresADeadTeamActor() throws Exception {
        BattleContext context = battleContext((short) 0, true);
        Skill revive = new Skill();
        revive.setId(5L);
        revive.setDamage(50);
        when(skillService.findSkillById(5L)).thenReturn(revive);
        PlayerCombatSystem combatSystem = mock(PlayerCombatSystem.class);
        when(context.game().getPlayerCombatSystem()).thenReturn(combatSystem);
        PlayerBattleState revivedPet = context.game().getPlayerBattleStates().stream()
                .filter(state -> state.getPosition() == 2)
                .findFirst()
                .orElseThrow();
        when(combatSystem.reviveAnyPlayer((short) 50, 0)).thenReturn(revivedPet);

        CMSGSpellHitsTarget packet = CMSGSpellHitsTarget.builder()
                .attackerPosition((short) 0)
                .targetPosition((short) 2)
                .skillId((byte) 5)
                .build();
        new SpellHitsTargetHandler().handle(context.connection(), packet);

        verify(skillService).findSkillById(5L);
        verify(combatSystem).reviveAnyPlayer((short) 50, 0);
    }

    @Test
    void petOriginatedSpellDamagesBattlemonTarget() throws Exception {
        BattleContext context = battleContext((short) 2, true);
        Skill skill = new Skill();
        skill.setId(6L);
        skill.setDamage(-10);
        when(skillService.findSkillById(6L)).thenReturn(skill);
        PlayerCombatSystem combatSystem = mock(PlayerCombatSystem.class);
        when(context.game().getPlayerCombatSystem()).thenReturn(combatSystem);
        when(combatSystem.dealDamage(2, 3, (short) -10, false, false, skill)).thenReturn((short) 90);
        CMSGSpellHitsTarget packet = CMSGSpellHitsTarget.builder()
                .attackerPosition((short) 2)
                .targetPosition((short) 3)
                .skillId((byte) 6)
                .attackerBuffId1((byte) -1)
                .attackerBuffId2((byte) -1)
                .receiverBuffId1((byte) -1)
                .receiverBuffId2((byte) -1)
                .build();

        new SpellHitsTargetHandler().handle(context.connection(), packet);

        verify(combatSystem).dealDamage(2, 3, (short) -10, false, false, skill);
    }

    @Test
    void guardianHealAppliesToBattlemonTarget() throws Exception {
        BattleContext context = battleContext((short) 0, true);
        Skill heal = new Skill();
        heal.setId(2L);
        heal.setDamage(15);
        when(skillService.findSkillById(2L)).thenReturn(heal);
        PlayerCombatSystem combatSystem = mock(PlayerCombatSystem.class);
        when(context.game().getPlayerCombatSystem()).thenReturn(combatSystem);
        when(combatSystem.heal(2, (short) 15)).thenReturn((short) 100);
        CMSGSpellHitsTarget packet = CMSGSpellHitsTarget.builder()
                .attackerPosition((short) 4)
                .targetPosition((short) 2)
                .skillId((byte) 2)
                .build();

        new SpellHitsTargetHandler().handle(context.connection(), packet);

        verify(combatSystem).heal(2, (short) 15);
    }

    @Test
    void guardianShieldEffectAppliesToBattlemonTarget() throws Exception {
        BattleContext context = battleContext((short) 0, true);
        Skill shield = new Skill();
        shield.setId(10L);
        shield.setDamage(1);
        when(skillService.findSkillById(10L)).thenReturn(shield);
        PlayerCombatSystem combatSystem = mock(PlayerCombatSystem.class);
        when(context.game().getPlayerCombatSystem()).thenReturn(combatSystem);
        when(combatSystem.dealDamage(4, 2, (short) 1, false, false, shield)).thenReturn((short) 100);
        CMSGSpellHitsTarget packet = CMSGSpellHitsTarget.builder()
                .attackerPosition((short) 4)
                .targetPosition((short) 2)
                .skillId((byte) 10)
                .attackerBuffId1((byte) -1)
                .attackerBuffId2((byte) -1)
                .receiverBuffId1((byte) -1)
                .receiverBuffId2((byte) -1)
                .build();

        new SpellHitsTargetHandler().handle(context.connection(), packet);

        verify(combatSystem).dealDamage(4, 2, (short) 1, false, false, shield);
    }

    @Test
    void petOriginatedBallLossUsesBattleCombatGeometry() throws Exception {
        BattleContext context = battleContext((short) 2, true);
        PlayerCombatSystem combatSystem = mock(PlayerCombatSystem.class);
        when(context.game().getPlayerCombatSystem()).thenReturn(combatSystem);
        when(combatSystem.dealDamageOnBallLoss(2, 1, false)).thenReturn((short) 99);
        CMSGSpellHitsTarget packet = CMSGSpellHitsTarget.builder()
                .attackerPosition((short) 2)
                .targetPosition((short) 1)
                .skillId((byte) 0)
                .damageType((byte) 0)
                .build();

        new SpellHitsTargetHandler().handle(context.connection(), packet);

        verify(combatSystem).dealDamageOnBallLoss(2, 1, false);
    }

    @Test
    void guardianServeSentinelFromGameplayEndpointUsesBattleCombatGeometry() throws Exception {
        BattleContext context = battleContext((short) 0, false);
        FTClient client = context.connection().getClient();
        when(client.getActiveGameSession().isGameplayEndpoint(client)).thenReturn(true);
        PlayerCombatSystem combatSystem = mock(PlayerCombatSystem.class);
        when(context.game().getPlayerCombatSystem()).thenReturn(combatSystem);
        when(combatSystem.dealDamageOnBallLoss(4, 1, false)).thenReturn((short) 99);
        CMSGSpellHitsTarget packet = CMSGSpellHitsTarget.builder()
                .attackerPosition((short) 4)
                .targetPosition((short) 1)
                .skillId((byte) 0)
                .damageType((byte) 0)
                .build();

        new SpellHitsTargetHandler().handle(context.connection(), packet);

        verify(combatSystem).dealDamageOnBallLoss(4, 1, false);
    }

    @Test
    void guardianServeSentinelFromNonGameplayEndpointFailsClosed() {
        BattleContext context = battleContext((short) 0, false);
        CMSGSpellHitsTarget packet = CMSGSpellHitsTarget.builder()
                .attackerPosition((short) 4)
                .targetPosition((short) 1)
                .skillId((byte) 0)
                .damageType((byte) 0)
                .build();

        new SpellHitsTargetHandler().handle(context.connection(), packet);

        verify(context.game(), never()).getPlayerCombatSystem();
    }

    @Test
    void guardianModeRejectsAPlayerActorNotOwnedByTheReportingEndpoint() {
        GuardianContext context = guardianContext((short) 1, false);
        when(context.session().hasOwnedPetSeats()).thenReturn(false);
        CMSGPlayerUseSkill packet = CMSGPlayerUseSkill.builder()
                .attackerPosition((byte) 1)
                .targetPosition((byte) 10)
                .isQuickSlot(true)
                .skillIndex((byte) 1)
                .build();

        new PlayerUseSkillHandler().handle(context.connection(), packet);

        verify(skillService, never()).findSkillByIndex(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void guardianModeRejectsNonexistentGuardianAttackersAndTargets() {
        GuardianContext context = guardianContext((short) 0, true);
        CMSGPlayerUseSkill nonexistentAttacker = CMSGPlayerUseSkill.builder()
                .attackerPosition((byte) 12)
                .targetPosition((byte) 0)
                .skillIndex((byte) 1)
                .build();
        CMSGSpellHitsTarget nonexistentTarget = CMSGSpellHitsTarget.builder()
                .attackerPosition((short) 0)
                .targetPosition((short) 12)
                .skillId((byte) 1)
                .build();

        new PlayerUseSkillHandler().handle(context.connection(), nonexistentAttacker);
        new SpellHitsTargetHandler().handle(context.connection(), nonexistentTarget);

        verify(skillService, never()).findSkillByIndex(org.mockito.ArgumentMatchers.anyInt());
        verify(skillService, never()).findSkillById(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void guardianCastRequiresTheMasterAndAServerGrant() {
        GuardianContext context = guardianContext((short) 0, true);
        Skill skill = new Skill();
        skill.setId(9L);
        skill.setDamage(-1);
        when(skillService.findSkillByIndex(8)).thenReturn(skill);
        CMSGPlayerUseSkill packet = CMSGPlayerUseSkill.builder()
                .attackerPosition((byte) 10)
                .targetPosition((byte) 0)
                .skillIndex((byte) 8)
                .build();

        new PlayerUseSkillHandler().handle(context.connection(), packet);
        verify(skillService, never()).findSkillByIndex(8);

        when(context.roomPlayer().isMaster()).thenReturn(true);
        new PlayerUseSkillHandler().handle(context.connection(), packet);
        verify(context.session(), never()).authorizeSkillHits(
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyLong());

        when(context.session().tryConsumeSkillCast(org.mockito.ArgumentMatchers.eq(10),
                org.mockito.ArgumentMatchers.eq(8),
                org.mockito.ArgumentMatchers.anyLong())).thenReturn(true);
        new PlayerUseSkillHandler().handle(context.connection(), packet);
        verify(context.session()).authorizeSkillHits(
                org.mockito.ArgumentMatchers.eq(10), org.mockito.ArgumentMatchers.eq(-1),
                org.mockito.ArgumentMatchers.eq(9), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void guardianHostCanReportSecondPlayerBallDamage() throws Exception {
        GuardianContext context = guardianContext((short) 1, false);
        GuardianCombatSystem combatSystem = mock(GuardianCombatSystem.class);
        PlayerCombatSystem playerCombatSystem = mock(PlayerCombatSystem.class);
        when(context.roomPlayer().isMaster()).thenReturn(true);
        when(context.game().getGuardianCombatSystem()).thenReturn(combatSystem);
        when(context.game().getPlayerCombatSystem()).thenReturn(playerCombatSystem);
        when(combatSystem.dealDamageOnBallLoss(1, 10, false)).thenReturn((short) 99);
        CMSGSpellHitsTarget packet = CMSGSpellHitsTarget.builder()
                .attackerPosition((short) 1)
                .targetPosition((short) 10)
                .skillId((byte) 0)
                .damageType((byte) 0)
                .applySkillEffect((byte) 0)
                .build();

        new SpellHitsTargetHandler().handle(context.connection(), packet);

        verify(combatSystem).dealDamageOnBallLoss(1, 10, false);
    }

    @Test
    void nonHostGuardianEndpointCannotReportAnotherPlayersBallDamage() throws Exception {
        GuardianContext context = guardianContext((short) 1, false);
        GuardianCombatSystem combatSystem = mock(GuardianCombatSystem.class);
        when(context.game().getGuardianCombatSystem()).thenReturn(combatSystem);
        CMSGSpellHitsTarget packet = CMSGSpellHitsTarget.builder()
                .attackerPosition((short) 1)
                .targetPosition((short) 10)
                .skillId((byte) 0)
                .damageType((byte) 0)
                .applySkillEffect((byte) 0)
                .build();

        new SpellHitsTargetHandler().handle(context.connection(), packet);

        verify(combatSystem, never()).dealDamageOnBallLoss(1, 10, false);
    }

    @Test
    void guardianHostOutsideGameplayEndpointCannotReportSecondPlayerBallDamage() throws Exception {
        GuardianContext context = guardianContext((short) 1, false);
        GuardianCombatSystem combatSystem = mock(GuardianCombatSystem.class);
        FTClient client = context.connection().getClient();
        when(context.roomPlayer().isMaster()).thenReturn(true);
        when(context.session().isGameplayEndpoint(client)).thenReturn(false);
        when(context.game().getGuardianCombatSystem()).thenReturn(combatSystem);
        CMSGSpellHitsTarget packet = CMSGSpellHitsTarget.builder()
                .attackerPosition((short) 1)
                .targetPosition((short) 10)
                .skillId((byte) 0)
                .damageType((byte) 0)
                .applySkillEffect((byte) 0)
                .build();

        new SpellHitsTargetHandler().handle(context.connection(), packet);

        verify(combatSystem, never()).dealDamageOnBallLoss(1, 10, false);
    }

    @Test
    void nonHostGuardianEndpointCannotReportGuardianHitAgainstAnotherPlayer() {
        GuardianContext context = guardianContext((short) 0, true);
        when(context.session().tryConsumeSkillHit(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyLong())).thenReturn(true);
        CMSGSpellHitsTarget packet = CMSGSpellHitsTarget.builder()
                .attackerPosition((short) 10)
                .targetPosition((short) 1)
                .skillId((byte) 9)
                .build();

        new SpellHitsTargetHandler().handle(context.connection(), packet);

        verify(skillService, never()).findSkillById(9L);
    }

    @Test
    void guardianHostCanReportGuardianHitAgainstSecondPlayer() {
        GuardianContext context = guardianContext((short) 0, true);
        when(context.roomPlayer().isMaster()).thenReturn(true);
        when(context.session().tryConsumeSkillHit(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyLong())).thenReturn(true);
        CMSGSpellHitsTarget packet = CMSGSpellHitsTarget.builder()
                .attackerPosition((short) 10)
                .targetPosition((short) 1)
                .skillId((byte) 9)
                .build();

        new SpellHitsTargetHandler().handle(context.connection(), packet);

        verify(skillService).findSkillById(9L);
    }

    @Test
    void guardianHostAuthorizedSpellDamagesGuardianForSecondPlayer() throws Exception {
        GuardianContext context = guardianContext((short) 1, false);
        GuardianCombatSystem combatSystem = mock(GuardianCombatSystem.class);
        Skill skill = new Skill();
        skill.setId(9L);
        skill.setDamage(-5);
        when(context.roomPlayer().isMaster()).thenReturn(true);
        when(context.game().getGuardianCombatSystem()).thenReturn(combatSystem);
        when(context.session().tryConsumeSkillHit(org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq(10), org.mockito.ArgumentMatchers.eq(9),
                org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(true);
        when(skillService.findSkillById(9L)).thenReturn(skill);
        when(combatSystem.dealDamage(1, 10, (short) -5, true, false, skill)).thenReturn((short) 95);
        CMSGSpellHitsTarget packet = CMSGSpellHitsTarget.builder()
                .attackerPosition((short) 1)
                .targetPosition((short) 10)
                .skillId((byte) 9)
                .applySkillEffect((byte) 0)
                .build();

        new SpellHitsTargetHandler().handle(context.connection(), packet);

        verify(combatSystem).dealDamage(1, 10, (short) -5, true, false, skill);
    }

    @Test
    void guardianHostAuthorizedHealAndShieldReachSecondPlayer() throws Exception {
        GuardianContext context = guardianContext((short) 1, false);
        PlayerCombatSystem combatSystem = mock(PlayerCombatSystem.class);
        GuardianCombatSystem guardianCombatSystem = mock(GuardianCombatSystem.class);
        Skill heal = new Skill();
        heal.setId(9L);
        heal.setDamage(10);
        Skill shield = new Skill();
        shield.setId(10L);
        shield.setDamage(1);
        when(context.roomPlayer().isMaster()).thenReturn(true);
        when(context.game().getPlayerCombatSystem()).thenReturn(combatSystem);
        when(context.game().getGuardianCombatSystem()).thenReturn(guardianCombatSystem);
        when(context.session().tryConsumeSkillHit(org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.eq(9),
                org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(true);
        when(context.session().tryConsumeSkillHit(org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.eq(10),
                org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(true);
        when(skillService.findSkillById(9L)).thenReturn(heal);
        when(skillService.findSkillById(10L)).thenReturn(shield);
        when(combatSystem.heal(1, (short) 10)).thenReturn((short) 100);
        when(guardianCombatSystem.dealDamageToPlayer(
                1, 1, (short) 1, true, false, shield)).thenReturn((short) 100);
        CMSGSpellHitsTarget healPacket = CMSGSpellHitsTarget.builder()
                .attackerPosition((short) 1)
                .targetPosition((short) 1)
                .skillId((byte) 9)
                .applySkillEffect((byte) 0)
                .build();
        CMSGSpellHitsTarget shieldPacket = CMSGSpellHitsTarget.builder()
                .attackerPosition((short) 1)
                .targetPosition((short) 1)
                .skillId((byte) 10)
                .applySkillEffect((byte) 0)
                .build();

        SpellHitsTargetHandler handler = new SpellHitsTargetHandler();
        handler.handle(context.connection(), healPacket);
        handler.handle(context.connection(), shieldPacket);

        verify(combatSystem).heal(1, (short) 10);
        verify(guardianCombatSystem).dealDamageToPlayer(
                1, 1, (short) 1, true, false, shield);
    }

    @Test
    void guardianHostNonzeroHitWithoutGrantIsRejected() throws Exception {
        GuardianContext context = guardianContext((short) 1, false);
        PlayerCombatSystem combatSystem = mock(PlayerCombatSystem.class);
        Skill heal = new Skill();
        heal.setId(9L);
        heal.setDamage(10);
        when(context.roomPlayer().isMaster()).thenReturn(true);
        when(context.game().getPlayerCombatSystem()).thenReturn(combatSystem);
        when(skillService.findSkillById(9L)).thenReturn(heal);
        CMSGSpellHitsTarget packet = CMSGSpellHitsTarget.builder()
                .attackerPosition((short) 1)
                .targetPosition((short) 1)
                .skillId((byte) 9)
                .applySkillEffect((byte) 0)
                .build();

        new SpellHitsTargetHandler().handle(context.connection(), packet);

        verify(combatSystem, never()).heal(1, (short) 10);
    }

    @Test
    void nonHostGuardianEndpointCannotReportAnotherPlayersSpell() {
        GuardianContext context = guardianContext((short) 1, false);
        CMSGSpellHitsTarget packet = CMSGSpellHitsTarget.builder()
                .attackerPosition((short) 1)
                .targetPosition((short) 10)
                .skillId((byte) 9)
                .applySkillEffect((byte) 0)
                .build();

        new SpellHitsTargetHandler().handle(context.connection(), packet);

        verify(skillService, never()).findSkillById(9L);
    }

    private static BattleContext battleContext(short actorPosition, boolean actorOwned) {
        FTPlayer player = mock(FTPlayer.class);
        Room room = mock(Room.class);
        RoomPlayer roomPlayer = mock(RoomPlayer.class);
        when(roomPlayer.getPosition()).thenReturn((short) 0);

        MatchplayBattleGame game = mock(MatchplayBattleGame.class);
        ConcurrentLinkedDeque<PlayerBattleState> states = new ConcurrentLinkedDeque<>();
        for (short position = 0; position < 4; position++) {
            states.add(new PlayerBattleState(position, 100L + position, 100, 10, 10, 10, 10));
        }
        when(game.getPlayerBattleStates()).thenReturn(states);
        when(game.getSkillCrystals()).thenReturn(new ConcurrentLinkedDeque<>());
        when(game.getCrystalSpawnInterval()).thenReturn(new AtomicLong(1));

        GameSession session = mock(GameSession.class);
        when(session.getMatchplayGame()).thenReturn(game);
        when(session.isDedicatedBattlemonRoom()).thenReturn(true);
        when(session.isActorOwnedBy(roomPlayer, actorPosition)).thenReturn(actorOwned);
        when(session.tryConsumeSkillHit(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyLong())).thenReturn(true);
        when(session.getFireables()).thenReturn(new ConcurrentLinkedDeque<>());

        FTClient client = mock(FTClient.class);
        when(client.hasPlayer()).thenReturn(true);
        when(client.getPlayer()).thenReturn(player);
        when(client.getActiveRoom()).thenReturn(room);
        when(client.getRoomPlayer()).thenReturn(roomPlayer);
        when(client.getActiveGameSession()).thenReturn(session);
        when(session.isGameplayEndpoint(client)).thenReturn(actorOwned);

        FTConnection connection = mock(FTConnection.class);
        when(connection.getClient()).thenReturn(client);
        when(client.getConnection()).thenReturn(connection);
        when(session.getClients()).thenReturn(new ConcurrentLinkedDeque<>(java.util.List.of(client)));
        return new BattleContext(connection, roomPlayer, game);
    }

    private static GuardianContext guardianContext(short actorPosition, boolean actorOwned) {
        FTPlayer player = mock(FTPlayer.class);
        Room room = mock(Room.class);
        RoomPlayer roomPlayer = mock(RoomPlayer.class);
        when(roomPlayer.getPosition()).thenReturn((short) 0);

        MatchplayGuardianGame game = mock(MatchplayGuardianGame.class);
        SMaps map = mock(SMaps.class);
        ConcurrentLinkedDeque<PlayerBattleState> playerStates = new ConcurrentLinkedDeque<>();
        playerStates.add(new PlayerBattleState((short) 0, 100L, 100, 10, 10, 10, 10));
        playerStates.add(new PlayerBattleState((short) 1, 200L, 100, 10, 10, 10, 10));
        GuardianBattleState guardianState = mock(GuardianBattleState.class);
        when(guardianState.getPosition()).thenReturn(10);
        when(guardianState.getCurrentHealth()).thenReturn(new AtomicInteger(100));
        when(guardianState.getLooted()).thenReturn(new AtomicBoolean(true));
        when(game.getPlayerBattleStates()).thenReturn(playerStates);
        when(game.getGuardianBattleStates()).thenReturn(new ConcurrentLinkedDeque<>(java.util.List.of(guardianState)));
        when(game.getMap()).thenReturn(map);
        when(game.getIsHardMode()).thenReturn(new AtomicBoolean(false));
        when(game.getStageChangingToBoss()).thenReturn(new AtomicBoolean(false));
        when(game.getBossBattleActive()).thenReturn(new AtomicBoolean(false));

        GameSession session = mock(GameSession.class);
        when(session.getMatchplayGame()).thenReturn(game);
        when(session.hasOwnedPetSeats()).thenReturn(true);
        when(session.isActorOwnedBy(roomPlayer, actorPosition)).thenReturn(actorOwned);

        FTClient client = mock(FTClient.class);
        when(client.hasPlayer()).thenReturn(true);
        when(client.getPlayer()).thenReturn(player);
        when(client.getActiveRoom()).thenReturn(room);
        when(client.getRoomPlayer()).thenReturn(roomPlayer);
        when(client.getActiveGameSession()).thenReturn(session);
        when(session.isGameplayEndpoint(client)).thenReturn(true);

        FTConnection connection = mock(FTConnection.class);
        when(connection.getClient()).thenReturn(client);
        when(client.getConnection()).thenReturn(connection);
        when(session.getClients()).thenReturn(new ConcurrentLinkedDeque<>(java.util.List.of(client)));
        return new GuardianContext(connection, roomPlayer, session, game);
    }

    private record BattleContext(FTConnection connection, RoomPlayer roomPlayer, MatchplayBattleGame game) {
    }

    private record GuardianContext(FTConnection connection, RoomPlayer roomPlayer, GameSession session,
                                   MatchplayGuardianGame game) {
    }
}
