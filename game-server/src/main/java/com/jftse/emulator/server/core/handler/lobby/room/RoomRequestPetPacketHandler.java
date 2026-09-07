package com.jftse.emulator.server.core.handler.lobby.room;

import com.jftse.emulator.server.core.client.PetView;
import com.jftse.emulator.server.core.constants.RoomPositionState;
import com.jftse.emulator.server.core.constants.RoomStatus;
import com.jftse.emulator.server.core.constants.RoomType;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.packets.lobby.room.S2CPetRequestRoomAnswerPacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.pet.Pet;
import com.jftse.server.core.constants.GameMode;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.service.PetService;
import com.jftse.server.core.shared.packets.pet.CMSGRequestPet;
import lombok.extern.log4j.Log4j2;

import java.util.Date;

@Log4j2
@PacketId(CMSGRequestPet.PACKET_ID)
public class RoomRequestPetPacketHandler implements PacketHandler<FTConnection, CMSGRequestPet> {
    private final PetService petService;

    public RoomRequestPetPacketHandler() {
        petService = ServiceManager.getInstance().getPetService();
    }

    @Override
    public void handle(FTConnection connection, CMSGRequestPet packet) {
        byte requestedSlot = packet.getSlot();
        byte slot = requestedSlot;
        try {
            FTClient ftClient = connection.getClient();
            if (ftClient == null) {
                return;
            }
            Room room = ftClient.getActiveRoom();
            RoomPlayer roomPlayer = ftClient.getRoomPlayer();
            if (room == null || roomPlayer == null) {
                return;
            }

            boolean dedicatedBattlemon = room.getRoomType() == RoomType.BATTLEMON;
            boolean guardianOwnedPet = room.getMode() == GameMode.GUARDIAN && room.getAllowBattlemon() != 0;
            if (!dedicatedBattlemon && !guardianOwnedPet) {
                handleOrdinaryRoom(connection, ftClient, room, roomPlayer, requestedSlot);
                return;
            }

            slot = (byte) roomPlayer.getPosition();
            int petPosition = roomPlayer.getPosition() + 2;
            PetView detached;
            synchronized (room) {
                if (room.getStatus() != RoomStatus.NotRunning || roomPlayer.isReady()
                        || requestedSlot != roomPlayer.getPosition()) {
                    connection.sendTCP(answer(S2CPetRequestRoomAnswerPacket.CAN_NOT_ADD_PET, false, slot, null));
                    return;
                }
                detached = roomPlayer.getPet();
                if (detached != null) {
                    if (dedicatedBattlemon) {
                        connection.sendTCP(answer(S2CPetRequestRoomAnswerPacket.CAN_NOT_ADD_PET, false, slot, null));
                        return;
                    }
                    roomPlayer.setPet(null);
                    if (petPosition < room.getPositions().size()
                            && room.getPositions().get(petPosition) == RoomPositionState.InUse
                            && !isSeatOccupied(room, petPosition)) {
                        room.getPositions().set(petPosition, RoomPositionState.Free);
                    }
                } else if (!isPetSeatFree(room, roomPlayer, petPosition)) {
                    connection.sendTCP(answer(S2CPetRequestRoomAnswerPacket.NO_FREE_SLOT, false, slot, null));
                    return;
                }
            }
            if (detached != null) {
                broadcast(connection, answer(S2CPetRequestRoomAnswerPacket.SUCCESS, false, slot, detached));
                return;
            }

            PetView selectedPetView = ftClient.getActivePet();
            if (selectedPetView == null) {
                connection.sendTCP(answer(S2CPetRequestRoomAnswerPacket.NO_PET_SELECTED, false, slot, null));
                return;
            }
            Pet selectedPet = petService.findByIdAndPlayerId(selectedPetView.id(), ftClient.getPlayer().getId());
            if (selectedPet == null || !Boolean.TRUE.equals(selectedPet.getAlive())
                    || selectedPet.getValidUntil() == null || selectedPet.getValidUntil().before(new Date())) {
                connection.sendTCP(answer(S2CPetRequestRoomAnswerPacket.CAN_NOT_ADD_PET, false, slot, null));
                return;
            }

            PetView attached;
            synchronized (room) {
                PetView currentSelectedPet = ftClient.getActivePet();
                if (ftClient.getActiveRoom() != room || room.getStatus() != RoomStatus.NotRunning
                        || requestedSlot != roomPlayer.getPosition() || roomPlayer.getPet() != null
                        || !isPetSeatFree(room, roomPlayer, petPosition)
                        || currentSelectedPet == null || currentSelectedPet.id() != selectedPet.getId()) {
                    connection.sendTCP(answer(S2CPetRequestRoomAnswerPacket.CAN_NOT_ADD_PET, false, slot, null));
                    return;
                }
                ftClient.setActivePet(selectedPet);
                room.getPositions().set(petPosition, RoomPositionState.InUse);
                attached = PetView.of(selectedPet);
                roomPlayer.setPet(attached);
            }
            broadcast(connection, answer(S2CPetRequestRoomAnswerPacket.SUCCESS, true, slot, attached));
        } catch (Exception e) {
            connection.sendTCP(answer(S2CPetRequestRoomAnswerPacket.CAN_NOT_ADD_PET, false, slot, null));
            log.error("Error in RoomRequestPetPacketHandler", e);
        }
    }

    private void handleOrdinaryRoom(FTConnection connection, FTClient ftClient, Room room,
                                    RoomPlayer roomPlayer, byte requestedSlot) {
        if (room.getAllowBattlemon() == 0) {
            connection.sendTCP(answer(S2CPetRequestRoomAnswerPacket.PET_NOT_ALLOWED, false, requestedSlot, null));
            return;
        }
        if (ftClient.getActivePet() == null) {
            connection.sendTCP(answer(S2CPetRequestRoomAnswerPacket.NO_PET_SELECTED, false, requestedSlot, null));
            return;
        }
        if (isSeatOccupied(room, requestedSlot + 2)) {
            connection.sendTCP(answer(S2CPetRequestRoomAnswerPacket.NO_FREE_SLOT, false, requestedSlot, null));
            return;
        }
        boolean isAdd = roomPlayer.getPet() == null;
        roomPlayer.setPet(isAdd ? ftClient.getActivePet() : null);
        broadcast(connection, answer(S2CPetRequestRoomAnswerPacket.SUCCESS, isAdd, requestedSlot, roomPlayer.getPet()));
    }

    private static boolean isPetSeatFree(Room room, RoomPlayer roomPlayer, int petPosition) {
        return roomPlayer.getPosition() >= 0 && roomPlayer.getPosition() <= 1
                && petPosition < room.getPositions().size()
                && room.getPositions().get(petPosition) == RoomPositionState.Free
                && !isSeatOccupied(room, petPosition);
    }

    private static boolean isSeatOccupied(Room room, int position) {
        return room.getRoomPlayerList().stream().anyMatch(player -> player.getPosition() == position);
    }

    private static S2CPetRequestRoomAnswerPacket answer(byte result, boolean isAdd, byte slot, PetView pet) {
        return new S2CPetRequestRoomAnswerPacket(result, isAdd, slot, pet);
    }

    private static void broadcast(FTConnection connection, S2CPetRequestRoomAnswerPacket answer) {
        GameManager.getInstance().sendPacketToAllClientsInSameRoom(answer, connection);
    }
}
