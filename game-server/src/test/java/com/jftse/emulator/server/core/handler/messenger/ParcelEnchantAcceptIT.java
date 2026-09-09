package com.jftse.emulator.server.core.handler.messenger;

import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.rabbit.service.RProducerService;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.ServerType;
import com.jftse.entities.database.model.account.Account;
import com.jftse.entities.database.model.player.CardSlotEquipment;
import com.jftse.entities.database.model.player.ClothEquipment;
import com.jftse.entities.database.model.player.Player;
import com.jftse.entities.database.model.player.PlayerStatistic;
import com.jftse.entities.database.model.player.QuickSlotEquipment;
import com.jftse.entities.database.model.player.SpecialSlotEquipment;
import com.jftse.entities.database.model.player.ToolSlotEquipment;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.entities.database.model.pocket.Pocket;
import com.jftse.entities.database.repository.account.AccountRepository;
import com.jftse.entities.database.repository.item.ItemEnchantRepository;
import com.jftse.entities.database.repository.item.ItemMaterialRepository;
import com.jftse.entities.database.repository.item.ProductRepository;
import com.jftse.entities.database.repository.messenger.ParcelRepository;
import com.jftse.entities.database.repository.player.PlayerRepository;
import com.jftse.entities.database.repository.pocket.PlayerPocketRepository;
import com.jftse.entities.database.repository.pocket.PocketRepository;
import com.jftse.server.core.jdbc.JdbcUtil;
import com.jftse.server.core.protocol.IPacket;
import com.jftse.server.core.service.GameLogService;
import com.jftse.server.core.service.ParcelService;
import com.jftse.server.core.service.PlayerPocketService;
import com.jftse.server.core.service.PlayerService;
import com.jftse.server.core.service.PocketService;
import com.jftse.server.core.service.ProductService;
import com.jftse.server.core.service.impl.ParcelServiceImpl;
import com.jftse.server.core.service.impl.PlayerPocketServiceImpl;
import com.jftse.server.core.service.impl.PlayerServiceImpl;
import com.jftse.server.core.service.impl.PocketServiceImpl;
import com.jftse.server.core.shared.MetricsService;
import com.jftse.server.core.shared.PlayerLoadType;
import com.jftse.server.core.shared.packets.messenger.CMSGAcceptParcel;
import com.jftse.server.core.shared.packets.messenger.CMSGSendParcel;
import io.netty.channel.ChannelFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@SpringBootTest(
        classes = ParcelEnchantAcceptIT.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:parcelit;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl",
        "spring.jpa.properties.hibernate.enable_lazy_load_no_trans=true",
        "spring.jpa.properties.org.hibernate.envers.store_data_at_delete=true",
        "spring.main.web-application-type=none",
        "spring.main.banner-mode=off",
        "grpc.server.port=-1"
})
class ParcelEnchantAcceptIT {
    private static final int LUCY_STARTER_RACKET = 61;
    private static final int WIND = 6;
    private static final String PARTS = "PARTS";
    private static final String USE_NA = "N/A";
    private static final String SENDER_NAME = "WindSender";
    private static final String RECEIVER_NAME = "WindRecv";

    @PersistenceContext
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private PlayerPocketRepository playerPocketRepository;
    @Autowired
    private ParcelRepository parcelRepository;
    @Autowired
    private PlayerService playerService;
    @Autowired
    private PlayerPocketService playerPocketService;
    @Autowired
    private ParcelService parcelService;
    @Autowired
    private ProductService productService;
    @Autowired
    private GameLogService gameLogService;
    @Autowired
    private JdbcUtil jdbcUtil;

    private TransactionTemplate tx;
    private long senderId;
    private long receiverId;
    private long senderPocketId;
    private long receiverPocketId;
    private long senderRacketId;

    @BeforeEach
    void seedSameAccountLucyPair() throws Exception {
        tx = new TransactionTemplate(transactionManager);
        installServiceManager();
        installNoopRabbit();

        tx.executeWithoutResult(status -> {
            Account account = new Account();
            account.setUsername("lucy-pair");
            account.setPassword("x");
            account.setStatus(0);
            account.setGameMaster(false);
            account.setAp(0);
            entityManager.persist(account);

            Player sender = persistPlayer(account, SENDER_NAME, 5000);
            Player receiver = persistPlayer(account, RECEIVER_NAME, 5000);
            senderId = sender.getId();
            receiverId = receiver.getId();
            senderPocketId = sender.getPocket().getId();
            receiverPocketId = receiver.getPocket().getId();

            PlayerPocket vanilla = racket(receiver.getPocket(), 0, 0);
            entityManager.persist(vanilla);

            PlayerPocket wind = racket(sender.getPocket(), WIND, 1);
            entityManager.persist(wind);
            senderRacketId = wind.getId();
        });
    }

