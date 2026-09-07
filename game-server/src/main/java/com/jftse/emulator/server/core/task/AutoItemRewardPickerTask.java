package com.jftse.emulator.server.core.task;

import com.jftse.emulator.server.core.handler.matchplay.MatchplayItemRewardPickHandler;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.matchplay.GameSessionManager;
import com.jftse.emulator.server.core.matchplay.MatchplayReward;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.server.core.thread.AbstractTask;

import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

public class AutoItemRewardPickerTask extends AbstractTask {
    private final ConcurrentLinkedDeque<FTClient> clients;
    private final Room room;
    private final MatchplayReward reward;
    private final Map<FTClient, RoomPlayer> participants = new java.util.IdentityHashMap<>();
    private final Map<FTClient, Long> generations = new java.util.IdentityHashMap<>();

    public AutoItemRewardPickerTask(ConcurrentLinkedDeque<FTClient> clients, short roomId) {
        this(clients, clients.isEmpty() ? null : clients.getFirst().getActiveRoom(),
                GameSessionManager.getInstance().getMatchplayReward(roomId));
    }

    public AutoItemRewardPickerTask(ConcurrentLinkedDeque<FTClient> clients, Room room, MatchplayReward reward) {
        this.clients = new ConcurrentLinkedDeque<>(clients);
        this.room = room;
        this.reward = reward;
        clients.forEach(client -> {
            synchronized (client) {
                if (client.hasPlayer() && client.getActiveRoom() == room) {
                    participants.put(client, client.getRoomPlayer());
                    generations.put(client, client.getGameSessionGeneration());
                }
            }
        });
    }

    @Override
    public void run() {
        if (room == null || reward == null) return;
        var handler = new MatchplayItemRewardPickHandler();
        for (FTClient client : clients) {
            RoomPlayer seat = participants.get(client);
            if (seat == null || seat.getPosition() >= 4) continue;
            while (reward.getSlotRewards().values().stream().noneMatch(item -> item.getClaimedPlayerPosition() == seat.getPosition())) {
                synchronized (client) {
                    if (client.getActiveRoom() != room || client.getRoomPlayer() != seat ||
                            client.getGameSessionGeneration() != generations.get(client) ||
                            GameSessionManager.getInstance().getMatchplayReward(room.getRoomId()) != reward) break;
                }
                var unclaimed = reward.getSlotRewards().entrySet().stream().filter(entry -> !entry.getValue().getClaimed().get()).toList();
                if (unclaimed.isEmpty()) break;
                byte slot = unclaimed.get((int) (Math.random() * unclaimed.size())).getKey();
                if (handler.claim(client, room, seat, generations.get(client), reward, slot)) break;
            }
        }
    }
}
