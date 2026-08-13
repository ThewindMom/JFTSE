package com.jftse.emulator.server.core.life.item.special;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.client.PlayerStatisticView;
import com.jftse.emulator.server.core.life.item.BaseItem;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.packets.inventory.S2CInventoryItemCountPacket;
import com.jftse.emulator.server.core.packets.player.S2CPlayerInfoPlayStatsPacket;
import com.jftse.server.core.service.ContractWithGuardianService;
import com.jftse.server.core.shared.packets.inventory.S2CInventoryItemRemoveAnswerPacket;

public class ContractWithGuardian extends BaseItem {
    private final long playerPocketId;
    private final ContractWithGuardianService contractWithGuardianService;
    private FTPlayer player;

    public ContractWithGuardian(long playerPocketId, int itemIndex, String name, String category) {
        super(itemIndex, name, category);
        this.playerPocketId = playerPocketId;
        this.contractWithGuardianService = ServiceManager.getInstance().getContractWithGuardianService();
    }

    @Override
    public boolean processPlayer(FTPlayer player) {
        this.player = player;
        this.localPlayerId = player.getId();
        return true;
    }

    @Override
    public boolean processPocket(Long pocketId) {
        ContractWithGuardianService.UseResult result = contractWithGuardianService.use(
                this.player.getPlayerStatisticId(),
                pocketId,
                this.playerPocketId
        );
        if (result.status() != ContractWithGuardianService.UseStatus.SUCCESS)
            return false;

        this.player.setPlayerStatistic(PlayerStatisticView.fromEntity(result.statistic()));
        this.packetsToSend.add(
                this.localPlayerId,
                new S2CPlayerInfoPlayStatsPacket(result.statistic())
        );
        if (result.itemRemoved()) {
            this.packetsToSend.add(
                    this.localPlayerId,
                    new S2CInventoryItemRemoveAnswerPacket(Math.toIntExact(this.playerPocketId))
            );
        } else {
            this.packetsToSend.add(this.localPlayerId, new S2CInventoryItemCountPacket(result.item()));
        }
        return true;
    }
}