    @Test
    void acceptKeepsWindEnchantOffTheVanillaRow() {
        List<Row> beforeReceiver = pocketRows(receiverPocketId);
        List<Row> beforeSender = pocketRows(senderPocketId);
        System.out.println("POCKET BEFORE receiver:");
        beforeReceiver.forEach(System.out::println);
        System.out.println("POCKET BEFORE sender:");
        beforeSender.forEach(System.out::println);

        FTConnection senderConn = bind(senderId);
        new SendParcelRequestHandler().handle(senderConn, CMSGSendParcel.builder()
                .receiverName(RECEIVER_NAME)
                .message("wind plus one")
                .playerPocketId((int) senderRacketId)
                .unk0((byte) 0)
                .cashOnDelivery(0)
                .build());

        Long parcelId = tx.execute(status -> parcelRepository.findAll().stream()
                .findFirst()
                .map(p -> p.getId())
                .orElse(null));
        assertTrue(parcelId != null, "send must persist a parcel");

        List<Row> midReceiver = pocketRows(receiverPocketId);
        List<Row> midSender = pocketRows(senderPocketId);
        System.out.println("POCKET AFTER SEND receiver:");
        midReceiver.forEach(System.out::println);
        System.out.println("POCKET AFTER SEND sender:");
        midSender.forEach(System.out::println);

        FTConnection receiverConn = bind(receiverId);
        new AcceptParcelRequestHandler().handle(receiverConn, CMSGAcceptParcel.builder()
                .parcelId(parcelId.intValue())
                .build());

        List<Row> afterReceiver = pocketRows(receiverPocketId);
        List<Row> afterSender = pocketRows(senderPocketId);
        System.out.println("POCKET AFTER ACCEPT receiver:");
        afterReceiver.forEach(System.out::println);
        System.out.println("POCKET AFTER ACCEPT sender:");
        afterSender.forEach(System.out::println);

        assertTrue(afterReceiver.stream().anyMatch(r ->
                        r.itemIndex == LUCY_STARTER_RACKET && r.element == WIND && r.level == 1 && r.count == 1),
                "enchanted Wind +1 row must still exist after accept");
        assertTrue(afterReceiver.stream().anyMatch(r ->
                        r.itemIndex == LUCY_STARTER_RACKET && r.element == 0 && r.level == 0 && r.count == 1),
                "existing vanilla 61 must stay unenchanted and not eat the sent count");
        assertEquals(2, afterReceiver.stream().filter(r -> r.itemIndex == LUCY_STARTER_RACKET).count());
        assertTrue(afterSender.stream().noneMatch(r -> r.itemIndex == LUCY_STARTER_RACKET));
        System.out.println("RESULT: PASS");
    }

    private Player persistPlayer(Account account, String name, int gold) {
        Pocket pocket = new Pocket();
        entityManager.persist(pocket);

        PlayerStatistic statistic = new PlayerStatistic();
        entityManager.persist(statistic);
        ClothEquipment cloth = new ClothEquipment();
        entityManager.persist(cloth);
        QuickSlotEquipment quick = new QuickSlotEquipment();
        entityManager.persist(quick);
        ToolSlotEquipment tool = new ToolSlotEquipment();
        entityManager.persist(tool);
        SpecialSlotEquipment special = new SpecialSlotEquipment();
        entityManager.persist(special);
        CardSlotEquipment card = new CardSlotEquipment();
        entityManager.persist(card);

        Player player = new Player();
        player.setAccount(account);
        player.setPocket(pocket);
        player.setPlayerStatistic(statistic);
        player.setClothEquipment(cloth);
        player.setQuickSlotEquipment(quick);
        player.setToolSlotEquipment(tool);
        player.setSpecialSlotEquipment(special);
        player.setCardSlotEquipment(card);
        player.setName(name);
        player.setLevel((byte) 20);
        player.setGold(gold);
        player.setAlreadyCreated(true);
        player.setPlayerType((byte) 0);
        entityManager.persist(player);
        return player;
    }

