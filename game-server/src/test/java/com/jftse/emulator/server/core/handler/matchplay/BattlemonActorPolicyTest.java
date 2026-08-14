package com.jftse.emulator.server.core.handler.matchplay;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.matchplay.combat.PlayerCombatSystem;
import com.jftse.emulator.server.core.matchplay.event.EventHandler;
import com.jftse.emulator.server.core.matchplay.event.RunnableEvent;
import com.jftse.emulator.server.core.matchplay.game.MatchplayBattleGame;
import com.jftse.emulator.server.core.matchplay.game.MatchplayGuardianGame;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.battle.Skill;
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
        ConcurrentLinkedDeque<PlayerBattleState> playerStates = new ConcurrentLinkedDeque<>();
        playerStates.add(new PlayerBattleState((short) 0, 100L, 100, 10, 10, 10, 10));
        playerStates.add(new PlayerBattleState((short) 1, 200L, 100, 10, 10, 10, 10));
        GuardianBattleState guardianState = mock(GuardianBattleState.class);
        when(guardianState.getPosition()).thenReturn(10);
        when(game.getPlayerBattleStates()).thenReturn(playerStates);
        when(game.getGuardianBattleStates()).thenReturn(new ConcurrentLinkedDeque<>(java.util.List.of(guardianState)));

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

        FTConnection connection = mock(FTConnection.class);
        when(connection.getClient()).thenReturn(client);
        when(client.getConnection()).thenReturn(connection);
        when(session.getClients()).thenReturn(new ConcurrentLinkedDeque<>(java.util.List.of(client)));
        return new GuardianContext(connection);
    }

    private record BattleContext(FTConnection connection, RoomPlayer roomPlayer, MatchplayBattleGame game) {
    }

    private record GuardianContext(FTConnection connection) {
    }
}
