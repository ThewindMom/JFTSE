package com.jftse.emulator.server.core.life.room;

import com.jftse.emulator.server.core.client.PetView;
import com.jftse.emulator.server.core.matchplay.MatchplayGame;
import com.jftse.emulator.server.core.matchplay.event.Fireable;
import com.jftse.emulator.server.core.matchplay.event.RunnableEvent;
import com.jftse.emulator.server.core.matchplay.game.MatchplayBasicGame;
import com.jftse.emulator.server.core.matchplay.game.MatchplayBattleGame;
import com.jftse.emulator.server.core.matchplay.game.MatchplayGuardianGame;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.entities.database.model.pet.Pet;
import com.jftse.entities.database.model.pet.PetStatistic;
import com.jftse.server.core.constants.GameMode;
import lombok.Getter;
import lombok.Setter;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
@Setter
public class GameSession {
    public GameSession() {
        this(false);
    }

    public GameSession(boolean battlemon) {
        clients = new ConcurrentLinkedDeque<>();
        fireables = new ConcurrentLinkedDeque<>();
        battlemonActors = new ConcurrentHashMap<>();
        completionHandled = new AtomicBoolean(false);
        relayAuthorizationRevoked = new AtomicBoolean(false);
        relayAuthorizationRevocationAttempts = new AtomicInteger(0);
        relayAuthorizationGeneration = UUID.randomUUID().toString();
        this.battlemon = battlemon;
        gameplayActorPositions = List.of();
    }

    private MatchplayGame matchplayGame;
    private int players;
    private int lastBallHitByPlayer = -1;
    private long timeLastBallWasHit = -1;
    private int timesCourtChanged = 0;
    private ConcurrentLinkedDeque<FTClient> clients;
    private ConcurrentLinkedDeque<Fireable> fireables;
    private ConcurrentHashMap<Short, BattlemonActor> battlemonActors;
    private AtomicBoolean completionHandled;
    private AtomicBoolean relayAuthorizationRevoked;
    private AtomicInteger relayAuthorizationRevocationAttempts;
    private final String relayAuthorizationGeneration;
    private final boolean battlemon;
    private volatile List<Short> gameplayActorPositions;
    private volatile RunnableEvent countDownRunnable;
    private int mode;

    public void setMatchplayGame(MatchplayGame game) {
        final int gameMode;
        if (game instanceof MatchplayBasicGame) {
            gameMode = GameMode.BASIC;
        } else if (game instanceof MatchplayBattleGame) {
            gameMode = GameMode.BATTLE;
        } else if (game instanceof MatchplayGuardianGame) {
            gameMode = GameMode.GUARDIAN;
        } else {
            throw new IllegalArgumentException("matchplay game not supported: " + game.getClass().getName());
        }

        this.matchplayGame = game;
        this.mode = gameMode;
    }

    public boolean isBasicMode() {
        return mode == GameMode.BASIC;
    }

    public boolean isBattleMode() {
        return mode == GameMode.BATTLE;
    }

    public boolean isGuardianMode() {
        return mode == GameMode.GUARDIAN;
    }

    public boolean isBattlemon() {
        return battlemon;
    }

    public void addBattlemonActor(RoomPlayer owner, Pet pet) {
        Objects.requireNonNull(owner, "Battlemon owner must not be null");
        Objects.requireNonNull(pet, "Battlemon pet must not be null");
        PetStatistic statistic = Objects.requireNonNull(pet.getPetStatistic(), "Battlemon pet statistic must not be null");
        short ownerPosition = owner.getPosition();
        if (ownerPosition < 0 || ownerPosition > 1) {
            throw new IllegalArgumentException("Battlemon owners must occupy position 0 or 1");
        }
        boolean hasOwnerEndpoint = clients.stream()
                .map(FTClient::getRoomPlayer)
                .anyMatch(roomPlayer -> roomPlayer != null &&
                        roomPlayer.getPlayerId() == owner.getPlayerId() &&
                        roomPlayer.getPosition() == ownerPosition);
        if (!hasOwnerEndpoint) {
            throw new IllegalArgumentException("Battlemon owner must have a matching client endpoint");
        }
        if (getBattlemonActorForOwner(owner.getPlayerId()) != null) {
            throw new IllegalStateException("Battlemon owner already has a gameplay actor");
        }

        short actorPosition = (short) (ownerPosition + 2);
        boolean actorPositionHasPlayer = clients.stream()
                .map(FTClient::getRoomPlayer)
                .anyMatch(roomPlayer -> roomPlayer != null && roomPlayer.getPosition() == actorPosition);
        if (actorPositionHasPlayer) {
            throw new IllegalStateException("Battlemon gameplay position is occupied by a player");
        }
        BattlemonActor actor = new BattlemonActor(
                actorPosition,
                ownerPosition,
                owner.getPlayerId(),
                PetView.of(pet),
                statistic.getBasicRecordWin(),
                statistic.getBasicRecordLoss(),
                statistic.getBattleRecordWin(),
                statistic.getBattleRecordLoss(),
                statistic.getConsecutiveWins()
        );
        if (battlemonActors.putIfAbsent(actorPosition, actor) != null) {
            throw new IllegalStateException("Battlemon gameplay position is already occupied");
        }
    }

