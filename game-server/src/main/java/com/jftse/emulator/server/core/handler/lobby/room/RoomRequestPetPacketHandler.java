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

            boolean dedicatedBattlemon = room.getRoomType() == RoomType.BATTLEMON;
            boolean guardianOwnedPetFeature = room.getMode() == GameMode.GUARDIAN &&
                    room.getAllowBattlemon() != 0;
            boolean enhanced = dedicatedBattlemon || guardianOwnedPetFeature;
            if (!enhanced) {
                handleOrdinaryRoom(connection, ftClient, room, roomPlayer, requestedSlot);
                return;
            }

            PetView selectedPetView = ftClient.getActivePet();
            if (selectedPetView == null) {
                connection.sendTCP(new S2CPetRequestRoomAnswerPacket(
                        S2CPetRequestRoomAnswerPacket.NO_PET_SELECTED, false, slot, null));
                return;
            }

            Pet selectedPet = petService.findByIdAndPlayerId(selectedPetView.id(), ftClient.getPlayer().getId());
            if (selectedPet == null || !Boolean.TRUE.equals(selectedPet.getAlive()) ||
                    selectedPet.getValidUntil() == null || selectedPet.getValidUntil().before(new Date())) {
                connection.sendTCP(new S2CPetRequestRoomAnswerPacket(
                        S2CPetRequestRoomAnswerPacket.CAN_NOT_ADD_PET, false, slot, null));
                return;
            }

            PetView pet;
            synchronized (room) {
                PetView currentSelectedPet = ftClient.getActivePet();
                int petPosition = roomPlayer.getPosition() + 2;
                if (ftClient.getActiveRoom() != room || room.getStatus() != RoomStatus.NotRunning ||
                        requestedSlot != roomPlayer.getPosition() || roomPlayer.getPet() != null ||
                        !dedicatedBattlemon && !(room.getMode() == GameMode.GUARDIAN && room.getAllowBattlemon() != 0) ||
                        roomPlayer.getPosition() < 0 || roomPlayer.getPosition() > 1 ||
                        petPosition >= room.getPositions().size() ||
                        room.getPositions().get(petPosition) != RoomPositionState.Free ||
                        room.getRoomPlayerList().stream()
                                .anyMatch(player -> player.getPosition() == petPosition) ||
                        currentSelectedPet == null || currentSelectedPet.id() != selectedPet.getId()) {
                    connection.sendTCP(new S2CPetRequestRoomAnswerPacket(
                            S2CPetRequestRoomAnswerPacket.CAN_NOT_ADD_PET, false, slot, null));
                    return;
                }
                ftClient.setActivePet(selectedPet);
                room.getPositions().set(petPosition, RoomPositionState.InUse);
                roomPlayer.setPet(PetView.of(selectedPet));
                pet = roomPlayer.getPet();
            }

            S2CPetRequestRoomAnswerPacket answer = new S2CPetRequestRoomAnswerPacket(
                    S2CPetRequestRoomAnswerPacket.SUCCESS, true, slot, pet);
            GameManager.getInstance().sendPacketToAllClientsInSameRoom(answer, connection);
        } catch (Exception e) {
            S2CPetRequestRoomAnswerPacket answer = new S2CPetRequestRoomAnswerPacket(
                    S2CPetRequestRoomAnswerPacket.CAN_NOT_ADD_PET, false, slot, null);
            connection.sendTCP(answer);
            log.error("Error in RoomRequestPetPacketHandler", e);
        }
    }

    private void handleOrdinaryRoom(FTConnection connection, FTClient ftClient, Room room,
                                    RoomPlayer roomPlayer, byte requestedSlot) {
        if (room.getAllowBattlemon() == 0) {
            connection.sendTCP(new S2CPetRequestRoomAnswerPacket(
                    S2CPetRequestRoomAnswerPacket.PET_NOT_ALLOWED, false, requestedSlot, null));
            return;
        }
        if (ftClient.getActivePet() == null) {
            connection.sendTCP(new S2CPetRequestRoomAnswerPacket(
                    S2CPetRequestRoomAnswerPacket.NO_PET_SELECTED, false, requestedSlot, null));
            return;
        }
        boolean slotNotFree = room.getRoomPlayerList().stream()
                .anyMatch(player -> player.getPosition() == requestedSlot + 2);
        if (slotNotFree) {
            connection.sendTCP(new S2CPetRequestRoomAnswerPacket(
                    S2CPetRequestRoomAnswerPacket.NO_FREE_SLOT, false, requestedSlot, null));
            return;
        }
        boolean isAdd = false;
        PetView ordinaryPet = roomPlayer.getPet();
        if (ordinaryPet != null) {
            roomPlayer.setPet(null);
        } else {
            roomPlayer.setPet(ftClient.getActivePet());
            ordinaryPet = roomPlayer.getPet();
            isAdd = true;
        }
        S2CPetRequestRoomAnswerPacket answer = new S2CPetRequestRoomAnswerPacket(
                S2CPetRequestRoomAnswerPacket.SUCCESS, isAdd, requestedSlot, ordinaryPet);
        GameManager.getInstance().sendPacketToAllClientsInSameRoom(answer, connection);
    }
}
