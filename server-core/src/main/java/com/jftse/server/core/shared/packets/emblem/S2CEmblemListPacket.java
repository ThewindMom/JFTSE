package com.jftse.server.core.shared.packets.emblem;

import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.protocol.PacketOperations;
import com.jftse.server.core.service.EmblemQuestState;

import java.util.List;

public final class S2CEmblemListPacket extends Packet {
    private S2CEmblemListPacket(short status, List<EmblemQuestState> states) {
        super(PacketOperations.S2CEmblemListAnswer);
        write(status);
        if (status == -1 || status == -10) return;
        write((char) states.size());
        for (EmblemQuestState state : states) {
            write(state.questIndex(), (byte) (state.inProgress() ? 1 : 0), state.completionCount());
            write((byte) (state.condition1Present() ? 1 : 0), state.progress1());
            write((byte) (state.condition2Present() ? 1 : 0), state.progress2());
            write((byte) (state.condition3Present() ? 1 : 0), state.progress3());
            write((byte) (state.condition4Present() ? 1 : 0), state.progress4());
        }
    }

    public static S2CEmblemListPacket success(List<EmblemQuestState> states) {
        return new S2CEmblemListPacket((short) 0, List.copyOf(states));
    }

    public static S2CEmblemListPacket sentinel(short sentinel) {
        if (sentinel != -1 && sentinel != -10) throw new IllegalArgumentException("unsupported emblem sentinel");
        return new S2CEmblemListPacket(sentinel, List.of());
    }
}
