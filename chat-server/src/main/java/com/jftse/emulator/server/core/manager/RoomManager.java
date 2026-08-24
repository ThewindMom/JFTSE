package com.jftse.emulator.server.core.manager;

import com.jftse.emulator.common.utilities.RandomUtils;
import com.jftse.emulator.common.utilities.StringUtils;
import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.constants.RoomStatus;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomJoinResult;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.packets.home.S2CHomeItemsLoadAnswerPacket;
import com.jftse.emulator.server.core.packets.lobby.room.S2CRoomInformationPacket;
import com.jftse.emulator.server.core.packets.lobby.room.S2CRoomPlayerInformationPacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.home.AccountHome;
import com.jftse.entities.database.model.home.HomeInventory;
import com.jftse.entities.database.model.messenger.Friend;
import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.protocol.PacketOperations;
import com.jftse.server.core.service.HomeService;
import com.jftse.server.core.service.SocialService;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomCreate;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomCreateQuick;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.IntStream;

@Service
@Log4j2
public class RoomManager {
    @Getter
    private ConcurrentLinkedDeque<Room> rooms;

    @Getter
    private Room townSquare;
    @Getter
    private Room clubCastle;

    private static final int MIN_ROOM_ID = 0;
    private static final int MAX_ROOM_ID_EXCLUSIVE = Short.MAX_VALUE;

    private final BitSet roomIds = new BitSet(MAX_ROOM_ID_EXCLUSIVE);

    @Autowired
    private SocialService socialService;
    @Autowired
    private HomeService homeService;

    @PostConstruct
    public void init() {
        rooms = new ConcurrentLinkedDeque<>();
    }

    public void setupChatLobby() {
        Room square = new Room();
        square.setRoomName("Town Square");
        square.setRoomType((byte) 1);
        square.setMode((byte) 2);
        square.setMap((byte) 0);
        square.setRule((byte) 0);
        square.setPlayers((byte) 100);
        square.setPrivate(false);
        square.setSkillFree(false);
        square.setQuickSlot(true);
        square.setLevel((byte) 0);
        square.setLevelRange((byte) 0);
        square.setBall((byte) 0);

        townSquare = square;
        registerRoom(square);
    }

    private synchronized Room registerRoom(Room room) {
        int roomId = roomIds.nextClearBit(MIN_ROOM_ID);
        if (roomId >= MAX_ROOM_ID_EXCLUSIVE) {
            log.warn("No available room IDs. Cannot create new room.");
            return null;
        }

        room.setRoomId((short) roomId);
        roomIds.set(roomId);
        rooms.add(room);

        return room;
    }

    public synchronized void clearRooms() {
        rooms.clear();
        roomIds.clear();
    }

    public synchronized void removeRoom(Room room) {
        if (rooms.remove(room)) {
            roomIds.clear(room.getRoomId());
        }
    }

    public Room createRoom(CMSGRoomCreate roomCreatePacket, final FTClient client) {
        final FTPlayer player = client.getPlayer();

        Room room = new Room();
        room.setRoomName(roomCreatePacket.getRoomName());
        room.setRoomType(roomCreatePacket.getRoomType());
        room.setAllowBattlemon((byte) 0);
        room.setMode(roomCreatePacket.getMode());
        room.setRule(roomCreatePacket.getRule());
        room.setPlayers(roomCreatePacket.getPlayers());
        room.setPrivate(roomCreatePacket.getIsPrivate());
        room.setPassword(roomCreatePacket.getPassword());
        room.setSkillFree(roomCreatePacket.getSkillFree());
        room.setQuickSlot(roomCreatePacket.getQuickSlot());
        room.setLevel((byte) player.getLevel());
        room.setLevelRange(roomCreatePacket.getLevelRange());
        room.setBettingType(roomCreatePacket.getBettingType());
        room.setBettingAmount(roomCreatePacket.getBettingAmount());
        room.setBall(roomCreatePacket.getBall());
        room.setMap(roomCreatePacket.getMapId());

        updatePlayerRelationship(player);
        createMaster(room, client, player);

        return registerRoom(room);
    }

