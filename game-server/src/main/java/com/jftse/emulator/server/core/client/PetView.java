package com.jftse.emulator.server.core.client;

import com.jftse.entities.database.model.pet.Pet;

public record PetView(long id, int type, String name, int level, int hp, int strength, int stamina, int dexterity, int willpower, int hunger, int energy) {
     public static PetView of(Pet pet) {
         return new PetView(
                 pet.getId(),
                 pet.getType(),
                 pet.getName(),
                 pet.getLevel(),
                 pet.getHp(),
                 pet.getStrength(),
                 pet.getStamina(),
                 pet.getDexterity(),
                 pet.getWillpower(),
                 pet.getHunger(),
                 pet.getEnergy()
         );
     }
}
