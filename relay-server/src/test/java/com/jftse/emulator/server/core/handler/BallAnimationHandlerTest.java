package com.jftse.emulator.server.core.handler;

import com.jftse.emulator.server.core.manager.RelayManager;
import com.jftse.emulator.server.core.manager.RelaySessionAuthorizationStore;
import com.jftse.emulator.server.core.rabbit.service.RProducerService;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.constants.BallHitAction;
import com.jftse.server.core.shared.packets.relay.CMSGBallAnimation;
import com.jftse.server.core.shared.packets.relay.SMSGBallAnimation;
import com.jftse.server.core.shared.rabbit.messages.MatchBallSyncMessage;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BallAnimationHandlerTest {
    private Object previousRelayManager;
    private Object previousProducer;
    private RelaySessionAuthorizationStore authorizationStore;
    private RProducerService producer;
    private FTConnection hostConnection;
    private FTConnection peerConnection;
    private FTClient peer;

    @BeforeEach
    void setUp() {
        authorizationStore = new RelaySessionAuthorizationStore();
        authorizationStore.init();
        putPolicy(false);
        setUpConnections();
    }

    private void putPolicy(boolean battlemon) {
        authorizationStore.put(RelaySessionAuthorizationMessage.builder()
                .gameSessionId(150)
                .battlemon(battlemon)
                .ownedPetSession(true)
                .actorPositionsByPlayerId(Map.of(
                        1000, List.of((short) 0, (short) 2),
                        2000, List.of((short) 1, (short) 3)))
                .build());
    }

    private void setUpConnections() {
        previousRelayManager = ReflectionTestUtils.getField(RelayManager.class, "instance");
        RelayManager relayManager = new RelayManager();
        ReflectionTestUtils.setField(relayManager, "sessionMap",
                new ConcurrentHashMap<Integer, ConcurrentLinkedDeque<FTClient>>());
        ReflectionTestUtils.setField(RelayManager.class, "instance", relayManager);

        FTClient host = client(1000);
        peer = client(2000);
        hostConnection = mock(FTConnection.class);
        peerConnection = mock(FTConnection.class);
        host.setConnection(hostConnection);
        peer.setConnection(peerConnection);
        when(hostConnection.getClient()).thenReturn(host);
        when(peerConnection.getClient()).thenReturn(peer);
        relayManager.addClientToSession(150, host);
        relayManager.addClientToSession(150, peer);

        previousProducer = ReflectionTestUtils.getField(RProducerService.class, "instance");
        producer = mock(RProducerService.class);
        ReflectionTestUtils.setField(RProducerService.class, "instance", producer);
    }

    @AfterEach
    void restoreSingletons() {
        ReflectionTestUtils.setField(RelayManager.class, "instance", previousRelayManager);
        ReflectionTestUtils.setField(RProducerService.class, "instance", previousProducer);
    }

    @Test
    void guardianServeBallFromHostReachesEveryPeerAndRallySink() {
        CMSGBallAnimation packet = CMSGBallAnimation.builder()
                .playerPosition((byte) 4)
                .hitAct((byte) BallHitAction.GUARDIAN_SERVE.getId())
                .build();

        new BallAnimationHandler().handle(hostConnection, packet);

        verify(hostConnection).sendTCP(any(SMSGBallAnimation.class));
        verify(peerConnection).sendTCP(any(SMSGBallAnimation.class));
        verify(producer).send(
                any(MatchBallSyncMessage.class),
                eq("game.stats.match.rally"),
                eq("MatchplaySystem(RelayServer)"));
    }

    @Test
    void nonHostSyntheticBallIsRelayedWithoutMutatingRallyState() {
        CMSGBallAnimation packet = CMSGBallAnimation.builder()
                .playerPosition((byte) 4)
                .hitAct((byte) BallHitAction.GUARDIAN_SERVE.getId())
                .build();

        new BallAnimationHandler().handle(peerConnection, packet);

        verify(hostConnection).sendTCP(any(SMSGBallAnimation.class));
        verify(peerConnection).sendTCP(any(SMSGBallAnimation.class));
        verify(producer, org.mockito.Mockito.never()).send(
                any(MatchBallSyncMessage.class),
                any(String.class),
                any(String.class));
    }

    @Test
    void spectatorCannotRelaySyntheticGuardianBall() {
        peer.setSpectator(true);
        CMSGBallAnimation packet = CMSGBallAnimation.builder()
                .playerPosition((byte) 4)
                .hitAct((byte) BallHitAction.GUARDIAN_SERVE.getId())
                .build();

        new BallAnimationHandler().handle(peerConnection, packet);

        verify(hostConnection, org.mockito.Mockito.never())
                .sendTCP(any(SMSGBallAnimation.class));
        verify(peerConnection, org.mockito.Mockito.never())
                .sendTCP(any(SMSGBallAnimation.class));
        verify(producer, org.mockito.Mockito.never()).send(
                any(MatchBallSyncMessage.class),
                any(String.class),
                any(String.class));
    }

    @Test
    void dedicatedBattlemonRejectsSyntheticGuardianBall() {
        putPolicy(true);
        CMSGBallAnimation packet = CMSGBallAnimation.builder()
                .playerPosition((byte) 4)
                .hitAct((byte) BallHitAction.GUARDIAN_SERVE.getId())
                .build();

        new BallAnimationHandler().handle(hostConnection, packet);

        verify(hostConnection, org.mockito.Mockito.never())
                .sendTCP(any(SMSGBallAnimation.class));
        verify(peerConnection, org.mockito.Mockito.never())
                .sendTCP(any(SMSGBallAnimation.class));
        verify(producer, org.mockito.Mockito.never()).send(
                any(MatchBallSyncMessage.class),
                any(String.class),
                any(String.class));
    }

    private static FTClient client(int playerId) {
        FTClient client = new FTClient();
        client.setGameSessionId(150);
        client.setPlayerId(playerId);
        return client;
    }
}
