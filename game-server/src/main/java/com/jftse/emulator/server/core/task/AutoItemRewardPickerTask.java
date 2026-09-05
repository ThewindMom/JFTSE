package com.jftse.emulator.server.core.task;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.matchplay.GameSessionManager;
import com.jftse.emulator.server.core.matchplay.MatchplayReward;
import com.jftse.emulator.server.core.packets.inventory.S2CInventoryItemCountPacket;
import com.jftse.emulator.server.core.packets.inventory.S2CInventoryItemsPlacePacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.entities.database.model.item.Product;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.entities.database.model.pocket.Pocket;
import com.jftse.server.core.item.EItemUseType;
import com.jftse.server.core.service.PlayerPocketService;
import com.jftse.server.core.service.PocketService;
import com.jftse.server.core.service.ProductService;
import com.jftse.server.core.shared.packets.matchplay.SMSGPickupItemReward;
import com.jftse.server.core.thread.AbstractTask;

import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentLinkedDeque;

public class AutoItemRewardPickerTask extends AbstractTask {
    private final ConcurrentLinkedDeque<FTClient> clients;
    private final short roomId;
    private final Room room;
    private final MatchplayReward matchplayReward;
    private final Map<FTClient, RoomPlayer> participants = new java.util.IdentityHashMap<>();
    private final Map<FTClient, Long> generations = new java.util.IdentityHashMap<>();

    private final PlayerPocketService playerPocketService;
    private final PocketService pocketService;
    private final ProductService productService;

    public AutoItemRewardPickerTask(final ConcurrentLinkedDeque<FTClient> clients, short roomId) {
        this(clients, clients.isEmpty() ? null : clients.getFirst().getActiveRoom(),
                GameSessionManager.getInstance().getMatchplayReward(roomId));
    }

    public AutoItemRewardPickerTask(final ConcurrentLinkedDeque<FTClient> clients, Room room, MatchplayReward reward) {
        this.clients = clients;
        this.room = room;
        this.roomId = room == null ? -1 : room.getRoomId();
        this.matchplayReward = reward;
        clients.forEach(client -> {
            synchronized (client) {
                if (client.hasPlayer() && client.getActiveRoom() == room) {
                    participants.put(client, client.getRoomPlayer());
                    generations.put(client, client.getGameSessionGeneration());
                }
            }
        });

        this.playerPocketService = ServiceManager.getInstance().getPlayerPocketService();
        this.pocketService = ServiceManager.getInstance().getPocketService();
        this.productService = ServiceManager.getInstance().getProductService();
    }

