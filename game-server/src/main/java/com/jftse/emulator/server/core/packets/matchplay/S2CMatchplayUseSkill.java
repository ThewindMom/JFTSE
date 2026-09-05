package com.jftse.emulator.server.core.packets.matchplay;

import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.protocol.PacketOperations;
import lombok.Getter;

@Getter
public class S2CMatchplayUseSkill extends Packet {
    private final byte attacker;
    private final byte skillIndex;

    public S2CMatchplayUseSkill(byte attacker, byte target, byte skillId, byte seed, float xTarget, float zTarget, float yTarget) {
        super(PacketOperations.S2CMatchplayUseSkill);
        this.attacker = attacker;
        this.skillIndex = skillId;

        this.write(attacker);
        this.write(target);
        this.write(skillId);
        this.write(seed);
        this.write(xTarget);
        this.write(zTarget);
        this.write(yTarget);
    }
}
