package com.jftse.emulator.server.core.matchplay.guardian;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-Guardian-match stand-pad state machine.
 * notActive → scheduled (onMatchStart) → visible (activate after delay) → grant on enter.
 * One-shot shield grant per player per match. Positions use the same units as spawn Point x/y
 * and CMSG_PlayerAnimation absoluteX / absoluteY (court Z).
 */
@Log4j2
public class GuardianShieldPads {
    public enum Phase { NOT_ACTIVE, SCHEDULED, VISIBLE }

    @Getter
    public static final class Config {
        private final boolean enabled;
        private final int delaySeconds;
        private final int leftX;
        private final int leftZ;
        private final int rightX;
        private final int rightZ;
        private final int radius;
        private final Path zoneFile;

        public Config(boolean enabled, int delaySeconds, int leftX, int leftZ, int rightX, int rightZ,
                      int radius, Path zoneFile) {
            this.enabled = enabled;
            this.delaySeconds = delaySeconds;
            this.leftX = leftX;
            this.leftZ = leftZ;
            this.rightX = rightX;
            this.rightZ = rightZ;
            this.radius = radius;
            this.zoneFile = zoneFile;
        }

        public static Config defaults() {
            return new Config(true, 10, -40, -40, 40, -40, 15, null);
        }

        public static Config defaultsWithZoneFile(Path zoneFile) {
            return new Config(true, 10, -40, -40, 40, -40, 15, zoneFile);
        }
    }

    public record Pad(int x, int z) {}

    public record LastCourtPos(int playerPosition, int x, int z) {}

    public record ClusterCenter(int x, int z) {
        public int getX() {
            return x;
        }

        public int getZ() {
            return z;
        }
    }

    @FunctionalInterface
    public interface GrantListener {
        void onShieldGranted(int sessionId, int playerId, int playerPosition);
    }

    static final class SessionState {
        volatile Phase phase = Phase.NOT_ACTIVE;
        final Set<Integer> grantedPlayerIds = ConcurrentHashMap.newKeySet();
        final Map<Integer, LastCourtPos> lastPosByPlayerId = new ConcurrentHashMap<>();
    }

    private final Config config;
    private final GrantListener grantListener;
    private final Map<Integer, SessionState> sessions = new ConcurrentHashMap<>();

    public GuardianShieldPads(Config config, GrantListener grantListener) {
        this.config = config;
        this.grantListener = grantListener;
    }

    public Config getConfig() {
        return config;
    }

    public List<Pad> pads() {
        return List.of(new Pad(config.leftX, config.leftZ), new Pad(config.rightX, config.rightZ));
    }

    public Phase phaseOf(int sessionId) {
        SessionState state = sessions.get(sessionId);
        return state == null ? Phase.NOT_ACTIVE : state.phase;
    }

    public boolean isVisible(int sessionId) {
        return phaseOf(sessionId) == Phase.VISIBLE;
    }

    public boolean hasGranted(int sessionId, int playerId) {
        SessionState state = sessions.get(sessionId);
        return state != null && state.grantedPlayerIds.contains(playerId);
    }

    public int activeVisibleSessionCount() {
        int count = 0;
        for (SessionState state : sessions.values()) {
            if (state.phase == Phase.VISIBLE) {
                count++;
            }
        }
        return count;
    }

    public void onMatchStart(int sessionId) {
        if (!config.enabled) {
            return;
        }
        SessionState state = new SessionState();
        state.phase = Phase.SCHEDULED;
        sessions.put(sessionId, state);
        log.info("Guardian shield pads scheduled for session {} ({}s delay, pads at {},{} and {},{}, r={})",
                sessionId, config.delaySeconds, config.leftX, config.leftZ, config.rightX, config.rightZ, config.radius);
    }

    /** Track court positions for a match without scheduling or displaying shield pads. */
    public void trackSession(int sessionId) {
        sessions.putIfAbsent(sessionId, new SessionState());
    }

    public void activate(int sessionId) {
        SessionState state = sessions.get(sessionId);
        if (state == null || state.phase != Phase.SCHEDULED) {
            return;
        }
        state.phase = Phase.VISIBLE;
        log.info("Guardian shield pads visible for session {}", sessionId);
        writeZoneFileIfAllowed();
        for (Map.Entry<Integer, LastCourtPos> entry : state.lastPosByPlayerId.entrySet()) {
            LastCourtPos pos = entry.getValue();
            tryGrant(sessionId, state, entry.getKey(), pos.playerPosition(), pos.x(), pos.z());
        }
    }

    public void onMatchEnd(int sessionId) {
        SessionState removed = sessions.remove(sessionId);
        if (removed == null) {
            writeZoneFileIfAllowed();
            return;
        }
        removed.phase = Phase.NOT_ACTIVE;
        log.info("Guardian shield pads cleared for session {}", sessionId);
        writeZoneFileIfAllowed();
    }

    /**
     * Persist last court X/Z and grant if the player just entered a visible pad.
     *
     * @return true if a shield was granted on this call
     */
    public boolean onCourtPosition(int sessionId, int playerId, int playerPosition, int x, int z) {
        if (!AtlantisV2Rules.isPlayerSlot((short) playerPosition)) {
            return false;
        }
        SessionState state = sessions.get(sessionId);
        if (state == null) {
            return false;
        }
        LastCourtPos previous = state.lastPosByPlayerId.get(playerId);
        boolean wasInside = previous != null && contains(previous.x(), previous.z());
        LastCourtPos next = new LastCourtPos(playerPosition, x, z);
        state.lastPosByPlayerId.put(playerId, next);
        boolean nowInside = contains(x, z);
        if (state.phase == Phase.VISIBLE && wasInside != nowInside) {
            log.info("Guardian pad {} session={} playerId={} pos={} at {},{}",
                    nowInside ? "enter" : "leave", sessionId, playerId, playerPosition, x, z);
        }
        if (state.phase != Phase.VISIBLE) {
            return false;
        }
        return tryGrant(sessionId, state, playerId, playerPosition, x, z);
    }