    @Override
    public void run() {
        if (room != null && matchplayReward != null &&
                GameSessionManager.getInstance().getMatchplayReward(roomId) == matchplayReward) {
            final Map<Byte, MatchplayReward.ItemReward> slotRewards = matchplayReward.getSlotRewards();

            for (final FTClient client : clients) {
                if (!client.hasPlayer())
                    continue;

                RoomPlayer rp = participants.get(client);
                FTPlayer player = client.getPlayer();
                if (rp == null)
                    continue;

                final boolean isActivePlayer = rp.getPosition() < 4;
                if (isActivePlayer) {
                    if (slotRewards.values().stream().anyMatch(r -> r.getClaimedPlayerPosition() == rp.getPosition())) {
                        continue;
                    }

                    boolean rewardClaimed = false;
                    while (!rewardClaimed && slotRewards.values().stream()
                            .noneMatch(item -> item.getClaimedPlayerPosition() == rp.getPosition())) {
                        List<Map.Entry<Byte, MatchplayReward.ItemReward>> unclaimedRewards = slotRewards.entrySet().stream()
                                .filter(entry -> !entry.getValue().getClaimed().get())
                                .toList();

                        if (unclaimedRewards.isEmpty()) {
                            GameSessionManager.getInstance().removeMatchplayReward(roomId, matchplayReward);
                            break;
                        }

                        int randomIndex = (int) (Math.random() * unclaimedRewards.size());
                        Map.Entry<Byte, MatchplayReward.ItemReward> selectedRewardEntry = unclaimedRewards.get(randomIndex);
                        byte requestingSlot = selectedRewardEntry.getKey();
                        MatchplayReward.ItemReward itemReward = selectedRewardEntry.getValue();

                        synchronized (client) {
                            if (client.getActiveRoom() != room || client.getRoomPlayer() != rp ||
                                    client.getGameSessionGeneration() != generations.get(client) ||
                                    GameSessionManager.getInstance().getMatchplayReward(roomId) != matchplayReward) break;
                            rewardClaimed = matchplayReward.tryClaim(requestingSlot, rp.getPosition());
                        }
                        if (rewardClaimed) {

                            SMSGPickupItemReward itemRewardPickup = SMSGPickupItemReward.builder()
                                    .playerPos((byte) rp.getPosition())
                                    .slot(requestingSlot)
                                    .type((byte) 0) // 0 = product, 1 = material
                                    .productIndex(itemReward.getProductIndex())
                                    .quantity(itemReward.getProductAmount())
                                    .build();
                            for (FTClient recipient : clients) {
                                synchronized (recipient) {
                                    if (recipient.getActiveRoom() == room && participants.get(recipient) != null &&
                                            recipient.getRoomPlayer() == participants.get(recipient) &&
                                            recipient.getGameSessionGeneration() == generations.get(recipient) &&
                                            recipient.getConnection() != null) {
                                        recipient.getConnection().sendTCP(itemRewardPickup);
                                    }
                                }
                            }

                            // add reward to player pocket
                            int productIndex = itemReward.getProductIndex();
                            int productAmount = itemReward.getProductAmount();
                            if (itemReward.getProductIndex() > 0) {
                                Product product = productService.findProductByProductItemIndex(productIndex);
                                if (product == null)
                                    break;

                                Pocket pocket = pocketService.findById(player.getPocketId());
                                PlayerPocket playerPocket = playerPocketService.getItemAsPocketByItemIndexAndCategoryAndPocket(product.getItem0(), product.getCategory(), pocket);
                                boolean existingItem = false;

                                if (playerPocket != null && !playerPocket.getUseType().equals("N/A")) {
                                    existingItem = true;
                                } else {
                                    playerPocket = new PlayerPocket();
                                }

                                playerPocket.setCategory(product.getCategory());
                                playerPocket.setItemIndex(product.getItem0());
                                playerPocket.setUseType(product.getUseType());

                                // no idea how itemCount can be null here, but ok
                                playerPocket.setItemCount((playerPocket.getItemCount() == null ? 0 : playerPocket.getItemCount()) + productAmount);

                                if (playerPocket.getUseType().equalsIgnoreCase(EItemUseType.TIME.getName())) {
                                    Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                                    cal.add(Calendar.DAY_OF_MONTH, playerPocket.getItemCount());

                                    playerPocket.setCreated(cal.getTime());
                                    playerPocket.setItemCount(1);
                                }
                                playerPocket.setPocket(pocket);

                                playerPocketService.save(playerPocket);
                                if (!existingItem)
                                    pocketService.incrementPocketBelongings(pocket);

                                if (!existingItem) {
                                    S2CInventoryItemsPlacePacket inventoryDataPacket = new S2CInventoryItemsPlacePacket(List.of(playerPocket));
                                    client.getConnection().sendTCP(inventoryDataPacket);
                                } else {
                                    S2CInventoryItemCountPacket inventoryDataPacket = new S2CInventoryItemCountPacket(playerPocket);
                                    client.getConnection().sendTCP(inventoryDataPacket);
                                }
                            }
                        }
                    }
                }
            }

            long claimedRewardCount = slotRewards.values().stream().filter(ir -> ir.getClaimed().get()).count();
            long activePlayerCount = room.getRoomPlayerList().stream().filter(rpi -> rpi.getPosition() < 4).count();

            // check if all rewards are claimed
            if (slotRewards.values().stream().allMatch(ir -> ir.getClaimed().get()) || claimedRewardCount == activePlayerCount) {
                GameSessionManager.getInstance().removeMatchplayReward(roomId, matchplayReward);
            }
        }
    }
}
