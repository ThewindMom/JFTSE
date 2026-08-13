package com.jftse.emulator.server.core.service;

import com.jftse.entities.database.model.pet.Pet;

public interface BattlemonLifecycleService {
    MutationResult usePetItem(long playerId, long pocketId, long petId, long itemPocketId);

    MutationResult renamePet(long playerId, long pocketId, long itemPocketId,
                             byte petType, String newName);

    MutationResult revivePet(long playerId, long pocketId, long itemPocketId, byte petType);

    record MutationResult(boolean successful, Pet pet, long itemPocketId, int remainingItemCount) {
        private static final long MAX_WIRE_ITEM_ID = 0xFFFF_FFFFL;

        public static MutationResult failed(long itemPocketId) {
            return new MutationResult(false, null, itemPocketId, -1);
        }

        public int itemPocketWireId() {
            if (itemPocketId < 0 || itemPocketId > MAX_WIRE_ITEM_ID) {
                throw new IllegalArgumentException("item pocket ID does not fit the native uint32 field");
            }
            return (int) itemPocketId;
        }
    }
}
