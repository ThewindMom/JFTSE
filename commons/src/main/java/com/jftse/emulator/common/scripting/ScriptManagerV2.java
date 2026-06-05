package com.jftse.emulator.common.scripting;

import lombok.extern.log4j.Log4j2;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Log4j2
public class ScriptManagerV2 {
    private static ScriptManagerV2 instance;

    private final ConcurrentHashMap<String, List<ScriptFile>> scripts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ScriptFile> libraries = new ConcurrentHashMap<>();

    public final static List<String> allowedTypes = Arrays.asList("EVENT", "QUEST", "COMMAND", "GUARDIAN-PHASE", "LIB");

    private ScriptManagerV2() {
        initialize(new ArrayList<>());
    }

    public ScriptManagerV2(List<ScriptFile> scriptFilesList) {
        initialize(scriptFilesList);
        instance = this;
    }

    public static ScriptManagerV2 getInstance() {
        return instance == null ? instance = new ScriptManagerV2() : instance;
    }

    private void initialize(List<ScriptFile> scriptFilesList) {
        scriptFilesList.removeIf(scriptFile -> !hasScriptValidType(scriptFile.getType()));

        for (ScriptFile scriptFile : scriptFilesList) {
            try {
                Context context = Context.newBuilder("js")
                        .allowHostAccess(HostAccess.ALL)
                        .allowHostClassLookup(s -> true)
                        .option("engine.WarnInterpreterOnly", "false")
                        .build();

                Source source = Source.newBuilder("js", new FileReader(scriptFile.getFile()), scriptFile.getName()).build();

                scriptFile.setContext(context);
                scriptFile.setSource(source);

                if (!scriptFile.isLibrary()) {
                    ensureExecutor(scriptFile);
                }

                if (scriptFile.isLibrary()) {
                    libraries.put(scriptFile.getIncludeKey(), scriptFile);
                    log.info("Library loaded: {}", scriptFile.getIncludeKey());
                } else {
                    scripts.computeIfAbsent(scriptFile.getType(), k -> new ArrayList<>()).add(scriptFile);
                    log.info("Script loaded: {} of type {}", scriptFile.getName(), scriptFile.getType());
                }
            } catch (IOException e) {
                log.error("Error reading script file: {}", scriptFile.getFile().getAbsolutePath(), e);
            } catch (Exception e) {
                log.error("Error evaluating script file: {}", scriptFile.getName(), e);
            }
        }
    }

    private ExecutorService createScriptExecutor(ScriptFile scriptFile) {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, scriptFile.getScriptThreadName());
            t.setDaemon(true);
            return t;
        });
    }

    public void ensureExecutor(ScriptFile scriptFile) {
        if (scriptFile.isLibrary()) {
            return;
        }

        if (scriptFile.getExecutor() != null && !scriptFile.getExecutor().isShutdown()) {
            return;
        }

        scriptFile.setExecutor(createScriptExecutor(scriptFile));
    }

    private boolean hasScriptValidType(String type) {
        return allowedTypes.stream().anyMatch(t -> t.equalsIgnoreCase(type));
    }

    public List<ScriptFile> getScriptFiles(String type) {
        return scripts.getOrDefault(type, new ArrayList<>());
    }

    public Value eval(ScriptFile scriptFile, Map<String, Object> bindings) {
        ensureExecutor(scriptFile);

        Callable<Value> task = () -> {
            final Context context = scriptFile.getContext();
            final Source source = scriptFile.getSource();

            try {
                Value jsBindings = context.getBindings("js");
                bindings.forEach(jsBindings::putMember);
                installInclude(scriptFile, context, jsBindings);

                return context.eval(source);
            } catch (Exception e) {
                log.error("Error evaluating script: {}", scriptFile.getName(), e);
                return null;
            }
        };

        try {
            Future<Value> future = scriptFile.submit(task);
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while evaluating script: {}", scriptFile.getName(), e);
            return null;
        } catch (ExecutionException e) {
            log.error("Error evaluating script on executor: {}", scriptFile.getName(), e);
            return null;
        }
    }

    public <T> T getInterfaceByImplementingObject(ScriptFile scriptFile, String key, Class<T> interfaceClass, Map<String, Object> bindings) {
        eval(scriptFile, bindings);

        Callable<T> task = () -> {
            final Context context = scriptFile.getContext();

            try {
                Value jsBindings = context.getBindings("js");
                Value member = jsBindings.getMember(key);

                if (member == null || member.isNull()) {
                    log.error("No member found for key: {} in script: {}", key, scriptFile.getName());
                    return null;
                }

                return member.as(interfaceClass);
            } catch (Exception e) {
                log.error("Error getting interface '{}' from script: {}", key, scriptFile.getName(), e);
                return null;
            }
        };

        try {
            Future<T> future = scriptFile.submit(task);
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while getting interface '{}' from script: {}", key, scriptFile.getName(), e);
            return null;
        } catch (ExecutionException e) {
            log.error("Error getting interface '{}' on executor from script: {}", key, scriptFile.getName(), e);
            return null;
        }
    }

    private void installInclude(ScriptFile owner, Context context, Value jsBindings) {
        if (jsBindings.hasMember("include")) {
            return;
        }

        jsBindings.putMember("include", new IncludeFunction(owner, context, libraries));
    }

    public <T> T callOnScriptThread(ScriptFile scriptFile, Callable<T> task) {
        ensureExecutor(scriptFile);

        try {
            Future<T> future = scriptFile.submit(task);
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while executing script task: {}", scriptFile.getScriptKey(), e);
            return null;
        } catch (ExecutionException e) {
            log.error("Error while executing script task: {}", scriptFile.getScriptKey(), e);
            return null;
        }
    }

    public void executeOnScriptThread(ScriptFile scriptFile, Runnable task) {
        ensureExecutor(scriptFile);

        try {
            scriptFile.execute(() -> {
                try {
                    task.run();
                } catch (Exception e) {
                    log.error("Error while executing script runnable: {}", scriptFile.getScriptKey(), e);
                }
            });
        } catch (Exception e) {
            log.error("Failed to submit script runnable: {}", scriptFile.getScriptKey(), e);
        }
    }

    public void shutdownExecutors(String type) {
        for (ScriptFile scriptFile : getScriptFiles(type)) {
            if (scriptFile.getExecutor() != null) {
                scriptFile.getExecutor().shutdownNow();
                scriptFile.setExecutor(null);
            }
        }
    }

    public void shutdownAllExecutors() {
        scripts.values().forEach(list -> {
            for (ScriptFile scriptFile : list) {
                if (scriptFile.getExecutor() != null) {
                    scriptFile.getExecutor().shutdownNow();
                    scriptFile.setExecutor(null);
                }
            }
        });
    }
}
