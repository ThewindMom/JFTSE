package com.jftse.emulator.server.core.service.impl;

import com.jftse.emulator.server.core.service.BattlemonLifecycleService;
import com.jftse.entities.database.model.pet.Pet;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.entities.database.model.pocket.Pocket;
import com.jftse.entities.database.repository.pet.PetRepository;
import com.jftse.entities.database.repository.pocket.PlayerPocketRepository;
import com.jftse.entities.database.repository.pocket.PocketRepository;
import com.jftse.server.core.item.EItemCategory;
import com.jftse.server.core.service.impl.PetLifecyclePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BattlemonLifecycleServiceImpl implements BattlemonLifecycleService {
    private static final int MAX_LIFE = 300;
    private static final int MAX_STAT = Byte.MAX_VALUE;
    private static final long MAX_WIRE_ITEM_ID = 0xFFFF_FFFFL;

    private final PetRepository petRepository;
    private final PlayerPocketRepository playerPocketRepository;
    private final PocketRepository pocketRepository;

    @Override
    @Transactional
    public MutationResult usePetItem(long playerId, long pocketId, long petId, long itemPocketId) {
        Pet pet = petRepository.findByIdAndPlayerIdForUpdate(petId, playerId).orElse(null);
        PetLifecyclePolicy.refresh(pet, Instant.now());
        PlayerPocket item = ownedItem(itemPocketId, pocketId, EItemCategory.PET_ITEM.getName());
        if (!isUsablePet(pet) || item == null || !applyPetItem(pet, item.getItemIndex())) {
            return MutationResult.failed(itemPocketId);
        }

        pet = petRepository.save(pet);
        return consume(item, pet);
    }

    @Override
    @Transactional
    public MutationResult renamePet(long playerId, long pocketId, long itemPocketId,
                                    byte petType, String newName) {
        Pet pet = findUniquePetByType(playerId, petType);
        PetLifecyclePolicy.refresh(pet, Instant.now());
        PlayerPocket item = ownedItem(itemPocketId, pocketId, EItemCategory.SPECIAL.getName());
        if (!isUsablePet(pet) || item == null || item.getItemIndex() != 10 ||
                newName == null || newName.isBlank() || newName.length() < 2 || newName.length() > 12) {
            return MutationResult.failed(itemPocketId);
        }

        pet.setName(newName);
        pet = petRepository.save(pet);
        return consume(item, pet);
    }

    @Override
    @Transactional
    public MutationResult revivePet(long playerId, long pocketId, long itemPocketId, byte petType) {
        Pet pet = findUniquePetByType(playerId, petType);
        Instant now = Instant.now();
        PetLifecyclePolicy.refresh(pet, now);
        PlayerPocket item = ownedItem(itemPocketId, pocketId, EItemCategory.SPECIAL.getName());
        PetLimits limits = pet == null ? null : PetLimits.forType(pet.getType());
        if (pet == null || item == null || item.getItemIndex() != 9 ||
                !Boolean.FALSE.equals(pet.getAlive()) ||
                pet.getLifeMax() == null || pet.getLifeMax() <= 0 || limits == null) {
            return MutationResult.failed(itemPocketId);
        }

        // Item_Special index 9 establishes maximum life, energy, and hunger, but
        // not the original server's exact expiry timestamp. Rebuilding expiry
        // from the pet's stored life maximum is this server's compatibility
        // interpretation until a successful retail response can distinguish it.
        pet.setAlive(true);
        pet.setEnergy(limits.energy());
        pet.setHunger(limits.hunger());
        pet.setValidUntil(Date.from(now.plus(Math.min(pet.getLifeMax(), MAX_LIFE), ChronoUnit.DAYS)));
        pet.setLifecycleUpdatedAt(Date.from(now));
        pet = petRepository.save(pet);
        return consume(item, pet);
    }

    private PlayerPocket ownedItem(long itemPocketId, long pocketId, String category) {
        if (itemPocketId < 0 || itemPocketId > MAX_WIRE_ITEM_ID) {
            return null;
        }
        PlayerPocket item = playerPocketRepository
                .findByIdAndPocketIdForUpdate(itemPocketId, pocketId)
                .orElse(null);
        if (item == null || !category.equals(item.getCategory()) ||
                !"Count".equals(item.getUseType()) || item.getItemCount() == null ||
                item.getItemCount() <= 0) {
            return null;
        }
        if (item.getItemCount() == 1) {
            Pocket pocket = pocketRepository.findByIdForUpdate(pocketId).orElse(null);
            if (pocket == null) return null;
            item.setPocket(pocket);
        }
        return item;
    }

    private boolean isUsablePet(Pet pet) {
        return pet != null && PetLimits.forType(pet.getType()) != null &&
                PetLifecyclePolicy.isAlive(pet, Instant.now());
    }

    private Pet findUniquePetByType(long playerId, byte petType) {
        if (PetLimits.forType(petType) == null) return null;
        List<Pet> pets = petRepository.findAllByPlayerIdAndTypeForUpdate(playerId, petType);
        return pets.size() == 1 ? pets.getFirst() : null;
    }

    private boolean applyPetItem(Pet pet, int itemIndex) {
        return switch (itemIndex) {
            case 1 -> addStrength(pet, 1);
            case 2 -> addStamina(pet, 1);
            case 3 -> addDexterity(pet, 1);
            case 4 -> addWillpower(pet, 1);
            case 5 -> addStrength(pet, 2);
            case 6 -> addStamina(pet, 2);
            case 7 -> addDexterity(pet, 2);
            case 8 -> addWillpower(pet, 2);
            case 9 -> addStrength(pet, 5);
            case 10 -> addStamina(pet, 5);
            case 11 -> addDexterity(pet, 5);
            case 12 -> addWillpower(pet, 5);
            case 13 -> extendLife(pet);
            case 14 -> increaseMaximumLife(pet);
            case 16 -> addHunger(pet, 5);
            case 17 -> addHunger(pet, 10);
            case 18 -> addHunger(pet, 20);
            case 19 -> addHunger(pet, 50);
            case 20 -> addEnergy(pet, 5);
            case 21 -> addEnergy(pet, 10);
            case 22 -> addEnergy(pet, 20);
            case 23 -> addEnergyAndHunger(pet, 50);
            default -> false;
        };
    }

    private boolean addStrength(Pet pet, int amount) {
        int value = Byte.toUnsignedInt(pet.getStrength()) + amount;
        if (value > MAX_STAT) return false;
        pet.setStrength((byte) value);
        return true;
    }

    private boolean addStamina(Pet pet, int amount) {
        int value = Byte.toUnsignedInt(pet.getStamina()) + amount;
        if (value > MAX_STAT) return false;
        pet.setStamina((byte) value);
        return true;
    }

    private boolean addDexterity(Pet pet, int amount) {
        int value = Byte.toUnsignedInt(pet.getDexterity()) + amount;
        if (value > MAX_STAT) return false;
        pet.setDexterity((byte) value);
        return true;
    }

    private boolean addWillpower(Pet pet, int amount) {
        int value = Byte.toUnsignedInt(pet.getWillpower()) + amount;
        if (value > MAX_STAT) return false;
        pet.setWillpower((byte) value);
        return true;
    }

    private boolean extendLife(Pet pet) {
        if (pet.getLifeMax() == null || pet.getLifeMax() <= 0 || pet.getValidUntil() == null) {
            return false;
        }
        Instant current = pet.getValidUntil().toInstant();
        Instant maximum = Instant.now().plus(Math.min(pet.getLifeMax(), MAX_LIFE), ChronoUnit.DAYS);
        Instant extended = current.plus(1, ChronoUnit.DAYS);
        if (extended.isAfter(maximum)) extended = maximum;
        if (!extended.isAfter(current)) return false;
        pet.setValidUntil(Date.from(extended));
        return true;
    }

    private boolean increaseMaximumLife(Pet pet) {
        int current = pet.getLifeMax() == null ? 0 : pet.getLifeMax();
        int increased = Math.min(MAX_LIFE, current + 5);
        if (increased <= current) return false;
        pet.setLifeMax(increased);
        return true;
    }

    private boolean addEnergy(Pet pet, int amount) {
        PetLimits limits = PetLimits.forType(pet.getType());
        if (limits == null) return false;
        int maximum = limits.energy();
        int increased = Math.min(maximum, pet.getEnergy() + amount);
        if (increased <= pet.getEnergy()) return false;
        pet.setEnergy(increased);
        return true;
    }

    private boolean addHunger(Pet pet, int amount) {
        PetLimits limits = PetLimits.forType(pet.getType());
        if (limits == null) return false;
        int maximum = limits.hunger();
        int increased = Math.min(maximum, pet.getHunger() + amount);
        if (increased <= pet.getHunger()) return false;
        pet.setHunger(increased);
        return true;
    }

    private boolean addEnergyAndHunger(Pet pet, int amount) {
        PetLimits limits = PetLimits.forType(pet.getType());
        if (limits == null) return false;
        int energy = Math.min(limits.energy(), pet.getEnergy() + amount);
        int hunger = Math.min(limits.hunger(), pet.getHunger() + amount);
        if (energy <= pet.getEnergy() && hunger <= pet.getHunger()) return false;
        pet.setEnergy(energy);
        pet.setHunger(hunger);
        return true;
    }

    private MutationResult consume(PlayerPocket item, Pet pet) {
        int remaining = item.getItemCount() - 1;
        if (remaining > 0) {
            item.setItemCount(remaining);
            playerPocketRepository.save(item);
        } else {
            Pocket pocket = item.getPocket();
            pocket.setBelongings(Math.max(0, pocket.getBelongings() - 1));
            playerPocketRepository.delete(item);
            pocketRepository.save(pocket);
        }
        return new MutationResult(true, pet, item.getId(), remaining);
    }

    private record PetLimits(int energy, int hunger) {
        private static PetLimits forType(byte type) {
            int unsignedType = Byte.toUnsignedInt(type);
            if (unsignedType == 0) return new PetLimits(50, 100);
            if (unsignedType <= 8) return new PetLimits(100, 150);
            return null;
        }
    }
}
