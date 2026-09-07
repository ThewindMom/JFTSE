package com.jftse.emulator.server.core.item;

import com.jftse.entities.database.model.messenger.Proposal;
import com.jftse.entities.database.model.player.Player;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.entities.database.model.pocket.Pocket;
import com.jftse.entities.database.repository.messenger.ProposalRepository;
import com.jftse.entities.database.repository.pocket.PlayerPocketRepository;
import com.jftse.entities.database.repository.pocket.PocketRepository;
import com.jftse.server.core.item.EItemCategory;
import com.jftse.server.core.service.ProposalService;
import com.jftse.server.core.service.impl.ProposalServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.LockModeType;
import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProposalServiceImplTest {
    @Mock private ProposalRepository proposalRepository;
    @Mock private PlayerPocketRepository playerPocketRepository;
    @Mock private PocketRepository pocketRepository;

    private ProposalServiceImpl service;
    private Player sender;
    private Player receiver;
    private Pocket pocket;

    @BeforeEach
    void setUp() {
        service = new ProposalServiceImpl(proposalRepository, playerPocketRepository, pocketRepository);
        pocket = new Pocket();
        pocket.setId(71L);
        pocket.setBelongings(4);
        sender = player(11L, pocket);
        receiver = player(12L, pocket(72L));
    }

    @Test
    void decrementsStackAndCreatesProposalInOneServiceCall() {
        PlayerPocket item = item(501L, 23, 2, pocket);
        when(playerPocketRepository.findLockedById(501L)).thenReturn(Optional.of(item));
        when(proposalRepository.save(any(Proposal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProposalService.ProposalCreationResult result =
                service.createWithItem(sender, receiver, "Message", 501L);

        assertEquals(ProposalService.ProposalCreationStatus.SUCCESS, result.status());
        assertEquals(1, item.getItemCount());
        assertFalse(result.itemRemoved());
        assertSame(item, result.item());
        assertEquals(23, result.proposal().getItemIndex());
        verify(playerPocketRepository).save(item);
        verify(playerPocketRepository, never()).delete(item);
        verify(pocketRepository, never()).save(any());
    }

    @Test
    void removesFinalUnitAndUpdatesPocketBelongings() {
        PlayerPocket item = item(502L, 25, 1, pocket);
        when(playerPocketRepository.findLockedById(502L)).thenReturn(Optional.of(item));
        when(pocketRepository.findLockedById(71L)).thenReturn(Optional.of(pocket));
        when(proposalRepository.save(any(Proposal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProposalService.ProposalCreationResult result =
                service.createWithItem(sender, receiver, "Message", 502L);

        assertEquals(ProposalService.ProposalCreationStatus.SUCCESS, result.status());
        assertTrue(result.itemRemoved());
        assertEquals(3, pocket.getBelongings());
        verify(playerPocketRepository).delete(item);
        verify(pocketRepository).save(pocket);
    }

    @Test
    void revalidatesOwnershipInsideTheMutationBoundary() {
        PlayerPocket foreignItem = item(503L, 24, 2, pocket(73L));
        when(playerPocketRepository.findLockedById(503L)).thenReturn(Optional.of(foreignItem));

        ProposalService.ProposalCreationResult result =
                service.createWithItem(sender, receiver, "Message", 503L);

        assertEquals(ProposalService.ProposalCreationStatus.NOT_OWNED, result.status());
        verify(playerPocketRepository, never()).save(any());
        verify(playerPocketRepository, never()).delete(any());
        verify(proposalRepository, never()).save(any());
    }

    @Test
    void rejectsOtherSpecialItemsInsideTheMutationBoundary() {
        PlayerPocket item = item(504L, 22, 2, pocket);
        when(playerPocketRepository.findLockedById(504L)).thenReturn(Optional.of(item));

        ProposalService.ProposalCreationResult result =
                service.createWithItem(sender, receiver, "Message", 504L);

        assertEquals(ProposalService.ProposalCreationStatus.INVALID_ITEM, result.status());
        verify(playerPocketRepository, never()).save(any());
        verify(proposalRepository, never()).save(any());
    }

    @Test
    void rejectsProposalItemsWithMissingCatalogIndex() {
        PlayerPocket item = item(505L, 23, 2, pocket);
        item.setItemIndex(null);
        when(playerPocketRepository.findLockedById(505L)).thenReturn(Optional.of(item));

        ProposalService.ProposalCreationResult result =
                service.createWithItem(sender, receiver, "Message", 505L);

        assertEquals(ProposalService.ProposalCreationStatus.INVALID_ITEM, result.status());
        verify(playerPocketRepository, never()).save(any());
        verify(proposalRepository, never()).save(any());
    }

    @Test
    void mutationUsesATransactionAndPessimisticItemLock() throws Exception {
        Method serviceMethod = ProposalServiceImpl.class.getMethod(
                "createWithItem", Player.class, Player.class, String.class, Long.class);
        Transactional transactional = serviceMethod.getAnnotation(Transactional.class);
        Method repositoryMethod = PlayerPocketRepository.class.getMethod("findLockedById", Long.class);
        Lock lock = repositoryMethod.getAnnotation(Lock.class);
        Method pocketRepositoryMethod = PocketRepository.class.getMethod("findLockedById", Long.class);
        Lock pocketLock = pocketRepositoryMethod.getAnnotation(Lock.class);

        assertEquals(Isolation.READ_COMMITTED, transactional.isolation());
        assertEquals(LockModeType.PESSIMISTIC_WRITE, lock.value());
        assertEquals(LockModeType.PESSIMISTIC_WRITE, pocketLock.value());
    }

    private Player player(long id, Pocket pocket) {
        Player player = new Player();
        player.setId(id);
        player.setPocket(pocket);
        return player;
    }

    private Pocket pocket(long id) {
        Pocket pocket = new Pocket();
        pocket.setId(id);
        return pocket;
    }

    private PlayerPocket item(long id, int itemIndex, int count, Pocket pocket) {
        PlayerPocket item = new PlayerPocket();
        item.setId(id);
        item.setItemIndex(itemIndex);
        item.setCategory(EItemCategory.SPECIAL.getName());
        item.setItemCount(count);
        item.setPocket(pocket);
        return item;
    }
}
