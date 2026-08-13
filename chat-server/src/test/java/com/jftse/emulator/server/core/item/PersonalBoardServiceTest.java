package com.jftse.emulator.server.core.item;

import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.entities.database.model.pocket.Pocket;
import com.jftse.entities.database.repository.pocket.PlayerPocketRepository;
import com.jftse.entities.database.repository.pocket.PocketRepository;
import com.jftse.server.core.item.EItemCategory;
import com.jftse.server.core.item.EItemUseType;
import com.jftse.server.core.service.PersonalBoardService;
import com.jftse.server.core.service.impl.PersonalBoardServiceImpl;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonalBoardServiceTest {
    @Mock private PlayerPocketRepository playerPocketRepository;
    @Mock private PocketRepository pocketRepository;

    private PersonalBoardServiceImpl service;
    private Pocket pocket;
    private PlayerPocket board;

    @BeforeEach
    void setUp() {
        service = new PersonalBoardServiceImpl(playerPocketRepository, pocketRepository);
        pocket = pocket(71L, 4);
        board = board(501L, pocket, 2);
    }

    @Test
    void consumesOneBoardFromAStack() {
        when(playerPocketRepository.findLockedById(501L)).thenReturn(Optional.of(board));

        PersonalBoardService.UseResult result = service.use(71L, 501L);

        assertEquals(PersonalBoardService.UseStatus.SUCCESS, result.status());
        assertFalse(result.itemRemoved());
        assertEquals(1, board.getItemCount());
        verify(playerPocketRepository).save(board);
        verify(playerPocketRepository, never()).delete(any());
        verify(pocketRepository, never()).save(any());
    }

    @Test
    void removesTheFinalBoardAndDecrementsPocketBelongings() {
        board.setItemCount(1);
        when(playerPocketRepository.findLockedById(501L)).thenReturn(Optional.of(board));
        when(pocketRepository.findLockedById(71L)).thenReturn(Optional.of(pocket));

        PersonalBoardService.UseResult result = service.use(71L, 501L);

        assertEquals(PersonalBoardService.UseStatus.SUCCESS, result.status());
        assertTrue(result.itemRemoved());
        assertEquals(3, pocket.getBelongings());
        verify(playerPocketRepository).delete(board);
        verify(pocketRepository).save(pocket);
    }

    @Test
    void rejectsAForgedPocketRowWithoutConsumption() {
        board.setPocket(pocket(72L, 1));
        when(playerPocketRepository.findLockedById(501L)).thenReturn(Optional.of(board));

        PersonalBoardService.UseResult result = service.use(71L, 501L);

        assertEquals(PersonalBoardService.UseStatus.NOT_OWNED, result.status());
        assertNoMutation();
    }

    @Test
    void rejectsWrongIndexCategoryUseTypeAndEmptyStacks() {
        PlayerPocket wrongIndex = board(501L, pocket, 1);
        wrongIndex.setItemIndex(18);
        PlayerPocket wrongCategory = board(502L, pocket, 1);
        wrongCategory.setCategory(EItemCategory.QUICK.getName());
        PlayerPocket wrongUseType = board(503L, pocket, 1);
        wrongUseType.setUseType(EItemUseType.TIME.getName());
        PlayerPocket empty = board(504L, pocket, 0);
        when(playerPocketRepository.findLockedById(501L)).thenReturn(Optional.of(wrongIndex));
        when(playerPocketRepository.findLockedById(502L)).thenReturn(Optional.of(wrongCategory));
        when(playerPocketRepository.findLockedById(503L)).thenReturn(Optional.of(wrongUseType));
        when(playerPocketRepository.findLockedById(504L)).thenReturn(Optional.of(empty));

        assertEquals(PersonalBoardService.UseStatus.INVALID_ITEM, service.use(71L, 501L).status());
        assertEquals(PersonalBoardService.UseStatus.INVALID_ITEM, service.use(71L, 502L).status());
        assertEquals(PersonalBoardService.UseStatus.INVALID_ITEM, service.use(71L, 503L).status());
        assertEquals(PersonalBoardService.UseStatus.INVALID_ITEM, service.use(71L, 504L).status());
        assertNoMutation();
    }

    @Test
    void replayAfterFinalConsumptionCannotConsumeAgain() {
        board.setItemCount(1);
        when(playerPocketRepository.findLockedById(501L))
                .thenReturn(Optional.of(board), Optional.empty());
        when(pocketRepository.findLockedById(71L)).thenReturn(Optional.of(pocket));

        PersonalBoardService.UseResult first = service.use(71L, 501L);
        PersonalBoardService.UseResult replay = service.use(71L, 501L);

        assertEquals(PersonalBoardService.UseStatus.SUCCESS, first.status());
        assertEquals(PersonalBoardService.UseStatus.ITEM_NOT_FOUND, replay.status());
        verify(playerPocketRepository).delete(board);
    }

    @Test
    void mutationUsesOneTransactionAndPessimisticItemAndPocketLocks() throws Exception {
        Method serviceMethod = PersonalBoardServiceImpl.class.getMethod("use", Long.class, Long.class);
        Transactional transactional = serviceMethod.getAnnotation(Transactional.class);
        Lock itemLock = PlayerPocketRepository.class.getMethod("findLockedById", Long.class)
                .getAnnotation(Lock.class);
        Lock pocketLock = PocketRepository.class.getMethod("findLockedById", Long.class)
                .getAnnotation(Lock.class);

        assertEquals(Isolation.READ_COMMITTED, transactional.isolation());
        assertEquals(LockModeType.PESSIMISTIC_WRITE, itemLock.value());
        assertEquals(LockModeType.PESSIMISTIC_WRITE, pocketLock.value());
    }

    private void assertNoMutation() {
        verify(playerPocketRepository, never()).save(any());
        verify(playerPocketRepository, never()).delete(any());
        verify(pocketRepository, never()).save(any());
    }

    private PlayerPocket board(long id, Pocket owner, int count) {
        PlayerPocket result = new PlayerPocket();
        result.setId(id);
        result.setPocket(owner);
        result.setCategory(EItemCategory.SPECIAL.getName());
        result.setItemIndex(19);
        result.setUseType(EItemUseType.COUNT.getName());
        result.setItemCount(count);
        return result;
    }

    private Pocket pocket(long id, int belongings) {
        Pocket result = new Pocket();
        result.setId(id);
        result.setBelongings(belongings);
        return result;
    }
}
