package com.jftse.emulator.server.core.service;

public interface MatchResultService {
    /**
     * Runs a match's persistence work once, in the same transaction as its
     * durable result claim. A failed completion rolls the claim back so it can
     * be retried; an already committed result returns {@code false}.
     */
    boolean executeOnce(String resultId, Runnable completion);
}
