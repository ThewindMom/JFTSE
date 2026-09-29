var S2CMatchplayUseSkill = Java.type("com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayUseSkill");
var S2CMatchplayDealDamage = Java.type("com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayDealDamage");
var S2CChatRoomAnswerPacket = Java.type("com.jftse.emulator.server.core.packets.chat.S2CChatRoomAnswerPacket");
var Skill2Guardians = Java.type("com.jftse.entities.database.model.battle.Skill2Guardians");
var GuardianAttackTask = Java.type("com.jftse.emulator.server.core.task.GuardianAttackTask");
var PhaseUpdateResult = Java.type("com.jftse.emulator.server.core.matchplay.guardian.PhaseUpdateResult");

var BOSS_POSITION = 10;
var WITCH_POSITIONS = [11, 12];
var SMALL_INFERNO_ID = 21;
var BIG_INFERNO_ID = 35;
var WITCH_SKILL_ID = 6;
var POLYMORPH_ID = 7;
var CHAOS_ID = 25;
var MOVEMENT_SPEED_ID = 46;
var BIG_WARNING_LEAD_MS = 2000;
var REVIVE_DELAY_MS = 20000;
var TRICK_FIRST_MS = 20000;
var TRICK_REPEAT_MS = 30000;
var RALLY_QUIET_MS = 2000;
var HEAL_PERCENT = 5;
var HEAL_ANIMATION_ID = 1;
var HEALTH_SYNC_POSITION = 4;
var SMALL_WAVE_MS = 5000;
var SMALL_REPEAT_MS = 900;
var WITCH_ATTACK_LOOP_MS = 8000;

var RULES = {
    2: { immune: true, smallCount: 1, bigCadence: 10000 },
    1: { immune: true, smallCount: 2, bigCadence: 7500 },
    0: { immune: false, smallCount: 3, bigCadence: 5000 }
};

var TRICK_OR_TREAT = [
    { key: "polymorph", skillId: POLYMORPH_ID, name: "Polymorph" },
    { key: "chaos", skillId: CHAOS_ID, name: "Chaos" },
    { key: "treat", skillId: 0, name: "Treat" },
    { key: "speed", skillId: MOVEMENT_SPEED_ID, name: "Movement Speed" }
];

var INTRO_LINES = [
    "Hell Blood is immune while a Witch lives. Won rallies heal 5 percent max HP.",
    "Witch curse: avoid equipped Heal and Shield. Crystal Heal, Shield, and Revive are OK.",
    "Small Inferno strikes every 5 seconds, more times as Witches fall."
];

class HalloweenArena {
    constructor() {
        this.timeStarted = 0;
        this.started = false;
        this.ended = false;
        this.boss = null;
        this.smallInferno = null;
        this.bigInferno = null;
        this.witchSkill = null;
        this.epoch = 0;
        this.nextSmallWaveAt = 0;
        this.nextBigAt = 0;
        this.bigWarnedFor = 0;
        this.bossProtected = true;
        this.witches = {
            11: { observedAlive: false, revivalDue: 0 },
            12: { observedAlive: false, revivalDue: 0 }
        };
        this.volleys = {};
        this.effects = { polymorph: null, chaos: null, speed: null };
        this.introPending = false;
        this.nextTrickAt = 0;
        this.rallyQuietUntil = 0;
        this.rallyHealPending = false;
    }
}

var arena = new HalloweenArena();

function living(state) {
    return state != null && state.getCurrentHealth().get() > 0;
}

function livingWitchCount() {
    var count = 0;
    for (var i = 0; i < WITCH_POSITIONS.length; i++) {
        if (living(game.getGuardianBattleStateByPosition(WITCH_POSITIONS[i]))) {
            count++;
        }
    }
    return count;
}

function ruleFor(count) {
    return RULES[count] || RULES[0];
}

function livingPlayers() {
    return game.getPlayerBattleStates().stream()
        .filter(function (player) {
            return player != null && player.getPosition() < 4 && !player.isDead() && player.getCurrentHealth().get() > 0;
        })
        .toArray();
}

