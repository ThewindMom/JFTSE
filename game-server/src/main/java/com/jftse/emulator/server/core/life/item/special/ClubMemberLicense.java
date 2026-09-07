package com.jftse.emulator.server.core.life.item.special;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.life.item.BaseItem;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.packets.inventory.S2CInventoryItemCountPacket;
import com.jftse.server.core.service.ClubMemberLicenseService;
import com.jftse.server.core.shared.packets.inventory.S2CInventoryItemRemoveAnswerPacket;

public class ClubMemberLicense extends BaseItem {
    private final long playerPocketId;
    private final ClubMemberLicenseService clubMemberLicenseService;

    public ClubMemberLicense(long playerPocketId, int itemIndex, String name, String category) {
        super(itemIndex, name, category);
        this.playerPocketId = playerPocketId;
        this.clubMemberLicenseService = ServiceManager.getInstance().getClubMemberLicenseService();
    }

    @Override
    public boolean processPlayer(FTPlayer player) {
        this.localPlayerId = player.getId();
        return true;
    }

    @Override
    public boolean processPocket(Long pocketId) {
        ClubMemberLicenseService.UseResult result = clubMemberLicenseService.use(
                this.localPlayerId,
                pocketId,
                this.playerPocketId
        );
        if (result.status() != ClubMemberLicenseService.UseStatus.SUCCESS)
            return false;

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
