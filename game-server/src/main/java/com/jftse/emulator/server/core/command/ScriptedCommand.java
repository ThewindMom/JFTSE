package com.jftse.emulator.server.core.command;

import com.jftse.emulator.common.scripting.ScriptFile;
import com.jftse.emulator.common.scripting.ScriptManagerV2;
import com.jftse.emulator.server.net.FTConnection;
import lombok.extern.log4j.Log4j2;

import java.util.List;

@Log4j2
public class ScriptedCommand extends AbstractCommand {
    private final ScriptFile scriptFile;
    private final ScriptManagerV2 scriptManager;
    private final AbstractCommand delegate;

    public ScriptedCommand(ScriptFile scriptFile, ScriptManagerV2 scriptManager, AbstractCommand delegate) {
        this.scriptFile = scriptFile;
        this.scriptManager = scriptManager;
        this.delegate = delegate;

        Integer rank = scriptManager.callOnScriptThread(scriptFile, delegate::getRank);
        String commandName = scriptManager.callOnScriptThread(scriptFile, delegate::getCommandName);
        String description = scriptManager.callOnScriptThread(scriptFile, delegate::getDescription);

        setRank(rank == null ? 0 : rank);
        setCommandName(commandName == null ? scriptFile.getName() : commandName);
        setDescription(description == null ? "" : description);
    }

    @Override
    public void execute(FTConnection connection, List<String> params) {
        scriptManager.executeOnScriptThread(scriptFile, () -> delegate.execute(connection, params));
    }
}
