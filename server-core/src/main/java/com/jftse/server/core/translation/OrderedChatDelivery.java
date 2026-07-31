package com.jftse.server.core.translation;

import lombok.extern.log4j.Log4j2;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

@Log4j2
public final class OrderedChatDelivery {
    private CompletableFuture<Void> tail = CompletableFuture.completedFuture(null);

    public synchronized void enqueue(
            CompletionStage<String> message,
            Consumer<String> delivery
    ) {
        CompletableFuture<Void> previous = tail.handle((ignored, failure) -> null);
        tail = previous
                .thenCombine(message, (ignored, text) -> text)
                .thenAccept(delivery)
                .exceptionally(failure -> {
                    log.warn("Unable to deliver ordered chat message: {}", failure.getMessage());
                    return null;
                });
    }
}
