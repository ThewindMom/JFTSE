package com.jftse.emulator.server.core.life.event;

import com.jftse.emulator.common.scripting.ScriptFile;
import lombok.extern.log4j.Log4j2;

import java.util.concurrent.RejectedExecutionException;

@Log4j2
public class ScriptEventBus {
    private final GameEventBus delegate;
    private final ScriptFile scriptFile;

    public ScriptEventBus(GameEventBus delegate, ScriptFile scriptFile) {
        this.delegate = delegate;
        this.scriptFile = scriptFile;
    }

    public void on(String eventType, GameEventCallback listener) {
        delegate.on(eventType, args -> {
            if (scriptFile.getExecutor() == null || scriptFile.getExecutor().isShutdown()) {
                log.warn("Script executor is not available for script {}", scriptFile.getScriptKey());
                return;
            }

            try {
                scriptFile.execute(() -> {
                    try {
                        listener.onEvent(args);
                    } catch (Exception e) {
                        log.error("Error while executing script event callback. script={}, event={}, error={}",
                                scriptFile.getScriptKey(),
                                eventType,
                                e.getMessage(), e);
                    }
                });
            } catch (RejectedExecutionException e) {
                log.warn("Script event rejected because executor is shutting down. script={}, event={}", scriptFile.getScriptKey(), eventType);
            }
        });
    }

    public void call(String eventType, Object... args) {
        delegate.call(eventType, args);
    }
}
