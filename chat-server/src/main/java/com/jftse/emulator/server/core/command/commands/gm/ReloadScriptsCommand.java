package com.jftse.emulator.server.core.command.commands.gm;

import com.jftse.emulator.common.scripting.ScriptManagerFactory;
import com.jftse.emulator.common.scripting.ScriptManagerV2;
import com.jftse.emulator.server.core.command.AbstractCommand;
import com.jftse.emulator.server.core.command.CommandManager;
import com.jftse.emulator.server.core.interaction.PlayerScriptableImpl;
import com.jftse.emulator.server.core.life.event.GameEventBus;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.net.FTConnection;
import lombok.extern.log4j.Log4j2;

import java.util.List;
import java.util.Optional;

@Log4j2
public class ReloadScriptsCommand extends AbstractCommand {
    private final GameManager gameManager;
    private final CommandManager commandManager;

    private final String MESSAGE_SUCCESS = "Scripts reloaded";
    private final String MESSAGE_FAIL = "Scripts not reloaded";
    private final String MESSAGE_SENDER = "Server";

    public ReloadScriptsCommand() {
        setDescription("Reloads all scripts");

        this.gameManager = GameManager.getInstance();
        this.commandManager = CommandManager.getInstance();
    }

    @Override
    public void execute(FTConnection connection, List<String> params) {
        PlayerScriptableImpl playerScriptable = new PlayerScriptableImpl(connection.getClient());

        try {
            gameManager.getScriptManager().ifPresent(ScriptManagerV2::shutdownAllExecutors);

            Optional<ScriptManagerV2> scriptManager = ScriptManagerFactory.loadScriptsV2("scripts", () -> log);
            if (scriptManager.isEmpty()) {
                playerScriptable.sendChat(MESSAGE_SENDER, MESSAGE_FAIL);
                return;
            }

            gameManager.setScriptManager(scriptManager);

            playerScriptable.sendChat(MESSAGE_SENDER, "Reloading commands...");
            boolean commandsValid = commandManager.reloadCommands();
            if (!commandsValid) {
                playerScriptable.sendChat(MESSAGE_SENDER, MESSAGE_FAIL);
                return;
            }

            playerScriptable.sendChat(MESSAGE_SENDER, "Reloading events...");
            boolean eventsValid = GameEventBus.getInstance().reloadEvents();
            if (!eventsValid) {
                playerScriptable.sendChat(MESSAGE_SENDER, MESSAGE_FAIL);
                return;
            }

            playerScriptable.sendChat(MESSAGE_SENDER, MESSAGE_SUCCESS);
        } catch (Exception e) {
            log.error("Failed to reload scripts", e);
            playerScriptable.sendChat(MESSAGE_SENDER, MESSAGE_FAIL);
        }
    }
}
