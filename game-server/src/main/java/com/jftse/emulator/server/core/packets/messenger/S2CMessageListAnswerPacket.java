package com.jftse.emulator.server.core.packets.messenger;

import com.jftse.entities.database.model.messenger.AbstractMessage;
import com.jftse.entities.database.model.messenger.Gift;
import com.jftse.entities.database.model.messenger.Message;
import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.protocol.PacketOperations;

import java.util.List;

public class S2CMessageListAnswerPacket extends Packet {
    public S2CMessageListAnswerPacket(byte listType, List<? extends AbstractMessage> messageList) {
        super(PacketOperations.S2CMessageListAnswer);

        this.write(listType);

        int size = 0;
        int packetSize = 10; // 8-byte header, list type and count
        while (size < messageList.size() && size < Byte.MAX_VALUE) {
            AbstractMessage message = messageList.get(size);
            String playerName = (listType % 2) == 0 ? message.getSender().getName() : message.getReceiver().getName();
            int entrySize = 22 + 2 * (playerName.length() + message.getMessage().length());
            if (packetSize + entrySize > 16 * 1024)
                break;
            packetSize += entrySize;
            size++;
        }
        messageList = messageList.subList(0, size);
        this.write((byte) size);
        for (AbstractMessage am : messageList) {
            if (am instanceof Message m) {
                this.write(Math.toIntExact(m.getId()));
                this.write((listType % 2) == 0 ? m.getSender().getName() : m.getReceiver().getName());
                this.write((listType % 2) == 0 ? m.getSeen() : true);
                this.write(m.getMessage());
                this.write(m.getCreated());
                this.write(0); // product index
                this.write(m.getUseTypeOption());

            } else if (am instanceof Gift g) {
                this.write(Math.toIntExact(g.getId()));
                this.write((listType % 2) == 0 ? g.getSender().getName() : g.getReceiver().getName());
                this.write((listType % 2) == 0 ? g.getSeen() : true);
                this.write(g.getMessage());
                this.write(g.getCreated());
                this.write(g.getProduct().getProductIndex());
                this.write(g.getUseTypeOption());
            }
        }
    }
}
