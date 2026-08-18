// Auto-pad player HP when a guardian match actually starts (after intro),
// then fire an armed -wavetest volley so we do not type under fire.
var MatchplayGuardianGame = Java.type("com.jftse.emulator.server.core.matchplay.game.MatchplayGuardianGame");
var S2CMatchplayDealDamage = Java.type("com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayDealDamage");
var S2CMatchplayUseSkill = Java.type("com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayUseSkill");
var S2CChatRoomAnswerPacket = Java.type("com.jftse.emulator.server.core.packets.chat.S2CChatRoomAnswerPacket");
var GameManager = Java.type("com.jftse.emulator.server.core.manager.GameManager");
var File = Java.type("java.io.File");

var ARM = "/tmp/jftse-wavetest.arm";
var SEA_WAVE_PACKET_ID = 27;
var STEP_MS = 5000;
var PAD_HP = 30000;

function chat(connection, text) {
    var packet = new S2CChatRoomAnswerPacket(2, "WaveTest", text);
    GameManager.getInstance().sendPacketToAllClientsInSameGameSession(packet, connection);
}

function padAndHeal(game, connection) {
    var states = game.getPlayerBattleStates();
    var it = states.iterator();
    while (it.hasNext()) {
        var pbs = it.next();
        pbs.setMaxHealth(PAD_HP);
        pbs.getCurrentHealth().set(PAD_HP);
        pbs.setDead(false);
    }
    var heal = new S2CMatchplayDealDamage(0, PAD_HP, 0, 1, 0.0, 0.0);
    GameManager.getInstance().sendPacketToAllClientsInSameGameSession(heal, connection);
}

function fireVolley(connection) {
    var steps = [
        { attacker: 4, x: 0, z: 0, y: 0, guess: "net / court origin" },
        { attacker: 4, x: -150, z: 0, y: 0, guess: "-X sideline (left?)" },
        { attacker: 4, x: 150, z: 0, y: 0, guess: "+X sideline (right?)" },
        { attacker: 4, x: 0, z: 0, y: -150, guess: "-Y baseline" },
        { attacker: 4, x: 0, z: 0, y: 150, guess: "+Y baseline" },
        { attacker: 4, x: 0, z: -150, y: 0, guess: "-Z axis" },
        { attacker: 4, x: 0, z: 150, y: 0, guess: "+Z axis" },
        { attacker: 5, x: 0, z: 0, y: 0, guess: "dummy slot 5, origin 0" },
        { attacker: 4, x: -150, z: 0, y: 150, guess: "corner -X / +Y" }
    ];
    chat(connection, "Wave origin test: " + steps.length + " SeaWaves, 5s apart. Watch spawn and travel.");
    var eventHandler = GameManager.getInstance().getEventHandler();
    var gameSession = connection.getClient().getActiveGameSession();
    for (var i = 0; i < steps.length; i++) {
        (function (step, n, delay) {
            var runnableEvent = eventHandler.createRunnableEvent(function () {
                var session = connection.getClient() && connection.getClient().getActiveGameSession();
                if (!session) return;
                try { padAndHeal(session.getMatchplayGame(), connection); } catch (e) {}
                var label = "WAVE " + n + "/" + steps.length
                    + " attacker=" + step.attacker
                    + " xyz=(" + step.x + ", " + step.z + ", " + step.y + ")"
                    + " " + step.guess;
                GameManager.getInstance().sendPacketToAllClientsInSameGameSession(
                    new S2CChatRoomAnswerPacket(2, "WaveTest", label), connection);
                var seed = Math.floor(Math.random() * 127);
                var packet = new S2CMatchplayUseSkill(step.attacker, 4, SEA_WAVE_PACKET_ID, seed, step.x, step.z, step.y);
                GameManager.getInstance().sendPacketToAllClientsInSameGameSession(packet, connection);
            }, delay);
            if (gameSession) gameSession.getFireables().push(runnableEvent);
            eventHandler.offerJS(runnableEvent);
        })(steps[i], i + 1, 500 + i * STEP_MS);
    }
}

geb.on("MP_GAME_ANIM_SKIP_END", function (game, room) {
    try {
        if (!(game instanceof MatchplayGuardianGame)) return;
        var clients = gameManager.getClientsInRoom(room.getRoomId());
        if (clients.isEmpty()) return;
        var connection = clients.get(0).getConnection();
        padAndHeal(game, connection);
        chat(connection, "HP padded to 30000 at match start.");
        var arm = new File(ARM);
        if (arm.exists()) {
            arm.delete();
            fireVolley(connection);
        }
    } catch (e) {
        console.log("wavetest autopad failed: " + e);
    }
});