    private static PlayerPocket racket(Pocket pocket, int element, int level) {
        PlayerPocket item = new PlayerPocket();
        item.setPocket(pocket);
        item.setCategory(PARTS);
        item.setItemIndex(LUCY_STARTER_RACKET);
        item.setUseType(USE_NA);
        item.setItemCount(1);
        item.setEnchantStr(0);
        item.setEnchantSta(0);
        item.setEnchantDex(0);
        item.setEnchantWil(0);
        item.setEnchantElement(element);
        item.setEnchantLevel(level);
        return item;
    }

    private FTConnection bind(long playerId) {
        Player player = playerService.findByIdFetched(playerId);
        QuietConnection connection = new QuietConnection();
        FTClient client = new FTClient();
        connection.setClient(client);
        client.setConnection(connection);
        client.loadPlayer(player.getAccount(), player, PlayerLoadType.BASIC);
        return connection;
    }

    private List<Row> pocketRows(long pocketId) {
        return tx.execute(status -> playerPocketRepository.findAll().stream()
                .filter(item -> item.getPocket().getId().equals(pocketId))
                .sorted(Comparator.comparing(PlayerPocket::getId))
                .map(Row::from)
                .toList());
    }

    private void installServiceManager() throws Exception {
        ServiceManager serviceManager = new ServiceManager();
        setField(serviceManager, "playerService", playerService);
        setField(serviceManager, "playerPocketService", playerPocketService);
        setField(serviceManager, "parcelService", parcelService);
        setField(serviceManager, "productService", productService);
        setField(serviceManager, "gameLogService", gameLogService);
        setField(serviceManager, "jdbcUtil", jdbcUtil);
        setField(serviceManager, "metricsService", mock(MetricsService.class));
        serviceManager.init();
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void installNoopRabbit() throws Exception {
        RProducerService noop = mock(RProducerService.class);
        Field instance = RProducerService.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, noop);
    }

    private static final class QuietConnection extends FTConnection {
        QuietConnection() {
            super(0, 0, ServerType.GAME_SERVER);
        }

        @Override
        public ChannelFuture sendTCP(IPacket... packets) {
            return null;
        }
    }

    private record Row(long id, int itemIndex, String useType, int count, int element, int level) {
        static Row from(PlayerPocket item) {
            return new Row(
                    item.getId(),
                    item.getItemIndex(),
                    item.getUseType(),
                    item.getItemCount(),
                    item.getEnchantElement(),
                    item.getEnchantLevel()
            );
        }

        @Override
        public String toString() {
            return "  id=" + id
                    + " itemIndex=" + itemIndex
                    + " useType=" + useType
                    + " count=" + count
                    + " element=" + element
                    + " level=" + level;
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = RabbitAutoConfiguration.class)
    @EntityScan(basePackages = {
            "com.jftse.entities.database.model.account",
            "com.jftse.entities.database.model.player",
            "com.jftse.entities.database.model.pocket",
            "com.jftse.entities.database.model.messenger",
            "com.jftse.entities.database.model.challenge",
            "com.jftse.entities.database.model.tutorial",
            "com.jftse.entities.database.model.pet",
            "com.jftse.entities.database.model.item"
    })
    @EnableJpaRepositories(basePackageClasses = {
            AccountRepository.class,
            PlayerRepository.class,
            PlayerPocketRepository.class,
            PocketRepository.class,
            ParcelRepository.class
    })
    @EnableTransactionManagement
    public static class TestApp {
        @Bean
        JdbcUtil jdbcUtil() {
            return new JdbcUtil();
        }

        @Bean
        PocketService pocketService(PocketRepository pocketRepository) {
            return new PocketServiceImpl(pocketRepository);
        }

        @Bean
        PlayerPocketService playerPocketService(
                PlayerPocketRepository playerPocketRepository,
                PocketService pocketService,
                JdbcUtil jdbcUtil
        ) {
            return new PlayerPocketServiceImpl(
                    mock(ItemMaterialRepository.class),
                    mock(ItemEnchantRepository.class),
                    mock(ProductRepository.class),
                    playerPocketRepository,
                    pocketService,
                    jdbcUtil
            );
        }

        @Bean
        PlayerService playerService(PlayerRepository playerRepository) {
            return new PlayerServiceImpl(playerRepository);
        }

        @Bean
        ParcelService parcelService(ParcelRepository parcelRepository) {
            return new ParcelServiceImpl(parcelRepository);
        }

        @Bean
        ProductService productService() {
            return mock(ProductService.class);
        }

        @Bean
        GameLogService gameLogService() {
            return mock(GameLogService.class);
        }
    }
}
