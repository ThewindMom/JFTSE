package com.jftse.emulator.server.core.matchplay;

import com.jftse.emulator.server.core.life.room.RoomPlayer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MatchplayGameSettlementTest {
    @Test
    void allowsExactlyOneConcurrentSettlement() throws Exception {
        MatchplayGame game = new TestMatchplayGame();
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger settlements = new AtomicInteger();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<? extends Future<?>> tasks = IntStream.range(0, 32)
                    .mapToObj(ignored -> executor.submit(() -> {
                        start.await();
                        if (game.beginSettlement())
                            settlements.incrementAndGet();
                        return null;
                    }))
                    .toList();

            start.countDown();
            for (Future<?> task : tasks)
                task.get();
        }

        assertEquals(1, settlements.get());
        assertFalse(game.beginSettlement());
    }

    private static final class TestMatchplayGame extends MatchplayGame {
        @Override
        public MatchplayReward getMatchRewards() {
            return null;
        }

        @Override
        public void addBonusesToRewards(java.util.concurrent.ConcurrentLinkedDeque<RoomPlayer> roomPlayers,
                                        List<PlayerReward> playerRewards) {
        }

        @Override
        protected MatchplayHandleable createHandler() {
            return null;
        }
    }
}
