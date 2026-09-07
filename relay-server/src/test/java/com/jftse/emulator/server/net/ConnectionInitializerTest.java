package com.jftse.emulator.server.net;

import com.jftse.emulator.common.service.ConfigService;
import com.jftse.emulator.server.core.manager.RelayManager;
import io.netty.channel.ChannelHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConnectionInitializerTest {
    @Test
    void unregisteredRelayConnectionsRetainTheRegistrationTimeout() throws Exception {
        Object previousConfigService = ReflectionTestUtils.getField(ConfigService.class, "instance");
        Object previousRelayManager = ReflectionTestUtils.getField(RelayManager.class, "instance");
        NioSocketChannel channel = new NioSocketChannel();
        try {
            ConfigService configService = mock(ConfigService.class);
            when(configService.getValue(anyString(), any())).thenReturn(false);
            ReflectionTestUtils.setField(ConfigService.class, "instance", configService);
            ReflectionTestUtils.setField(RelayManager.class, "instance", mock(RelayManager.class));

            new ConnectionInitializer().initChannel(channel);

            ChannelHandler timeoutHandler = channel.pipeline().get("readTimeoutHandler");
            assertNotNull(timeoutHandler);
            assertInstanceOf(ReadTimeoutHandler.class, timeoutHandler);
        } finally {
            channel.unsafe().closeForcibly();
            ReflectionTestUtils.setField(ConfigService.class, "instance", previousConfigService);
            ReflectionTestUtils.setField(RelayManager.class, "instance", previousRelayManager);
        }
    }
}