    public Room createRoom(CMSGRoomCreateQuick roomCreateQuickPacket, final FTClient client) {
        final FTPlayer player = client.getPlayer();

        byte playerSize = roomCreateQuickPacket.getPlayers();
        if (playerSize == 0) {
            playerSize = 8;
        }

        Room room = new Room();
        room.setRoomName(String.format("%s's room", player.getName()));
        room.setRoomType(roomCreateQuickPacket.getRoomType());
        room.setAllowBattlemon((byte) 0);
        room.setMode(roomCreateQuickPacket.getMode());
        room.setRule((byte) 0);
        room.setPlayers(playerSize);
        room.setPrivate(false);
        room.setSkillFree(false);
        room.setQuickSlot(false);
        room.setLevel((byte) player.getLevel());
        room.setLevelRange((byte) -1);
        room.setBettingType('0');
        room.setBettingAmount(0);
        room.setBall(1);

        if (room.getMode() == 1) {
            AccountHome accountHome = homeService.findAccountHomeByAccountId(client.getAccountId());
            if (accountHome != null) {
                room.setMap(accountHome.getLevel());
            } else {
                room.setMap((byte) 0);
            }
        } else {
            room.setMap((byte) 0);
        }

        updatePlayerRelationship(player);
        createMaster(room, client, player);

        return registerRoom(room);
    }

    private void updatePlayerRelationship(FTPlayer player) {
        Friend couple = socialService.getRelationshipWithFriend(player.getPlayerRef());
        if (couple != null) {
            player.setCoupleId(couple.getFriend().getId());
            player.setCoupleName(couple.getFriend().getName());
        }
    }

    private void createMaster(Room room, final FTClient client, final FTPlayer player) {
        RoomPlayer roomPlayer = new RoomPlayer(player);
        roomPlayer.setGameMaster(client.isGameMaster());
        roomPlayer.setPosition((short) 0);
        roomPlayer.setMaster(true);
        roomPlayer.setFitting(false);

        final SecureRandom rnd = RandomUtils.random;
        float spawnX = 0.0f, spawnY = 0.0f;
        if (room.getMode() == 0) {
            spawnX = rnd.nextFloat(10.0f, 21.0f);
            spawnY = rnd.nextFloat(15.0f, 50.0f);
        } else if (room.getMode() == 1) {
            AccountHome accountHome = homeService.findAccountHomeByAccountId(client.getAccountId());

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
        } else {
            spawnX = rnd.nextFloat(40.0f, 46.0f);
            spawnY = rnd.nextFloat(60.0f, 64.0f);
        }

        roomPlayer.setLastX(spawnX);
        roomPlayer.setLastY(spawnY);
        roomPlayer.setLastMapLayer(0);

        room.getRoomPlayerList().add(roomPlayer);
    }

    private void createPlayer(Room room, final FTClient client, final FTPlayer player, int position) {
        RoomPlayer roomPlayer = new RoomPlayer(player);
        roomPlayer.setGameMaster(client.isGameMaster());
        roomPlayer.setPosition((short) position);
        roomPlayer.setMaster(false);
        roomPlayer.setFitting(false);
        room.getRoomPlayerList().add(roomPlayer);
    }

