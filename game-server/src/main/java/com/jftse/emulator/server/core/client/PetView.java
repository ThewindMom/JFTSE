package com.jftse.emulator.server.core.client;

import com.jftse.entities.database.model.pet.Pet;

public record PetView(long id, int type, String name, int level, int hp, int strength, int stamina, int dexterity, int willpower, int hunger, int energy) {
     public static PetView of(Pet pet) {
         return new PetView(
                 pet.getId(),
                 Byte.toUnsignedInt(pet.getType()),
                 pet.getName(),
                 pet.getLevel(),
                 pet.getHp(),
                 Byte.toUnsignedInt(pet.getStrength()),
                 Byte.toUnsignedInt(pet.getStamina()),
                 Byte.toUnsignedInt(pet.getDexterity()),
                 Byte.toUnsignedInt(pet.getWillpower()),
                 pet.getHunger(),
                 pet.getEnergy()
         );
     }
}
