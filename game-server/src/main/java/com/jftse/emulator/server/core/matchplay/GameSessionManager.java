package com.jftse.emulator.server.core.matchplay;

import com.jftse.emulator.server.core.life.room.GameSession;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Getter
@Log4j2
public class GameSessionManager {
    private static GameSessionManager instance;
    private static final SecureRandom SESSION_ID_RANDOM = new SecureRandom();

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
        Integer id = nextSessionId();
        while (gameSessionList.putIfAbsent(id, gameSession) != null) {
            id = nextSessionId();
        }
        return id;
    }

    private static int nextSessionId() {
        return SESSION_ID_RANDOM.nextInt(100_000);
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

    public boolean removeMatchplayReward(int roomId, MatchplayReward matchplayReward) {
        return matchplayRewardList.remove(roomId, matchplayReward);
    }

    public boolean hasMatchplayReward(int roomId) {
        return matchplayRewardList.containsKey(roomId);
    }
}