function effectPlayers() {
    return game.getPlayerBattleStates().stream()
        .filter(function (player) {
            return player != null
                && player.getPosition() >= 0
                && player.getPosition() <= 3
                && !player.isDead()
                && player.getCurrentHealth().get() > 0;
        })
        .toArray();
}

function playerCount() {
    var count = game.getPlayerBattleStates().stream()
        .filter(function (player) {
            return player != null && player.getPosition() < 4;
        })
        .count();
    if (count < 1) {
        return 1;
    }
    if (count > 4) {
        return 4;
    }
    return count;
}

function say(connection, message) {
    var packet = new S2CChatRoomAnswerPacket(2, "Server", message);
    gameManager.sendPacketToAllClientsInSameGameSession(packet, connection);
}

function applyStats(state, maxHealth, str, sta, dex, will) {
    state.setMaxHealth(maxHealth);
    state.getCurrentHealth().set(maxHealth);
    state.setStr(str);
    state.setSta(sta);
    state.setDex(dex);
    state.setWill(will);
}

function attachWitchSkill(state) {
    var skills = state.getSkills();
    if (skills == null) {
        return false;
    }
    skills.clear();
    var binding = new Skill2Guardians();
    binding.setSkill(arena.witchSkill);
    binding.setBtItemID(state.getBtItemId());
    binding.setChance(100.0);
    skills.add(binding);
    return true;
}

function clearVolleys() {
    arena.volleys = {};
}

function endEncounter() {
    if (arena.ended) {
        return;
    }
    arena.ended = true;
    arena.epoch++;
    clearVolleys();
    arena.witches[11].revivalDue = 0;
    arena.witches[12].revivalDue = 0;
    arena.introPending = false;
    arena.rallyHealPending = false;
    arena.bigWarnedFor = 0;
}

function resetCadence(now, count) {
    var rule = ruleFor(count);
    arena.nextSmallWaveAt = now + SMALL_WAVE_MS;
    arena.nextBigAt = now + rule.bigCadence;
    arena.bigWarnedFor = 0;
}

function castSkill(connection, target, skill) {
    var packet = new S2CMatchplayUseSkill(
        BOSS_POSITION,
        target,
        skill.getId() - 1,
        Math.floor(Math.random() * 127),
        0, 0, 0
    );
    gameManager.sendPacketToAllClientsInSameGameSession(packet, connection);
}

function resolveOptional(skillService, id, name) {
    var skill = skillService.findSkillById(id);
    if (skill == null) {
        log.warn("Halloween Arena optional skill missing: " + name);
    }
    return skill;
}

function availableTricks() {
    var tricks = [];
    for (var i = 0; i < TRICK_OR_TREAT.length; i++) {
        var entry = TRICK_OR_TREAT[i];
        if (entry.key === "treat") {
            tricks.push("treat");
        } else if (arena.effects[entry.key] != null) {
            tricks.push(arena.effects[entry.key]);
        }
    }
    return tricks;
}

function chooseTrick(tricks) {
    return tricks[Math.floor(Math.random() * tricks.length)];
}

function healPlayers(connection, players) {
    var combat = game.getPlayerCombatSystem();
    for (var i = 0; i < players.length; i++) {
        var player = players[i];
        try {
            if (player.isDead() || player.getCurrentHealth().get() <= 0) {
                continue;
            }
            var position = player.getPosition();
            combat.heal(position, HEAL_PERCENT);
            var packet = new S2CMatchplayDealDamage(
                position,
                player.getCurrentHealth().get(),
                HEALTH_SYNC_POSITION,
                HEAL_ANIMATION_ID,
                0.0,
                0.0
            );
            gameManager.sendPacketToAllClientsInSameGameSession(packet, connection);
        } catch (e) {
            log.warn("Halloween Arena heal failed for player " + i);
        }
    }
}

