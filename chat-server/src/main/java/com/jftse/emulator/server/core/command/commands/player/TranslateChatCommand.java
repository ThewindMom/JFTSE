package com.jftse.emulator.server.core.command.commands.player;

import com.jftse.emulator.server.core.command.AbstractCommand;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.packets.chat.S2CChatLobbyAnswerPacket;
import com.jftse.emulator.server.core.packets.chat.S2CChatRoomAnswerPacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.player.Player;
import com.jftse.server.core.translation.ChatTranslationServices;
import lombok.extern.log4j.Log4j2;

import java.util.List;
import java.util.Locale;

@Log4j2
public class TranslateChatCommand extends AbstractCommand {
    public TranslateChatCommand() {
        setDescription("Translate Thai player chat to English");
    }

    @Override
    public void execute(FTConnection connection, List<String> params) {
        FTClient client = connection.getClient();
        if (client == null || !client.hasPlayer()) {
            sendStatus(connection, "Translation preference requires an active player");
            return;
        }

        if (params.size() != 1) {
            sendStatus(connection, "Use -translate on or -translate off");
            return;
        }

        String mode = params.get(0).toLowerCase(Locale.ROOT);
        if (!mode.equals("on") && !mode.equals("off")) {
            sendStatus(connection, "Use -translate on or -translate off");
            return;
        }

        boolean enabled = mode.equals("on");
        Player player = client.getPlayer().getPlayer();
        Boolean previousPreference = player.getTranslateChatToEnglish();
        player.setTranslateChatToEnglish(enabled);
        try {
            ServiceManager.getInstance().getPlayerService().save(player);
        } catch (RuntimeException exception) {
            player.setTranslateChatToEnglish(previousPreference);
            log.error("Failed to save chat translation preference for player {}", player.getId(), exception);
            sendStatus(connection, "Translation preference could not be saved");
            return;
        }
        client.setTranslateChatToEnglish(enabled);

        String message = "Thai to English chat translation " + (enabled ? "enabled" : "disabled");
        if (enabled && !ChatTranslationServices.get().isEnabled()) {
            message += " (server translation is currently disabled)";
        }
        sendStatus(connection, message);
    }

    private void sendStatus(FTConnection connection, String message) {
        if (connection.getClient() != null && connection.getClient().getActiveRoom() != null) {
            connection.sendTCP(new S2CChatRoomAnswerPacket((byte) 2, "Translation", message));
        } else {
            connection.sendTCP(new S2CChatLobbyAnswerPacket((char) 0, "Translation", message));
        }
    }
}
