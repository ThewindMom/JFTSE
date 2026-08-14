package com.jftse.emulator.server.core.packets.matchplay;

import com.jftse.emulator.server.core.life.room.GameplayActor;
import com.jftse.emulator.server.core.matchplay.PlayerReward;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class S2CMatchplaySetGameResultDataTest {
    @Test
    void battlemonResultsShowEachPetWithItsOwnersExperienceAndNoGold() {
        PlayerReward playerZero = reward(0, 350, 420, 8);
        PlayerReward playerOne = reward(1, 270, 300, 8);
        List<GameplayActor> actors = List.of(
                actor((short) 2, (short) 0, 11L),
                actor((short) 3, (short) 1, 22L));

        S2CMatchplaySetGameResultData packet = new S2CMatchplaySetGameResultData(
                List.of(playerOne, playerZero), actors);

        ByteBuffer expected = ByteBuffer.allocate(1 + 4 * 16).order(ByteOrder.nativeOrder());
        expected.put((byte) 4);
        putReward(expected, 0, 350, 420, 8);
        putReward(expected, 1, 270, 300, 8);
        putReward(expected, 2, 350, 0, 0);
        putReward(expected, 3, 270, 0, 0);
        assertArrayEquals(expected.array(), packetPayload(packet));
    }

    private static PlayerReward reward(int position, int experience, int gold, int activeBonuses) {
        PlayerReward reward = new PlayerReward(position);
        reward.setExp(experience);
        reward.setGold(gold);
        reward.setActiveBonuses(activeBonuses);
        return reward;
    }

    private static GameplayActor actor(short position, short ownerPosition, long ownerPlayerId) {
        return new GameplayActor(
                position, ownerPosition, ownerPlayerId,
                new com.jftse.emulator.server.core.client.PetView(
                        ownerPlayerId, 1, "Pet", 1, 100, 0, 0, 0, 0, 0, 0),
                0, 0, 0, 0, 0);
    }

    private static void putReward(ByteBuffer buffer, int position, int experience, int gold, int activeBonuses) {
        buffer.putShort((short) position);
        buffer.putShort((short) 0);
        buffer.putInt(experience);
        buffer.putInt(gold);
        buffer.putInt(activeBonuses);
    }

    private static byte[] packetPayload(S2CMatchplaySetGameResultData packet) {
        return java.util.Arrays.copyOf(packet.getData(), packet.getDataLength());
    }
}
