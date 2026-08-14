package com.jftse.emulator.server.core.matchplay.handler;

import com.jftse.emulator.common.service.ConfigService;
import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.client.PlayerStatisticView;
import com.jftse.emulator.server.core.constants.MiscConstants;
import com.jftse.emulator.server.core.constants.PacketEventType;
import com.jftse.emulator.server.core.constants.RoomStatus;
import com.jftse.emulator.server.core.constants.ServeType;
import com.jftse.emulator.server.core.life.event.GameEventBus;
import com.jftse.emulator.server.core.life.event.GameEventType;
import com.jftse.emulator.server.core.life.item.BaseItem;
import com.jftse.emulator.server.core.life.item.ItemFactory;
import com.jftse.emulator.server.core.life.item.special.RingOfExp;
import com.jftse.emulator.server.core.life.item.special.RingOfGold;
import com.jftse.emulator.server.core.life.item.special.RingOfWiseman;
import com.jftse.emulator.server.core.life.match.PlayerStats;
import com.jftse.emulator.server.core.life.match.RallyResult;
import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.life.room.GameplayActor;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.life.room.ServeInfo;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.matchplay.GameSessionManager;
import com.jftse.emulator.server.core.matchplay.MatchplayHandleable;
import com.jftse.emulator.server.core.matchplay.MatchplayReward;
import com.jftse.emulator.server.core.matchplay.PlayerReward;
import com.jftse.emulator.server.core.matchplay.event.EventHandler;
import com.jftse.emulator.server.core.matchplay.game.MatchplayBasicGame;
import com.jftse.emulator.server.core.packets.matchplay.*;
import com.jftse.emulator.server.core.packets.pet.S2CPetDataAnswerPacket;
import com.jftse.emulator.server.core.rabbit.MatchRallyStatsConsumer;
import com.jftse.emulator.server.core.rabbit.messages.MatchFinishedMessage;
import com.jftse.emulator.server.core.rabbit.service.RProducerService;
import com.jftse.emulator.server.core.task.AutoItemRewardPickerTask;
import com.jftse.emulator.server.core.utils.RankingUtils;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.log.GameLog;
import com.jftse.entities.database.model.log.GameLogType;
import com.jftse.entities.database.model.map.SMaps;
import com.jftse.entities.database.model.pet.Pet;
import com.jftse.entities.database.model.player.PlayerStatistic;
import com.jftse.server.core.constants.GameMode;
import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.protocol.PacketOperations;
import com.jftse.server.core.service.*;
import com.jftse.server.core.shared.packets.matchplay.CMSGPoint;
import lombok.extern.log4j.Log4j2;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Log4j2
public class MatchplayBasicModeHandler implements MatchplayHandleable {
    private final MatchplayBasicGame game;
    private final EventHandler eventHandler;
    private final GameLogService gameLogService;
    private final LevelService levelService;
    private final PocketService pocketService;
    private final PlayerStatisticService playerStatisticService;
    private final PetService petService;
    private final MapService mapService;
    private final MatchRallyStatsConsumer matchRallyStatsConsumer;

    private final Map<Integer, List<RallyResult>> rallyResultMap = new HashMap<>();

    public MatchplayBasicModeHandler(MatchplayBasicGame game) {
        this.game = game;
        this.eventHandler = GameManager.getInstance().getEventHandler();
        this.gameLogService = ServiceManager.getInstance().getGameLogService();
        this.levelService = ServiceManager.getInstance().getLevelService();
        this.pocketService = ServiceManager.getInstance().getPocketService();
        this.playerStatisticService = ServiceManager.getInstance().getPlayerStatisticService();
        this.petService = ServiceManager.getInstance().getPetService();
        this.mapService = ServiceManager.getInstance().getMapService();
        this.matchRallyStatsConsumer = GameManager.getInstance().getMatchRallyStatsConsumer();
    }

