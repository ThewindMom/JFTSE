package com.jftse.emulator.server.core.handler.lobby.room;

import com.jftse.emulator.common.utilities.StringUtils;
import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.constants.RoomStatus;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.packets.home.S2CHomeItemsLoadAnswerPacket;
import com.jftse.emulator.server.core.packets.lobby.room.*;
import com.jftse.emulator.server.core.service.impl.ClothEquipmentServiceImpl;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.home.AccountHome;
import com.jftse.entities.database.model.home.HomeInventory;
import com.jftse.entities.database.model.messenger.Friend;
import com.jftse.entities.database.model.player.*;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.protocol.PacketOperations;
import com.jftse.server.core.service.*;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomJoin;
import com.jftse.server.core.shared.packets.lobby.room.SMSGRoomJoin;
import com.jftse.server.core.shared.packets.shop.SMSGSetMoney;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.IntStream;

@PacketId(CMSGRoomJoin.PACKET_ID)
public class RoomJoinRequestPacketHandler implements PacketHandler<FTConnection, CMSGRoomJoin> {
    private final ClothEquipmentServiceImpl clothEquipmentService;
    private final SpecialSlotEquipmentService specialSlotEquipmentService;
    private final CardSlotEquipmentService cardSlotEquipmentService;
    private final SocialService socialService;
    private final HomeService homeService;
    private final GuildCastleService guildCastleService;

    public RoomJoinRequestPacketHandler() {
        clothEquipmentService = ServiceManager.getInstance().getClothEquipmentService();
        specialSlotEquipmentService = ServiceManager.getInstance().getSpecialSlotEquipmentService();
        cardSlotEquipmentService = ServiceManager.getInstance().getCardSlotEquipmentService();
        socialService = ServiceManager.getInstance().getSocialService();
        homeService = ServiceManager.getInstance().getHomeService();
        guildCastleService = ServiceManager.getInstance().getGuildCastleService();
    }

    @Override
    public void handle(FTConnection connection, CMSGRoomJoin roomJoinRequestPacket) {
        FTClient ftClient = connection.getClient();
        if (ftClient == null || ftClient.getPlayer() == null) {
            SMSGRoomJoin answer = SMSGRoomJoin.builder()
                    .result((char) -10)
                    .roomType((byte) 0)
                    .mode((byte) 0)
                    .mapId((byte) 0)
                    .build();
            connection.sendTCP(answer);
            return;
        }

        if (!ftClient.getIsJoiningOrLeavingRoom().compareAndSet(false, true)) {
            return;
        }

        try {
            handleJoin(connection, roomJoinRequestPacket, ftClient);
        } finally {
            resetIsJoiningOrLeavingRoom(ftClient);
        }
    }

