package com.jftse.emulator.server.core.handler.pet;

import com.jftse.emulator.server.core.client.PetView;
import com.jftse.emulator.server.core.constants.RoomType;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.pet.Pet;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.service.PetService;
import com.jftse.server.core.shared.packets.pet.CMSGPickupPet;
import com.jftse.server.core.shared.packets.pet.SMSGPickupPet;

import java.util.Date;

@PacketId(CMSGPickupPet.PACKET_ID)
public class PetPickupRequestPacketHandler implements PacketHandler<FTConnection, CMSGPickupPet> {
    private final PetService petService;

    public PetPickupRequestPacketHandler() {
        petService = ServiceManager.getInstance().getPetService();
    }

    @Override
    public void handle(FTConnection connection, CMSGPickupPet packet) {
        FTClient ftClient = connection.getClient();
        if (!ftClient.hasPlayer()) {
            return;
        }

        int newActivePetType = packet.getPetType();
        Room activeRoom = ftClient.getActiveRoom();
        if (activeRoom != null) {
            synchronized (activeRoom) {
                handleSelection(connection, ftClient, activeRoom, newActivePetType);
            }
            return;
        }
        handleSelection(connection, ftClient, null, newActivePetType);
    }

    private void handleSelection(FTConnection connection, FTClient ftClient, Room activeRoom,
                                 int newActivePetType) {
        RoomPlayer roomPlayer = activeRoom == null ? null : ftClient.getRoomPlayer();
        PetView roomPet = roomPlayer == null ? null : roomPlayer.getPet();
        if (activeRoom != null && (activeRoom.getRoomType() == RoomType.BATTLEMON || roomPet != null)) {
            PetView activePet = ftClient.getActivePet();
            boolean unchanged = roomPet != null && activePet != null &&
                    roomPet.id() == activePet.id() && roomPet.type() == activePet.type() &&
                    newActivePetType == roomPet.type();
            connection.sendTCP(SMSGPickupPet.builder()
                    .result((short) (unchanged ? 0 : 1))
                    .petType(newActivePetType)
                    .build());
            return;
        }

        SMSGPickupPet petPickup;
        if (newActivePetType == -1) {
            ftClient.setActivePet(null);
            petPickup = SMSGPickupPet.builder()
                    .result((short) 0)
                    .petType(newActivePetType)
                    .build();
        } else {
            Pet pet = petService.findAllByPlayerId(ftClient.getPlayer().getId()).stream()
                    .filter(candidate -> candidate.getType() == (byte) newActivePetType)
                    .findFirst()
                    .orElse(null);
            if (!isSelectable(pet)) {
                petPickup = SMSGPickupPet.builder()
                        .result((short) 1)
                        .petType(newActivePetType)
                        .build();
            } else {
                ftClient.setActivePet(pet);
                petPickup = SMSGPickupPet.builder()
                        .result((short) 0)
                        .petType(pet.getType().intValue())
                        .build();
            }
        }
        connection.sendTCP(petPickup);
    }

    private boolean isSelectable(Pet pet) {
        return pet != null && Boolean.TRUE.equals(pet.getAlive()) &&
                pet.getValidUntil() != null && pet.getValidUntil().after(new Date());
    }
}
