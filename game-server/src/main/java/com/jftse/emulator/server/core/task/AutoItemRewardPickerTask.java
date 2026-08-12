package com.jftse.emulator.server.core.task;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentLinkedDeque;

public class AutoItemRewardPickerTask extends AbstractTask {
    private final ConcurrentLinkedDeque<FTClient> clients;
    private final Map<FTClient, Short> playerPositions;
    private final short roomId;
    private final MatchplayReward expectedReward;

    private final PlayerPocketService playerPocketService;
    private final PocketService pocketService;
    private final ProductService productService;

    public AutoItemRewardPickerTask(final ConcurrentLinkedDeque<FTClient> clients, short roomId) {
        this.clients = clients;
        this.playerPositions = new LinkedHashMap<>();
        clients.forEach(client -> {
            RoomPlayer roomPlayer = client.getRoomPlayer();
            if (client.hasPlayer() && roomPlayer != null &&
                    roomPlayer.getPosition() >= 0 && roomPlayer.getPosition() < 4) {
                playerPositions.put(client, roomPlayer.getPosition());
            }
        });
        this.roomId = roomId;
        this.expectedReward = GameSessionManager.getInstance().getMatchplayReward((int) roomId);

        this.playerPocketService = ServiceManager.getInstance().getPlayerPocketService();
        this.pocketService = ServiceManager.getInstance().getPocketService();
        this.productService = ServiceManager.getInstance().getProductService();
    }

    @Override
    public void run() {
        MatchplayReward matchplayReward = GameSessionManager.getInstance().getMatchplayReward(roomId);
        if (matchplayReward == expectedReward && matchplayReward != null) {
            final Map<Byte, MatchplayReward.ItemReward> slotRewards = matchplayReward.getSlotRewards();
            Map<Integer, Product> productsByIndex = new HashMap<>();
            for (MatchplayReward.ItemReward itemReward : slotRewards.values()) {
                int productIndex = itemReward.getProductIndex();
                if (productIndex > 0 && !productsByIndex.containsKey(productIndex)) {
                    Product product = productService.findProductByProductItemIndex(productIndex);
                    if (product == null) {
                        return;
                    }
                    productsByIndex.put(productIndex, product);
                }
            }

            for (Map.Entry<FTClient, Short> entry : playerPositions.entrySet()) {
                FTClient client = entry.getKey();
                short playerPosition = entry.getValue();
                if (!client.hasPlayer())
                    continue;

                FTPlayer player = client.getPlayer();
                Long eligiblePlayerId = matchplayReward.getEligiblePlayerIdsByPosition().get(playerPosition);
                if (eligiblePlayerId == null || eligiblePlayerId != player.getId())
                    continue;

                if (slotRewards.values().stream().anyMatch(r ->
                        r.getClaimed().get() && r.getClaimedPlayerPosition() == playerPosition)) {
                    continue;
                }

                boolean rewardClaimed = false;
                while (!rewardClaimed) {
                    List<Map.Entry<Byte, MatchplayReward.ItemReward>> unclaimedRewards = slotRewards.entrySet().stream()
                            .filter(unclaimedEntry -> !unclaimedEntry.getValue().getClaimed().get())
                            .toList();

                    if (unclaimedRewards.isEmpty()) {
                        GameSessionManager.getInstance().removeMatchplayReward(roomId, matchplayReward);
                        break;
                    }

                    int randomIndex = (int) (Math.random() * unclaimedRewards.size());
                    Map.Entry<Byte, MatchplayReward.ItemReward> selectedRewardEntry = unclaimedRewards.get(randomIndex);
                    byte requestingSlot = selectedRewardEntry.getKey();
                    MatchplayReward.ItemReward itemReward = selectedRewardEntry.getValue();

                    if (itemReward.getClaimed().compareAndSet(false, true)) {
                        rewardClaimed = true;

                        itemReward.setClaimedPlayerPosition(playerPosition);

                        SMSGPickupItemReward itemRewardPickup = SMSGPickupItemReward.builder()
                                .playerPos((byte) playerPosition)
                                .slot(requestingSlot)
                                .type((byte) 0) // 0 = product, 1 = material
                                .productIndex(itemReward.getProductIndex())
                                .quantity(itemReward.getProductAmount())
                                .build();
                        clients.stream()
                                .filter(notificationClient -> notificationClient.getConnection() != null)
                                .map(FTClient::getConnection)
                                .forEach(notificationConnection -> notificationConnection.sendTCP(itemRewardPickup));

                        // add reward to player pocket
                        int productIndex = itemReward.getProductIndex();
                        int productAmount = itemReward.getProductAmount();
                        if (itemReward.getProductIndex() > 0) {
                            Product product = productsByIndex.get(productIndex);

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

            long claimedRewardCount = slotRewards.values().stream().filter(ir -> ir.getClaimed().get()).count();
            long activePlayerCount = matchplayReward.getEligiblePlayerIdsByPosition().size();

            // check if all rewards are claimed
            if (slotRewards.values().stream().allMatch(ir -> ir.getClaimed().get()) || claimedRewardCount == activePlayerCount) {
                GameSessionManager.getInstance().removeMatchplayReward(roomId, matchplayReward);
            }
        }
    }
}
