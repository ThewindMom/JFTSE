package com.jftse.emulator.server.core.matchplay.guardian;

import com.jftse.emulator.server.core.matchplay.event.EventHandler;
import com.jftse.server.core.thread.ThreadManager;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MatchSchedulerTest {
    @Test
    void oneFailedInlineCallbackDoesNotLoseOtherMatchesEvents() {
        EventHandler events = new EventHandler();
        events.init();
        AtomicInteger calls = new AtomicInteger();
        events.offerJS(events.createRunnableEvent(() -> { throw new IllegalStateException("isolated script failure"); }, -1));
        events.offerJS(events.createRunnableEvent(calls::incrementAndGet, -1));
        events.handleQueuedEvents();
        assertEquals(1, calls.get());
        assertTrue(events.getFireableDeque().isEmpty());
        events.handleQueuedEvents();
        assertEquals(1, calls.get());
    }

    @Test
    void cancelledLongDelayTasksReleaseSchedulerQueueImmediately() throws Exception {
        Object previous = ReflectionTestUtils.getField(ThreadManager.class, "instance");
        ThreadManager threads = new ThreadManager();
        threads.init();
        try {
            ScheduledThreadPoolExecutor scheduler = (ScheduledThreadPoolExecutor) threads.getVirtualScheduledExecutor();
            AtomicInteger calls = new AtomicInteger();
            for (int i = 0; i < 256; i++) {
                assertTrue(threads.schedule(calls::incrementAndGet, 1, TimeUnit.HOURS).cancel(false));
            }
            assertEquals(0, scheduler.getQueue().size(), "cancelled match tasks must not retain their sessions until deadline");
            assertEquals(0, calls.get());
        } finally {
            threads.getVirtualScheduledExecutor().shutdownNow();
            threads.getVirtualThreadExecutor().shutdownNow();
            assertTrue(threads.getVirtualScheduledExecutor().awaitTermination(5, TimeUnit.SECONDS));
            ReflectionTestUtils.setField(ThreadManager.class, "instance", previous);
        }
    }

    @Test
    void realAsyncExecutorCompletesEachQueuedEventOnceWhileCancelledEventsNeverRun() throws Exception {
        Object previous = ReflectionTestUtils.getField(ThreadManager.class, "instance");
        ThreadManager threads = new ThreadManager();
        threads.init();
        try {
            EventHandler events = new EventHandler();
            events.init();
            CountDownLatch completed = new CountDownLatch(256);
            AtomicInteger calls = new AtomicInteger();
            for (int i = 0; i < 256; i++) {
                events.offer(events.createRunnableEvent(() -> { calls.incrementAndGet(); completed.countDown(); }, -1));
                var cancelled = events.createRunnableEvent(() -> calls.addAndGet(1000), -1);
                cancelled.setCancelled(true);
                events.offer(cancelled);
            }
            events.handleQueuedEvents();
            assertTrue(completed.await(5, TimeUnit.SECONDS));
            events.handleQueuedEvents();
            assertEquals(256, calls.get());
            assertTrue(events.getFireableDeque().isEmpty());
        } finally {
            threads.getVirtualScheduledExecutor().shutdownNow();
            threads.getVirtualThreadExecutor().shutdownNow();
            assertTrue(threads.getVirtualThreadExecutor().awaitTermination(5, TimeUnit.SECONDS));
            ReflectionTestUtils.setField(ThreadManager.class, "instance", previous);
        }
    }
}