function dispatchIntro(connection) {
    if (!arena.introPending) {
        return;
    }
    arena.introPending = false;
    for (var line = 0; line < INTRO_LINES.length; line++) {
        say(connection, INTRO_LINES[line]);
    }
    if (arena.effects.polymorph == null) {
        return;
    }
    var players = effectPlayers();
    for (var i = 0; i < players.length; i++) {
        castSkill(connection, players[i].getPosition(), arena.effects.polymorph);
    }
}

function dispatchTricks(connection, now) {
    if (now < arena.nextTrickAt) {
        return;
    }
    arena.nextTrickAt = now + TRICK_REPEAT_MS;
    var tricks = availableTricks();
    var players = effectPlayers();
    var casts = [];
    var heals = [];
    for (var i = 0; i < players.length; i++) {
        var choice = chooseTrick(tricks);
        if (choice === "treat") {
            heals.push(players[i]);
        } else {
            casts.push({ player: players[i], skill: choice });
        }
    }
    for (var c = 0; c < casts.length; c++) {
        castSkill(connection, casts[c].player.getPosition(), casts[c].skill);
    }
    healPlayers(connection, heals);
}

function dispatchRallyHeal(connection) {
    if (!arena.rallyHealPending) {
        return;
    }
    arena.rallyHealPending = false;
    healPlayers(connection, effectPlayers());
}

function playerAt(position) {
    return game.getPlayerBattleStates().stream()
        .filter(function (player) {
            return player != null && player.getPosition() === position;
        })
        .findFirst()
        .orElse(null);
}

function replaceSmallWave(now) {
    var count = livingWitchCount();
    var players = livingPlayers();
    var repeats = ruleFor(count).smallCount;
    var volleys = {};
    for (var i = 0; i < players.length; i++) {
        var position = players[i].getPosition();
        volleys[position] = {
            epoch: arena.epoch,
            witchCount: count,
            target: position,
            remaining: repeats,
            nextDue: now
        };
    }
    arena.volleys = volleys;
    arena.nextSmallWaveAt = now + SMALL_WAVE_MS;
}

function dispatchVolleys(connection, now) {
    if (arena.ended || !living(arena.boss) || arena.smallInferno == null) {
        return;
    }
    var count = livingWitchCount();
    var positions = Object.keys(arena.volleys);
    for (var i = 0; i < positions.length; i++) {
        var volley = arena.volleys[positions[i]];
        if (volley == null || volley.remaining < 1 || volley.nextDue > now) {
            continue;
        }
        if (volley.epoch !== arena.epoch || volley.witchCount !== count) {
            delete arena.volleys[volley.target];
            continue;
        }
        var target = playerAt(volley.target);
        if (!living(target)) {
            delete arena.volleys[volley.target];
            continue;
        }
        castSkill(connection, volley.target, arena.smallInferno);
        volley.remaining--;
        volley.nextDue = now + SMALL_REPEAT_MS;
        if (volley.remaining < 1) {
            delete arena.volleys[volley.target];
        }
    }
}

function dispatchBig(connection, now) {
    if (arena.ended || !living(arena.boss) || arena.bigInferno == null) {
        return;
    }
    var players = livingPlayers();
    if (players.length === 0) {
        return;
    }
    if (now >= arena.nextBigAt) {
        var target = players[Math.floor(Math.random() * players.length)];
        castSkill(connection, target.getPosition(), arena.bigInferno);
        arena.nextBigAt = now + ruleFor(livingWitchCount()).bigCadence;
        return;
    }
    if (now >= arena.nextBigAt - BIG_WARNING_LEAD_MS && arena.bigWarnedFor !== arena.nextBigAt) {
        arena.bigWarnedFor = arena.nextBigAt;
        say(connection, "Big Inferno incoming!");
    }
}

function syncProtection(connection, count) {
    var immune = ruleFor(count).immune;
    if (immune === arena.bossProtected) {
        return;
    }
    arena.bossProtected = immune;
    say(connection, immune ? "Hell Blood is protected." : "Hell Blood is vulnerable.");
}