    @Override
    public void onStart(final FTClient ftClient) {
        Packet removeBlackBarsPacket = new Packet(PacketOperations.S2CGameRemoveBlackBars);
        GameManager.getInstance().sendPacketToAllClientsInSameGameSession(removeBlackBarsPacket, ftClient.getConnection());

        List<ServeInfo> serveInfo = new ArrayList<>();

        GameSession gameSession = ftClient.getActiveGameSession();
        if (!isEnhancedActorSession(gameSession)) {
            for (FTClient client : gameSession.getClients()) {
                RoomPlayer rp = client.getRoomPlayer();
                if (rp == null || rp.getPosition() >= 4)
                    continue;

                byte serveType = ServeType.None;
                if (rp.getPosition() == 0) {
                    serveType = ServeType.ServeBall;
                    game.getServePlayer().set(rp);
                } else if (rp.getPosition() == 1) {
                    serveType = ServeType.ReceiveBall;
                    game.getReceiverPlayer().set(rp);
                }
                ServeInfo playerServeInfo = new ServeInfo();
                playerServeInfo.setPlayerPosition(rp.getPosition());
                playerServeInfo.setPlayerStartLocation(game.getPlayerLocationsOnMap().get(rp.getPosition()));
                playerServeInfo.setServeType(serveType);
                serveInfo.add(playerServeInfo);
            }
            GameManager.getInstance().sendPacketToAllClientsInSameGameSession(
                    new S2CMatchplayTriggerServe(serveInfo), ftClient.getConnection());
            return;
        }

        game.getServePlayerPosition().set(0);
        game.getReceiverPlayerPosition().set(1);
        for (short position : gameSession.getGameplayActorPositions()) {
            Point playerLocation = game.getPlayerLocationsOnMap().get(position);

            byte serveType = ServeType.None;
            if (position == 0) {
                serveType = ServeType.ServeBall;
            } else if (position == 1) {
                serveType = ServeType.ReceiveBall;
            }

            ServeInfo playerServeInfo = new ServeInfo();
            playerServeInfo.setPlayerPosition(position);
            playerServeInfo.setPlayerStartLocation(playerLocation);
            playerServeInfo.setServeType(serveType);
            serveInfo.add(playerServeInfo);
        }
        if (!game.isSingles()) {
            game.setPlayerLocationsForDoubles(serveInfo);
            serveInfo.stream()
                    .filter(info -> info.getServeType() == ServeType.ReceiveBall)
                    .findFirst()
                    .ifPresent(receiver -> game.getReceiverPlayerPosition().set(receiver.getPlayerPosition()));
        }
        S2CMatchplayTriggerServe matchplayTriggerServe = new S2CMatchplayTriggerServe(serveInfo);
        GameManager.getInstance().sendPacketToAllClientsInSameGameSession(matchplayTriggerServe, ftClient.getConnection());
    }

