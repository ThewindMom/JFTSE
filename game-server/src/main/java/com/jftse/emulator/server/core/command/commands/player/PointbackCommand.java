package com.jftse.emulator.server.core.command.commands.player;

import com.jftse.emulator.server.core.command.AbstractCommand;
import com.jftse.emulator.server.core.constants.PacketEventType;
import com.jftse.emulator.server.core.constants.ServeType;
import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.life.room.ServeInfo;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.matchplay.event.EventHandler;
import com.jftse.emulator.server.core.matchplay.event.PacketEvent;
import com.jftse.emulator.server.core.matchplay.game.MatchplayBasicGame;
import com.jftse.emulator.server.core.packets.chat.S2CChatRoomAnswerPacket;
import com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayTeamWinsPoint;
import com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayTeamWinsSet;
import com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayTriggerServe;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;

public class PointbackCommand extends AbstractCommand {
    private final EventHandler eventHandler;


    public PointbackCommand() {
        setDescription("vote to reset points to last one");

        eventHandler = GameManager.getInstance().getEventHandler();
    }

    @Override
    public void execute(FTConnection connection, List<String> params) {
        if (connection.getClient().getActiveRoom() == null || connection.getClient().getRoomPlayer() == null || connection.getClient().getActiveGameSession() == null)
            return;

        Room activeRoom = connection.getClient().getActiveRoom();
        RoomPlayer roomPlayer = connection.getClient().getRoomPlayer();

        GameSession gameSession = connection.getClient().getActiveGameSession();
        if (gameSession.getMatchplayGame() instanceof MatchplayBasicGame game) {
            final boolean isFinished = game.getFinished().get();

            if (isFinished || (game.getSetsBlueTeam().get() == 0 && game.getSetsRedTeam().get() == 0 && game.getPointsBlueTeam().get() == 0 & game.getPointsRedTeam().get() == 0))
                return;

            if (!gameSession.isDedicatedBattlemonRoom() && !gameSession.hasOwnedPetSeats()) {
                executeOrdinary(activeRoom, roomPlayer, gameSession, game);
                return;
            }

            boolean isSingles = game.isSingles();
            List<ServeInfo> serveInfos = new ArrayList<>();
            List<FTClient> clients = new ArrayList<>(gameSession.getClients());
            boolean setsDownGraded = false;
            boolean pointsBackSuccess = false;
            if (roomPlayer.getPosition() >= 0 && roomPlayer.getPosition() < 4) {
                game.setPointBackVote(roomPlayer.getPosition());
                S2CChatRoomAnswerPacket chatRoomAnswerPacket = new S2CChatRoomAnswerPacket((byte) 2, "Room", roomPlayer.getName() + " voted for point back");
                GameManager.getInstance().getClientsInRoom(activeRoom.getRoomId()).forEach(c -> c.getConnection().sendTCP(chatRoomAnswerPacket));
            }

            if (game.isPointBackAvailable()) {
                game.pointBack();
                pointsBackSuccess = true;
                if (game.getSetDowngraded().get()) {
                    setsDownGraded = true;
                }
                Optional<PacketEvent> packetEvent = eventHandler.getFireableDeque().stream()
                        .filter(f -> f instanceof PacketEvent)
                        .map(f -> (PacketEvent) f)
                        .filter(pe -> !pe.isFired() && pe.getPacketEventType() == PacketEventType.FIRE_DELAYED && pe.getPacket() instanceof S2CMatchplayTriggerServe)
                        .findFirst();
                packetEvent.ifPresent(eventHandler::remove);
            }

            if (setsDownGraded) {
                gameSession.setTimesCourtChanged(gameSession.getTimesCourtChanged() - 1);
                game.getPlayerLocationsOnMap().forEach(x -> x.setLocation(game.invertPointY(x)));
            }

            if (pointsBackSuccess) {
                for (short actorPosition : gameSession.getGameplayActorPositions()) {
                    boolean isRedTeamServing = game.isRedTeamServing(gameSession.getTimesCourtChanged());
                    boolean shouldPlayerSwitchServingSide =
                            game.shouldSwitchServingSide(isSingles, isRedTeamServing, setsDownGraded, actorPosition);
                    if (shouldPlayerSwitchServingSide) {
                        Point playerLocation = game.getPlayerLocationsOnMap().get(actorPosition);
                        game.getPlayerLocationsOnMap().set(actorPosition, game.invertPointX(playerLocation));
                    }

                    byte serveType = ServeType.None;
                    if (actorPosition == game.getPreviousServePlayerPosition().get()) {
                        serveType = ServeType.ServeBall;
                        game.getServePlayerPosition().set(actorPosition);
                    } else if (actorPosition == game.getPreviousReceiverPlayerPosition().get()) {
                        serveType = ServeType.ReceiveBall;
                        game.getReceiverPlayerPosition().set(actorPosition);
                    }

                    ServeInfo playerServeInfo = new ServeInfo();
                    playerServeInfo.setPlayerPosition(actorPosition);
                    playerServeInfo.setPlayerStartLocation(game.getPlayerLocationsOnMap().get(actorPosition));
                    playerServeInfo.setServeType(serveType);
                    serveInfos.add(playerServeInfo);
                }

                for (FTClient client : clients) {
                    S2CMatchplayTeamWinsPoint matchplayTeamWinsPoint = new S2CMatchplayTeamWinsPoint((byte) 0, (byte) 0, (byte) game.getPointsRedTeam().get(), (byte) game.getPointsBlueTeam().get());
                    eventHandler.offer(eventHandler.createPacketEvent(client, matchplayTeamWinsPoint, PacketEventType.DEFAULT, 0));

                    if (setsDownGraded) {
                        S2CMatchplayTeamWinsSet matchplayTeamWinsSet = new S2CMatchplayTeamWinsSet((byte) game.getSetsRedTeam().get(), (byte) game.getSetsBlueTeam().get());
                        eventHandler.offer(eventHandler.createPacketEvent(client, matchplayTeamWinsSet, PacketEventType.DEFAULT, 0));
                    }
                }
            }

            if (serveInfos.size() > 0) {
                if (!isSingles) {
                    game.setPlayerLocationsForDoubles(serveInfos);
                    serveInfos.stream()
                            .filter(x -> x.getServeType() == ServeType.ReceiveBall)
                            .findFirst()
                            .ifPresent(receiver -> game.getReceiverPlayerPosition().set(receiver.getPlayerPosition()));
                }

                S2CMatchplayTriggerServe matchplayTriggerServe = new S2CMatchplayTriggerServe(serveInfos);
                for (FTClient client : clients)
                    eventHandler.offer(eventHandler.createPacketEvent(client, matchplayTriggerServe, PacketEventType.FIRE_DELAYED, TimeUnit.SECONDS.toMillis(6)));
            }

            if (pointsBackSuccess) {
                S2CChatRoomAnswerPacket chatRoomAnswerPacket = new S2CChatRoomAnswerPacket((byte) 2, "Room", "Point back voted successfully.");
                GameManager.getInstance().getClientsInRoom(activeRoom.getRoomId()).forEach(c -> c.getConnection().sendTCP(chatRoomAnswerPacket));
            }
        }
    }

