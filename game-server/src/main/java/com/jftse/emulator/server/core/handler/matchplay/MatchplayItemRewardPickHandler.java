package com.jftse.emulator.server.core.handler.matchplay;

import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.matchplay.GameSessionManager;
import com.jftse.emulator.server.core.matchplay.MatchplayReward;
import com.jftse.emulator.server.core.packets.inventory.S2CInventoryItemCountPacket;
import com.jftse.emulator.server.core.packets.inventory.S2CInventoryItemsPlacePacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.item.EItemUseType;
import com.jftse.server.core.protocol.IPacket;
import com.jftse.server.core.shared.packets.matchplay.CMSGPickupItemReward;
import com.jftse.server.core.shared.packets.matchplay.SMSGPickupItemReward;
import com.jftse.server.core.thread.ThreadManager;

import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@PacketId(CMSGPickupItemReward.PACKET_ID)
public class MatchplayItemRewardPickHandler implements PacketHandler<FTConnection, CMSGPickupItemReward> {
    @Override
    public void handle(FTConnection connection, CMSGPickupItemReward packet) {
        FTClient client = connection.getClient();
        Room room;
        RoomPlayer seat;
        long generation;
        synchronized (client) {
            room = client.getActiveRoom();
            seat = client.getRoomPlayer();
            generation = client.getGameSessionGeneration();
            if (!client.hasPlayer() || room == null || seat == null) {
                connection.close();
                return;
            }
        }
        MatchplayReward reward = GameSessionManager.getInstance().getMatchplayReward(room.getRoomId());
        if (reward != null) claim(client, room, seat, generation, reward, packet.getSlot());
    }

    public boolean claim(FTClient client, Room room, RoomPlayer seat, long generation, MatchplayReward reward, byte slot) {
        var itemReward = reward.getSlotReward(slot);
        if (itemReward == null) return false;
        long pocketId;
        short position;
        int productIndex = itemReward.getProductIndex();
        int quantity = itemReward.getProductAmount();
        synchronized (client) {
            if (!client.hasPlayer() || client.getActiveRoom() != room || client.getRoomPlayer() != seat ||
                    client.getGameSessionGeneration() != generation ||
                    GameSessionManager.getInstance().getMatchplayReward(room.getRoomId()) != reward) return false;
            position = seat.getPosition();
            if (!reward.tryClaim(slot, position)) return false;
            pocketId = client.getPlayer().getPocketId();
        }
        AtomicReference<IPacket> inventory = new AtomicReference<>();
        record Recipient(FTClient client, RoomPlayer seat, long generation) {}
        var recipients = GameManager.getInstance().getClientsInRoom(room.getRoomId()).stream().map(recipient -> {
            synchronized (recipient) {
                return new Recipient(recipient, recipient.getRoomPlayer(), recipient.getGameSessionGeneration());
            }
        }).toList();
        ServiceManager services = ServiceManager.getInstance();
        try {
            services.getMatchResultService().executeOnce(itemReward.getResultId(), () -> {
                if (productIndex <= 0) return;
                var product = services.getProductService().findProductByProductItemIndex(productIndex);
                if (product == null) throw new IllegalStateException("Reward product is unavailable");
                var pocket = services.getPocketService().findById(pocketId);
                PlayerPocket item = services.getPlayerPocketService().getItemAsPocketByItemIndexAndCategoryAndPocket(
                        product.getItem0(), product.getCategory(), pocket);
                boolean existing = item != null && !"N/A".equals(item.getUseType());
                if (!existing) item = new PlayerPocket();
                item.setCategory(product.getCategory());
                item.setItemIndex(product.getItem0());
                item.setUseType(product.getUseType());
                item.setItemCount((item.getItemCount() == null ? 0 : item.getItemCount()) + quantity);
                if (EItemUseType.TIME.getName().equalsIgnoreCase(item.getUseType())) {
                    Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                    calendar.add(Calendar.DAY_OF_MONTH, item.getItemCount());
                    item.setCreated(calendar.getTime());
                    item.setItemCount(1);
                }
                item.setPocket(pocket);
                services.getPlayerPocketService().save(item);
                if (!existing) services.getPocketService().incrementPocketBelongings(pocket);
                inventory.set(existing ? new S2CInventoryItemCountPacket(item) : new S2CInventoryItemsPlacePacket(List.of(item)));
            });
        } catch (RuntimeException failure) {
            reward.releaseClaim(slot, position, itemReward);
            throw failure;
        }
        itemReward.getCommitted().set(true);
        var response = SMSGPickupItemReward.builder().playerPos((byte) position).slot(slot)
                .type((byte) 0).productIndex(productIndex).quantity(quantity).build();
        try {
            synchronized (client) {
                if (client.getActiveRoom() == room && client.getRoomPlayer() == seat &&
                        GameSessionManager.getInstance().getMatchplayReward(room.getRoomId()) == reward &&
                        client.getGameSessionGeneration() == generation && client.getConnection() != null) {
                    client.getConnection().sendTCP(response);
                    if (inventory.get() != null) client.getConnection().sendTCP(inventory.get());
                }
            }
            for (Recipient endpoint : recipients) {
                FTClient recipient = endpoint.client();
                RoomPlayer recipientSeat = endpoint.seat();
                long recipientGeneration = endpoint.generation();
                if (recipientSeat == null || recipientSeat.getPosition() == position) continue;
                ThreadManager.getInstance().schedule(() -> {
                    synchronized (recipient) {
                        MatchplayReward current = GameSessionManager.getInstance().getMatchplayReward(room.getRoomId());
                        if (recipient.getActiveRoom() == room && recipient.getRoomPlayer() == recipientSeat &&
                                (current == null || current == reward) &&
                                recipient.getGameSessionGeneration() == recipientGeneration && recipient.getConnection() != null) {
                            recipient.getConnection().sendTCP(response);
                        }
                    }
                }, 20, TimeUnit.MILLISECONDS);
            }
        } finally {
            long committed = reward.getSlotRewards().values().stream().filter(item -> item.getCommitted().get()).count();
            long players = room.getRoomPlayerList().stream().filter(player -> player.getPosition() < 4).count();
            if (committed == reward.getSlotRewards().size() || committed == players)
                GameSessionManager.getInstance().removeMatchplayReward(room.getRoomId(), reward);
        }
        return true;
    }
}
