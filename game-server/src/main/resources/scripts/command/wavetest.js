var AbstractCommand = Java.type("com.jftse.emulator.server.core.command.AbstractCommand");
var CommandAdapter = Java.extend(AbstractCommand);
var S2CMatchplayUseSkill = Java.type("com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayUseSkill");
var S2CChatRoomAnswerPacket = Java.type("com.jftse.emulator.server.core.packets.chat.S2CChatRoomAnswerPacket");
var MatchplayGuardianGame = Java.type("com.jftse.emulator.server.core.matchplay.game.MatchplayGuardianGame");
var GameManager = Java.type("com.jftse.emulator.server.core.manager.GameManager");

var SEA_WAVE_PACKET_ID = 27;
var STEP_MS = 7000;

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
        if (!client || !client.getActiveGameSession()) {
            sendChat(connection, "Need an active match. Start a guardian game first.");
            return;
        }

        const gameSession = client.getActiveGameSession();
        const game = gameSession.getMatchplayGame();
        if (!(game instanceof MatchplayGuardianGame)) {
            sendChat(connection, "Only works in guardian mode.");
            return;
        }

        const steps = [
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

        sendChat(connection, "Wave origin test: " + steps.length + " SeaWaves, 7s apart. Watch spawn and travel.");

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
            session.getFireables().push(runnableEvent);
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