    public synchronized RoomJoinResult joinRoom(final FTClient client, int roomId, byte unk0, String password) {
        Room room = rooms.stream()
                .filter(r -> r.getRoomId() == roomId)
                .findFirst()
                .orElse(null);

        if (room == null) {
            log.warn("Room with ID {} not found.", roomId);
            return RoomJoinResult.of((char) -10, null, null);
        }

        final boolean isTownSquare = room.getRoomType() == 1 && room.getMode() == 2;
        final ConcurrentLinkedDeque<RoomPlayer> roomPlayerList = room.getRoomPlayerList();

        if (room.getStatus() != RoomStatus.NotRunning) {
            return RoomJoinResult.of((char) -1, room, null);
        }

        FTPlayer player = client.getPlayer();
        if (!client.isGameMaster() && room.isPrivate() && (StringUtils.isEmpty(password) || !room.getPassword().equals(password))) {
            return RoomJoinResult.of((char) -5, room, null);
        }

        boolean anyPositionAvailable = roomPlayerList.size() < room.getPlayers();
        if (!anyPositionAvailable) {
            return RoomJoinResult.of((char) -10, room, null);
        }

        // prevent abusive room joins
        if (client.getActiveRoom() != null) {
            Room activeRoom = client.getActiveRoom();
            handleRoomUponJoin(activeRoom, client, true);
            return RoomJoinResult.of((char) 1, activeRoom, client.getRoomPlayer());
        }

        if (room.getBannedPlayers().contains(player.getId())) {
            return RoomJoinResult.of((char) -4, room, null);
        }

        if (player.getLevel() < (room.getLevel() - room.getLevelRange()) && player.getLevel() > room.getLevel()) {
            return RoomJoinResult.of((char) -10, room, null);
        }

        List<Short> positions = roomPlayerList.stream().map(RoomPlayer::getPosition).toList();
        final short position = (short) IntStream.range(0, room.getPlayers())
                .filter(p -> !positions.contains((short) p))
                .findFirst()
                .orElse(-1);

        if (position == -1) {
            return RoomJoinResult.of((char) -10, room, null);
        }

        updatePlayerRelationship(player);
        createPlayer(room, client, player, position);

        client.setActiveRoom(room);
        client.setInLobby(room.getMode() == 2);

        handleRoomUponJoin(room, client, false);

        return RoomJoinResult.of((char) 0, room, client.getRoomPlayer());
    }

    private void handleRoomUponJoin(final Room room, final FTClient client, boolean existingRoom) {
        RoomPlayer roomPlayer = client.getRoomPlayer();
        Optional<RoomPlayer> roomPlayerMaster = room.getRoomPlayerList().stream().filter(RoomPlayer::isMaster).findFirst();

        AccountHome accountHome = null;
        if (roomPlayerMaster.isPresent() && room.getMode() == 1) {
            RoomPlayer master = roomPlayerMaster.get();
            accountHome = homeService.findAccountHomeByAccountId(master.getAccountId());
        }

        final SecureRandom rnd = RandomUtils.random;
        float spawnX = 0.0f, spawnY = 0.0f;
        if (room.getMode() == 0) {
            spawnX = rnd.nextFloat(10.0f, 21.0f);
            spawnY = rnd.nextFloat(15.0f, 50.0f);
        } else if (room.getMode() == 1 && accountHome != null) {
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
    }

    public void sendRoomInformation(final FTConnection connection, final Room room, List<FTClient> clientsInRoom) {
        if (room == null || connection.getClient() == null) {
            log.warn("Cannot send room information.");
            return;
        }

        Optional<RoomPlayer> roomPlayerMaster = room.getRoomPlayerList().stream().filter(RoomPlayer::isMaster).findFirst();

        S2CRoomInformationPacket roomInformationPacket = new S2CRoomInformationPacket(room);
        connection.sendTCP(roomInformationPacket);

        if (roomPlayerMaster.isPresent() && room.getMode() == 1) {
            RoomPlayer master = roomPlayerMaster.get();
            AccountHome accountHome = homeService.findAccountHomeByAccountId(master.getAccountId());
            List<HomeInventory> homeInventoryList = homeService.findAllByAccountHome(accountHome);

            S2CHomeItemsLoadAnswerPacket homeItemsLoadAnswerPacket = new S2CHomeItemsLoadAnswerPacket(homeInventoryList);
            connection.sendTCP(homeItemsLoadAnswerPacket);
        }

        for (final RoomPlayer rp : room.getRoomPlayerList()) {
            S2CRoomPlayerInformationPacket roomPlayerInformationPacket = new S2CRoomPlayerInformationPacket(rp, rp.getLastX(), rp.getLastY(), room.getMode() == 2 ? 0.0f : rp.getLastX(), room.getMode() == 2 ? 0.0f : rp.getLastY(), rp.getLastMapLayer());
            GameManager.getInstance().sendPacketToAllClientsInSameRoom(roomPlayerInformationPacket, connection);
        }

        if (room.getMode() == 2) {
            Packet enableMovement = new Packet(PacketOperations.S2CEnableTownSquareMovement);
            connection.sendTCP(enableMovement);
        }
    }
}
