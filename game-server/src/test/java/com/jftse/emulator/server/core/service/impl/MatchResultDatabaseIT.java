package com.jftse.emulator.server.core.service.impl;

import com.jftse.emulator.server.core.service.MatchResultService;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MatchResultDatabaseIT {
    @Test
    void realSerializableClaimsCommitOnceAndRollbackWithCompletion() throws Exception {
        String url = System.getenv("JFTSE_AUDIT_JDBC_URL");
        assertNotNull(url, "Run this integration test explicitly against the isolated audit database");
        assertTrue(url.matches("jdbc:mysql://[^/]+/jftse_server_audit_tx(?:\\?.*)?"));
        String user = System.getenv("JFTSE_AUDIT_JDBC_USER");
        String password = System.getenv("JFTSE_AUDIT_JDBC_PASSWORD");
        try (var database = DriverManager.getConnection(url, user, password)) {
            database.createStatement().execute("CREATE TABLE IF NOT EXISTS MatchResult (resultId VARCHAR(36) PRIMARY KEY, claimToken VARCHAR(36) NOT NULL, created DATETIME(6) NOT NULL) ENGINE=InnoDB");
            database.createStatement().execute("CREATE TABLE IF NOT EXISTS AuditCompletion (resultId VARCHAR(36) PRIMARY KEY, amount INT NOT NULL) ENGINE=InnoDB");
            Configuration configuration = new Configuration()
                    .setProperty("hibernate.connection.url", url)
                    .setProperty("hibernate.connection.username", user)
                    .setProperty("hibernate.connection.password", password)
                    .setProperty("hibernate.dialect", "org.hibernate.dialect.MySQL8Dialect")
                    .setProperty("hibernate.connection.handling_mode", "DELAYED_ACQUISITION_AND_HOLD")
                    .setProperty("hibernate.connection.pool_size", "4");
            try (var factory = configuration.buildSessionFactory()) {
                var entityManager = SharedEntityManagerCreator.createSharedEntityManager(factory);
                var implementation = new MatchResultServiceImpl();
                ReflectionTestUtils.setField(implementation, "entityManager", entityManager);
                var transactions = new JpaTransactionManager(factory);
                transactions.setJpaDialect(new HibernateJpaDialect());
                ProxyFactory proxy = new ProxyFactory(implementation);
                proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
                MatchResultService service = (MatchResultService) proxy.getProxy();
                String resultId = UUID.randomUUID().toString();
                String failedId = UUID.randomUUID().toString();
                AtomicInteger completions = new AtomicInteger();
                CountDownLatch entered = new CountDownLatch(1);
                CountDownLatch release = new CountDownLatch(1);
                try (var executor = Executors.newFixedThreadPool(2)) {
                    var first = executor.submit(() -> service.executeOnce(resultId, () -> {
                        entityManager.createNativeQuery("INSERT INTO AuditCompletion VALUES (:id, 50)")
                                .setParameter("id", resultId).executeUpdate();
                        completions.incrementAndGet();
                        entered.countDown();
                        try {
                            assertTrue(release.await(5, TimeUnit.SECONDS));
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(exception);
                        }
                    }));
                    try {
                        if (!entered.await(3, TimeUnit.SECONDS)) {
                            first.get(1, TimeUnit.SECONDS);
                            fail("Transaction did not reach completion callback");
                        }
                        var duplicate = executor.submit(() -> service.executeOnce(resultId, completions::incrementAndGet));
                        assertThrows(TimeoutException.class, () -> duplicate.get(150, TimeUnit.MILLISECONDS));
                        release.countDown();
                        assertTrue(first.get(3, TimeUnit.SECONDS));
                        assertFalse(duplicate.get(3, TimeUnit.SECONDS));
                        assertEquals(1, completions.get());
                    } finally {
                        release.countDown();
                    }
                    assertThrows(IllegalStateException.class, () -> service.executeOnce(failedId, () -> {
                        entityManager.createNativeQuery("INSERT INTO AuditCompletion VALUES (:id, 70)")
                                .setParameter("id", failedId).executeUpdate();
                        throw new IllegalStateException("Rollback both claim and reward");
                    }));
                    try (var statement = database.prepareStatement("SELECT COUNT(*) FROM MatchResult WHERE resultId=?")) {
                        statement.setString(1, failedId);
                        try (var rows = statement.executeQuery()) {
                            assertTrue(rows.next());
                            assertEquals(0, rows.getInt(1));
                        }
                    }
                    assertTrue(service.executeOnce(failedId, () -> entityManager
                            .createNativeQuery("INSERT INTO AuditCompletion VALUES (:id, 70)")
                            .setParameter("id", failedId).executeUpdate()));
                    try (var statement = database.prepareStatement("SELECT SUM(amount) FROM AuditCompletion WHERE resultId IN (?,?)")) {
                        statement.setString(1, resultId);
                        statement.setString(2, failedId);
                        try (var rows = statement.executeQuery()) {
                            assertTrue(rows.next());
                            assertEquals(120, rows.getInt(1));
                        }
                    }
                } finally {
                    for (String table : new String[]{"AuditCompletion", "MatchResult"}) {
                        try (var statement = database.prepareStatement("DELETE FROM " + table + " WHERE resultId IN (?,?)")) {
                            statement.setString(1, resultId);
                            statement.setString(2, failedId);
                            statement.executeUpdate();
                        }
                    }
                }
            }
        }
    }
}