function onWitchEdge(connection, now, count) {
    arena.epoch++;
    clearVolleys();
    resetCadence(now, count);
    syncProtection(connection, count);
}

function reviveWitch(connection, position, record) {
    var state = game.getGuardianBattleStateByPosition(position);
    if (state == null) {
        record.revivalDue = 0;
        return false;
    }
    state.getCurrentHealth().set(state.getMaxHealth());
    if (state.isDead()) {
        state.setDead(false);
    }
    record.observedAlive = true;
    record.revivalDue = 0;

    var packet = new S2CMatchplayDealDamage(position, state.getCurrentHealth().get(), 4, 29, 0.0, 0.0);
    gameManager.sendPacketToAllClientsInSameGameSession(packet, connection);
    threadManager.newTask(new GuardianAttackTask(connection, state));
    say(connection, "A Witch revives.");
    return true;
}

function pollWitches(connection, now) {
    var transitioned = false;
    for (var i = 0; i < WITCH_POSITIONS.length; i++) {
        var position = WITCH_POSITIONS[i];
        var record = arena.witches[position];
        var alive = living(game.getGuardianBattleStateByPosition(position));
        if (record.observedAlive && !alive) {
            record.observedAlive = false;
            record.revivalDue = now + REVIVE_DELAY_MS;
            transitioned = true;
        } else if (!record.observedAlive && alive && record.revivalDue === 0) {
            record.observedAlive = true;
            transitioned = true;
        }
        if (record.revivalDue > 0 && record.revivalDue <= now && !alive) {
            if (reviveWitch(connection, position, record)) {
                transitioned = true;
            }
        }
    }
    return transitioned;
}

function unchangedBossHealth(target) {
    var state = game.getGuardianBattleStateByPosition(target);
    if (state != null && state.isBoss() && livingWitchCount() > 0) {
        return state.getCurrentHealth().get();
    }
    return null;
}

