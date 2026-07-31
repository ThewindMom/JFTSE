package com.jftse.emulator.server.core.command;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.command.commands.player.TranslateChatCommand;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.packets.chat.S2CChatLobbyAnswerPacket;
import com.jftse.emulator.server.core.packets.chat.S2CChatRoomAnswerPacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.account.Account;
import com.jftse.entities.database.model.player.Player;
import com.jftse.entities.database.model.player.PlayerStatistic;
import com.jftse.entities.database.model.pocket.Pocket;
import com.jftse.server.core.protocol.IPacket;
import com.jftse.server.core.service.CommandLogService;
import com.jftse.server.core.service.PlayerService;
import com.jftse.server.core.service.ScriptStateService;
import com.jftse.server.core.shared.PlayerLoadType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatTranslationCommandIntegrationTest {
    private Object previousGameManager;
    private Object previousServiceManager;

    private CommandManager commandManager;
    private ServiceManager serviceManager;
    private PlayerService playerService;
    private FTConnection connection;
    private FTClient client;
    private Player player;

    @BeforeEach
    void setUp() throws Exception {
        GameManager gameManager = mock(GameManager.class);
        serviceManager = mock(ServiceManager.class);
        playerService = mock(PlayerService.class);

        when(gameManager.getServiceManager()).thenReturn(serviceManager);
        when(serviceManager.getCommandLogService()).thenReturn(mock(CommandLogService.class));
        when(serviceManager.getPlayerService()).thenReturn(playerService);

        previousGameManager = replaceStatic(GameManager.class, "instance", gameManager);
        previousServiceManager = replaceStatic(ServiceManager.class, "instance", serviceManager);

        commandManager = new CommandManager(mock(ScriptStateService.class));
        commandManager.setRegisteredCommands(new LinkedHashMap<>());
        commandManager.registerCommand("translate", 0, new TranslateChatCommand());

        connection = mock(FTConnection.class);
        client = mock(FTClient.class);
        Account account = mock(Account.class);
        FTPlayer ftPlayer = mock(FTPlayer.class);
        player = new Player();

        when(connection.getClient()).thenReturn(client);
        when(client.getAccount()).thenReturn(account);
        when(account.getGameMaster()).thenReturn(false);
        when(client.hasPlayer()).thenReturn(true);
        when(client.getPlayer()).thenReturn(ftPlayer);
        when(ftPlayer.getPlayer()).thenReturn(player);
    }

    @AfterEach
    void tearDown() throws Exception {
        replaceStatic(GameManager.class, "instance", previousGameManager);
        replaceStatic(ServiceManager.class, "instance", previousServiceManager);
    }

    @Test
    void dispatchesPostCommandArgumentAndPersistsPreference() {
        commandManager.handle(connection, "-translate on");

        verify(client).setTranslateChatToEnglish(true);
        verify(playerService).save(player);
    }

    @Test
    void normalizesCommandNameAndSurroundingWhitespace() {
        assertTrue(commandManager.isCommand("  -TRANSLATE on  "));

        commandManager.handle(connection, "  -TRANSLATE on  ");

        verify(client).setTranslateChatToEnglish(true);
        verify(playerService).save(player);
    }

    @Test
    void persistsDisabledPreference() {
        player.setTranslateChatToEnglish(true);

        commandManager.handle(connection, "-translate off");

        assertFalse(player.getTranslateChatToEnglish());
        verify(client).setTranslateChatToEnglish(false);
        verify(playerService).save(player);
    }

    @Test
    void rollsBackAndReportsFailedPreferenceSave() {
        doThrow(new RuntimeException("database unavailable")).when(playerService).save(player);

        commandManager.handle(connection, "-translate on");

        assertFalse(player.getTranslateChatToEnglish());
        verify(client, never()).setTranslateChatToEnglish(true);
        verify(connection).sendTCP(argThat((IPacket packet) ->
                packet instanceof S2CChatLobbyAnswerPacket));
    }

    @Test
    void invalidModeDoesNotChangeOrPersistPreference() {
        commandManager.handle(connection, "-translate maybe");

        verify(client, never()).setTranslateChatToEnglish(true);
        verify(client, never()).setTranslateChatToEnglish(false);
        verify(playerService, never()).save(player);
    }

    @Test
    void roomCommandUsesRoomChatFeedback() {
        when(client.getActiveRoom()).thenReturn(mock(Room.class));

        commandManager.handle(connection, "-translate off");

        verify(connection).sendTCP(argThat((IPacket packet) ->
                packet instanceof S2CChatRoomAnswerPacket));
    }

    @Test
    void freshClientRestoresPersistedPreference() {
        Player persistedPlayer = new Player();
        persistedPlayer.setId(1L);
        persistedPlayer.setPlayerType((byte) 0);
        Field preference = assertDoesNotThrow(
                () -> Player.class.getDeclaredField("translateChatToEnglish"));
        preference.setAccessible(true);
        assertDoesNotThrow(() -> preference.set(persistedPlayer, true));

        Account account = new Account();
        account.setId(1L);
        account.setAp(0);
        account.setGameMaster(false);
        account.setStatus(0);
        persistedPlayer.setAccount(account);
        Pocket pocket = new Pocket();
        pocket.setId(1L);
        persistedPlayer.setPocket(pocket);
        PlayerStatistic statistic = new PlayerStatistic();
        statistic.setId(1L);
        persistedPlayer.setPlayerStatistic(statistic);

        FTClient freshClient = new FTClient();
        freshClient.loadPlayer(account, persistedPlayer, PlayerLoadType.BASIC);

        assertTrue(freshClient.isTranslateChatToEnglish());
    }

    @Test
    void newPlayerDefaultsToTranslationDisabled() {
        FTClient freshClient = new FTClient();
        assertFalse(freshClient.isTranslateChatToEnglish());
    }

    private static Object replaceStatic(Class<?> type, String fieldName, Object value) throws Exception {
        Field field = type.getDeclaredField(fieldName);
        field.setAccessible(true);
        Object previous = field.get(null);
        field.set(null, value);
        return previous;
    }
}
