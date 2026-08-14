package com.jftse.emulator.server.core.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchResultServiceImplTest {
    @Test
    void runsCompletionAfterClaimingDurableResult() {
        Query insert = insertQuery();
        Query select = selectQuery();
        MatchResultServiceImpl service = service(insert, select);
        AtomicInteger calls = new AtomicInteger();

        captureInsertedClaimToken(insert, select);
        assertTrue(service.executeOnce("result-1", calls::incrementAndGet));
        assertEquals(1, calls.get());
        verify(insert).setParameter("resultId", "result-1");
        verify(select).setParameter("resultId", "result-1");
    }

    @Test
    void committedDuplicateDoesNotRunCompletionAgain() {
        MatchResultServiceImpl service = service(insertQuery(), selectQueryReturning("earlier-claim"));
        AtomicInteger calls = new AtomicInteger();

        assertFalse(service.executeOnce("result-1", calls::incrementAndGet));
        assertEquals(0, calls.get());
    }

    @Test
    void completionFailureEscapesSoSpringRollsBackItsClaim() {
        Query insert = insertQuery();
        Query select = selectQuery();
        captureInsertedClaimToken(insert, select);
        MatchResultServiceImpl service = service(insert, select);

        assertThrows(IllegalStateException.class,
                () -> service.executeOnce("result-1", () -> {
                    throw new IllegalStateException("persistence failed");
                }));
    }

    @Test
    void rejectsResultIdsThatDoNotFitTheDurableKey() {
        MatchResultServiceImpl service = service(insertQuery(), selectQuery());

        assertThrows(IllegalArgumentException.class,
                () -> service.executeOnce("x".repeat(37), () -> { }));
    }

    private static MatchResultServiceImpl service(Query insert, Query select) {
        EntityManager entityManager = mock(EntityManager.class);
        when(entityManager.createNativeQuery(org.mockito.ArgumentMatchers.contains("INSERT"))).thenReturn(insert);
        when(entityManager.createNativeQuery(org.mockito.ArgumentMatchers.contains("SELECT"))).thenReturn(select);
        MatchResultServiceImpl service = new MatchResultServiceImpl();
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        return service;
    }

    private static Query insertQuery() {
        Query query = mock(Query.class);
        when(query.setParameter(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(1);
        return query;
    }

    private static Query selectQuery() {
        return selectQueryReturning(null);
    }

    private static Query selectQueryReturning(String value) {
        Query query = mock(Query.class);
        when(query.setParameter(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(value);
        return query;
    }

    private static void captureInsertedClaimToken(Query insert, Query select) {
        AtomicReference<Object> claimToken = new AtomicReference<>();
        when(insert.setParameter(org.mockito.ArgumentMatchers.eq("claimToken"),
                org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            claimToken.set(invocation.getArgument(1));
            return insert;
        });
        when(select.getSingleResult()).thenAnswer(invocation -> claimToken.get());
    }
}
