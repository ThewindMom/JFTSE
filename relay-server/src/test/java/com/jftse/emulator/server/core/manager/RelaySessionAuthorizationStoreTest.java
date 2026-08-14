package com.jftse.emulator.server.core.manager;

import com.jftse.emulator.server.net.FTClient;
import com.jftse.server.core.shared.rabbit.messages.RelaySessionAuthorizationMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RelaySessionAuthorizationStoreTest {
    private RelaySessionAuthorizationStore store;

    @BeforeEach
    void setUp() {
        store = new RelaySessionAuthorizationStore();
        store.init();
    }

    @Test
    void noPolicyPreservesOrdinaryRegisteredClientBehavior() {
        FTClient client = client(100, 999);
        assertTrue(store.canAct(client, 0));
        assertTrue(store.canAct(client, 3, true));
        assertTrue(store.canParticipate(client));
        assertTrue(store.isAuthorizedActor(100, 27));
        assertFalse(store.isBattlemon(100));
    }

    @Test
    void dedicatedOwnersOnlyControlTheirActorsAndPetCoverageNeedsController() {
        store.put(policy(100, Map.of(
                1000, List.of((short) 0, (short) 2),
                2000, List.of((short) 1, (short) 3)), Map.of(1000, true, 2000, false)));
        FTClient first = client(100, 1000);
        FTClient second = client(100, 2000);

        assertTrue(store.canAct(first, 0));
        assertTrue(store.canAct(first, 2));
        assertFalse(store.canAct(first, 1));
        assertTrue(store.canAct(first, 2, true));
        assertTrue(store.canAct(second, 3));
        assertFalse(store.canAct(second, 3, true));
        assertTrue(store.isAuthorizedActor(100, 1));
        assertFalse(store.isAuthorizedActor(100, 4));
    }

    @Test
    void validationCopiesInputRejectsOverlapAndOverwritePreventsStalePolicy() {
        assertThrows(IllegalArgumentException.class, () -> store.put(policy(100, Map.of(
                1000, List.of((short) 0, (short) 2),
                2000, List.of((short) 1, (short) 2)), Map.of())));

        List<Short> mutable = new ArrayList<>(List.of((short) 0));
        Map<Integer, List<Short>> actors = new LinkedHashMap<>();
        actors.put(1000, mutable);
        store.put(RelaySessionAuthorizationMessage.builder().gameSessionId(200).battlemon(false)
                .actorPositionsByPlayerId(actors).build());
        mutable.add((short) 1);
        assertFalse(store.canAct(client(200, 1000), 1));

        store.put(RelaySessionAuthorizationMessage.builder().gameSessionId(200).battlemon(false)
                .actorPositionsByPlayerId(Map.of(2000, List.of((short) 1))).build());
        assertFalse(store.canAct(client(200, 1000), 0));
        assertTrue(store.canAct(client(200, 2000), 1));
        store.remove(200);
        assertTrue(store.canAct(client(200, 1000), 3));
    }

    private static RelaySessionAuthorizationMessage policy(int sessionId,
                                                             Map<Integer, List<Short>> actors,
                                                             Map<Integer, Boolean> controllers) {
        return RelaySessionAuthorizationMessage.builder().gameSessionId(sessionId).battlemon(true)
                .actorPositionsByPlayerId(actors).battlemonControllerByPlayerId(controllers).build();
    }

    private static FTClient client(int sessionId, int playerId) {
        FTClient client = new FTClient();
        client.setGameSessionId(sessionId);
        client.setPlayerId(playerId);
        return client;
    }
}
