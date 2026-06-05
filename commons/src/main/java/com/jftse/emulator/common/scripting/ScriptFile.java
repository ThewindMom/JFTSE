package com.jftse.emulator.common.scripting;

import com.jftse.emulator.common.utilities.StringUtils;
import lombok.Getter;
import lombok.Setter;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

@Getter
@Setter
public class ScriptFile {
    private String name;
    private File file;
    private String type;
    private String groupPath;

    private Context context;
    private Source source;

    private ExecutorService executor;

    public ScriptFile(String name, File file, String type, String groupPath) {
        this.name = name;
        this.file = file;
        this.type = type;
        this.groupPath = groupPath;
    }

    public String getGroupKey() {
        return groupPath == null ? "" : groupPath.replace("\\", "/");
    }

    public String getScriptKey() {
        String scriptName = StringUtils.isEmpty(name)
                ? "unknown"
                : name.toLowerCase();

        String base = StringUtils.isEmpty(type)
                ? "unknown"
                : type.toLowerCase();

        String group = getGroupKey().toLowerCase();

        if (!group.isEmpty()) {
            return base + ":" + group + ":" + scriptName;
        }

        return base + ":" + scriptName;
    }

    public boolean isLibrary() {
        return "LIB".equalsIgnoreCase(type);
    }

    public String getIncludeKey() {
        if (!isLibrary()) {
            return "";
        }

        String group = getGroupKey();

        if (!group.isEmpty()) {
            return (group + "/" + name).toLowerCase();
        }

        return name.toLowerCase();
    }

    public String getScriptThreadName() {
        return "Script-" + getScriptKey();
    }

    public boolean isScriptThread() {
        return Thread.currentThread().getName().equals(getScriptThreadName());
    }

    public void execute(Runnable runnable) {
        if (executor == null || isScriptThread()) {
            runnable.run();
            return;
        }

        executor.execute(runnable);
    }

    public <T> Future<T> submit(Callable<T> callable) {
        if (executor == null || isScriptThread()) {
            CompletableFuture<T> future = new CompletableFuture<>();
            try {
                future.complete(callable.call());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
            return future;
        }

        return executor.submit(callable);
    }

    @Override
    public String toString() {
        return "ScriptFile { " +
                "name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", groupPath='" + groupPath + '\'' +
                ", file=" + file.getName() +
                " }";
    }
}