    @Override
    public void onEnd(final FTClient ftClient) {
        GameSession gameSession = ftClient.getActiveGameSession();
        if (gameSession == null)
            return;

        final Integer gameSessionId = ftClient.getGameSessionId();

        Room activeRoom = ftClient.getActiveRoom();
        if (activeRoom == null)
            return;

        boolean enhancedActorSession = isEnhancedActorSession(gameSession);
        if (enhancedActorSession && !gameSession.getCompletionHandled().compareAndSet(false, true))
            return;

        if (!enhancedActorSession)
            activeRoom.setStatus(RoomStatus.NotRunning);

        boolean completionSucceeded = false;
        try {

        gameSession.getFireables().forEach(f -> f.setCancelled(true));
        gameSession.getFireables().clear();

        StringBuilder gameLogContent = new StringBuilder();

        gameLogContent.append("Basic game finished. ");
        boolean redTeamWon = game.getSetsRedTeam().get() == 2;
        gameLogContent.append(redTeamWon ? "Red " : "Blue ").append("team won. ");

        MatchplayReward matchplayReward = game.getMatchRewards();
        if (enhancedActorSession)
            matchplayReward.getPlayerRewards().removeIf(reward ->
                    !gameSession.isHumanSeat(reward.getPlayerPosition()));
        ConcurrentLinkedDeque<FTClient> clients = gameSession.getClients();
        List<FTPlayer> playerList = clients.stream()
                .filter(FTClient::hasPlayer)
                .map(FTClient::getPlayer)
                .collect(Collectors.toList());

        game.addBonusesToRewards(activeRoom.getRoomPlayerList(), matchplayReward.getPlayerRewards());

        GameSessionManager.getInstance().addMatchplayReward(activeRoom.getRoomId(), matchplayReward);

        List<MatchFinishedMessage.PlayerDto> playerDtoList = new ArrayList<>();

        for (final FTClient client : clients) {
            if (!client.hasPlayer())
                continue;

            RoomPlayer rp = client.getRoomPlayer();
            if (rp == null)
                continue;

            final boolean isActivePlayer = rp.getPosition() < 4;
            final boolean isCurrentPlayerInRedTeam = game.isRedTeam(rp.getPosition());
            if (isActivePlayer) {
                gameLogContent.append(isCurrentPlayerInRedTeam ? "red " : "blue ").append(rp.getName()).append(" acc: ").append(rp.getAccountId()).append("; ");

                boolean wonGame = (isCurrentPlayerInRedTeam && game.getSetsRedTeam().get() == 2) || (!isCurrentPlayerInRedTeam && game.getSetsBlueTeam().get() == 2);

                PlayerReward playerReward = matchplayReward.getPlayerReward(rp.getPosition());
                if (playerReward == null) {
                    playerReward = new PlayerReward(rp.getPosition());
                }

                FTPlayer player = client.getPlayer();

                playerDtoList.add(new MatchFinishedMessage.PlayerDto(player.getName(), isCurrentPlayerInRedTeam ? "red" : "blue"));

                List<BaseItem> ringItemList = new ArrayList<>();
                if (!rp.isRingOfWisemanEquipped()) {
                    if (rp.isRingOfExpEquipped()) {
                        RingOfExp ringOfExp = (RingOfExp) ItemFactory.getItem(rp.getPpIdRingExp(), player.getPocketId());
                        if (ringOfExp != null) {
                            ringItemList.add(ringOfExp);
                        }
                    }
                    if (rp.isRingOfGoldEquipped()) {
                        RingOfGold ringOfGold = (RingOfGold) ItemFactory.getItem(rp.getPpIdRingGold(), player.getPocketId());
                        if (ringOfGold != null) {
                            ringItemList.add(ringOfGold);
                        }
                    }
                } else {
                    RingOfWiseman ringOfWiseman = (RingOfWiseman) ItemFactory.getItem(rp.getPpIdRingWiseman(), player.getPocketId());
                    if (ringOfWiseman != null) {
                        ringItemList.add(ringOfWiseman);
                    }
                }

                for (BaseItem ring : ringItemList) {
                    if (ring.processPlayer(player) && ring.processPocket(player.getPocketId())) {
                        ring.getPacketsToSend().forEach((playerId, packets) -> {
                            final FTConnection connectionByPlayerId = GameManager.getInstance().getConnectionByPlayerId(playerId);
                            if (connectionByPlayerId != null)
                                connectionByPlayerId.sendTCP(packets.toArray(Packet[]::new));
                        });
                    }
                }

                final int oldLevel = player.getLevel();
                final int level = levelService.getLevel(playerReward.getExp(), player.getExpPoints(), (byte) oldLevel);
                if ((level < ConfigService.getInstance().getValue("player.level.max", 60)) || (oldLevel < level))
                    player.syncExpPoints(player.getExpPoints() + playerReward.getExp());
                player.syncGold(player.getGold() + playerReward.getGold());
                player.syncCouplePoints(player.getCouplePoints() + playerReward.getCouplePoints());
                levelService.setNewLevelStatusPoints((byte) level, player.getPlayer());
                player.syncLevel(level);

                GameplayActor ownedPet = enhancedActorSession ? gameSession.getOwnedPetSeat(player.getId()) : null;
                if (ownedPet != null) {
                    Pet pet = petService.awardExperience(ownedPet.pet().id(), player.getId(), playerReward.getExp());
                    if (pet != null) {
                        client.setActivePet(pet);
                        client.getConnection().sendTCP(new S2CPetDataAnswerPacket(
                                petService.findAllByPlayerId(player.getId())));
                    }
                }

                PlayerStatisticView playerStatistic = player.getPlayerStatistic();

                List<RallyResult> rallyResultsForPlayer = rallyResultMap.getOrDefault((int) rp.getPosition(), Collections.emptyList());
                PlayerStats playerStats = matchRallyStatsConsumer.getPlayerStats(gameSessionId, Math.toIntExact(player.getId()));
                int serviceAces = Math.toIntExact(rallyResultsForPlayer.stream().filter(RallyResult::serviceAce).count());
                int returnAces = Math.toIntExact(rallyResultsForPlayer.stream().filter(RallyResult::returnAce).count());

                HashMap<Long, Integer> playerRatings = RankingUtils.calculateNewRating(playerList, player, wonGame, (byte) GameMode.BASIC);
                int playerRankingPoints = playerRatings.get(player.getId()) - playerStatistic.basicRP();
                int playerNewRating = playerRatings.get(player.getId());

                playerReward.setRankingPoints(playerRankingPoints);

                PlayerStatistic dbPlayerStatistic = playerStatisticService.updatePlayerStats(player.getPlayerStatisticId(), GameMode.BASIC, wonGame,
                        playerNewRating, serviceAces, returnAces, playerStats.getStroke(), playerStats.getSlice(), playerStats.getLob(),
                        playerStats.getSmash(), playerStats.getVolley(), playerStats.getTopSpin(), playerStats.getRising(),
                        playerStats.getServe(), playerStats.getGuardBreakShot(), playerStats.getChargeShot(), playerStats.getSkillShot());

                player.setPlayerStatistic(PlayerStatisticView.fromEntity(dbPlayerStatistic));

                rp.setReady(false);
                int playerLevel = player.getLevel();
                byte resultTitle = (byte) (wonGame ? 1 : 0);
                if (playerLevel != oldLevel) {
                    S2CGameEndLevelUpPlayerStatsPacket gameEndLevelUpPlayerStatsPacket = new S2CGameEndLevelUpPlayerStatsPacket(rp.getPosition(), player);
                    eventHandler.offer(eventHandler.createPacketEvent(client, gameEndLevelUpPlayerStatsPacket, PacketEventType.DEFAULT, 0));
                }

                S2CMatchplayItemRewardsPacket itemRewardsPacket = new S2CMatchplayItemRewardsPacket(matchplayReward);
                client.getConnection().sendTCP(itemRewardsPacket);

                S2CMatchplaySetExperienceGainInfoData setExperienceGainInfoData = new S2CMatchplaySetExperienceGainInfoData(resultTitle, (int) Math.ceil((double) game.getTimeNeeded() / 1000), playerReward, (byte) playerLevel, rp);
                eventHandler.offer(eventHandler.createPacketEvent(client, setExperienceGainInfoData, PacketEventType.DEFAULT, 0));
            } else {
                gameLogContent.append("spec: ").append(rp.getName()).append(" acc: ").append(rp.getAccountId()).append("; ");

                if (rp.getPosition() != MiscConstants.InvisibleGmSlot) {
                    playerDtoList.add(new MatchFinishedMessage.PlayerDto(rp.getName(), "spectator"));
                }
            }
            S2CMatchplaySetGameResultData setGameResultData = enhancedActorSession
                    ? new S2CMatchplaySetGameResultData(matchplayReward.getPlayerRewards(), gameSession.getOwnedPetSeats())
                    : new S2CMatchplaySetGameResultData(matchplayReward.getPlayerRewards());
            eventHandler.offer(eventHandler.createPacketEvent(client, setGameResultData, PacketEventType.DEFAULT, 0));

            S2CMatchplayBackToRoom backToRoomPacket = new S2CMatchplayBackToRoom();
            eventHandler.offer(eventHandler.createPacketEvent(client, backToRoomPacket, PacketEventType.FIRE_DELAYED, TimeUnit.SECONDS.toMillis(12)));
            client.setActiveGameSession(null);
        }

        if (!enhancedActorSession) {
            matchRallyStatsConsumer.clearSession(gameSessionId);
            GameEventBus.call(GameEventType.MP_MATCH_END, game, activeRoom, clients);
        } else {
            GameEventBus.call(GameEventType.MP_MATCH_END, game, activeRoom, clients);
        }

        eventHandler.offer(eventHandler.createRunnableEvent(new AutoItemRewardPickerTask(new ConcurrentLinkedDeque<>(clients), activeRoom.getRoomId()), TimeUnit.SECONDS.toMillis(9)));

        gameLogContent.append("playtime: ").append(TimeUnit.MILLISECONDS.toSeconds(game.getTimeNeeded())).append("s");

        GameLog gameLog = new GameLog();
        gameLog.setGameLogType(GameLogType.BASIC_GAME);
        gameLog.setContent(gameLogContent.toString());
        gameLogService.save(gameLog);

        gameSession.getClients().removeIf(c -> c.getActiveGameSession() == null);
        if (gameSession.getClients().isEmpty()) {
            if (!enhancedActorSession)
                GameSessionManager.getInstance().removeGameSession(gameSessionId, gameSession);
            MatchFinishedMessage message = MatchFinishedMessage.builder()
                    .gameSessionId(gameSessionId)
                    .time(game.getTimeNeeded())
                    .mode("BASIC")
                    .winner(redTeamWon ? "red" : "blue")
                    .map(game.getMap().getName())
                    .players(playerDtoList)
                    .isBoss(false)
                    .isRandom(false)
                    .isHard(false)
                    .build();
            RProducerService.getInstance().send(message, "game.stats.match", "MatchplaySystem");
        }
        completionSucceeded = true;
        } finally {
            if (enhancedActorSession && !completionSucceeded) {
                GameSessionManager.getInstance().removeMatchplayReward(activeRoom.getRoomId());
            }
            if (enhancedActorSession)
                GameManager.getInstance().cleanupFinishedGameSession(gameSessionId, gameSession, activeRoom);
        }
    }