    private void executeOrdinary(Room activeRoom, RoomPlayer roomPlayer, GameSession gameSession, MatchplayBasicGame game) {
        boolean isSingles = gameSession.getPlayers() == 2;
        ConcurrentLinkedDeque<RoomPlayer> roomPlayerList = activeRoom.getRoomPlayerList();
        List<ServeInfo> serveInfos = new ArrayList<>();
        List<FTClient> clients = new ArrayList<>(gameSession.getClients());
        boolean setsDownGraded = false;
        boolean pointsBackSuccess = false;
        for (FTClient client : clients) {
            RoomPlayer rp = client.getRoomPlayer();
            if (rp == null)
                continue;
            if (rp.getPosition() < 4 && roomPlayer.getPlayerId() == rp.getPlayerId()) {
                game.setPointBackVote(rp.getPosition());
                S2CChatRoomAnswerPacket votePacket = new S2CChatRoomAnswerPacket(
                        (byte) 2, "Room", rp.getName() + " voted for point back");
                GameManager.getInstance().getClientsInRoom(activeRoom.getRoomId())
                        .forEach(c -> c.getConnection().sendTCP(votePacket));
            }
        }

        if (game.isPointBackAvailable()) {
            game.pointBack();
            pointsBackSuccess = true;
            setsDownGraded = game.getSetDowngraded().get();
            Optional<PacketEvent> packetEvent = eventHandler.getFireableDeque().stream()
                    .filter(f -> f instanceof PacketEvent)
                    .map(f -> (PacketEvent) f)
                    .filter(pe -> !pe.isFired() && pe.getPacketEventType() == PacketEventType.FIRE_DELAYED
                            && pe.getPacket() instanceof S2CMatchplayTriggerServe)
                    .findFirst();
            packetEvent.ifPresent(eventHandler::remove);
        }

        if (setsDownGraded) {
            gameSession.setTimesCourtChanged(gameSession.getTimesCourtChanged() - 1);
            game.getPlayerLocationsOnMap().forEach(point -> point.setLocation(game.invertPointY(point)));
        }

        for (FTClient client : clients) {
            RoomPlayer rp = client.getRoomPlayer();
            if (rp == null)
                continue;
            if (rp.getPosition() < 4 && pointsBackSuccess) {
                boolean redServing = game.isRedTeamServing(gameSession.getTimesCourtChanged());
                if (game.shouldSwitchServingSide(isSingles, redServing, setsDownGraded, rp.getPosition())) {
                    Point location = game.getPlayerLocationsOnMap().get(rp.getPosition());
                    game.getPlayerLocationsOnMap().set(rp.getPosition(), game.invertPointX(location));
                }

                byte serveType = ServeType.None;
                if (rp.getPosition() == game.getPreviousServePlayerPosition().get()) {
                    serveType = ServeType.ServeBall;
                    game.getServePlayer().set(rp);
                } else if (rp.getPosition() == game.getPreviousReceiverPlayerPosition().get()) {
                    serveType = ServeType.ReceiveBall;
                    game.getReceiverPlayer().set(rp);
                }
                ServeInfo info = new ServeInfo();
                info.setPlayerPosition(rp.getPosition());
                info.setPlayerStartLocation(game.getPlayerLocationsOnMap().get(rp.getPosition()));
                info.setServeType(serveType);
                serveInfos.add(info);

                eventHandler.offer(eventHandler.createPacketEvent(client,
                        new S2CMatchplayTeamWinsPoint((byte) 0, (byte) 0,
                                (byte) game.getPointsRedTeam().get(), (byte) game.getPointsBlueTeam().get()),
                        PacketEventType.DEFAULT, 0));
                if (setsDownGraded)
                    eventHandler.offer(eventHandler.createPacketEvent(client,
                            new S2CMatchplayTeamWinsSet((byte) game.getSetsRedTeam().get(), (byte) game.getSetsBlueTeam().get()),
                            PacketEventType.DEFAULT, 0));
            }
        }

        if (!serveInfos.isEmpty()) {
            if (!isSingles) {
                game.setPlayerLocationsForDoubles(serveInfos);
                serveInfos.stream().filter(info -> info.getServeType() == ServeType.ReceiveBall).findFirst()
                        .flatMap(receiver -> roomPlayerList.stream()
                                .filter(rp -> rp.getPosition() == receiver.getPlayerPosition()).findFirst())
                        .ifPresent(rp -> game.getReceiverPlayer().set(rp));
            }
            S2CMatchplayTriggerServe triggerServe = new S2CMatchplayTriggerServe(serveInfos);
            for (FTClient client : clients)
                eventHandler.offer(eventHandler.createPacketEvent(client, triggerServe,
                        PacketEventType.FIRE_DELAYED, TimeUnit.SECONDS.toMillis(6)));
        }

        if (pointsBackSuccess) {
            S2CChatRoomAnswerPacket successPacket = new S2CChatRoomAnswerPacket(
                    (byte) 2, "Room", "Point back voted successfully.");
            GameManager.getInstance().getClientsInRoom(activeRoom.getRoomId())
                    .forEach(c -> c.getConnection().sendTCP(successPacket));
        }
    }
}
