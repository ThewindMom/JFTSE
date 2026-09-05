package com.jftse.emulator.server.core.service.impl;

import com.jftse.emulator.common.service.ConfigService;
import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.client.PlayerStatisticView;
import com.jftse.emulator.server.core.life.room.*;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.matchplay.*;
import com.jftse.emulator.server.core.matchplay.event.EventHandler;
import com.jftse.emulator.server.core.matchplay.game.MatchplayBasicGame;
import com.jftse.emulator.server.core.matchplay.game.MatchplayBattleGame;
import com.jftse.emulator.server.core.matchplay.game.MatchplayGuardianGame;
import com.jftse.emulator.server.core.matchplay.handler.MatchplayBasicModeHandler;
import com.jftse.emulator.server.core.matchplay.handler.MatchplayBattleModeHandler;
import com.jftse.emulator.server.core.matchplay.handler.MatchplayGuardianModeHandler;
import com.jftse.emulator.server.core.rabbit.MatchRallyStatsConsumer;
import com.jftse.emulator.server.core.service.MatchResultService;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.account.Account;
import com.jftse.entities.database.model.player.Player;
import com.jftse.entities.database.model.player.PlayerStatistic;
import com.jftse.entities.database.model.pocket.Pocket;
import com.jftse.entities.database.repository.config.ConfigRepository;
import com.jftse.entities.database.repository.level.LevelExpRepository;
import com.jftse.entities.database.repository.log.GameLogRepository;
import com.jftse.entities.database.repository.player.PlayerRepository;
import com.jftse.entities.database.repository.player.PlayerStatisticRepository;
import com.jftse.server.core.service.*;
import com.jftse.server.core.service.impl.*;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.*;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.Entity;
import javax.persistence.EntityManager;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ModeCompletionDatabaseIT {
    private static SessionFactory factory;
    private static EntityManager entities;
    private static JpaTransactionManager transactions;
    private static JpaRepositoryFactory repositories;
    private static String url;
    private static String user;
    private static String password;

    @BeforeAll
    static void database() throws Exception {
        url = System.getenv("JFTSE_AUDIT_JDBC_URL");
        assertNotNull(url, "Requires the task-owned full-entity audit database");
        assertTrue(url.matches("jdbc:mysql://[^/]+/jftse_server_audit_modes(?:\\?.*)?"));
        user = System.getenv("JFTSE_AUDIT_JDBC_USER");
        password = System.getenv("JFTSE_AUDIT_JDBC_PASSWORD");
        Configuration configuration = new Configuration()
                .setProperty("hibernate.connection.url", url)
                .setProperty("hibernate.connection.username", user)
                .setProperty("hibernate.connection.password", password)
                .setProperty("hibernate.dialect", "org.hibernate.dialect.MySQL8Dialect")
                .setProperty("hibernate.hbm2ddl.auto", "update")
                .setProperty("hibernate.hbm2ddl.halt_on_error", "true")
                .setProperty("hibernate.connection.handling_mode", "DELAYED_ACQUISITION_AND_HOLD");
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        for (var candidate : scanner.findCandidateComponents("com.jftse.entities.database.model")) {
            configuration.addAnnotatedClass(Class.forName(candidate.getBeanClassName()));
        }
        factory = configuration.buildSessionFactory();
        entities = SharedEntityManagerCreator.createSharedEntityManager(factory);
        transactions = new JpaTransactionManager(factory);
        transactions.setJpaDialect(new HibernateJpaDialect());
        repositories = new JpaRepositoryFactory(entities);
        repositories.addRepositoryProxyPostProcessor((proxy, information) ->
                proxy.addAdvice(new TransactionInterceptor(transactions,
                        new MatchAlwaysTransactionAttributeSource())));
        try (var connection = DriverManager.getConnection(url, user, password)) {
            connection.createStatement().execute("CREATE TABLE IF NOT EXISTS MatchResult (resultId VARCHAR(36) PRIMARY KEY, claimToken VARCHAR(36) NOT NULL, created DATETIME(6) NOT NULL) ENGINE=InnoDB");
        }
    }

    @AfterAll
    static void closeDatabase() {
        if (factory != null) factory.close();
    }

    private static <T> T service(Object implementation, Class<T> contract) {
        ProxyFactory proxy = new ProxyFactory(implementation);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return contract.cast(proxy.getProxy());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("completionCases")
    void completionPersistsRealPlayerAndStatistics(String mode, boolean pets, String stage) throws Exception {
        boolean inventory = stage.startsWith("ring");
        boolean rollback = stage.equals("log") || stage.equals("commit") || stage.equals("ringRollback") || stage.startsWith("after");
        boolean laterCompletion = stage.equals("laterCompletion") || stage.equals("laterRefresh") || stage.equals("overlapCompletion");
        boolean readRace = stage.equals("readRace") || stage.equals("fullRefresh") || laterCompletion;
        boolean blockedRead = readRace || stage.equals("ringSwitch");
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var changingSnapshot = new java.util.concurrent.atomic.AtomicReference<FTPlayer>();
        AtomicInteger refreshAttempts = new AtomicInteger();
        AtomicInteger lockingReads = new AtomicInteger();
        var secondLockAttempt = new java.util.concurrent.CountDownLatch(1);
        AtomicBoolean firstLog = new AtomicBoolean(true);
        Map<Class<?>, Object> previous = new LinkedHashMap<>();
        for (Class<?> type : List.of(ServiceManager.class, GameManager.class, GameSessionManager.class, ConfigService.class)) {
            previous.put(type, ReflectionTestUtils.getField(type, "instance"));
        }
        try {
            ServiceManager services = mock(ServiceManager.class);
            GameManager manager = mock(GameManager.class);
            EventHandler events = new EventHandler();
            events.init();
            GameSessionManager sessions = new GameSessionManager();
            sessions.init();
            when(manager.getEventHandler()).thenReturn(events);
            when(manager.getMatchRallyStatsConsumer()).thenReturn(new MatchRallyStatsConsumer(sessions));
            ReflectionTestUtils.setField(ServiceManager.class, "instance", services);
            ReflectionTestUtils.setField(GameManager.class, "instance", manager);
            ConfigService config = new ConfigService();
            ReflectionTestUtils.setField(config, "configRepository", repositories.getRepository(ConfigRepository.class));
            config.init();
            PlayerService persistedPlayers = service(new PlayerServiceImpl(repositories.getRepository(PlayerRepository.class)), PlayerService.class);
            ProxyFactory playerAccess = new ProxyFactory(persistedPlayers);
            AtomicBoolean blockRead = new AtomicBoolean(blockedRead);
            playerAccess.addAdvice((org.aopalliance.intercept.MethodInterceptor) invocation -> {
                if (changingSnapshot.get() != null) assertFalse(Thread.holdsLock(changingSnapshot.get()), "No player persistence under snapshot monitor");
                if (invocation.getMethod().getName().equals("findByIdForUpdate") && lockingReads.incrementAndGet() == 3)
                    secondLockAttempt.countDown();
                Object result = invocation.proceed();
                if (stage.equals("afterPlayer") && invocation.getMethod().getName().equals("save")) {
                    entities.flush();
                    throw new IllegalStateException("Injected after flushed player persistence");
                }
                if (stage.equals("refreshExhaust") && invocation.getMethod().getName().equals("findWithStatisticById")) {
                    refreshAttempts.incrementAndGet();
                    changingSnapshot.get().syncGold(100);
                }
                if (invocation.getMethod().getName().equals("findWithStatisticById") && blockRead.compareAndSet(true, false)) {
                    entered.countDown();
                    assertTrue(release.await(5, java.util.concurrent.TimeUnit.SECONDS));
                }
                return result;
            });
            PlayerService players = (PlayerService) playerAccess.getProxy();
            when(services.getPlayerService()).thenReturn(players);
            when(services.getLevelService()).thenReturn(service(new LevelServiceImpl(
                    repositories.getRepository(LevelExpRepository.class), players, config), LevelService.class));
            PlayerStatisticService statisticsService = service(new PlayerStatisticServiceImpl(
                    repositories.getRepository(PlayerStatisticRepository.class)), PlayerStatisticService.class);
            ProxyFactory statisticsAccess = new ProxyFactory(statisticsService);
            statisticsAccess.addAdvice((org.aopalliance.intercept.MethodInterceptor) invocation -> {
                Object result = invocation.proceed();
                if (stage.equals("afterStats") && invocation.getMethod().getName().equals("updatePlayerStats")) {
                    entities.flush();
                    throw new IllegalStateException("Injected after flushed statistics persistence");
                }
                return result;
            });
            when(services.getPlayerStatisticService()).thenReturn((PlayerStatisticService) statisticsAccess.getProxy());
            PetService petService = service(new PetServiceImpl(
                    repositories.getRepository(com.jftse.entities.database.repository.pet.PetRepository.class),
                    repositories.getRepository(com.jftse.entities.database.repository.pet.PetStatisticRepository.class)), PetService.class);
            ProxyFactory petAccess = new ProxyFactory(petService);
            petAccess.addAdvice((org.aopalliance.intercept.MethodInterceptor) invocation -> {
                Object result = invocation.proceed();
                if (stage.equals("afterPet") && invocation.getMethod().getName().equals("awardExperience")) {
                    entities.flush();
                    throw new IllegalStateException("Injected after flushed pet persistence");
                }
                return result;
            });
            when(services.getPetService()).thenReturn((PetService) petAccess.getProxy());
            PocketService pockets = service(new PocketServiceImpl(repositories.getRepository(
                    com.jftse.entities.database.repository.pocket.PocketRepository.class)), PocketService.class);
            PlayerPocketService items = service(new PlayerPocketServiceImpl(
                    repositories.getRepository(com.jftse.entities.database.repository.item.ItemMaterialRepository.class),
                    repositories.getRepository(com.jftse.entities.database.repository.item.ItemEnchantRepository.class),
                    repositories.getRepository(com.jftse.entities.database.repository.item.ProductRepository.class),
                    repositories.getRepository(com.jftse.entities.database.repository.pocket.PlayerPocketRepository.class),
                    pockets, null), PlayerPocketService.class);
            when(services.getPocketService()).thenReturn(pockets);
            when(services.getPlayerPocketService()).thenReturn(items);
            SpecialSlotEquipmentService persistedSlots = service(new SpecialSlotEquipmentServiceImpl(
                    repositories.getRepository(com.jftse.entities.database.repository.player.SpecialSlotEquipmentRepository.class),
                    items), SpecialSlotEquipmentService.class);
            ProxyFactory slotAccess = new ProxyFactory(persistedSlots);
            AtomicBoolean blockSlots = new AtomicBoolean(stage.equals("ringStaleRead"));
            slotAccess.addAdvice((org.aopalliance.intercept.MethodInterceptor) invocation -> {
                if (changingSnapshot.get() != null) assertFalse(Thread.holdsLock(changingSnapshot.get()), "No equipment persistence under snapshot monitor");
                Object result = invocation.proceed();
                if (invocation.getMethod().getName().equals("findById") && blockSlots.compareAndSet(true, false)) {
                    entered.countDown();
                    assertTrue(release.await(5, java.util.concurrent.TimeUnit.SECONDS));
                }
                return result;
            });
            when(services.getSpecialSlotEquipmentService()).thenReturn((SpecialSlotEquipmentService) slotAccess.getProxy());
            when(services.getItemSpecialService()).thenReturn(new ItemSpecialServiceImpl(repositories.getRepository(
                    com.jftse.entities.database.repository.item.ItemSpecialRepository.class)));
            GameLogService logs = new GameLogServiceImpl(repositories.getRepository(GameLogRepository.class));
            when(services.getGameLogService()).thenReturn(log -> {
                var saved = logs.save(log);
                if (stage.equals("log") || stage.equals("ringRollback")) throw new IllegalStateException("Injected after actual game-log persistence");
                if (stage.equals("commit")) {
                    org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                            new org.springframework.transaction.support.TransactionSynchronization() {
                                @Override
                                public void beforeCommit(boolean readOnly) {
                                    throw new IllegalStateException("Injected during commit after completion callback returned");
                                }
                            });
                }
                if (stage.equals("duplicate") || stage.equals("overlapCompletion") && firstLog.compareAndSet(true, false)) {
                    entered.countDown();
                    try {
                        assertTrue(release.await(5, java.util.concurrent.TimeUnit.SECONDS));
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    }
                }
                return saved;
            });
            var resultImplementation = new MatchResultServiceImpl();
            ReflectionTestUtils.setField(resultImplementation, "entityManager", entities);
            when(services.getMatchResultService()).thenReturn(service(resultImplementation, MatchResultService.class));

            List<Player> seeded = new ArrayList<>();
            List<Long> ringIds = new ArrayList<>();
            List<Integer> replacementRingIds = new ArrayList<>();
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                if (inventory && services.getItemSpecialService().findByItemIndex(1) == null) {
                    var definition = new com.jftse.entities.database.model.item.ItemSpecial();
                    definition.setItemIndex(1);
                    definition.setName("Ring of EXP");
                    entities.persist(definition);
                }
                for (int index = 0; index < 2; index++) {
                    Account account = new Account();
                    account.setUsername(UUID.randomUUID().toString());
                    account.setAp(123);
                    entities.persist(account);
                    Pocket pocket = new Pocket();
                    entities.persist(pocket);
                    PlayerStatistic statistics = new PlayerStatistic();
                    entities.persist(statistics);
                    Player player = new Player();
                    player.setName("audit" + index);
                    player.setPlayerType((byte) 0);
                    player.setAccount(account);
                    player.setPocket(pocket);
                    player.setPlayerStatistic(statistics);
                    player.setPetList(new ArrayList<>());
                    player.setChallengeProgressList(new ArrayList<>());
                    player.setTutorialProgressList(new ArrayList<>());
                    player.setGold(100);
                    player.setExpPoints(10);
                    if (inventory) {
                        var ring = new com.jftse.entities.database.model.pocket.PlayerPocket();
                        ring.setPocket(pocket);
                        ring.setCategory(com.jftse.server.core.item.EItemCategory.SPECIAL.getName());
                        ring.setItemIndex(1);
                        ring.setItemCount(stage.equals("ringCount") ? 2 : 1);
                        entities.persist(ring);
                        entities.flush();
                        ringIds.add(ring.getId());
                        pocket.setBelongings(1);
                        var equipment = new com.jftse.entities.database.model.player.SpecialSlotEquipment();
                        equipment.setSlot1(ring.getId().intValue());
                        entities.persist(equipment);
                        player.setSpecialSlotEquipment(equipment);
                    }
                    entities.persist(player);
                    entities.flush();
                    entities.refresh(statistics);
                    seeded.add(player);
                }
            });
            List<com.jftse.entities.database.model.pet.Pet> ownedPets = new ArrayList<>();
            if (pets) {
                for (Player player : seeded) {
                    var pet = petService.createPet(1, player);
                    player.getPetList().add(pet);
                    ownedPets.add(pet);
                }
            }
            GameSession session = new GameSession(pets && !mode.equals("guardian"));
            sessions.getGameSessionList().put(71, session);
            MatchplayGame game = completedGame(mode);
            MatchplayHandleable handler = handler(game);
            MatchplayReward rewards = new MatchplayReward();
            when(game.getMatchRewards()).thenReturn(rewards);
            session.setMatchplayGame(game);
            Room room = mock(Room.class);
            when(room.getRoomId()).thenReturn((short) 71);
            when(room.getRoomPlayerList()).thenReturn(new ConcurrentLinkedDeque<>());
            List<FTPlayer> snapshots = new ArrayList<>();
            List<FTConnection> connections = new ArrayList<>();
            List<FTClient> owners = new ArrayList<>();
            for (short index = 0; index < 2; index++) {
                FTPlayer player = FTPlayer.init(seeded.get(index));
                player.setPlayerStatistic(PlayerStatisticView.fromEntity(seeded.get(index).getPlayerStatistic()));
                player.setSpecialSlots(new com.jftse.emulator.server.core.client.EquippedSpecialSlots(0, 123, 0, 0, 0));
                snapshots.add(player);
                if (index == 0) changingSnapshot.set(player);
                RoomPlayer seat = mock(RoomPlayer.class);
                when(seat.getPosition()).thenReturn(index);
                when(seat.getName()).thenReturn(player.getName());
                when(seat.getPlayerId()).thenReturn(player.getId());
                when(seat.getAccountId()).thenReturn(player.getAccountId());
                when(seat.getEquippedSpecialSlots()).thenReturn(new com.jftse.emulator.server.core.client.EquippedSpecialSlots(0, 0, 0, 0, 0));
                when(seat.getEquippedCardSlots()).thenReturn(new com.jftse.emulator.server.core.client.EquippedCardSlots(0, 0, 0, 0, 0));
                if (inventory) {
                    player.setSpecialSlots(com.jftse.emulator.server.core.client.EquippedSpecialSlots.of(seeded.get(index)));
                    when(seat.isRingOfExpEquipped()).thenReturn(true);
                    when(seat.getPpIdRingExp()).thenReturn(ringIds.get(index));
                }
                room.getRoomPlayerList().add(seat);
                FTClient client = new FTClient();
                client.refreshPlayer(player);
                client.setRoomPlayer(seat);
                client.setActiveRoom(room);
                client.setActiveGameSession(71);
                FTConnection connection = mock(FTConnection.class);
                when(connection.getClient()).thenReturn(client);
                if (stage.equals("network")) doThrow(new IllegalStateException("Injected publication failure"))
                        .when(connection).sendTCP(any(com.jftse.server.core.protocol.IPacket.class));
                client.setConnection(connection);
                connections.add(connection);
                session.getClients().add(client);
                owners.add(client);
                if (pets) session.addOwnedPetSeat(seat, ownedPets.get(index));
                PlayerReward reward = new PlayerReward(index);
                reward.setGold(50);
                reward.setExp(20);
                rewards.addPlayerReward(reward);
            }
            FTClient loading = mock(FTClient.class);
            when(loading.getActiveGameSession()).thenReturn(session);
            session.getClients().add(loading);
            FTClient host = session.getClients().getFirst();
            if (stage.equals("ringStaleRead")) {
                try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
                    var request = mock(com.jftse.server.core.shared.packets.inventory.CMSGInventoryWearSpecial.class);
                    when(request.getSpecialSlotList()).thenReturn(List.of(ringIds.getFirst().intValue(), 0, 0, 0));
                    var stale = executor.submit(() -> new com.jftse.emulator.server.core.handler.inventory.InventoryWearSpecialPacketHandler()
                            .handle(connections.getFirst(), request));
                    try {
                        assertTrue(entered.await(3, java.util.concurrent.TimeUnit.SECONDS));
                        handler.onEnd(host);
                    } finally {
                        release.countDown();
                    }
                    stale.get(3, java.util.concurrent.TimeUnit.SECONDS);
                }
            } else if (stage.equals("duplicate") || blockedRead) {
                try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
                    var first = executor.submit(() -> handler.onEnd(host));
                    try {
                        assertTrue(entered.await(3, java.util.concurrent.TimeUnit.SECONDS));
                        assertFalse(sessions.hasMatchplayReward(71));
                        assertTrue(events.getFireableDeque().isEmpty());
                        connections.forEach(connection -> verify(connection, never()).sendTCP(any(com.jftse.server.core.protocol.IPacket.class)));
                        snapshots.forEach(player -> assertEquals(100, player.getGold(), "Uncommitted gold must not enter the live player snapshot"));
                        if (laterCompletion) {
                            GameSession second = new GameSession(pets && !mode.equals("guardian"));
                            MatchplayGame secondGame = completedGame(mode);
                            MatchplayReward secondRewards = new MatchplayReward();
                            when(secondGame.getMatchRewards()).thenReturn(secondRewards);
                            second.setMatchplayGame(secondGame);
                            sessions.getGameSessionList().put(72, second);
                            second.getClients().addAll(owners);
                            for (int index = 0; index < owners.size(); index++) {
                                FTClient owner = owners.get(index);
                                owner.setActiveGameSession(72);
                                if (pets) second.addOwnedPetSeat(owner.getRoomPlayer(), ownedPets.get(index));
                                PlayerReward reward = new PlayerReward(index);
                                reward.setGold(50);
                                reward.setExp(20);
                                secondRewards.addPlayerReward(reward);
                            }
                            FTClient secondLoading = mock(FTClient.class);
                            when(secondLoading.getActiveGameSession()).thenReturn(second);
                            second.getClients().add(secondLoading);
                            var secondCompletion = executor.submit(() -> handler(secondGame).onEnd(host));
                            if (stage.equals("overlapCompletion")) {
                                assertTrue(secondLockAttempt.await(3, java.util.concurrent.TimeUnit.SECONDS));
                                assertThrows(java.util.concurrent.TimeoutException.class, () ->
                                        secondCompletion.get(100, java.util.concurrent.TimeUnit.MILLISECONDS));
                                release.countDown();
                            }
                            secondCompletion.get(3, java.util.concurrent.TimeUnit.SECONDS);
                            if (stage.equals("laterRefresh")) {
                                for (FTPlayer snapshot : snapshots) snapshot.sync(players.findWithStatisticById(snapshot.getId()));
                            }
                        } else if (stage.equals("ringSwitch")) {
                            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                                for (Player player : seeded) {
                                    var pocket = entities.find(Pocket.class, player.getPocket().getId());
                                    var ring = new com.jftse.entities.database.model.pocket.PlayerPocket();
                                    ring.setPocket(pocket);
                                    ring.setCategory(com.jftse.server.core.item.EItemCategory.SPECIAL.getName());
                                    ring.setItemIndex(2);
                                    ring.setItemCount(2);
                                    entities.persist(ring);
                                    entities.flush();
                                    pocket.setBelongings(1);
                                    entities.find(com.jftse.entities.database.model.player.SpecialSlotEquipment.class,
                                            player.getSpecialSlotEquipment().getId()).setSlot1(ring.getId().intValue());
                                    replacementRingIds.add(ring.getId().intValue());
                                }
                            });
                            for (int index = 0; index < snapshots.size(); index++) {
                                var request = mock(com.jftse.server.core.shared.packets.inventory.CMSGInventoryWearSpecial.class);
                                when(request.getSpecialSlotList()).thenReturn(List.of(replacementRingIds.get(index), 0, 0, 0));
                                new com.jftse.emulator.server.core.handler.inventory.InventoryWearSpecialPacketHandler()
                                        .handle(connections.get(index), request);
                            }
                        } else if (readRace) {
                            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                                Player updated = players.findByIdForUpdate(seeded.getFirst().getId());
                                updated.setGold(200);
                                updated.setExpPoints(50);
                                updated.setName("renamed");
                            });
                            if (stage.equals("fullRefresh")) snapshots.getFirst().sync(players.findWithStatisticById(seeded.getFirst().getId()));
                            else snapshots.getFirst().syncGold(200);
                        } else executor.submit(() -> handler.onEnd(host)).get(1, java.util.concurrent.TimeUnit.SECONDS);
                    } finally {
                        release.countDown();
                    }
                    first.get(3, java.util.concurrent.TimeUnit.SECONDS);
                }
            } else if (stage.equals("refreshExhaust")) {
                assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () ->
                        assertThrows(ConcurrentModificationException.class, () -> handler.onEnd(host)));
                assertEquals(4, refreshAttempts.get());
                handler.onEnd(host);
                connections.forEach(connection -> verify(connection).close());
                verify(manager).cleanupGameSession(71, session, room);
                assertFalse(sessions.hasMatchplayReward(71));
                assertTrue(events.getFireableDeque().isEmpty());
            } else if (rollback || stage.equals("network")) {
                assertThrows(IllegalStateException.class, () -> handler.onEnd(host));
                handler.onEnd(host);
            } else handler.onEnd(host);
            if (stage.equals("ringSwitch")) handler.onEnd(host);
            if (rollback) {
                assertFalse(sessions.hasMatchplayReward(71));
                assertTrue(events.getFireableDeque().isEmpty());
                connections.forEach(connection -> verify(connection, never()).sendTCP(any(com.jftse.server.core.protocol.IPacket.class)));
            }
            try (var connection = DriverManager.getConnection(url, user, password)) {
                try (var statement = connection.prepareStatement("SELECT COUNT(*) FROM MatchResult WHERE resultId=?")) {
                    statement.setString(1, session.getResultId().toString());
                    try (var rows = statement.executeQuery()) {
                        assertTrue(rows.next());
                        assertEquals(rollback ? 0 : 1, rows.getInt(1), "Durable claim follows commit, not publication success");
                    }
                }
                for (int index = 0; index < 2; index++) {
                    try (var statement = connection.prepareStatement("SELECT p.gold,p.expPoints,s." + mode + "RecordWin,s." + mode + "RecordLoss,a.ap FROM Player p JOIN PlayerStatistic s ON s.id=p.playerStatistic_id JOIN Account a ON a.id=p.account_id WHERE p.id=?")) {
                        statement.setLong(1, seeded.get(index).getId());
                        try (var rows = statement.executeQuery()) {
                            assertTrue(rows.next());
                            assertEquals(rollback ? 100 : laterCompletion || readRace && index == 0 ? 200 : 150, rows.getInt(1));
                            assertEquals(rollback ? 10 : laterCompletion || readRace && index == 0 ? 50 : 30, rows.getInt(2));
                            boolean winner = mode.equals("guardian") || index == 0;
                            assertEquals(!rollback && winner ? laterCompletion ? 2 : 1 : 0, rows.getInt(3));
                            assertEquals(!rollback && !winner ? laterCompletion ? 2 : 1 : 0, rows.getInt(4));
                            assertEquals(123, rows.getInt(5));
                        }
                    }
                    if (rollback) {
                        verify(connections.get(index)).close();
                        assertEquals(100, snapshots.get(index).getGold());
                        assertEquals(10, snapshots.get(index).getExpPoints());
                    }
                    else if (!stage.equals("refreshExhaust")) {
                        assertEquals(laterCompletion || readRace && index == 0 ? 200 : 150, snapshots.get(index).getGold());
                        assertEquals(laterCompletion || readRace && index == 0 ? 50 : 30, snapshots.get(index).getExpPoints());
                    }
                    assertEquals(stage.equals("fullRefresh") && index == 0 ? "renamed" : "audit" + index, snapshots.get(index).getName());
                    if (inventory) {
                        var ring = items.getItemAsPocket(ringIds.get(index), seeded.get(index).getPocket());
                        boolean depleted = stage.equals("ringDepleted") || stage.equals("ringStaleRead");
                        if (depleted || stage.equals("ringSwitch")) assertNull(ring);
                        else assertEquals(1, ring.getItemCount());
                        var equipment = services.getSpecialSlotEquipmentService().findById(seeded.get(index).getSpecialSlotEquipment().getId());
                        int expectedSlot = stage.equals("ringSwitch") ? replacementRingIds.get(index) : depleted ? 0 : ringIds.get(index).intValue();
                        assertEquals(expectedSlot, equipment.getSlot1());
                        assertEquals(expectedSlot, snapshots.get(index).getSpecialSlots().slot1(), "Committed inventory must reach the live equipment cache");
                        assertEquals(depleted ? 0 : 1, pockets.findById(seeded.get(index).getPocket().getId()).getBelongings());
                        if (stage.equals("ringSwitch") || stage.equals("ringStaleRead")) {
                            var wear = mockingDetails(connections.get(index)).getInvocations().stream()
                                    .filter(invocation -> invocation.getMethod().getName().equals("sendTCP"))
                                    .flatMap(invocation -> Arrays.stream((com.jftse.server.core.protocol.IPacket[]) invocation.getRawArguments()[0]))
                                    .filter(packet -> packet instanceof
                                    com.jftse.emulator.server.core.packets.inventory.S2CInventoryWearSpecialAnswerPacket)
                                    .reduce((first, last) -> last).orElseThrow();
                            assertArrayEquals(new com.jftse.emulator.server.core.packets.inventory.S2CInventoryWearSpecialAnswerPacket(
                                    List.of(expectedSlot, 0, 0, 0)).toBytes(), wear.toBytes());
                        }
                    } else assertEquals(123, snapshots.get(index).getSpecialSlots().slot1());
                    if (pets) {
                        try (var statement = connection.prepareStatement("SELECT expPoints FROM Pet WHERE id=?")) {
                            statement.setLong(1, ownedPets.get(index).getId());
                            try (var rows = statement.executeQuery()) {
                                assertTrue(rows.next());
                                assertEquals(rollback ? 0 : laterCompletion ? 40 : 20, rows.getInt(1));
                            }
                        }
                    }
                }
            }
        } finally {
            release.countDown();
            previous.forEach((type, instance) -> ReflectionTestUtils.setField(type, "instance", instance));
        }
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> pickerCases() {
        return java.util.stream.Stream.of(false, true).flatMap(auto ->
                java.util.stream.Stream.of(false, true).flatMap(stack ->
                        java.util.stream.Stream.of("success", "pocket", "belongings", "network", "duplicate", "durable", "replacement")
                                .filter(stage -> !stack || !stage.equals("belongings"))
                                .map(stage -> org.junit.jupiter.params.provider.Arguments.of(auto, stack, stage))));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("pickerCases")
    void realItemPickerCommitsBeforePublication(boolean auto, boolean stack, String stage) throws Exception {
        Map<Class<?>, Object> previous = new LinkedHashMap<>();
        for (Class<?> type : List.of(ServiceManager.class, GameManager.class, GameSessionManager.class,
                com.jftse.server.core.thread.ThreadManager.class)) previous.put(type, ReflectionTestUtils.getField(type, "instance"));
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        AtomicBoolean failOnce = new AtomicBoolean(true);
        var observedClient = new java.util.concurrent.atomic.AtomicReference<FTClient>();
        try {
            var services = mock(ServiceManager.class);
            ReflectionTestUtils.setField(ServiceManager.class, "instance", services);
            ReflectionTestUtils.setField(GameManager.class, "instance", mock(GameManager.class));
            ReflectionTestUtils.setField(com.jftse.server.core.thread.ThreadManager.class, "instance",
                    mock(com.jftse.server.core.thread.ThreadManager.class));
            var sessions = new GameSessionManager();
            sessions.init();
            var pocketRepo = repositories.getRepository(com.jftse.entities.database.repository.pocket.PocketRepository.class);
            var itemRepo = repositories.getRepository(com.jftse.entities.database.repository.pocket.PlayerPocketRepository.class);
            var productRepo = repositories.getRepository(com.jftse.entities.database.repository.item.ProductRepository.class);
            PocketService pockets = service(new PocketServiceImpl(pocketRepo), PocketService.class);
            ProxyFactory pocketProxy = new ProxyFactory(pockets);
            pocketProxy.addAdvice((org.aopalliance.intercept.MethodInterceptor) invocation -> {
                if (observedClient.get() != null) assertFalse(Thread.holdsLock(observedClient.get()));
                Object result = invocation.proceed();
                if (stage.equals("belongings") && invocation.getMethod().getName().equals("incrementPocketBelongings") && failOnce.getAndSet(false)) {
                    if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) entities.flush();
                    throw new IllegalStateException("Injected after belongings SQL");
                }
                return result;
            });
            when(services.getPocketService()).thenReturn((PocketService) pocketProxy.getProxy());
            var itemImplementation = new PlayerPocketServiceImpl(
                    repositories.getRepository(com.jftse.entities.database.repository.item.ItemMaterialRepository.class),
                    repositories.getRepository(com.jftse.entities.database.repository.item.ItemEnchantRepository.class), productRepo, itemRepo, pockets, null);
            ProxyFactory itemProxy = new ProxyFactory(service(itemImplementation, PlayerPocketService.class));
            itemProxy.addAdvice((org.aopalliance.intercept.MethodInterceptor) invocation -> {
                if (observedClient.get() != null) assertFalse(Thread.holdsLock(observedClient.get()));
                Object result = invocation.proceed();
                if (invocation.getMethod().getName().equals("save")) {
                    if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) entities.flush();
                    if (stage.equals("pocket") && failOnce.getAndSet(false)) throw new IllegalStateException("Injected after item SQL");
                    if (stage.equals("duplicate") || stage.equals("replacement")) {
                        entered.countDown();
                        assertTrue(release.await(5, java.util.concurrent.TimeUnit.SECONDS));
                    }
                }
                return result;
            });
            when(services.getPlayerPocketService()).thenReturn((PlayerPocketService) itemProxy.getProxy());
            when(services.getProductService()).thenReturn(new ProductServiceImpl(productRepo,
                    null, null, null, null, null, null, null, null, null, null, null, null, null, null));
            var resultService = new MatchResultServiceImpl();
            ReflectionTestUtils.setField(resultService, "entityManager", entities);
            when(services.getMatchResultService()).thenReturn(service(resultService, MatchResultService.class));
            Pocket pocket = new TransactionTemplate(transactions).execute(status -> {
                var product = productRepo.findProductByProductIndex(777);
                if (product == null) {
                    product = new com.jftse.entities.database.model.item.Product();
                    product.setProductIndex(777); product.setCategory("SPECIAL"); product.setItem0(777); product.setUseType("COUNT");
                    entities.persist(product);
                }
                Pocket result = new Pocket();
                result.setBelongings(stack ? 1 : 0);
                entities.persist(result);
                if (stack) {
                    var item = new com.jftse.entities.database.model.pocket.PlayerPocket();
                    item.setPocket(result); item.setCategory("SPECIAL"); item.setItemIndex(777); item.setItemCount(3); item.setUseType("COUNT");
                    entities.persist(item);
                }
                return result;
            });
            FTClient client = new FTClient();
            observedClient.set(client);
            FTPlayer player = mock(FTPlayer.class);
            when(player.getPocketId()).thenReturn(pocket.getId());
            client.refreshPlayer(player);
            FTConnection connection = mock(FTConnection.class);
            client.setConnection(connection);
            when(connection.getClient()).thenReturn(client);
            if (stage.equals("network")) doThrow(new IllegalStateException("Injected after durable commit"))
                    .when(connection).sendTCP(any(com.jftse.server.core.protocol.IPacket.class));
            Room room = mock(Room.class);
            when(room.getRoomId()).thenReturn((short) 77);
            RoomPlayer seat = mock(RoomPlayer.class);
            when(room.getRoomPlayerList()).thenReturn(new ConcurrentLinkedDeque<>(List.of(seat)));
            client.setActiveRoom(room); client.setRoomPlayer(seat);
            MatchplayReward reward = new MatchplayReward();
            reward.getSlotRewards().put((byte) 0, new MatchplayReward.ItemReward(777, 1, 1.0));
            sessions.addMatchplayReward(77, reward);
            if (stage.equals("durable")) services.getMatchResultService().executeOnce(reward.getSlotReward((byte) 0).getResultId(), () -> {
                var item = itemRepo.findByItemIndexAndCategoryAndPocket(777, "SPECIAL", pocket).orElse(null);
                if (item == null) {
                    item = new com.jftse.entities.database.model.pocket.PlayerPocket();
                    item.setPocket(pocket); item.setCategory("SPECIAL"); item.setItemIndex(777); item.setUseType("COUNT");
                    item.setItemCount(0);
                }
                item.setItemCount(item.getItemCount() + 1);
                itemRepo.save(item);
                if (!stack) pockets.incrementPocketBelongings(pocket);
            });
            MatchplayReward replacementReward = new MatchplayReward();
            Runnable manual = () -> new com.jftse.emulator.server.core.handler.matchplay.MatchplayItemRewardPickHandler()
                    .handle(connection, com.jftse.server.core.shared.packets.matchplay.CMSGPickupItemReward.builder().slot((byte) 0).build());
            Runnable timeout = new com.jftse.emulator.server.core.task.AutoItemRewardPickerTask(new ConcurrentLinkedDeque<>(List.of(client)), room, reward);
            Runnable action = auto ? timeout : manual;
            if (stage.equals("duplicate") || stage.equals("replacement")) {
                try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
                    var first = executor.submit(action);
                    try {
                        assertTrue(entered.await(3, java.util.concurrent.TimeUnit.SECONDS));
                        verify(connection, never()).sendTCP(any(com.jftse.server.core.protocol.IPacket.class));
                        if (stage.equals("replacement")) sessions.addMatchplayReward(77, replacementReward);
                        else executor.submit(auto ? manual : timeout).get(3, java.util.concurrent.TimeUnit.SECONDS);
                    } finally { release.countDown(); }
                    first.get(3, java.util.concurrent.TimeUnit.SECONDS);
                }
            } else {
                try { action.run(); } catch (IllegalStateException expected) {
                    assertNotEquals("success", stage);
                }
            }
            boolean rollback = stage.equals("pocket") || stage.equals("belongings");
            try (var connectionDb = DriverManager.getConnection(url, user, password);
                 var statement = connectionDb.prepareStatement("SELECT p.belongings, COALESCE(SUM(i.itemCount),0) FROM Pocket p LEFT JOIN PlayerPocket i ON i.pocket_id=p.id WHERE p.id=? GROUP BY p.id")) {
                statement.setLong(1, pocket.getId());
                try (var rows = statement.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals(rollback ? stack ? 1 : 0 : 1, rows.getInt(1));
                    assertEquals((stack ? 3 : 0) + (rollback ? 0 : 1), rows.getInt(2));
                }
            }
            assertEquals(!rollback, reward.getSlotReward((byte) 0).getClaimed().get());
            if (rollback) verify(connection, never()).sendTCP(any(com.jftse.server.core.protocol.IPacket.class));
            if (stage.equals("replacement")) {
                assertSame(replacementReward, sessions.getMatchplayReward(77));
                verify(connection, never()).sendTCP(any(com.jftse.server.core.protocol.IPacket.class));
            }
            String resultId = reward.getSlotReward((byte) 0).getResultId();
            action.run();
            assertEquals(resultId, reward.getSlotReward((byte) 0).getResultId());
            assertTrue(reward.getSlotReward((byte) 0).getCommitted().get());
            try (var connectionDb = DriverManager.getConnection(url, user, password);
                 var statement = connectionDb.prepareStatement("SELECT COALESCE(SUM(itemCount),0) FROM PlayerPocket WHERE pocket_id=?")) {
                statement.setLong(1, pocket.getId());
                try (var rows = statement.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals((stack ? 3 : 0) + 1, rows.getInt(1), "Retry after rollback or committed publication failure grants once");
                }
            }
        } finally {
            release.countDown();
            previous.forEach((type, value) -> ReflectionTestUtils.setField(type, "instance", value));
        }
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> completionCases() {
        return java.util.stream.Stream.of("basic", "battle", "guardian").flatMap(mode ->
                java.util.stream.Stream.of(false, true).flatMap(pets ->
                        java.util.stream.Stream.of("success", "log", "commit", "duplicate", "network", "readRace", "fullRefresh", "laterCompletion", "laterRefresh", "ringCount", "ringDepleted", "ringRollback", "refreshExhaust", "ringSwitch", "ringStaleRead", "overlapCompletion", "afterPlayer", "afterStats", "afterPet")
                                .filter(stage -> pets || !stage.equals("afterPet"))
                                .map(stage -> org.junit.jupiter.params.provider.Arguments.of(mode, pets, stage))));
    }

    private static MatchplayHandleable handler(MatchplayGame game) {
        if (game instanceof MatchplayBasicGame basic) return new MatchplayBasicModeHandler(basic);
        if (game instanceof MatchplayBattleGame battle) return new MatchplayBattleModeHandler(battle);
        return new MatchplayGuardianModeHandler((MatchplayGuardianGame) game);
    }

    private static MatchplayGame completedGame(String mode) {
        MatchplayGame game;
        if (mode.equals("basic")) {
            MatchplayBasicGame basic = mock(MatchplayBasicGame.class);
            when(basic.getSetsRedTeam()).thenReturn(new AtomicInteger(2));
            when(basic.getSetsBlueTeam()).thenReturn(new AtomicInteger());
            game = basic;
        } else if (mode.equals("battle")) {
            MatchplayBattleGame battle = mock(MatchplayBattleGame.class);
            var red = new com.jftse.server.core.matchplay.battle.PlayerBattleState((short) 0, 1, 100, 0, 0, 0, 0);
            var blue = new com.jftse.server.core.matchplay.battle.PlayerBattleState((short) 1, 2, 100, 0, 0, 0, 0);
            blue.getCurrentHealth().set(0);
            when(battle.getPlayerBattleStates()).thenReturn(new ConcurrentLinkedDeque<>(List.of(red, blue)));
            when(battle.isTeamDead(true)).thenReturn(false);
            when(battle.isTeamDead(false)).thenReturn(true);
            game = battle;
        } else {
            MatchplayGuardianGame guardian = mock(MatchplayGuardianGame.class);
            when(guardian.getBossBattleActive()).thenReturn(new AtomicBoolean());
            when(guardian.getIsHardMode()).thenReturn(new AtomicBoolean());
            when(guardian.getIsRandomGuardiansMode()).thenReturn(new AtomicBoolean());
            when(guardian.getPlayerBattleStates()).thenReturn(new ConcurrentLinkedDeque<>(List.of(
                    new com.jftse.server.core.matchplay.battle.PlayerBattleState((short) 0, 1, 100, 0, 0, 0, 0),
                    new com.jftse.server.core.matchplay.battle.PlayerBattleState((short) 1, 2, 100, 0, 0, 0, 0))));
            var definition = new com.jftse.entities.database.model.battle.Guardian();
            definition.setId(1L);
            definition.setBtItemID(1);
            var dead = new com.jftse.server.core.matchplay.battle.GuardianBattleState(definition, (short) 4, 100, 0, 0, 0, 0, 20, 50, 10);
            dead.getCurrentHealth().set(0);
            dead.getLooted().set(true);
            when(guardian.getGuardianBattleStates()).thenReturn(new ConcurrentLinkedDeque<>(List.of(dead)));
            var map = new com.jftse.entities.database.model.map.SMaps();
            map.setName("audit");
            when(guardian.getMap()).thenReturn(map);
            game = guardian;
        }
        when(game.getFinished()).thenReturn(new AtomicBoolean(mode.equals("basic")));
        when(game.isRedTeam((short) 0)).thenReturn(true);
        return game;
    }
}
