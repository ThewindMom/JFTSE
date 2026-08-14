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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

@Getter
@Setter
public class GameSession {
    public GameSession() {
        this(false);
    }

    public GameSession(boolean dedicatedBattlemonRoom) {
        clients = new ConcurrentLinkedDeque<>();
        fireables = new ConcurrentLinkedDeque<>();
        actors = new ConcurrentHashMap<>();
        completionHandled = new AtomicBoolean(false);
        this.dedicatedBattlemonRoom = dedicatedBattlemonRoom;
        gameplayActorPositions = List.of();
    }

    private MatchplayGame matchplayGame;
    private int players;
    private int lastBallHitByPlayer = -1;
    private long timeLastBallWasHit = -1;
    private int timesCourtChanged = 0;
    private ConcurrentLinkedDeque<FTClient> clients;
    private ConcurrentLinkedDeque<Fireable> fireables;
    private ConcurrentHashMap<Short, GameplayActor> actors;
    private AtomicBoolean completionHandled;
    private final boolean dedicatedBattlemonRoom;
    private volatile List<Short> gameplayActorPositions;
    private volatile RunnableEvent countDownRunnable;
    private int mode;

    public void setMatchplayGame(MatchplayGame game) {
        final int gameMode;
        if (game instanceof MatchplayBasicGame) {
            gameMode = GameMode.BASIC;
        } else if (game instanceof MatchplayBattleGame) {
            gameMode = GameMode.BATTLE;
        } else {
            gameMode = GameMode.GUARDIAN;
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

    /**
     * Dedicated Battlemon rooms ({@code roomType=2}) require the full 0–3 seating
     * policy. Ordinary rooms may still attach optional pet seats; matchplay must
     * not branch on this flag.
     */
    public boolean isDedicatedBattlemonRoom() {
        return dedicatedBattlemonRoom;
    }

    public void addOwnedPetSeat(RoomPlayer owner, Pet pet) {
        Objects.requireNonNull(owner, "Pet owner must not be null");
        Objects.requireNonNull(pet, "Pet must not be null");
        PetStatistic statistic = Objects.requireNonNull(pet.getPetStatistic(), "Pet statistic must not be null");
        short ownerPosition = owner.getPosition();
        if (ownerPosition < 0 || ownerPosition > 1) {
            throw new IllegalArgumentException("Pet owners must occupy position 0 or 1");
        }
        boolean hasOwnerEndpoint = clients.stream()
                .map(FTClient::getRoomPlayer)
                .anyMatch(roomPlayer -> roomPlayer != null &&
                        roomPlayer.getPlayerId() == owner.getPlayerId() &&
                        roomPlayer.getPosition() == ownerPosition);
        if (!hasOwnerEndpoint) {
            throw new IllegalArgumentException("Pet owner must have a matching client endpoint");
        }
        if (getOwnedPetSeat(owner.getPlayerId()) != null) {
            throw new IllegalStateException("Owner already has a pet seat");
        }

        short actorPosition = (short) (ownerPosition + 2);
        boolean actorPositionHasPlayer = clients.stream()
                .map(FTClient::getRoomPlayer)
                .anyMatch(roomPlayer -> roomPlayer != null && roomPlayer.getPosition() == actorPosition);
        if (actorPositionHasPlayer) {
            throw new IllegalStateException("Pet gameplay position is occupied by a player");
        }
        GameplayActor actor = new GameplayActor(
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
        if (actors.putIfAbsent(actorPosition, actor) != null) {
            throw new IllegalStateException("Pet gameplay position is already occupied");
        }
    }

    public Collection<GameplayActor> getActors() {
        return actors.values();
    }

    public Collection<GameplayActor> getOwnedPetSeats() {
        return actors.values().stream().filter(actor -> !actor.isHuman()).toList();
    }

    public GameplayActor getOwnedPetSeat(long ownerPlayerId) {
        return actors.values().stream()
                .filter(actor -> !actor.isHuman() && actor.ownerPlayerId() == ownerPlayerId)
                .findFirst()
                .orElse(null);
    }

    public GameplayActor getActor(int actorPosition) {
        return actors.get((short) actorPosition);
    }

    public boolean hasOwnedPetSeats() {
        return actors.values().stream().anyMatch(actor -> !actor.isHuman());
    }

    public boolean isHumanSeat(int actorPosition) {
        GameplayActor actor = actors.get((short) actorPosition);
        return actor == null || actor.isHuman();
    }

    public boolean isActorOwnedBy(RoomPlayer owner, int actorPosition) {
        if (owner == null) {
            return false;
        }
        GameplayActor actor = actors.get((short) actorPosition);
        if (actor != null) {
            return actor.ownerPlayerId() == owner.getPlayerId();
        }
        return gameplayActorPositions.contains((short) actorPosition) && owner.getPosition() == actorPosition;
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
        GameplayActor actor = actors.get((short) actorPosition);
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
        for (RoomPlayer roomPlayer : clients.stream()
                .map(FTClient::getRoomPlayer)
                .filter(player -> player != null && player.getPosition() >= 0 && player.getPosition() < 4)
                .toList()) {
            actors.putIfAbsent(roomPlayer.getPosition(), humanSeat(roomPlayer));
        }
        positions.addAll(actors.keySet());
        positions = positions.stream().distinct().sorted(Comparator.naturalOrder()).toList();

        if (dedicatedBattlemonRoom && !positions.equals(List.of((short) 0, (short) 1, (short) 2, (short) 3))) {
            throw new IllegalStateException("Dedicated Battlemon rooms must occupy seats 0, 1, 2 and 3");
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

    private static GameplayActor humanSeat(RoomPlayer owner) {
        return new GameplayActor(
                owner.getPosition(),
                owner.getPosition(),
                owner.getPlayerId(),
                null,
                0,
                0,
                0,
                0,
                0
        );
    }
}
