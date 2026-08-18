var AbstractCommand = Java.type("com.jftse.emulator.server.core.command.AbstractCommand");
var CommandAdapter = Java.extend(AbstractCommand);
var S2CMatchplayUseSkill = Java.type("com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayUseSkill");
var S2CChatRoomAnswerPacket = Java.type("com.jftse.emulator.server.core.packets.chat.S2CChatRoomAnswerPacket");
var MatchplayGuardianGame = Java.type("com.jftse.emulator.server.core.matchplay.game.MatchplayGuardianGame");
var GameManager = Java.type("com.jftse.emulator.server.core.manager.GameManager");
var S2CMatchplayDealDamage = Java.type("com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayDealDamage");

var SEA_WAVE_PACKET_ID = 27;
var STEP_MS = 5000;

var impl = new CommandAdapter({
    getRank: function () {
        return 0;
    },
    getCommandName: function () {
        return "wavetest";
    },
    getDescription: function () {
        return "Fire labeled SeaWave origin tests. Use in a guardian match: -wavetest";
    },
    execute: function (connection, params) {
        const client = connection.getClient();
        if (!client) {
            return;
        }
        if (!client.getActiveGameSession()) {
            try {
                var fw = new (Java.type("java.io.FileWriter"))("/tmp/jftse-wavetest.arm");
                fw.write("1");
                fw.close();
            } catch (e) {
                sendChat(connection, "Arm failed: " + e);
                return;
            }
            sendChat(connection, "Armed. START a guardian match; volley fires after the intro.");
            return;
        }

        const gameSession = client.getActiveGameSession();
        const game = gameSession.getMatchplayGame();
        if (!(game instanceof MatchplayGuardianGame)) {
            sendChat(connection, "Only works in guardian mode.");
            return;
        }

        // Confirmed LTR look: dummy 4, packet 27, xyz=(-200,0,0). Five copies, 5s apart.
        const steps = [
            { attacker: 4, x: -200, z: 0, y: 0, guess: "LTR confirmed spawn" },
            { attacker: 4, x: -200, z: 0, y: 0, guess: "LTR confirmed spawn" },
            { attacker: 4, x: -200, z: 0, y: 0, guess: "LTR confirmed spawn" },
            { attacker: 4, x: -200, z: 0, y: 0, guess: "LTR confirmed spawn" },
            { attacker: 4, x: -200, z: 0, y: 0, guess: "LTR confirmed spawn" }
        ];

        try {
            const states = game.getPlayerBattleStates();
            const it = states.iterator();
            while (it.hasNext()) {
                const pbs = it.next();
                pbs.setMaxHealth(30000);
                pbs.getCurrentHealth().set(30000);
                pbs.setDead(false);
            }
            const heal = new S2CMatchplayDealDamage(0, 30000, 0, 1, 0.0, 0.0);
            GameManager.getInstance().sendPacketToAllClientsInSameGameSession(heal, connection);
            sendChat(connection, "HP padded to 30000 (client heal packet).");
        } catch (e) {
            sendChat(connection, "HP pad failed: " + e);
        }

        sendChat(connection, "Wave origin test: " + steps.length + " SeaWaves, 5s apart. Watch spawn and travel.");

        const eventHandler = GameManager.getInstance().getEventHandler();
        for (let i = 0; i < steps.length; i++) {
            const step = steps[i];
            const n = i + 1;
            const delay = 500 + i * STEP_MS;
            const runnableEvent = eventHandler.createRunnableEvent(function () {
                const session = connection.getClient() && connection.getClient().getActiveGameSession();
                if (!session) {
                    return;
                }
                const label = "WAVE " + n + "/" + steps.length
                    + " attacker=" + step.attacker
                    + " xyz=(" + step.x + ", " + step.z + ", " + step.y + ")"
                    + " " + step.guess;
                const msg = new S2CChatRoomAnswerPacket(2, "WaveTest", label);
                GameManager.getInstance().sendPacketToAllClientsInSameGameSession(msg, connection);

                try {
                    const heal = new S2CMatchplayDealDamage(0, 30000, 0, 1, 0.0, 0.0);
                    GameManager.getInstance().sendPacketToAllClientsInSameGameSession(heal, connection);
                } catch (e2) {}
                const seed = Math.floor(Math.random() * 127);
                const packet = new S2CMatchplayUseSkill(
                    step.attacker,
                    4,
                    SEA_WAVE_PACKET_ID,
                    seed,
                    step.x,
                    step.z,
                    step.y
                );
                GameManager.getInstance().sendPacketToAllClientsInSameGameSession(packet, connection);
            }, delay);
            gameSession.getFireables().push(runnableEvent);
            eventHandler.offerJS(runnableEvent);
        }
    }
});

function sendChat(connection, text) {
    const packet = new S2CChatRoomAnswerPacket(2, "WaveTest", text);
    const client = connection.getClient();
    if (client && client.getActiveGameSession()) {
        GameManager.getInstance().sendPacketToAllClientsInSameGameSession(packet, connection);
    } else {
        connection.sendTCP(packet);
    }
}