    @Override
    public void onPrepare(final FTClient ftClient) {
        Room room = ftClient.getActiveRoom();

        Optional<SMaps> map = mapService.findByMap((int) room.getMap());
        if (map.isEmpty()) {
            log.error("No map found for mapId: " + room.getMap());
            return;
        }
        game.setMap(map.get());
    }

    @Override
    public void onPoint(final FTClient ftClient, CMSGPoint pointPacket) {
        GameSession gameSession = ftClient.getActiveGameSession();
        if (gameSession == null)
            return;

        Room activeRoom = ftClient.getActiveRoom();
        if (activeRoom == null)
            return;

        if (!isEnhancedActorSession(gameSession)) {
            onOrdinaryPoint(ftClient, pointPacket, gameSession, activeRoom);
            return;
        }

        short scoringActor = pointPacket.getPlayerPosition();
        short scoringTeam = pointPacket.getPointsTeam();
        if (scoringTeam < 0 || scoringTeam > 3)
            return;

        synchronized (game) {
            if (game.getFinished().get() || gameSession.getCompletionHandled().get())
                return;

            boolean isSingles = game.isSingles();
            final int pointsTeamRed = game.getPointsRedTeam().get();
            final int pointsTeamBlue = game.getPointsBlueTeam().get();
            final int setsTeamRead = game.getSetsRedTeam().get();
            final int setsTeamBlue = game.getSetsBlueTeam().get();

            boolean winningTeamIsRed = game.isRedTeam(scoringTeam);
            RallyResult rallyResult = matchRallyStatsConsumer.onPoint(ftClient.getGameSessionId(), winningTeamIsRed);

            if (gameSession.getGameplayActorPositions().contains(scoringActor)) {
                int ownerPosition = gameSession.getOwnerPositionForActor(scoringActor);
                game.increasePerformancePointForPlayer(ownerPosition);
                rallyResultMap.computeIfAbsent(ownerPosition, k -> new ArrayList<>()).add(rallyResult);
            }

            if (winningTeamIsRed)
                game.setPoints((byte) (pointsTeamRed + 1), (byte) pointsTeamBlue);
            else
                game.setPoints((byte) pointsTeamRed, (byte) (pointsTeamBlue + 1));

            final boolean isFinished = game.getFinished().get();

            if (isFinished) {
                this.onEnd(ftClient);
                return;
            }

            boolean anyTeamWonSet = setsTeamRead != game.getSetsRedTeam().get() || setsTeamBlue != game.getSetsBlueTeam().get();
            if (anyTeamWonSet) {
                gameSession.setTimesCourtChanged(gameSession.getTimesCourtChanged() + 1);
                game.getPlayerLocationsOnMap().forEach(x -> x.setLocation(game.invertPointY(x)));
            }
            boolean isRedTeamServing = game.isRedTeamServing(gameSession.getTimesCourtChanged());

            List<ServeInfo> serveInfo = new ArrayList<>();
            ConcurrentLinkedDeque<FTClient> clients = gameSession.getClients();
            for (short position : gameSession.getGameplayActorPositions()) {
                boolean shouldPlayerSwitchServingSide = game.shouldSwitchServingSide(isSingles, isRedTeamServing, anyTeamWonSet, position);
                if (shouldPlayerSwitchServingSide) {
                    Point playerLocation = game.getPlayerLocationsOnMap().get(position);
                    game.getPlayerLocationsOnMap().set(position, game.invertPointX(playerLocation));
                }

                boolean shouldServeBall = game.shouldPlayerServe(isSingles, gameSession.getTimesCourtChanged(), position);
                byte serveType = ServeType.None;
                if (shouldServeBall) {
                    serveType = ServeType.ServeBall;
                    game.getServePlayerPosition().set(position);
                } else if (isSingles) {
                    serveType = ServeType.ReceiveBall;
                    game.getReceiverPlayerPosition().set(position);
                }

                ServeInfo playerServeInfo = new ServeInfo();
                playerServeInfo.setPlayerPosition(position);
                playerServeInfo.setPlayerStartLocation(game.getPlayerLocationsOnMap().get(position));
                playerServeInfo.setServeType(serveType);
                serveInfo.add(playerServeInfo);
            }

            short pointingTeamPosition = -1;
            if (game.isRedTeam(pointPacket.getPointsTeam()))
                pointingTeamPosition = 0;
            else if (game.isBlueTeam(pointPacket.getPointsTeam()))
                pointingTeamPosition = 1;

            S2CMatchplayTeamWinsPoint matchplayTeamWinsPoint = new S2CMatchplayTeamWinsPoint(pointingTeamPosition, pointPacket.getBallState(), (byte) game.getPointsRedTeam().get(), (byte) game.getPointsBlueTeam().get());
            S2CMatchplayTeamWinsSet matchplayTeamWinsSet = anyTeamWonSet
                    ? new S2CMatchplayTeamWinsSet((byte) game.getSetsRedTeam().get(), (byte) game.getSetsBlueTeam().get())
                    : null;
            for (FTClient client : clients) {
                eventHandler.offer(eventHandler.createPacketEvent(client, matchplayTeamWinsPoint, PacketEventType.DEFAULT, 0));
                if (matchplayTeamWinsSet != null)
                    eventHandler.offer(eventHandler.createPacketEvent(client, matchplayTeamWinsSet, PacketEventType.DEFAULT, 0));
            }

            if (!serveInfo.isEmpty()) {
                if (!isSingles) {
                    game.setPlayerLocationsForDoubles(serveInfo);
                    serveInfo.stream()
                            .filter(x -> x.getServeType() == ServeType.ReceiveBall)
                            .findFirst()
                            .ifPresent(receiver -> game.getReceiverPlayerPosition().set(receiver.getPlayerPosition()));
                }
                S2CMatchplayTriggerServe matchplayTriggerServe = new S2CMatchplayTriggerServe(serveInfo);
                for (FTClient client : clients)
                    eventHandler.offer(eventHandler.createPacketEvent(client, matchplayTriggerServe, PacketEventType.FIRE_DELAYED, TimeUnit.SECONDS.toMillis(6)));
            }
        }
    }

