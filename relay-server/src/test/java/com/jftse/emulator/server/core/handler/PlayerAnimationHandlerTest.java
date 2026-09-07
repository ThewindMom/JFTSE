package com.jftse.emulator.server.core.handler;

import com.jftse.emulator.server.core.manager.RelayManager;
import com.jftse.emulator.server.core.manager.RelaySessionAuthorizationStore;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.protocol.IPacket;
import com.jftse.server.core.shared.packets.relay.CMSGPlayerAnimation;
import com.jftse.server.core.shared.packets.relay.SMSGPlayerAnimation;
import com.jftse.server.core.shared.rabbit.messages.RelaySessionAuthorizationMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerAnimationHandlerTest {
    private Object previousRelayManager;
    private RelayManager relayManager;
    private FTClient owner;
    private FTConnection ownerConnection;
    private FTConnection peerConnection;

    @BeforeEach
    void setUp() {
        previousRelayManager = ReflectionTestUtils.getField(RelayManager.class, "instance");
        relayManager = new RelayManager();
        ReflectionTestUtils.setField(relayManager, "sessionMap",
                new ConcurrentHashMap<Integer, ConcurrentLinkedDeque<FTClient>>());
        ReflectionTestUtils.setField(RelayManager.class, "instance", relayManager);

        RelaySessionAuthorizationStore store = new RelaySessionAuthorizationStore();
        store.init();
        store.put(policy(true, true));

        owner = client(1000);
        FTClient peer = client(2000);
        ownerConnection = mock(FTConnection.class);
        peerConnection = mock(FTConnection.class);
        owner.setConnection(ownerConnection);
        peer.setConnection(peerConnection);
        when(ownerConnection.getClient()).thenReturn(owner);
        relayManager.addClientToSession(150, owner);
        relayManager.addClientToSession(150, peer);
    }

    @AfterEach
    void restoreRelayManager() {
        ReflectionTestUtils.setField(RelayManager.class, "instance", previousRelayManager);
    }

    @Test
    void ownerControllerForwardsAllFourPetDirectionsToEveryPeer() {
        PlayerAnimationHandler handler = new PlayerAnimationHandler();

        for (int animationType = 0x6d; animationType <= 0x70; animationType++) {
            handler.handle(ownerConnection, packet(2, animationType));
        }

        verify(ownerConnection, times(4)).sendTCP(any(SMSGPlayerAnimation.class));
        verify(peerConnection, times(4)).sendTCP(any(SMSGPlayerAnimation.class));
    }

    @Test
    void repeatedControllerDirectionIsForwardedDeterministically() {
        PlayerAnimationHandler handler = new PlayerAnimationHandler();
        CMSGPlayerAnimation packet = packet(2, 0x6d);

        handler.handle(ownerConnection, packet);
        handler.handle(ownerConnection, packet);
        handler.handle(ownerConnection, packet);

        verify(peerConnection, times(3)).sendTCP(any(SMSGPlayerAnimation.class));
    }

    @Test
    void controllerCommandRejectsMissingControllerAndCrossOwnerPet() {
        RelaySessionAuthorizationStore.getInstance().put(policy(true, false));
        PlayerAnimationHandler handler = new PlayerAnimationHandler();

        handler.handle(ownerConnection, packet(2, 0x6d));
        handler.handle(ownerConnection, packet(3, 0x6d));

        verify(ownerConnection, never()).sendTCP(any(IPacket.class));
        verify(peerConnection, never()).sendTCP(any(IPacket.class));
    }

    @Test
    void guardianOwnedPetUsesTheSameControllerAuthorization() {
        RelaySessionAuthorizationStore.getInstance().put(policy(false, true));

        new PlayerAnimationHandler().handle(ownerConnection, packet(2, 0x70));

        verify(peerConnection).sendTCP(any(SMSGPlayerAnimation.class));
    }

    @Test
    void guardianActorAnimationFromHostReachesSecondEndpoint() {
        RelaySessionAuthorizationStore.getInstance().put(policy(false, true));

        new PlayerAnimationHandler().handle(ownerConnection, packet(10, 1));

        verify(peerConnection).sendTCP(any(SMSGPlayerAnimation.class));
    }

    @Test
    void ordinaryCrossActorAnimationRemainsDevelopmentCompatible() {
        RelaySessionAuthorizationStore.getInstance().put(policy(false, true));

        new PlayerAnimationHandler().handle(ownerConnection, packet(1, 1));

        verify(peerConnection).sendTCP(any(SMSGPlayerAnimation.class));
    }

    @Test
    void dedicatedBattlemonRejectsCrossActorAnimation() {
        new PlayerAnimationHandler().handle(ownerConnection, packet(1, 1));

        verify(peerConnection, never()).sendTCP(any(SMSGPlayerAnimation.class));
    }

    @Test
    void spectatorCannotBroadcastOrdinaryGuardianAnimation() {
        owner.setSpectator(true);

        new PlayerAnimationHandler().handle(ownerConnection, packet(10, 1));

        verify(peerConnection, never()).sendTCP(any(SMSGPlayerAnimation.class));
    }

    private static RelaySessionAuthorizationMessage policy(boolean battlemon, boolean ownerHasController) {
        return RelaySessionAuthorizationMessage.builder()
                .gameSessionId(150)
                .battlemon(battlemon)
                .ownedPetSession(true)
                .actorPositionsByPlayerId(Map.of(
                        1000, List.of((short) 0, (short) 2),
                        2000, List.of((short) 1, (short) 3)))
                .battlemonControllerByPlayerId(Map.of(1000, ownerHasController, 2000, false))
                .build();
    }

    private static FTClient client(int playerId) {
        FTClient client = new FTClient();
        client.setGameSessionId(150);
        client.setPlayerId(playerId);
        return client;
    }

    private static CMSGPlayerAnimation packet(int actorPosition, int animationType) {
        return CMSGPlayerAnimation.builder()
                .playerPosition((char) actorPosition)
                .animationType((byte) animationType)
                .build();
    }
}