    private void handleJoin(FTConnection connection, CMSGRoomJoin roomJoinRequestPacket, FTClient ftClient) {

        Room room = GameManager.getInstance().getRooms().stream()
                .filter(r -> r.getRoomId() == roomJoinRequestPacket.getRoomId())
                .findAny()
                .orElse(null);

        if (room == null) {
            SMSGRoomJoin roomJoinAnswerPacket = SMSGRoomJoin.builder()
                    .result((char) -10)
                    .roomType((byte) 0)
                    .mode((byte) 0)
                    .mapId((byte) 0)
                    .build();
            S2CRoomListAnswerPacket roomListAnswerPacket = new S2CRoomListAnswerPacket(
                    GameManager.getInstance().getFilteredRoomsForClient(ftClient));

            resetIsJoiningOrLeavingRoom(ftClient);

            connection.sendTCP(roomJoinAnswerPacket, roomListAnswerPacket);
            return;
        }

        final ConcurrentLinkedDeque<RoomPlayer> roomPlayerList = room.getRoomPlayerList();

        if (room.getStatus() != RoomStatus.NotRunning) {
            SMSGRoomJoin roomJoinAnswerPacket = SMSGRoomJoin.builder()
                    .result((char) -1)
                    .roomType((byte) 0)
                    .mode((byte) 0)
                    .mapId((byte) 0)
                    .build();
            connection.sendTCP(roomJoinAnswerPacket);

            resetIsJoiningOrLeavingRoom(ftClient);

            GameManager.getInstance().updateRoomForAllClientsInMultiplayer(ftClient.getConnection(), room);
            return;
        }

        FTPlayer activePlayer = ftClient.getPlayer();
        if (!ftClient.isGameMaster() && room.isPrivate() && (StringUtils.isEmpty(roomJoinRequestPacket.getPassword()) || !roomJoinRequestPacket.getPassword().equals(room.getPassword()))) {
            SMSGRoomJoin roomJoinAnswerPacket = SMSGRoomJoin.builder()
                    .result((char) -5)
                    .roomType((byte) 0)
                    .mode((byte) 0)
                    .mapId((byte) 0)
                    .build();
            connection.sendTCP(roomJoinAnswerPacket);

            resetIsJoiningOrLeavingRoom(ftClient);

            GameManager.getInstance().updateRoomForAllClientsInMultiplayer(ftClient.getConnection(), room);
            return;
        }

        boolean anyPositionAvailable = roomPlayerList.size() < room.getPlayers();
        if (!anyPositionAvailable) {
            SMSGRoomJoin roomJoinAnswerPacket = SMSGRoomJoin.builder()
                    .result((char) -10)
                    .roomType((byte) 0)
                    .mode((byte) 0)
                    .mapId((byte) 0)
                    .build();
            connection.sendTCP(roomJoinAnswerPacket);

            resetIsJoiningOrLeavingRoom(ftClient);

            GameManager.getInstance().updateRoomForAllClientsInMultiplayer(ftClient.getConnection(), room);
            return;
        }

        // prevent abusive room joins
        if (ftClient.getActiveRoom() != null) {
            Room clientRoom = ftClient.getActiveRoom();
            handleRoomUponJoin(connection, clientRoom, true);

            resetIsJoiningOrLeavingRoom(ftClient);
            return;
        }

        if (room.getBannedPlayers().contains(activePlayer.getId())) {
            SMSGRoomJoin roomJoinAnswerPacket = SMSGRoomJoin.builder()
                    .result((char) -4)
                    .roomType((byte) 0)
                    .mode((byte) 0)
                    .mapId((byte) 0)
                    .build();
            connection.sendTCP(roomJoinAnswerPacket);

            resetIsJoiningOrLeavingRoom(ftClient);

            GameManager.getInstance().updateRoomForAllClientsInMultiplayer(ftClient.getConnection(), room);
            return;
        }

        if (activePlayer.getLevel() < (room.getLevel() - room.getLevelRange()) && activePlayer.getLevel() > room.getLevel()) {
            SMSGRoomJoin roomJoinAnswerPacket = SMSGRoomJoin.builder()
                    .result((char) -10)
                    .roomType((byte) 0)
                    .mode((byte) 0)
                    .mapId((byte) 0)
                    .build();
            connection.sendTCP(roomJoinAnswerPacket);

            resetIsJoiningOrLeavingRoom(ftClient);

            GameManager.getInstance().updateRoomForAllClientsInMultiplayer(ftClient.getConnection(), room);
            return;
        }

        Player chargedPlayer = null;
        synchronized (room) {
            boolean playerAlreadyPresent = roomPlayerList.stream()
                    .anyMatch(player -> player.getPlayerId() == activePlayer.getId());
            if (playerAlreadyPresent || roomPlayerList.size() >= room.getPlayers()) {
                rejectRoomJoin(connection, ftClient, room);
                return;
            }

            List<Short> positions = roomPlayerList.stream().map(RoomPlayer::getPosition).toList();
            short position = (short) IntStream.range(0, room.getPlayers())
                    .filter(p -> !positions.contains((short) p))
                    .findFirst()
                    .orElse(-1);
            if (position == -1) {
                rejectRoomJoin(connection, ftClient, room);
                return;
            }

            if (!room.isClubHouse() && room.getRoomType() == 1 && room.getMode() == 3) {
                rejectRoomJoin(connection, ftClient, room);
                return;
            }

            Friend couple = socialService.getRelationshipWithFriend(activePlayer.getPlayerRef());
            if (couple != null) {
                activePlayer.setCoupleId(couple.getFriend().getId());
                activePlayer.setCoupleName(couple.getFriend().getName());
            }

            RoomPlayer roomPlayer = new RoomPlayer(activePlayer);
            roomPlayer.setGameMaster(ftClient.isGameMaster());
            roomPlayer.setPosition(position);
            roomPlayer.setMaster(false);
            roomPlayer.setFitting(false);

            if (room.isClubHouse()) {
                if (room.getRoomType() != 1 || room.getMode() != 3) {
                    rejectRoomJoin(connection, ftClient, room);
                    return;
                }
                chargedPlayer = guildCastleService.chargeAdmission(
                        activePlayer.getId(), room.getCastleGuildId());
                if (chargedPlayer == null) {
                    rejectRoomJoin(connection, ftClient, room);
                    return;
                }
                activePlayer.syncGold(chargedPlayer.getGold());
            }

            ftClient.setActiveRoom(room);
            ftClient.setInLobby(room.getMode() == 2);

            roomPlayerList.add(roomPlayer);
        }

        handleRoomUponJoin(connection, room, false);

        if (chargedPlayer != null) {
            connection.sendTCP(SMSGSetMoney.builder()
                    .ap(ftClient.getAp().get())
                    .gold(chargedPlayer.getGold())
                    .build());
        }
    }