var phase = {
    getPhaseName: function () {
        return "Halloween Arena";
    },
    start: function () {
        arena.timeStarted = Date.now();
        arena.started = true;
        arena.epoch++;

        var players = playerCount();
        var boss = game.getGuardianBattleStateByPosition(BOSS_POSITION);
        var left = game.getGuardianBattleStateByPosition(11);
        var right = game.getGuardianBattleStateByPosition(12);
        if (boss == null || !boss.isBoss() || left == null || left.isBoss() || right == null || right.isBoss()) {
            log.error("Halloween Arena requires Hell Blood at 10 and Witches at 11 and 12");
            endEncounter();
            return;
        }

        var skillService = serviceManager.getSkillService();
        arena.smallInferno = skillService.findSkillById(SMALL_INFERNO_ID);
        arena.bigInferno = skillService.findSkillById(BIG_INFERNO_ID);
        arena.witchSkill = skillService.findSkillById(WITCH_SKILL_ID);
        if (arena.smallInferno == null || arena.bigInferno == null || arena.witchSkill == null) {
            log.error("Halloween Arena required skill missing");
            endEncounter();
            return;
        }
        for (var trick = 0; trick < TRICK_OR_TREAT.length; trick++) {
            var entry = TRICK_OR_TREAT[trick];
            if (entry.key === "treat") {
                continue;
            }
            arena.effects[entry.key] = resolveOptional(skillService, entry.skillId, entry.name);
        }

        arena.boss = boss;
        applyStats(boss, 7000 + (3000 * players), 120, 52, 165, 130);
        boss.getSkills().clear();
        applyStats(left, 2230 + (800 * players), 110, 45, 165, 120);
        applyStats(right, 2230 + (800 * players), 110, 45, 165, 120);
        if (!attachWitchSkill(left) || !attachWitchSkill(right)) {
            log.error("Halloween Arena could not normalize Witch skills");
            endEncounter();
            return;
        }

        arena.witches[11].observedAlive = true;
        arena.witches[11].revivalDue = 0;
        arena.witches[12].observedAlive = true;
        arena.witches[12].revivalDue = 0;
        arena.bossProtected = true;
        arena.introPending = true;
        arena.nextTrickAt = arena.timeStarted + TRICK_FIRST_MS;
        arena.rallyQuietUntil = 0;
        arena.rallyHealPending = false;
        resetCadence(arena.timeStarted, 2);
    },
    update: function (connection) {
        if (!arena.started || arena.ended) {
            return PhaseUpdateResult.END_PHASE;
        }
        if (!living(arena.boss)) {
            endEncounter();
            return PhaseUpdateResult.END_PHASE;
        }

        var now = Date.now();
        if (pollWitches(connection, now)) {
            onWitchEdge(connection, now, livingWitchCount());
        }

        if (!living(arena.boss)) {
            endEncounter();
            return PhaseUpdateResult.END_PHASE;
        }

        dispatchIntro(connection);
        dispatchRallyHeal(connection);
        if (now >= arena.nextTrickAt) {
            dispatchTricks(connection, now);
        }

        if (now >= arena.nextSmallWaveAt) {
            replaceSmallWave(now);
        }
        dispatchVolleys(connection, now);
        dispatchBig(connection, now);

        if (!living(arena.boss)) {
            endEncounter();
            return PhaseUpdateResult.END_PHASE;
        }
        return PhaseUpdateResult.CONTINUE;
    },
    end: function () {
        endEncounter();
    },
    phaseTime: function () {
        return arena.timeStarted === 0 ? 0 : Date.now() - arena.timeStarted;
    },
    playTime: function () {
        return 0;
    },
    hasEnded: function () {
        return arena.ended || (arena.boss != null && !living(arena.boss));
    },
    getGuardianAttackLoopTime: function (guardian) {
        if (guardian == null || guardian.isBoss() || !living(guardian)) {
            return -1;
        }
        return WITCH_ATTACK_LOOP_MS;
    },
    onHeal: function (target, healAmount, isGuardian) {
        if (isGuardian) {
            return game.getGuardianCombatSystem().heal(target, healAmount);
        }
        return game.getPlayerCombatSystem().heal(target, healAmount);
    },
    onDealDamage: function (attackingPlayer, targetGuardian, damage, hasAttackerDmgBuff, hasTargetDefBuff, skill) {
        var unchanged = unchangedBossHealth(targetGuardian);
        if (unchanged != null) {
            return unchanged;
        }
        return game.getGuardianCombatSystem().dealDamage(attackingPlayer, targetGuardian, damage, hasAttackerDmgBuff, hasTargetDefBuff, skill);
    },
    onDealDamageToPlayer: function (attackingGuardian, targetPlayer, damageAmount, hasAttackerDmgBuff, hasTargetDefBuff, skill) {
        return game.getGuardianCombatSystem().dealDamageToPlayer(attackingGuardian, targetPlayer, damageAmount, hasAttackerDmgBuff, hasTargetDefBuff, skill);
    },
    onDealDamageOnBallLoss: function (attackerPos, targetPos, hasAttackerWillBuff) {
        if (arena.started && !arena.ended) {
            var now = Date.now();
            if (now >= arena.rallyQuietUntil) {
                arena.rallyQuietUntil = now + RALLY_QUIET_MS;
                arena.rallyHealPending = true;
            }
        }
        var unchanged = unchangedBossHealth(targetPos);
        if (unchanged != null) {
            return unchanged;
        }
        return game.getGuardianCombatSystem().dealDamageOnBallLoss(attackerPos, targetPos, hasAttackerWillBuff);
    },
    onDealDamageOnBallLossToPlayer: function (attackerPos, targetPos, hasAttackerWillBuff) {
        return game.getGuardianCombatSystem().dealDamageOnBallLossToPlayer(attackerPos, targetPos, hasAttackerWillBuff);
    }
};
