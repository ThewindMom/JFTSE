package com.jftse.emulator.server.net;

import com.jftse.entities.database.model.pet.Pet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class FTClientStateTest {
    @Test
    void reconnectStartsWithoutPreviousActivePetSelection() {
        FTClient disconnectedClient = new FTClient();
        Pet selectedPet = new Pet();
        selectedPet.setId(10L);
        selectedPet.setType((byte) 1);
        selectedPet.setName("Pet");
        selectedPet.setLevel(1);
        selectedPet.setHp(100);
        selectedPet.setStrength((byte) 0);
        selectedPet.setStamina((byte) 0);
        selectedPet.setDexterity((byte) 0);
        selectedPet.setWillpower((byte) 0);
        selectedPet.setHunger(150);
        selectedPet.setEnergy(100);
        disconnectedClient.setActivePet(selectedPet);
        assertNotNull(disconnectedClient.getActivePet());

        FTClient reconnectedClient = new FTClient();

        assertNull(reconnectedClient.getActivePet());
    }
}
