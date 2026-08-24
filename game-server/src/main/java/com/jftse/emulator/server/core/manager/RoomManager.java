package com.jftse.emulator.server.core.manager;

import com.jftse.emulator.common.service.ConfigService;
import com.jftse.emulator.common.utilities.RandomUtils;
import com.jftse.emulator.common.utilities.StringUtils;
import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.constants.MiscConstants;
import com.jftse.emulator.server.core.constants.RoomPositionState;
import com.jftse.emulator.server.core.constants.RoomStatus;
import com.jftse.emulator.server.core.constants.RoomType;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomJoinResult;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.packets.lobby.room.S2CRoomInformationPacket;
import com.jftse.emulator.server.core.packets.lobby.room.S2CRoomPlayerInformationPacket;
import com.jftse.emulator.server.core.packets.lobby.room.S2CRoomPlayerListInformationPacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.messenger.Friend;
import com.jftse.server.core.constants.GameMode;
import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.protocol.PacketOperations;
import com.jftse.server.core.service.SocialService;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomCreate;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomCreateQuick;
import com.jftse.server.core.shared.packets.lobby.room.SMSGRoomCloseSlot;
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
        room.setAllowBattlemon(room.getRoomType() == 2 ? (byte) 1 : (byte) 0);
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

        room.getPositions().set(0, RoomPositionState.InUse);

        lockSlots(room);
        updatePlayerRelationship(player);
        createMaster(room, client, player);

        return registerRoom(room);
    }

    public Room createRoom(CMSGRoomCreateQuick roomCreateQuickPacket, final FTClient client) {
        final FTPlayer player = client.getPlayer();

        Room room = new Room();
        room.setRoomName(String.format("%s's room", player.getName()));
        room.setRoomType(roomCreateQuickPacket.getRoomType());
        room.setAllowBattlemon(room.getRoomType() == 2 ? (byte) 1 : (byte) 0);

        if (roomCreateQuickPacket.getMode() == -1) {
            roomCreateQuickPacket.setMode((byte) RandomUtils.random.nextInt(2));
        }

        room.setMode(roomCreateQuickPacket.getMode());
        room.setRule((byte) 0);

        byte playerSize = roomCreateQuickPacket.getPlayers();
        if (roomCreateQuickPacket.getMode() == GameMode.GUARDIAN)
            room.setPlayers((byte) 4);
        else
            room.setPlayers(playerSize == 0 ? 2 : playerSize);

        if (room.getRoomType() == RoomType.BATTLEMON)
            room.setPlayers((byte) 4);

        room.setPrivate(false);
        room.setSkillFree(true);
        room.setQuickSlot(true);
        room.setLevel((byte) player.getLevel());
        room.setLevelRange((byte) -1);
        room.setBettingType('0');
        room.setBettingAmount(0);
        room.setBall(1);
        room.setMap((byte) 0);

        room.getPositions().set(0, RoomPositionState.InUse);

        lockSlots(room);
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

    private void lockSlots(Room room) {
        if (room.getPlayers() == 2) {
            room.getPositions().set(2, RoomPositionState.Locked);
            room.getPositions().set(3, RoomPositionState.Locked);
        }
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

        boolean anyPositionAvailable;
        if (isTownSquare) {
            anyPositionAvailable = roomPlayerList.size() < room.getPlayers();
        } else {
            anyPositionAvailable = room.getPositions().stream().anyMatch(x -> x == RoomPositionState.Free);
        }

        if (!anyPositionAvailable && !client.isGameMaster()) {
            return RoomJoinResult.of((char) -10, room, null);
        }

        // prevent abusive room joins
        if (client.getActiveRoom() != null) {
            Room activeRoom = client.getActiveRoom();
            handleRoomUponJoin(activeRoom, client, true);
            return RoomJoinResult.of((char) 1, activeRoom, client.getRoomPlayer());
        }

        if ((room.isHardMode() || room.isArcade()) && player.getLevel() < ConfigService.getInstance().getValue("command.room.mode.change.player.level", 60)) {
            return RoomJoinResult.of((char) -10, room, null);
        }

        if (room.getBannedPlayers().contains(player.getId())) {
            return RoomJoinResult.of((char) -4, room, null);
        }

        boolean useGmSlot = false;
        int gmSlot = 9;
        if (!isTownSquare) {
            if (client.isGameMaster()) {
                int i = 0;
                boolean isGmSlotInUse = false;
                for (Short pos : room.getPositions()) {
                    if (i == gmSlot && pos == RoomPositionState.InUse) {
                        isGmSlotInUse = true;
                        break;
                    }
                    i++;
                }
                anyPositionAvailable = room.getPositions().stream().anyMatch(x -> x == RoomPositionState.Free);
                if (!isGmSlotInUse) {
                    useGmSlot = true;
                } else if (!anyPositionAvailable) {
                    return RoomJoinResult.of((char) -10, room, null);
                }
            }
        }

        if (player.getLevel() < (room.getLevel() - room.getLevelRange()) && player.getLevel() > room.getLevel()) {
            return RoomJoinResult.of((char) -10, room, null);
        }

        int newPosition = -1;
        if (!isTownSquare) {
            Optional<Short> num = room.getPositions().stream().filter(x -> x == RoomPositionState.Free).findFirst();
            newPosition = useGmSlot ? 9 : num.map(pos -> room.getPositions().indexOf(pos)).orElse(-1);
        } else {
            List<Short> positions = roomPlayerList.stream().map(RoomPlayer::getPosition).toList();
            newPosition = (short) IntStream.range(0, room.getPlayers())
                    .filter(p -> !positions.contains((short) p))
                    .findFirst()
                    .orElse(-1);
        }

        if (newPosition == -1) {
            return RoomJoinResult.of((char) -10, room, null);
        }

        if (!isTownSquare) {
            room.getPositions().set(newPosition, RoomPositionState.InUse);
        }

        updatePlayerRelationship(player);
        createPlayer(room, client, player, newPosition);

        client.setActiveRoom(room);
        if (isTownSquare) {
            client.setInLobby(true);
        } else {
            client.setInLobby(false);
        }

        handleRoomUponJoin(room, client, false);

        return RoomJoinResult.of((char) 0, room, client.getRoomPlayer());
    }

    private void handleRoomUponJoin(final Room room, final FTClient client, boolean existingRoom) {
        RoomPlayer roomPlayer = client.getRoomPlayer();

        final SecureRandom rnd = RandomUtils.random;
        float spawnX = 0.0f, spawnY = 0.0f;
        if (isTownSquare(room)) {
            spawnX = rnd.nextFloat(40.0f, 46.0f);
            spawnY = rnd.nextFloat(60.0f, 64.0f);

            if (!existingRoom) {
                roomPlayer.setLastX(spawnX);
                roomPlayer.setLastY(spawnY);
            } else {
                roomPlayer.setLastX(roomPlayer.getLastX());
                roomPlayer.setLastY(roomPlayer.getLastY());
                client.setInLobby(true);
            }
        }
        roomPlayer.setLastMapLayer(0);
    }

    private void closeRoomSlots(final FTConnection connection, ArrayList<Short> positions) {
        int i = 0;
        for (Iterator<Short> it = positions.iterator(); it.hasNext(); ) {
            short positionState = it.next();
            if (positionState == RoomPositionState.Locked) {
                SMSGRoomCloseSlot closeSlot = SMSGRoomCloseSlot.builder().slot((byte) i).close(true).build();
                connection.sendTCP(closeSlot);
            }
            i++;
        }
    }

    public boolean isTownSquare(Room room) {
        return room.getRoomType() == 1 && room.getMode() == 2;
    }

    public void sendRoomInformation(final FTConnection connection, final Room room, List<FTClient> clientsInRoom) {
        if (room == null || connection.getClient() == null) {
            log.warn("Cannot send room information.");
            return;
        }

        final boolean isTownSquare = isTownSquare(room);
        final FTClient client = connection.getClient();
        RoomPlayer roomPlayer = client.getRoomPlayer();

        S2CRoomInformationPacket roomInformationPacket = new S2CRoomInformationPacket(room);
        connection.sendTCP(roomInformationPacket);

        List<RoomPlayer> filteredRoomPlayerList = roomPlayer.getPosition() == MiscConstants.InvisibleGmSlot
                ? room.getRoomPlayerList().stream().toList()
                : room.getRoomPlayerList().stream()
                        .filter(x -> isTownSquare || x.getPosition() != MiscConstants.InvisibleGmSlot)
                        .toList();

        if (!isTownSquare) {
            final ArrayList<Short> positions = room.getPositions();
            closeRoomSlots(connection, positions);

            S2CRoomPlayerListInformationPacket roomPlayerListInformationPacket = new S2CRoomPlayerListInformationPacket(filteredRoomPlayerList);
            connection.sendTCP(roomPlayerListInformationPacket);
        }

        for (RoomPlayer rp : filteredRoomPlayerList) {
            S2CRoomPlayerInformationPacket roomPlayerInformationPacket = new S2CRoomPlayerInformationPacket(rp, isTownSquare ? rp.getLastX() : 0.0f, isTownSquare ? rp.getLastY() : 0.0f, 0.0f, 0.0f, rp.getLastMapLayer());
            if (!isTownSquare) {
                clientsInRoom.stream()
                        .filter(c -> c.getPlayer().getId() != client.getPlayer().getId())
                        .forEach(c -> c.getConnection().sendTCP(roomPlayerInformationPacket));
            } else {
                clientsInRoom.forEach(c -> c.getConnection().sendTCP(roomPlayerInformationPacket));
            }
        }

        if (isTownSquare) {
            Packet enableMovement = new Packet(PacketOperations.S2CEnableTownSquareMovement);
            connection.sendTCP(enableMovement);
        }
    }
}