    private void rejectRoomJoin(FTConnection connection, FTClient client, Room room) {
        connection.sendTCP(SMSGRoomJoin.builder()
                .result((char) -10)
                .roomType((byte) 0)
                .mode((byte) 0)
                .mapId((byte) 0)
                .build());
        resetIsJoiningOrLeavingRoom(client);
        GameManager.getInstance().updateRoomForAllClientsInMultiplayer(client.getConnection(), room);
    }

    private void handleRoomUponJoin(final FTConnection connection, Room room, boolean existingRoom) {
        FTClient client = connection.getClient();
        RoomPlayer roomPlayer = client.getRoomPlayer();

        Optional<RoomPlayer> roomPlayerMaster = room.getRoomPlayerList().stream().filter(RoomPlayer::isMaster).findFirst();

        SMSGRoomJoin roomJoinAnswerPacket = SMSGRoomJoin.builder()
                .result((char) 0)
                .roomType(room.getRoomType())
                .mode(room.getMode())
                .mapId(room.getMap())
                .build();
        S2CRoomInformationPacket roomInformationPacket = new S2CRoomInformationPacket(room);

        connection.sendTCP(roomJoinAnswerPacket);
        connection.sendTCP(roomInformationPacket);

        AccountHome accountHome = null;
        if (roomPlayerMaster.isPresent() && room.getMode() == 1) {
            RoomPlayer master = roomPlayerMaster.get();
            accountHome = homeService.findAccountHomeByAccountId(master.getAccountId());
            List<HomeInventory> homeInventoryList = homeService.findAllByAccountHome(accountHome);

            S2CHomeItemsLoadAnswerPacket homeItemsLoadAnswerPacket = new S2CHomeItemsLoadAnswerPacket(homeInventoryList);
            connection.sendTCP(homeItemsLoadAnswerPacket);
        }

        Random rnd = new Random();
        float spawnX, spawnY;
        if (room.getMode() == 0) {
            spawnX = rnd.nextFloat(10.0f, 21.0f);
            spawnY = rnd.nextFloat(15.0f, 50.0f);
        } else if (room.getMode() == 1) {
            spawnX = switch (accountHome.getLevel()) {
                case 3, 4 -> 10.0f;
                default -> 9.0f;
            };
            spawnY = switch (accountHome.getLevel()) {
                case 2 -> 15.0f;
                case 3 -> 17.0f;
                case 4 -> 20.0f;
                default -> 12.0f;
            };
        } else if (room.isClubHouse()) {
            // FantaCastle's interior exit tiles are x=15..17, y=36; spawn just inside them.
            spawnX = GameManager.CLUB_HOUSE_SPAWN_X;
            spawnY = GameManager.CLUB_HOUSE_SPAWN_Y;
        } else {
            spawnX = rnd.nextFloat(40.0f, 46.0f);
            spawnY = rnd.nextFloat(60.0f, 64.0f);
        }

        if (!existingRoom) {
            roomPlayer.setLastX(spawnX);
            roomPlayer.setLastY(spawnY);
        } else {
            roomPlayer.setLastX(roomPlayer.getLastX());
            roomPlayer.setLastY(roomPlayer.getLastY());
            client.setInLobby(true);
        }

        roomPlayer.setLastMapLayer(0);

        for (final RoomPlayer rp : room.getRoomPlayerList()) {
            S2CRoomPlayerInformationPacket roomPlayerInformationPacket = new S2CRoomPlayerInformationPacket(rp, rp.getLastX(), rp.getLastY(), room.getMode() == 2 ? 0.0f : rp.getLastX(), room.getMode() == 2 ? 0.0f : rp.getLastY(), rp.getLastMapLayer());
            GameManager.getInstance().sendPacketToAllClientsInSameRoom(roomPlayerInformationPacket, client.getConnection());
        }

        if (room.getMode() == 2 || room.isClubHouse()) {
            Packet enableMovement = new Packet(PacketOperations.S2CEnableTownSquareMovement);
            connection.sendTCP(enableMovement);
        }

        GameManager.getInstance().updateLobbyRoomListForAllClients(client.getConnection());
        GameManager.getInstance().refreshLobbyPlayerListForAllClients();
    }

    private void resetIsJoiningOrLeavingRoom(FTClient ftClient) {
        ftClient.getIsJoiningOrLeavingRoom().set(false);
    }
}