    private void onOrdinaryPoint(FTClient ftClient, CMSGPoint pointPacket, GameSession gameSession, Room activeRoom) {
        boolean isSingles = gameSession.getPlayers() == 2;
        final int pointsTeamRed = game.getPointsRedTeam().get();
        final int pointsTeamBlue = game.getPointsBlueTeam().get();
        final int setsTeamRed = game.getSetsRedTeam().get();
        final int setsTeamBlue = game.getSetsBlueTeam().get();

        boolean winningTeamIsRed = game.isRedTeam(pointPacket.getPointsTeam());
        RallyResult rallyResult = matchRallyStatsConsumer.onPoint(ftClient.getGameSessionId(), winningTeamIsRed);
        if (pointPacket.getPlayerPosition() < 4) {
            game.increasePerformancePointForPlayer(pointPacket.getPlayerPosition());
            rallyResultMap.computeIfAbsent((int) pointPacket.getPlayerPosition(), ignored -> new ArrayList<>()).add(rallyResult);
        }

        if (game.isRedTeam(pointPacket.getPointsTeam()))
            game.setPoints((byte) (pointsTeamRed + 1), (byte) pointsTeamBlue);
        else if (game.isBlueTeam(pointPacket.getPointsTeam()))
            game.setPoints((byte) pointsTeamRed, (byte) (pointsTeamBlue + 1));

        if (game.getFinished().get()) {
            onEnd(ftClient);
            return;
        }

        boolean anyTeamWonSet = setsTeamRed != game.getSetsRedTeam().get() || setsTeamBlue != game.getSetsBlueTeam().get();
        if (anyTeamWonSet) {
            gameSession.setTimesCourtChanged(gameSession.getTimesCourtChanged() + 1);
            game.getPlayerLocationsOnMap().forEach(point -> point.setLocation(game.invertPointY(point)));
        }
        boolean isRedTeamServing = game.isRedTeamServing(gameSession.getTimesCourtChanged());
        List<ServeInfo> serveInfo = new ArrayList<>();
        ConcurrentLinkedDeque<FTClient> clients = gameSession.getClients();
        ConcurrentLinkedDeque<RoomPlayer> roomPlayers = activeRoom.getRoomPlayerList();
        for (FTClient client : clients) {
            RoomPlayer rp = client.getRoomPlayer();
            if (rp == null)
                continue;

            boolean activePlayer = rp.getPosition() < 4;
            if (activePlayer && game.shouldSwitchServingSide(isSingles, isRedTeamServing, anyTeamWonSet, rp.getPosition())) {
                Point location = game.getPlayerLocationsOnMap().get(rp.getPosition());
                game.getPlayerLocationsOnMap().set(rp.getPosition(), game.invertPointX(location));
            }

            short pointingTeamPosition = game.isRedTeam(pointPacket.getPointsTeam()) ? (short) 0
                    : game.isBlueTeam(pointPacket.getPointsTeam()) ? (short) 1 : (short) -1;
            eventHandler.offer(eventHandler.createPacketEvent(client,
                    new S2CMatchplayTeamWinsPoint(pointingTeamPosition, pointPacket.getBallState(),
                            (byte) game.getPointsRedTeam().get(), (byte) game.getPointsBlueTeam().get()),
                    PacketEventType.DEFAULT, 0));
            if (anyTeamWonSet)
                eventHandler.offer(eventHandler.createPacketEvent(client,
                        new S2CMatchplayTeamWinsSet((byte) game.getSetsRedTeam().get(), (byte) game.getSetsBlueTeam().get()),
                        PacketEventType.DEFAULT, 0));

            if (activePlayer) {
                boolean shouldServe = game.shouldPlayerServe(isSingles, gameSession.getTimesCourtChanged(), rp.getPosition());
                byte serveType = ServeType.None;
                if (shouldServe) {
                    serveType = ServeType.ServeBall;
                    game.getServePlayer().set(rp);
                } else if (isSingles) {
                    serveType = ServeType.ReceiveBall;
                    game.getReceiverPlayer().set(rp);
                }
                ServeInfo info = new ServeInfo();
                info.setPlayerPosition(rp.getPosition());
                info.setPlayerStartLocation(game.getPlayerLocationsOnMap().get(rp.getPosition()));
                info.setServeType(serveType);
                serveInfo.add(info);
            }
        }

        if (!serveInfo.isEmpty()) {
            if (!isSingles) {
                game.setPlayerLocationsForDoubles(serveInfo);
                serveInfo.stream().filter(info -> info.getServeType() == ServeType.ReceiveBall).findFirst()
                        .flatMap(receiver -> roomPlayers.stream()
                                .filter(rp -> rp.getPosition() == receiver.getPlayerPosition()).findFirst())
                        .ifPresent(rp -> game.getReceiverPlayer().set(rp));
            }
            S2CMatchplayTriggerServe triggerServe = new S2CMatchplayTriggerServe(serveInfo);
            for (FTClient client : clients)
                eventHandler.offer(eventHandler.createPacketEvent(client, triggerServe,
                        PacketEventType.FIRE_DELAYED, TimeUnit.SECONDS.toMillis(6)));
        }
    }

    private boolean isEnhancedActorSession(GameSession gameSession) {
        return gameSession.isDedicatedBattlemonRoom() || gameSession.hasOwnedPetSeats();
    }
}
