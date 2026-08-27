package com.jftse.emulator.server.core.handler.messenger;

import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.rabbit.service.RProducerService;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.ServerType;
import com.jftse.entities.database.model.account.Account;
import com.jftse.entities.database.model.messenger.Parcel;
import com.jftse.entities.database.model.player.CardSlotEquipment;
import com.jftse.entities.database.model.player.ClothEquipment;
import com.jftse.entities.database.model.player.Player;
import com.jftse.entities.database.model.player.PlayerStatistic;
import com.jftse.entities.database.model.player.QuickSlotEquipment;
import com.jftse.entities.database.model.player.SpecialSlotEquipment;
import com.jftse.entities.database.model.player.ToolSlotEquipment;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.entities.database.model.pocket.Pocket;
import com.jftse.entities.database.repository.messenger.ParcelRepository;
import com.jftse.entities.database.repository.pocket.PlayerPocketRepository;
import com.jftse.server.core.item.EItemChar;
import com.jftse.server.core.jdbc.JdbcUtil;
import com.jftse.server.core.messenger.ParcelItemPlacement;
import com.jftse.server.core.protocol.IPacket;
import com.jftse.server.core.service.GameLogService;
import com.jftse.server.core.service.ParcelService;
import com.jftse.server.core.service.PlayerPocketService;
import com.jftse.server.core.service.PlayerService;
import com.jftse.server.core.service.ProductService;
import com.jftse.server.core.shared.MetricsService;
import com.jftse.server.core.shared.PlayerLoadType;
import com.jftse.server.core.shared.packets.messenger.CMSGAcceptParcel;
import com.jftse.server.core.shared.packets.messenger.CMSGSendParcel;
import io.netty.channel.ChannelFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@SpringBootTest(
        classes = ParcelEnchantAcceptIT.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:leo3lucy;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
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
class LeoThreeLucyParcelIT {
    private static final int FIRE_HATCHET_LUCY = 10378;
    private static final int FIRE_HATCHET_NIKI = 10375;
    private static final int WIND = 6;
    private static final String PARTS = "PARTS";
    private static final String USE_NA = "N/A";

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

    @BeforeEach
    void wireSingletons() throws Exception {
        tx = new TransactionTemplate(transactionManager);
        installServiceManager();
        installNoopRabbit();
    }

    @Test
    void onlyLucyCDropsWhenSheAlreadyOwnsTheSentIndex() {
        System.out.println("HYPOTHESIS: Lucy C already has Fire Hatchet Lucy itemIndex="
                + FIRE_HATCHET_LUCY + " in pocket. Lucy A and B do not. Cloth racket=0 on all.");
        System.out.println("CATALOG: Item_Parts Fire Hatchet Lucy EnableParcel=1 Level=60. Shark Sickle 61 is Level=1 EnableParcel=0.");

        World before = seed("old");
        System.out.println("BEFORE SEED A:");
        dump(before.aPocketId);
        System.out.println("BEFORE SEED B:");
        dump(before.bPocketId);
        System.out.println("BEFORE SEED C:");
        dump(before.cPocketId);
        System.out.println("BEFORE SEED NIKI:");
        dump(before.nikiPocketId);

        send(before.aId, before.aWindIds.get(0), before.bName);
        acceptOld(before.bId);
        List<Row> oldB = pocketRows(before.bPocketId);
        System.out.println("BEFORE A->B B pocket:");
        oldB.forEach(System.out::println);

        send(before.aId, before.aWindIds.get(1), before.cName);
        acceptOld(before.cId);
        List<Row> oldC = pocketRows(before.cPocketId);
        System.out.println("BEFORE A->C C pocket:");
        oldC.forEach(System.out::println);

        send(before.nikiId, before.nikiWindId, before.cName);
        acceptOld(before.cId);
        List<Row> oldCAfterNiki = pocketRows(before.cPocketId);
        System.out.println("BEFORE NIKI->C C pocket:");
        oldCAfterNiki.forEach(System.out::println);

        boolean oldABKeep = oldB.stream().anyMatch(r ->
                r.itemIndex == FIRE_HATCHET_LUCY && r.element == WIND && r.level == 1 && r.count == 1);
        boolean oldACLose = oldC.stream().anyMatch(r ->
                r.itemIndex == FIRE_HATCHET_LUCY && r.element == 0 && r.level == 0 && r.count == 2)
                && oldC.stream().noneMatch(r ->
                r.itemIndex == FIRE_HATCHET_LUCY && r.element == WIND && r.level == 1);
        boolean oldNikiKeep = oldCAfterNiki.stream().anyMatch(r ->
                r.itemIndex == FIRE_HATCHET_NIKI && r.element == WIND && r.level == 1 && r.count == 1);
        System.out.println("BEFORE A->B: " + (oldABKeep ? "PASS keep" : "FAIL"));
        System.out.println("BEFORE A->C: " + (oldACLose ? "PASS lose" : "FAIL"));
        System.out.println("BEFORE NIKI->C: " + (oldNikiKeep ? "PASS keep" : "FAIL"));
        assertTrue(oldABKeep, "old accept A->B must keep Wind +1");
        assertTrue(oldACLose, "old accept A->C must stack onto C's vanilla row and drop Wind +1");
        assertTrue(oldNikiKeep, "old accept Niki->C must keep Wind +1 on a different index");

        World after = seed("new");
        System.out.println("AFTER SEED A:");
        dump(after.aPocketId);
        System.out.println("AFTER SEED B:");
        dump(after.bPocketId);
        System.out.println("AFTER SEED C:");
        dump(after.cPocketId);
        System.out.println("AFTER SEED NIKI:");
        dump(after.nikiPocketId);

        send(after.aId, after.aWindIds.get(0), after.bName);
        acceptNew(after.bId);
        List<Row> newB = pocketRows(after.bPocketId);
        System.out.println("AFTER A->B B pocket:");
        newB.forEach(System.out::println);

        send(after.aId, after.aWindIds.get(1), after.cName);
        acceptNew(after.cId);
        List<Row> newC = pocketRows(after.cPocketId);
        System.out.println("AFTER A->C C pocket:");
        newC.forEach(System.out::println);

        send(after.nikiId, after.nikiWindId, after.cName);
        acceptNew(after.cId);
        List<Row> newCAfterNiki = pocketRows(after.cPocketId);
        System.out.println("AFTER NIKI->C C pocket:");
        newCAfterNiki.forEach(System.out::println);

        boolean newABKeep = newB.stream().anyMatch(r ->
                r.itemIndex == FIRE_HATCHET_LUCY && r.element == WIND && r.level == 1 && r.count == 1);
        boolean newACKeep = newC.stream().anyMatch(r ->
                r.itemIndex == FIRE_HATCHET_LUCY && r.element == WIND && r.level == 1 && r.count == 1)
                && newC.stream().anyMatch(r ->
                r.itemIndex == FIRE_HATCHET_LUCY && r.element == 0 && r.level == 0 && r.count == 1);
        boolean newNikiKeep = newCAfterNiki.stream().anyMatch(r ->
                r.itemIndex == FIRE_HATCHET_NIKI && r.element == WIND && r.level == 1 && r.count == 1);
        System.out.println("AFTER A->B: " + (newABKeep ? "PASS keep" : "FAIL"));
        System.out.println("AFTER A->C: " + (newACKeep ? "PASS keep" : "FAIL"));
        System.out.println("AFTER NIKI->C: " + (newNikiKeep ? "PASS keep" : "FAIL"));
        assertTrue(newABKeep, "fixed accept A->B must still keep Wind +1");
        assertTrue(newACKeep, "fixed accept A->C must keep Wind +1 off C's vanilla row");
        assertTrue(newNikiKeep, "fixed accept Niki->C must keep Wind +1");
        System.out.println("RESULT: PASS");
    }

    private World seed(String tag) {
        return tx.execute(status -> {
            Account account = new Account();
            account.setUsername("leo-" + tag);
            account.setPassword("x");
            account.setStatus(0);
            account.setGameMaster(false);
            account.setAp(0);
            entityManager.persist(account);

            Player a = persistPlayer(account, "LucyA" + tag, EItemChar.LUCY.getValue());
            Player b = persistPlayer(account, "LucyB" + tag, EItemChar.LUCY.getValue());
            Player c = persistPlayer(account, "LucyC" + tag, EItemChar.LUCY.getValue());
            Player niki = persistPlayer(account, "Niki" + tag, EItemChar.NIKI.getValue());

            PlayerPocket a1 = racket(a.getPocket(), FIRE_HATCHET_LUCY, WIND, 1);
            PlayerPocket a2 = racket(a.getPocket(), FIRE_HATCHET_LUCY, WIND, 1);
            entityManager.persist(a1);
            entityManager.persist(a2);
            entityManager.persist(racket(c.getPocket(), FIRE_HATCHET_LUCY, 0, 0));
            PlayerPocket nikiWind = racket(niki.getPocket(), FIRE_HATCHET_NIKI, WIND, 1);
            entityManager.persist(nikiWind);
            entityManager.flush();

            World world = new World();
            world.aId = a.getId();
            world.bId = b.getId();
            world.cId = c.getId();
            world.nikiId = niki.getId();
            world.aPocketId = a.getPocket().getId();
            world.bPocketId = b.getPocket().getId();
            world.cPocketId = c.getPocket().getId();
            world.nikiPocketId = niki.getPocket().getId();
            world.bName = b.getName();
            world.cName = c.getName();
            world.aWindIds = List.of(a1.getId(), a2.getId());
            world.nikiWindId = nikiWind.getId();
            return world;
        });
    }

    private void send(long senderId, long pocketItemId, String receiverName) {
        new SendParcelRequestHandler().handle(bind(senderId), CMSGSendParcel.builder()
                .receiverName(receiverName)
                .message("wind plus one")
                .playerPocketId((int) pocketItemId)
                .unk0((byte) 0)
                .cashOnDelivery(0)
                .build());
    }

    private void acceptNew(long receiverId) {
        Long parcelId = latestParcelId();
        new AcceptParcelRequestHandler().handle(bind(receiverId), CMSGAcceptParcel.builder()
                .parcelId(parcelId.intValue())
                .build());
    }

    private void acceptOld(long receiverId) {
        Long parcelId = latestParcelId();
        tx.executeWithoutResult(status -> {
            Parcel parcel = parcelService.findById(parcelId);
            Player receiver = playerService.findById(parcel.getReceiver().getId());
            PlayerPocket existing = playerPocketService.getItemAsPocketByItemIndexAndCategoryAndPocket(
                    parcel.getItemIndex(), parcel.getCategory(), receiver.getPocket());
            PlayerPocket item;
            if (existing != null) {
                existing.setItemCount(existing.getItemCount() + parcel.getItemCount());
                item = existing;
            } else {
                item = ParcelItemPlacement.toPocketItem(null, parcel, receiver.getPocket());
            }
            playerPocketService.save(item);
            parcelService.remove(parcel.getId());
        });
    }

    private Long latestParcelId() {
        Long parcelId = tx.execute(status -> parcelRepository.findAll().stream()
                .max(Comparator.comparing(Parcel::getId))
                .map(Parcel::getId)
                .orElse(null));
        if (parcelId == null) {
            throw new IllegalStateException("send did not persist a parcel");
        }
        return parcelId;
    }

    private Player persistPlayer(Account account, String name, byte playerType) {
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
        player.setLevel((byte) 60);
        player.setGold(5000);
        player.setAlreadyCreated(true);
        player.setPlayerType(playerType);
        entityManager.persist(player);
        return player;
    }

    private static PlayerPocket racket(Pocket pocket, int itemIndex, int element, int level) {
        PlayerPocket item = new PlayerPocket();
        item.setPocket(pocket);
        item.setCategory(PARTS);
        item.setItemIndex(itemIndex);
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

    private void dump(long pocketId) {
        pocketRows(pocketId).forEach(System.out::println);
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

    private static final class World {
        long aId;
        long bId;
        long cId;
        long nikiId;
        long aPocketId;
        long bPocketId;
        long cPocketId;
        long nikiPocketId;
        String bName;
        String cName;
        List<Long> aWindIds;
        long nikiWindId;
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
}