    public boolean contains(int x, int z) {
        return inside(x, z, config.leftX, config.leftZ) || inside(x, z, config.rightX, config.rightZ);
    }

    /**
     * SeaWave safe zone: last court X/Z is inside a currently visible pad circle.
     * Does not consume the one-shot {@code shieldActive} grant.
     */
    public boolean isInsideVisiblePad(int sessionId, int playerId, int playerPosition) {
        SessionState state = sessions.get(sessionId);
        if (state == null || state.phase != Phase.VISIBLE) {
            return false;
        }
        LastCourtPos byId = state.lastPosByPlayerId.get(playerId);
        if (byId != null && AtlantisV2Rules.isPlayerSlot((short) byId.playerPosition())) {
            return contains(byId.x(), byId.z());
        }
        for (LastCourtPos pos : state.lastPosByPlayerId.values()) {
            if (pos.playerPosition() == playerPosition && AtlantisV2Rules.isPlayerSlot((short) pos.playerPosition())) {
                return contains(pos.x(), pos.z());
            }
        }
        return false;
    }

    /** Return one centroid for each connected cluster of at least two active players. */
    public ClusterCenter[] clusteredPlayerCenters(int sessionId, int radius, Set<Integer> activePlayerPositions) {
        SessionState state = sessions.get(sessionId);
        if (state == null || radius < 0 || activePlayerPositions == null || activePlayerPositions.size() < 2) {
            return new ClusterCenter[0];
        }

        Map<Integer, LastCourtPos> byPosition = new ConcurrentHashMap<>();
        for (LastCourtPos position : state.lastPosByPlayerId.values()) {
            if (activePlayerPositions.contains(position.playerPosition())) {
                byPosition.put(position.playerPosition(), position);
            }
        }

        List<LastCourtPos> positions = byPosition.values().stream()
                .sorted(Comparator.comparingInt(LastCourtPos::playerPosition))
                .toList();
        List<ClusterCenter> centers = new ArrayList<>();
        boolean[] visited = new boolean[positions.size()];
        long radiusSquared = (long) radius * radius;
        for (int start = 0; start < positions.size(); start++) {
            if (visited[start]) {
                continue;
            }
            List<LastCourtPos> cluster = new ArrayList<>();
            ArrayDeque<Integer> pending = new ArrayDeque<>();
            visited[start] = true;
            pending.add(start);
            while (!pending.isEmpty()) {
                int current = pending.removeFirst();
                LastCourtPos currentPosition = positions.get(current);
                cluster.add(currentPosition);
                for (int candidate = 0; candidate < positions.size(); candidate++) {
                    if (!visited[candidate]
                            && withinRadius(currentPosition, positions.get(candidate), radiusSquared)) {
                        visited[candidate] = true;
                        pending.add(candidate);
                    }
                }
            }

            if (cluster.size() >= 2) {
                double averageX = cluster.stream().mapToInt(LastCourtPos::x).average().orElseThrow();
                double averageZ = cluster.stream().mapToInt(LastCourtPos::z).average().orElseThrow();
                centers.add(new ClusterCenter((int) Math.round(averageX), (int) Math.round(averageZ)));
            }
        }
        return centers.toArray(ClusterCenter[]::new);
    }

    private static boolean withinRadius(LastCourtPos left, LastCourtPos right, long radiusSquared) {
        long dx = (long) left.x() - right.x();
        long dz = (long) left.z() - right.z();
        return dx * dx + dz * dz <= radiusSquared;
    }

    public String visibleZoneFileContent() {
        return "pad " + config.leftX + " " + config.leftZ + "\n"
                + "pad " + config.rightX + " " + config.rightZ + "\n";
    }

    public static String clearZoneFileContent() {
        return "clear\n";
    }

    boolean inside(int x, int z, int padX, int padZ) {
        long dx = (long) x - padX;
        long dz = (long) z - padZ;
        long r = config.radius;
        return dx * dx + dz * dz <= r * r;
    }

    private boolean tryGrant(int sessionId, SessionState state, int playerId, int playerPosition, int x, int z) {
        if (state.grantedPlayerIds.contains(playerId)) {
            return false;
        }
        if (!contains(x, z)) {
            return false;
        }
        if (!state.grantedPlayerIds.add(playerId)) {
            return false;
        }
        log.info("Guardian shield granted session={} playerId={} pos={} at {},{}",
                sessionId, playerId, playerPosition, x, z);
        if (grantListener != null) {
            grantListener.onShieldGranted(sessionId, playerId, playerPosition);
        }
        return true;
    }

    void writeZoneFileIfAllowed() {
        Path zoneFile = config.zoneFile;
        if (zoneFile == null) {
            return;
        }
        int visible = activeVisibleSessionCount();
        if (visible > 1) {
            log.warn("Guardian shield-pads zone-file skipped: {} visible Guardian rooms (path can only represent one). path={}",
                    visible, zoneFile);
            return;
        }
        String content = visible == 1 ? visibleZoneFileContent() : clearZoneFileContent();
        try {
            writeAtomic(zoneFile, content);
        } catch (IOException e) {
            log.warn("Failed to write guardian shield-pads zone-file {}: {}", zoneFile, e.getMessage());
        }
    }

    static void writeAtomic(Path path, String content) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try {
            Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
