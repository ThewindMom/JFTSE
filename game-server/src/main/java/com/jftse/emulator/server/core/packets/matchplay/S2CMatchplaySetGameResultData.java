package com.jftse.emulator.server.core.packets.matchplay;

import com.jftse.emulator.server.core.life.room.GameplayActor;
import com.jftse.emulator.server.core.matchplay.PlayerReward;
import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.protocol.PacketOperations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public class S2CMatchplaySetGameResultData extends Packet {
    public S2CMatchplaySetGameResultData(List<PlayerReward> playerRewards,
                                         Collection<GameplayActor> ownedPetSeats) {
        this(withOwnedPetRewards(playerRewards, ownedPetSeats));
    }

    public S2CMatchplaySetGameResultData(List<PlayerReward> playerRewards) {
        super(PacketOperations.S2CMatchplaySetGameResultData);

        this.write((byte) playerRewards.size());
        for (PlayerReward playerReward : playerRewards) {
            this.write((short) playerReward.getPlayerPosition());
            this.write((short) 0);
            this.write(playerReward.getExp()); // EXP
            this.write(playerReward.getGold()); // GOLD

            // 0000 0001 = PF, 0000 0010 = GB, 0000 0100 = Time, 0000 1000 = matchplay, 0001 0000 = Lv up, ...
            // 0000 0001 = Couple Bonus
            // 0000 0001 = EXP Bonus, 0000 0010 = Gold Bonus, 0000 1000 = Ring Wiseman, 0000 0100 = Event
            this.write(playerReward.getActiveBonuses());
        }
    }

    static List<PlayerReward> withOwnedPetRewards(List<PlayerReward> playerRewards,
                                                 Collection<GameplayActor> ownedPetSeats) {
        List<PlayerReward> resultRewards = new ArrayList<>(playerRewards);
        for (GameplayActor actor : ownedPetSeats) {
            if (actor.isHuman()) {
                continue;
            }
            PlayerReward ownerReward = playerRewards.stream()
                    .filter(reward -> reward.getPlayerPosition() == actor.ownerPosition())
                    .findFirst()
                    .orElse(null);
            if (ownerReward == null) {
                continue;
            }
            PlayerReward petReward = new PlayerReward(actor.position());
            petReward.setExp(ownerReward.getExp());
            resultRewards.add(petReward);
        }
        resultRewards.sort(Comparator.comparingInt(PlayerReward::getPlayerPosition));
        return resultRewards;
    }
}
