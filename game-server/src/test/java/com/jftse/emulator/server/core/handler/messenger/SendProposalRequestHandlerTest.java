package com.jftse.emulator.server.core.handler.messenger;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.rabbit.service.RProducerService;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.log.GameLog;
import com.jftse.entities.database.model.messenger.Proposal;
import com.jftse.entities.database.model.player.Player;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.entities.database.model.pocket.Pocket;
import com.jftse.server.core.item.EItemCategory;
import com.jftse.server.core.protocol.IPacket;
import com.jftse.server.core.service.FriendService;
import com.jftse.server.core.service.GameLogService;
import com.jftse.server.core.service.PlayerPocketService;
import com.jftse.server.core.service.PlayerService;
import com.jftse.server.core.service.ProposalService;
import com.jftse.server.core.shared.packets.messenger.CMSGSendProposal;
import com.jftse.server.core.shared.packets.messenger.SMSGSendProposal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SendProposalRequestHandlerTest {
    @Mock private PlayerPocketService playerPocketService;
    @Mock private ProposalService proposalService;
    @Mock private PlayerService playerService;
    @Mock private FriendService friendService;
    @Mock private GameLogService gameLogService;
    @Mock private RProducerService producerService;
    @Mock private FTConnection connection;
    @Mock private FTClient client;
    @Mock private FTPlayer sender;

    private SendProposalRequestHandler handler;
    private Pocket senderPocket;

    @BeforeEach
    void setUp() {
        handler = new SendProposalRequestHandler(
                playerPocketService,
                proposalService,
                playerService,
                friendService,
                gameLogService,
                producerService
        );
        senderPocket = pocket(71L);

        when(connection.getClient()).thenReturn(client);
        when(client.hasPlayer()).thenReturn(true);
    }

    @Test
    void rejectsNonSpecialRowsThatReuseAProposalItemIndex() {
        PlayerPocket item = item(501L, 23, EItemCategory.QUICK.getName(), senderPocket, 2);
        when(playerPocketService.findById(501L)).thenReturn(item);

        handler.handle(connection, request(501, "Receiver"));

        assertStatus((byte) -7);
        verify(playerPocketService, never()).save(item);
        verify(proposalService, never()).save(any());
    }

    @Test
    void rejectsMissingProposalItems() {
        when(playerPocketService.findById(500L)).thenReturn(null);

        handler.handle(connection, request(500, "Receiver"));

        assertStatus((byte) -7);
        verify(proposalService, never()).createWithItem(any(), any(), any(), anyLong());
    }

    @Test
    void rejectsOtherSpecialItems() {
        PlayerPocket item = item(500L, 22, EItemCategory.SPECIAL.getName(), senderPocket, 2);
        when(playerPocketService.findById(500L)).thenReturn(item);

        handler.handle(connection, request(500, "Receiver"));

        assertStatus((byte) -7);
        verify(proposalService, never()).createWithItem(any(), any(), any(), anyLong());
    }

    @Test
    void rejectsProposalRowsWithMissingCatalogIndex() {
        PlayerPocket item = item(506L, 23, EItemCategory.SPECIAL.getName(), senderPocket, 2);
        item.setItemIndex(null);
        when(playerPocketService.findById(506L)).thenReturn(item);

        handler.handle(connection, request(506, "Receiver"));

        assertStatus((byte) -7);
        verify(proposalService, never()).createWithItem(any(), any(), any(), anyLong());
    }

    @Test
    void rejectsAndLogsForeignProposalItemsWithoutConsumingThem() {
        PlayerPocket item = item(502L, 24, EItemCategory.SPECIAL.getName(), pocket(72L), 2);
        when(playerPocketService.findById(502L)).thenReturn(item);
        when(client.getPlayer()).thenReturn(sender);
        when(sender.getPocketId()).thenReturn(71L);
        when(sender.getId()).thenReturn(11L);

        handler.handle(connection, request(502, "Receiver"));

        assertStatus((byte) -2);
        verify(gameLogService).save(any(GameLog.class));
        verify(playerPocketService, never()).save(item);
        verify(playerPocketService, never()).remove(item.getId());
    }

    @Test
    void missingReceiverDoesNotConsumeAValidProposalItem() {
        PlayerPocket item = item(503L, 25, EItemCategory.SPECIAL.getName(), senderPocket, 2);
        when(playerPocketService.findById(503L)).thenReturn(item);
        when(client.getPlayer()).thenReturn(sender);
        when(sender.getPocketId()).thenReturn(71L);
        when(playerService.findByName("Missing")).thenReturn(null);

        handler.handle(connection, request(503, "Missing"));

        assertStatus((byte) -1);
        verify(playerPocketService, never()).save(item);
        verify(playerPocketService, never()).remove(item.getId());
        verify(proposalService, never()).save(any());
    }

    @Test
    void validOwnedProposalItemCreatesProposalAndDecrementsStack() {
        PlayerPocket item = item(504L, 23, EItemCategory.SPECIAL.getName(), senderPocket, 2);
        Player senderEntity = player(11L, "Sender");
        Player receiver = player(12L, "Receiver");
        AtomicReference<Proposal> savedProposal = new AtomicReference<>();

        when(playerPocketService.findById(504L)).thenReturn(item);
        when(client.getPlayer()).thenReturn(sender);
        when(sender.getPocketId()).thenReturn(71L);
        when(sender.getId()).thenReturn(11L);
        when(sender.getName()).thenReturn("Sender");
        when(sender.getPlayerRef()).thenReturn(senderEntity);
        when(sender.getPlayer()).thenReturn(senderEntity);
        when(playerService.findByName("Receiver")).thenReturn(receiver);
        when(friendService.findByPlayer(any(Player.class))).thenReturn(List.of());
        Proposal proposal = proposal(senderEntity, receiver, item);
        item.setItemCount(1);
        savedProposal.set(proposal);
        when(proposalService.createWithItem(senderEntity, receiver, "Will you play with me?", 504L))
                .thenReturn(new ProposalService.ProposalCreationResult(
                        ProposalService.ProposalCreationStatus.SUCCESS,
                        proposal,
                        item,
                        false
                ));
        when(proposalService.findWithPlayerBySender(anyLong())).thenAnswer(ignored -> List.of(savedProposal.get()));

        handler.handle(connection, request(504, "Receiver"));

        assertEquals(1, item.getItemCount());
        assertEquals(23, savedProposal.get().getItemIndex());
        assertStatus((byte) 0);
        verify(playerPocketService, never()).remove(item.getId());
        verify(producerService).send(any(), any(), any());
    }

    @Test
    void validFinalProposalItemSendsRemovalAfterAtomicCreation() {
        PlayerPocket item = item(505L, 25, EItemCategory.SPECIAL.getName(), senderPocket, 1);
        Player senderEntity = player(11L, "Sender");
        Player receiver = player(12L, "Receiver");
        Proposal proposal = proposal(senderEntity, receiver, item);

        when(playerPocketService.findById(505L)).thenReturn(item);
        when(client.getPlayer()).thenReturn(sender);
        when(sender.getPocketId()).thenReturn(71L);
        when(sender.getId()).thenReturn(11L);
        when(sender.getName()).thenReturn("Sender");
        when(sender.getPlayerRef()).thenReturn(senderEntity);
        when(sender.getPlayer()).thenReturn(senderEntity);
        when(playerService.findByName("Receiver")).thenReturn(receiver);
        when(friendService.findByPlayer(any(Player.class))).thenReturn(List.of());
        when(proposalService.createWithItem(senderEntity, receiver, "Will you play with me?", 505L))
                .thenReturn(new ProposalService.ProposalCreationResult(
                        ProposalService.ProposalCreationStatus.SUCCESS,
                        proposal,
                        item,
                        true
                ));
        when(proposalService.findWithPlayerBySender(11L)).thenReturn(List.of(proposal));

        handler.handle(connection, request(505, "Receiver"));

        assertStatus((byte) 0);
        verify(proposalService).createWithItem(senderEntity, receiver, "Will you play with me?", 505L);
        verify(playerPocketService, never()).remove(anyLong());
    }

    private void assertStatus(byte expected) {
        ArgumentCaptor<IPacket> packets = ArgumentCaptor.forClass(IPacket.class);
        verify(connection, atLeastOnce()).sendTCP(packets.capture());
        byte status = packets.getAllValues().stream()
                .filter(SMSGSendProposal.class::isInstance)
                .map(SMSGSendProposal.class::cast)
                .map(SMSGSendProposal::getStatus)
                .findFirst()
                .orElseThrow();
        assertEquals(expected, status);
    }

    private CMSGSendProposal request(int itemPocketId, String receiverName) {
        return CMSGSendProposal.builder()
                .receiverName(receiverName)
                .playerPocketId(itemPocketId)
                .itemIndex(23)
                .message("Will you play with me?")
                .build();
    }

    private PlayerPocket item(long id, int itemIndex, String category, Pocket pocket, int count) {
        PlayerPocket item = new PlayerPocket();
        item.setId(id);
        item.setItemIndex(itemIndex);
        item.setCategory(category);
        item.setPocket(pocket);
        item.setItemCount(count);
        return item;
    }

    private Pocket pocket(long id) {
        Pocket pocket = new Pocket();
        pocket.setId(id);
        return pocket;
    }

    private Player player(long id, String name) {
        Player player = new Player();
        player.setId(id);
        player.setName(name);
        return player;
    }

    private Proposal proposal(Player sender, Player receiver, PlayerPocket item) {
        Proposal proposal = new Proposal();
        proposal.setId(601L);
        proposal.setCreated(new Date(1_000L));
        proposal.setSender(sender);
        proposal.setReceiver(receiver);
        proposal.setMessage("Will you play with me?");
        proposal.setCategory(item.getCategory());
        proposal.setItemIndex(item.getItemIndex());
        return proposal;
    }
}
