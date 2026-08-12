package com.jftse.emulator.server.core.matchplay;

import com.jftse.emulator.server.core.life.room.GameSession;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Getter
@Log4j2
public class GameSessionManager {
    private static GameSessionManager instance;

    private ConcurrentHashMap<Integer, GameSession> gameSessionList;
    private ConcurrentHashMap<Integer, MatchplayReward> matchplayRewardList;

    @PostConstruct
    public void init() {
        instance = this;
        gameSessionList = new ConcurrentHashMap<>();
        matchplayRewardList = new ConcurrentHashMap<>();

        log.info(this.getClass().getSimpleName() + " initialized");
    }

    public static GameSessionManager getInstance() {
        return instance;
    }

    public Integer addGameSession(GameSession gameSession) {
        int serverType = Math.max(0, Math.min(9, Integer.getInteger("GameServerType", 1)));
        Integer id = serverType * 10_000 + ThreadLocalRandom.current().nextInt(10_000);
        while (gameSessionList.putIfAbsent(id, gameSession) != null) {
            id = serverType * 10_000 + ThreadLocalRandom.current().nextInt(10_000);
        }
        return id;
    }
    public boolean removeGameSession(Integer gameSessionId, GameSession gameSession) {
        return gameSessionList.remove(gameSessionId, gameSession);
    }

    public GameSession getGameSessionBySessionId(int sessionId) {
        return gameSessionList.get(sessionId);
    }

    public void addMatchplayReward(int roomId, MatchplayReward matchplayReward) {
        matchplayRewardList.put(roomId, matchplayReward);
    }

    public MatchplayReward getMatchplayReward(int roomId) {
        return matchplayRewardList.get(roomId);
    }

    public void removeMatchplayReward(int roomId) {
        matchplayRewardList.remove(roomId);
    }

    public boolean hasMatchplayReward(int roomId) {
        return matchplayRewardList.containsKey(roomId);
    }
}
