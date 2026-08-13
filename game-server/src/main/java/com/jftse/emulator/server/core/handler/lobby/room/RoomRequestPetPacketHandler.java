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
            if (room == null) {
                return;
            }

            RoomPlayer roomPlayer = ftClient.getRoomPlayer();
            if (roomPlayer == null) {
                return;
            }
            slot = (byte) roomPlayer.getPosition();
            PetView pet;
            synchronized (room) {
                if (room.getStatus() != RoomStatus.NotRunning || roomPlayer.isReady() ||
                        requestedSlot != roomPlayer.getPosition()) {
                    S2CPetRequestRoomAnswerPacket answer = new S2CPetRequestRoomAnswerPacket(
                            S2CPetRequestRoomAnswerPacket.CAN_NOT_ADD_PET, false, slot, null);
                    connection.sendTCP(answer);
                    return;
                }

                pet = roomPlayer.getPet();
                if (pet != null) {
                    if (room.getRoomType() == RoomType.BATTLEMON) {
                        S2CPetRequestRoomAnswerPacket answer = new S2CPetRequestRoomAnswerPacket(
                                S2CPetRequestRoomAnswerPacket.CAN_NOT_ADD_PET, false, slot, null);
                        connection.sendTCP(answer);
                        return;
                    }
                    int petPosition = roomPlayer.getPosition() + 2;
                    roomPlayer.setPet(null);
                    if (petPosition >= 0 && petPosition < room.getPositions().size() &&
                            room.getPositions().get(petPosition) == RoomPositionState.InUse &&
                            room.getRoomPlayerList().stream()
                                    .noneMatch(player -> player.getPosition() == petPosition)) {
                        room.getPositions().set(petPosition, RoomPositionState.Free);
                    }
                } else if (room.getAllowBattlemon() == 0 ||
                        room.getRoomType() != RoomType.BATTLEMON && room.getMode() != GameMode.GUARDIAN) {
                    S2CPetRequestRoomAnswerPacket petRequestRoomAnswerPacket = new S2CPetRequestRoomAnswerPacket(S2CPetRequestRoomAnswerPacket.PET_NOT_ALLOWED, false, slot, null);
                    connection.sendTCP(petRequestRoomAnswerPacket);
                    return;
                } else {
                    int petPosition = roomPlayer.getPosition() + 2;
                    if (roomPlayer.getPosition() < 0 || roomPlayer.getPosition() > 1 ||
                            petPosition >= room.getPositions().size() ||
                            room.getPositions().get(petPosition) != RoomPositionState.Free ||
                            room.getRoomPlayerList().stream()
                                    .anyMatch(player -> player.getPosition() == petPosition)) {
                        S2CPetRequestRoomAnswerPacket answer = new S2CPetRequestRoomAnswerPacket(
                                S2CPetRequestRoomAnswerPacket.NO_FREE_SLOT, false, slot, null);
                        connection.sendTCP(answer);
                        return;
                    }
                }
            }
            if (pet != null) {
                S2CPetRequestRoomAnswerPacket petRequestRoomAnswerPacket = new S2CPetRequestRoomAnswerPacket(S2CPetRequestRoomAnswerPacket.SUCCESS, false, slot, pet);
                GameManager.getInstance().sendPacketToAllClientsInSameRoom(petRequestRoomAnswerPacket, connection);
                return;
            }

            PetView selectedPetView = ftClient.getActivePet();
            if (selectedPetView == null) {
                S2CPetRequestRoomAnswerPacket petRequestRoomAnswerPacket = new S2CPetRequestRoomAnswerPacket(S2CPetRequestRoomAnswerPacket.NO_PET_SELECTED, false, slot, null);
                connection.sendTCP(petRequestRoomAnswerPacket);
                return;
            }

            Pet selectedPet = petService.findByIdAndPlayerId(selectedPetView.id(), ftClient.getPlayer().getId());
            if (selectedPet == null || !Boolean.TRUE.equals(selectedPet.getAlive()) ||
                    selectedPet.getValidUntil() == null || selectedPet.getValidUntil().before(new Date())) {
                S2CPetRequestRoomAnswerPacket petRequestRoomAnswerPacket = new S2CPetRequestRoomAnswerPacket(S2CPetRequestRoomAnswerPacket.CAN_NOT_ADD_PET, false, slot, null);
                connection.sendTCP(petRequestRoomAnswerPacket);
                return;
            }

            synchronized (room) {
                PetView currentSelectedPet = ftClient.getActivePet();
                int petPosition = roomPlayer.getPosition() + 2;
                if (ftClient.getActiveRoom() != room || room.getStatus() != RoomStatus.NotRunning ||
                        requestedSlot != roomPlayer.getPosition() || roomPlayer.getPet() != null ||
                        room.getAllowBattlemon() == 0 ||
                        room.getRoomType() != RoomType.BATTLEMON && room.getMode() != GameMode.GUARDIAN ||
                        roomPlayer.getPosition() < 0 || roomPlayer.getPosition() > 1 ||
                        petPosition >= room.getPositions().size() ||
                        room.getPositions().get(petPosition) != RoomPositionState.Free ||
                        room.getRoomPlayerList().stream()
                                .anyMatch(player -> player.getPosition() == petPosition) ||
                        currentSelectedPet == null || currentSelectedPet.id() != selectedPet.getId()) {
                    S2CPetRequestRoomAnswerPacket answer = new S2CPetRequestRoomAnswerPacket(
                            S2CPetRequestRoomAnswerPacket.CAN_NOT_ADD_PET, false, slot, null);
                    connection.sendTCP(answer);
                    return;
                }
                ftClient.setActivePet(selectedPet);
                room.getPositions().set(petPosition, RoomPositionState.InUse);
                roomPlayer.setPet(PetView.of(selectedPet));
                pet = roomPlayer.getPet();
            }

            S2CPetRequestRoomAnswerPacket petRequestRoomAnswerPacket = new S2CPetRequestRoomAnswerPacket(S2CPetRequestRoomAnswerPacket.SUCCESS, true, slot, pet);
            GameManager.getInstance().sendPacketToAllClientsInSameRoom(petRequestRoomAnswerPacket, connection);
        } catch (Exception e) {
            S2CPetRequestRoomAnswerPacket petRequestRoomAnswerPacket = new S2CPetRequestRoomAnswerPacket(S2CPetRequestRoomAnswerPacket.CAN_NOT_ADD_PET, false, slot, null);
            connection.sendTCP(petRequestRoomAnswerPacket);

            log.error("Error in RoomRequestPetPacketHandler", e);
        }
    }
}