    public Collection<BattlemonActor> getBattlemonActors() {
        return battlemonActors.values();
    }

    public BattlemonActor getBattlemonActorForOwner(long ownerPlayerId) {
        return battlemonActors.values().stream()
                .filter(actor -> actor.ownerPlayerId() == ownerPlayerId)
                .findFirst()
                .orElse(null);
    }

    public BattlemonActor getBattlemonActor(int actorPosition) {
        return battlemonActors.get((short) actorPosition);
    }

    public boolean isActorOwnedBy(RoomPlayer owner, int actorPosition) {
        if (owner == null) {
            return false;
        }
        if (gameplayActorPositions.contains((short) actorPosition) && owner.getPosition() == actorPosition) {
            return true;
        }
        BattlemonActor actor = battlemonActors.get((short) actorPosition);
        return actor != null && actor.ownerPlayerId() == owner.getPlayerId();
    }

    public boolean isGameplayEndpoint(FTClient client) {
        if (client == null) {
            return false;
        }
        RoomPlayer roomPlayer = client.getRoomPlayer();
        return roomPlayer != null
                ? gameplayActorPositions.contains(roomPlayer.getPosition())
                : !client.isSpectator() && clients.contains(client);
    }

    public int getOwnerPositionForActor(int actorPosition) {
        BattlemonActor actor = battlemonActors.get((short) actorPosition);
        return actor == null ? actorPosition : actor.ownerPosition();
    }

    public synchronized void initializeGameplayActorPositions() {
        if (!gameplayActorPositions.isEmpty()) {
            throw new IllegalStateException("Gameplay actor positions are already initialized");
        }

        List<Short> positions = clients.stream()
                .map(FTClient::getRoomPlayer)
                .filter(roomPlayer -> roomPlayer != null && roomPlayer.getPosition() >= 0 && roomPlayer.getPosition() < 4)
                .map(RoomPlayer::getPosition)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        positions.addAll(battlemonActors.keySet());
        positions = positions.stream().distinct().sorted(Comparator.naturalOrder()).toList();

        if (battlemon && !positions.equals(List.of((short) 0, (short) 1, (short) 2, (short) 3))) {
            throw new IllegalStateException("Battlemon gameplay actors must occupy positions 0, 1, 2 and 3");
        }
        if (positions.isEmpty()) {
            throw new IllegalStateException("Game session has no gameplay actors");
        }
        gameplayActorPositions = List.copyOf(positions);
    }

    public List<Short> getGameplayActorPositions() {
        return gameplayActorPositions;
    }

    public boolean isValid() {
        return matchplayGame != null;
    }

    public FTClient getClientByPlayerId(long playerId) {
        return clients.stream()
                .filter(c -> c.hasPlayer() && c.getPlayer().getId() == playerId)
                .findFirst()
                .orElse(null);
    }

    public void clearCountDownRunnable() {
        if (this.getCountDownRunnable() != null) {
            this.getFireables().remove(this.getCountDownRunnable());
            this.getCountDownRunnable().setCancelled(true);
            this.setCountDownRunnable(null);
        }
    }

    public record BattlemonActor(short position, short ownerPosition, long ownerPlayerId, PetView pet,
                                  int basicWins, int basicLosses, int battleWins, int battleLosses,
                                  int consecutiveWins) {
    }
}
