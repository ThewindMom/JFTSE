package com.jftse.emulator.server.core.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GuardianSeedDatabaseIT {
    @Test
    void currentTrackedSeedsImportWithRealForeignKeysAndPreserveEveryAvailableSkillRelationship() throws Exception {
        String url = System.getenv("JFTSE_AUDIT_JDBC_URL");
        assertNotNull(url);
        assertTrue(url.matches("jdbc:mysql://[^/]+/jftse_server_audit_seed(?:\\?.*)?"));
        Path sql = Path.of("../scripts/sql");
        Map<Long, List<String>> available = new HashMap<>();
        Set<Long> disabled = new HashSet<>();
        for (String line : Files.readAllLines(sql.resolve("skill2guardians.sql"))) {
            if (!line.startsWith("INSERT INTO")) continue;
            String[] values = line.substring(line.indexOf("VALUES(") + 7, line.length() - 2).split(", ");
            long id = Long.parseLong(values[0]);
            if (!values[5].equals("NULL") && Long.parseLong(values[5]) >= 283 && Long.parseLong(values[5]) <= 291)
                disabled.add(id);
            else available.put(id, List.of(values[3], values[4], values[5], values[6], values[7]));
        }
        assertEquals(100, disabled.size(), "Only relationships of the nine disabled Snow Moon guardians");
        assertEquals(197, available.size());
        try (var connection = DriverManager.getConnection(url, System.getenv("JFTSE_AUDIT_JDBC_USER"),
                System.getenv("JFTSE_AUDIT_JDBC_PASSWORD"))) {
            connection.setAutoCommit(false);
            try {
                for (String table : List.of("K_Status", "M_Scenarios", "S_Maps", "Guardian_2_Maps", "Skill_2_Guardians")) {
                    try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                        assertTrue(rows.next());
                        assertEquals(0, rows.getInt(1), "Requires empty audit seed tables, not an existing game database");
                    }
                }
                try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT @@foreign_key_checks")) {
                    assertTrue(rows.next());
                    assertEquals(1, rows.getInt(1));
                }
                for (String file : List.of("status", "scenarios", "maps", "map2scenarios", "guardian2maps", "skill2guardians"))
                    ScriptUtils.executeSqlScript(connection, new FileSystemResource(sql.resolve(file + ".sql")));
                var configuration = new org.hibernate.cfg.Configuration()
                        .setProperty("hibernate.connection.url", url)
                        .setProperty("hibernate.connection.username", System.getenv("JFTSE_AUDIT_JDBC_USER"))
                        .setProperty("hibernate.connection.password", System.getenv("JFTSE_AUDIT_JDBC_PASSWORD"))
                        .setProperty("hibernate.dialect", "org.hibernate.dialect.MySQL8Dialect");
                var scanner = new org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider(false);
                scanner.addIncludeFilter(new org.springframework.core.type.filter.AnnotationTypeFilter(javax.persistence.Entity.class));
                for (var candidate : scanner.findCandidateComponents("com.jftse.entities.database.model"))
                    configuration.addAnnotatedClass(Class.forName(candidate.getBeanClassName()));
                try (var factory = configuration.buildSessionFactory();
                     var session = factory.withOptions().connection(connection).openSession()) {
                    var repository = new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(session)
                            .getRepository(com.jftse.entities.database.repository.map.MapsRepository.class);
                    var maps = new com.jftse.server.core.service.impl.MapServiceImpl(repository);
                    for (int nativeMap = -1; nativeMap <= 15; nativeMap++)
                        assertEquals(nativeMap >= 0 && nativeMap <= 14 && nativeMap != 4,
                                maps.isGuardianMapAvailable(nativeMap), "Native map" + nativeMap);
                }
                Map<Long, List<String>> actual = new HashMap<>();
                try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                        "SELECT id,btItemID,chance,guardian_2_maps_id,skill_id,status_id FROM Skill_2_Guardians")) {
                    while (rows.next()) actual.put(rows.getLong(1), List.of(
                            rows.getObject(2) == null ? "NULL" : rows.getString(2), rows.getString(3),
                            rows.getObject(4) == null ? "NULL" : rows.getString(4), rows.getString(5), rows.getString(6)));
                }
                assertEquals(available, actual, "Every non-disabled ID, chance, target, skill and status survives unchanged");
                try (var statement = connection.createStatement()) {
                    SQLException missingGuardian = assertThrows(SQLException.class, () -> statement.executeUpdate(
                            "INSERT INTO Skill_2_Guardians (guardian_2_maps_id,skill_id,status_id) VALUES (291,6,1)"));
                    assertEquals(1452, missingGuardian.getErrorCode());
                }
                try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                        "SELECT m.map_id,m.scenario_id,COUNT(g.id) FROM Map_2_Scenarios m LEFT JOIN Guardian_2_Maps g " +
                                "ON g.map_id=m.map_id AND g.scenario_id=m.scenario_id GROUP BY m.map_id,m.scenario_id")) {
                    int links = 0;
                    Set<String> unavailable = new HashSet<>();
                    while (rows.next()) {
                        links++;
                        if (rows.getInt(3) == 0) unavailable.add(rows.getInt(1) + "/" + rows.getInt(2));
                    }
                    assertEquals(25, links);
                    assertEquals(Set.of("3/2", "6/1", "6/2"), unavailable);
                }
            } finally {
                connection.rollback();
            }
        }
    }
}
