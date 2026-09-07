package com.jftse.emulator.server.core.handler.messenger;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.packets.inventory.S2CInventoryItemCountPacket;
import com.jftse.emulator.server.core.packets.messenger.S2CProposalListPacket;
import com.jftse.emulator.server.core.packets.messenger.S2CReceivedProposalNotificationPacket;
import com.jftse.emulator.server.core.rabbit.service.RProducerService;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.log.GameLog;
import com.jftse.entities.database.model.log.GameLogType;
import com.jftse.entities.database.model.messenger.EFriendshipState;
import com.jftse.entities.database.model.messenger.Friend;
import com.jftse.entities.database.model.messenger.Proposal;
import com.jftse.entities.database.model.player.Player;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.item.EItemCategory;
import com.jftse.server.core.service.*;
import com.jftse.server.core.shared.packets.inventory.S2CInventoryItemRemoveAnswerPacket;
import com.jftse.server.core.shared.packets.messenger.CMSGSendProposal;
import com.jftse.server.core.shared.packets.messenger.SMSGSendProposal;
import com.jftse.server.core.shared.rabbit.messages.PacketMessage;

import java.util.List;

@PacketId(CMSGSendProposal.PACKET_ID)
public class SendProposalRequestHandler implements PacketHandler<FTConnection, CMSGSendProposal> {
    private final PlayerPocketService playerPocketService;
    private final ProposalService proposalService;
    private final PlayerService playerService;
    private final FriendService friendService;

    private final GameLogService gameLogService;

    private final RProducerService rProducerService;

    public SendProposalRequestHandler() {
        this(
                ServiceManager.getInstance().getPlayerPocketService(),
                ServiceManager.getInstance().getProposalService(),
                ServiceManager.getInstance().getPlayerService(),
                ServiceManager.getInstance().getFriendService(),
                ServiceManager.getInstance().getGameLogService(),
                RProducerService.getInstance()
        );
    }

    SendProposalRequestHandler(PlayerPocketService playerPocketService,
                               ProposalService proposalService,
                               PlayerService playerService,
                               FriendService friendService,
                               GameLogService gameLogService,
                               RProducerService rProducerService) {
        this.playerPocketService = playerPocketService;
        this.proposalService = proposalService;
        this.playerService = playerService;
        this.friendService = friendService;
        this.gameLogService = gameLogService;
        this.rProducerService = rProducerService;
    }

    @Override
    public void handle(FTConnection connection, CMSGSendProposal packet) {
        FTClient ftClient = connection.getClient();
        if (!ftClient.hasPlayer())
            return;

        PlayerPocket item = playerPocketService.findById((long) packet.getPlayerPocketId());

        // 0 = MSG_PROPOSE_SUCCESS
        //-1 = MSG_NO_CHARACTER_AT_CHARACTER_LIST
        //-3 = MSG_PROPOSE_ACCEPT_FAILED_ALREADY_COUPLE
        //-4 = MSG_PROPOSE_FAILED_ALREADY_PROPOSING
        //-6 = MSG_YOU_CAN_NOT_PROPOSE_FOR_SAME_ACCOUNT
        //-7 = MSG_NO_HAVE_PROPOSE_ITEM
        //-9 = MSG_YOU_CAN_NOT_PROPOSE_FOR_SAME_SEX
        Integer itemIndex = item == null ? null : item.getItemIndex();
        boolean isValidProposalItem = item != null
                && EItemCategory.SPECIAL.getName().equals(item.getCategory())
                && itemIndex != null
                && itemIndex >= 23
                && itemIndex <= 25;
        if (!isValidProposalItem) {
            SMSGSendProposal response = SMSGSendProposal.builder().status((byte) -7).build();
            connection.sendTCP(response);
            return;
        }

        FTPlayer sender = ftClient.getPlayer();

        if (!item.getPocket().getId().equals(sender.getPocketId())) {
            SMSGSendProposal response = SMSGSendProposal.builder().status((byte) -2).build();
            connection.sendTCP(response);

            GameLog gameLog = new GameLog();
            gameLog.setGameLogType(GameLogType.BANABLE);
            gameLog.setContent("pockets are not equal! requested pocketId: " + item.getPocket().getId() + ", requested playerPocketId: " + item.getId() + ", requesting player pocketId: " + sender.getPocketId() + ", requesting playerId: " + sender.getId());
            gameLogService.save(gameLog);

            return;
        }

        Player receiver = playerService.findByName(packet.getReceiverName());
        if (receiver == null) {
            SMSGSendProposal response = SMSGSendProposal.builder().status((byte) -1).build();
            connection.sendTCP(response);
            return;
        }

        List<Friend> senderFriend = friendService.findByPlayer(sender.getPlayerRef());
        if (senderFriend.stream().anyMatch(x -> x.getEFriendshipState().equals(EFriendshipState.Relationship))) {
            SMSGSendProposal response = SMSGSendProposal.builder().status((byte) -3).build();
            connection.sendTCP(response);
            return;
        }

        List<Friend> receiverFriends = friendService.findByPlayer(receiver);
        if (receiverFriends.stream().anyMatch(x -> x.getEFriendshipState().equals(EFriendshipState.Relationship))) {
            SMSGSendProposal response = SMSGSendProposal.builder().status((byte) -3).build();
            connection.sendTCP(response);
            return;
        }

        ProposalService.ProposalCreationResult creationResult = proposalService.createWithItem(
                sender.getPlayer(),
                receiver,
                packet.getMessage(),
                item.getId()
        );
        if (creationResult.status() != ProposalService.ProposalCreationStatus.SUCCESS) {
            SMSGSendProposal response = SMSGSendProposal.builder()
                    .status((byte) (creationResult.status() == ProposalService.ProposalCreationStatus.NOT_OWNED ? -2 : -7))
                    .build();
            connection.sendTCP(response);
            return;
        }

        Proposal proposal = creationResult.proposal();
        item = creationResult.item();
        if (creationResult.itemRemoved()) {
            S2CInventoryItemRemoveAnswerPacket inventoryItemRemoveAnswerPacket =
                    new S2CInventoryItemRemoveAnswerPacket(item.getId().intValue());
            connection.sendTCP(inventoryItemRemoveAnswerPacket);
        } else {
            S2CInventoryItemCountPacket inventoryItemCountPacket = new S2CInventoryItemCountPacket(item);
            connection.sendTCP(inventoryItemCountPacket);
        }

        S2CReceivedProposalNotificationPacket s2CReceivedProposalNotificationPacket = new S2CReceivedProposalNotificationPacket(proposal);

        PacketMessage packetMessage = PacketMessage.builder()
                .receivingPlayerId(receiver.getId())
                .packet(s2CReceivedProposalNotificationPacket)
                .build();
        rProducerService.send(packetMessage, "game.messenger.proposal chat.messenger.proposal", sender.getName() + "(GameServer)");

        SMSGSendProposal response = SMSGSendProposal.builder().status((byte) 0).build();
        connection.sendTCP(response);

        List<Proposal> sentProposals = proposalService.findWithPlayerBySender(sender.getId());
        S2CProposalListPacket s2CSentProposalListPacket = new S2CProposalListPacket((byte) 1, sentProposals);
        connection.sendTCP(s2CSentProposalListPacket);
    }
}
