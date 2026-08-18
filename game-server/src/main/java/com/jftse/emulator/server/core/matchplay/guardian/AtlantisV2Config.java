package com.jftse.emulator.server.core.matchplay.guardian;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * Off-by-default switch for map 10 {@code guardian-phase/10-v2/}.
 * Old {@code guardian-phase/10/} stays loaded until this is true.
 */
@Component
@Log4j2
public class AtlantisV2Config {
    @Getter
    private static AtlantisV2Config instance;

    @Getter
    private final boolean enabled;

    public AtlantisV2Config(@Value("${jftse.guardian.atlantis.v2.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    @PostConstruct
    public void init() {
        instance = this;
        log.info("Atlantis V2 scripts {} (jftse.guardian.atlantis.v2.enabled={})",
                enabled ? "ENABLED — loading guardian-phase/10-v2" : "disabled — loading guardian-phase/10",
                enabled);
    }
}
