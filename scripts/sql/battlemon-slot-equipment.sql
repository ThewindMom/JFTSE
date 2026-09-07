-- Adds persistent Magic Pocket equipment for both new and existing players.
-- Run after the base JFTSE schema has been created. Safe to run repeatedly.

SET @schema_name = DATABASE();
SET @migration_lock_name = CONCAT(@schema_name, ':battlemon-slot-equipment');
SELECT GET_LOCK(@migration_lock_name, 300) INTO @migration_lock_acquired;

CREATE TABLE IF NOT EXISTS `BattlemonSlotEquipment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `created` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    `modified` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    `slot1` INT DEFAULT 0,
    `slot2` INT DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- Keep the Envers schema complete when auditing is enabled by a server.
CREATE TABLE IF NOT EXISTS `BattlemonSlotEquipment_AUD` (
    `id` BIGINT NOT NULL,
    `REV` INT NOT NULL,
    `REVTYPE` TINYINT DEFAULT NULL,
    `created` TIMESTAMP NULL DEFAULT NULL,
    `modified` TIMESTAMP NULL DEFAULT NULL,
    `slot1` INT DEFAULT NULL,
    `slot2` INT DEFAULT NULL,
    PRIMARY KEY (`id`, `REV`),
    KEY `IDX_BattlemonSlotEquipment_AUD_REV` (`REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

SET @statement = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @schema_name
          AND TABLE_NAME = 'Player'
          AND COLUMN_NAME = 'battlemonSlotEquipment_id'
    ),
    'SELECT 1',
    'ALTER TABLE `Player` ADD COLUMN `battlemonSlotEquipment_id` BIGINT NULL'
);
PREPARE battlemon_migration FROM @statement;
EXECUTE battlemon_migration;
DEALLOCATE PREPARE battlemon_migration;

-- Give every existing player a distinct canonical (0, 0) equipment row.
START TRANSACTION;
DROP TEMPORARY TABLE IF EXISTS `_BattlemonSlotEquipmentBackfill`;
CREATE TEMPORARY TABLE `_BattlemonSlotEquipmentBackfill` (
    `player_id` BIGINT NOT NULL,
    `equipment_id` BIGINT NOT NULL,
    PRIMARY KEY (`player_id`),
    UNIQUE KEY `UK_BattlemonSlotEquipmentBackfill_equipment` (`equipment_id`)
);
SET @battlemon_equipment_id_base = (
    SELECT COALESCE(MAX(`id`), 0) FROM `BattlemonSlotEquipment`
);
INSERT INTO `_BattlemonSlotEquipmentBackfill` (`player_id`, `equipment_id`)
SELECT `id`, @battlemon_equipment_id_base + ROW_NUMBER() OVER (ORDER BY `id`)
FROM `Player`
WHERE `battlemonSlotEquipment_id` IS NULL;
INSERT INTO `BattlemonSlotEquipment` (`id`, `created`, `modified`, `slot1`, `slot2`)
SELECT `equipment_id`, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0
FROM `_BattlemonSlotEquipmentBackfill`;
UPDATE `Player` AS player
INNER JOIN `_BattlemonSlotEquipmentBackfill` AS backfill ON backfill.`player_id` = player.`id`
SET player.`battlemonSlotEquipment_id` = backfill.`equipment_id`
WHERE player.`battlemonSlotEquipment_id` IS NULL;
DROP TEMPORARY TABLE `_BattlemonSlotEquipmentBackfill`;
COMMIT;

SET @statement = IF(
    EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = @schema_name
          AND TABLE_NAME = 'Player'
          AND COLUMN_NAME = 'battlemonSlotEquipment_id'
          AND NON_UNIQUE = 0
    ),
    'SELECT 1',
    'ALTER TABLE `Player` ADD UNIQUE INDEX `UK_Player_battlemonSlotEquipment` (`battlemonSlotEquipment_id`)'
);
PREPARE battlemon_migration FROM @statement;
EXECUTE battlemon_migration;
DEALLOCATE PREPARE battlemon_migration;

SET @statement = IF(
    EXISTS(
        SELECT 1 FROM information_schema.KEY_COLUMN_USAGE
        WHERE TABLE_SCHEMA = @schema_name
          AND TABLE_NAME = 'Player'
          AND COLUMN_NAME = 'battlemonSlotEquipment_id'
          AND REFERENCED_TABLE_NAME = 'BattlemonSlotEquipment'
    ),
    'SELECT 1',
    'ALTER TABLE `Player` ADD CONSTRAINT `FK_Player_battlemonSlotEquipment` FOREIGN KEY (`battlemonSlotEquipment_id`) REFERENCES `BattlemonSlotEquipment` (`id`)'
);
PREPARE battlemon_migration FROM @statement;
EXECUTE battlemon_migration;
DEALLOCATE PREPARE battlemon_migration;

SET @statement = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'Player_AUD'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @schema_name
          AND TABLE_NAME = 'Player_AUD'
          AND COLUMN_NAME = 'battlemonSlotEquipment_id'
    ),
    'ALTER TABLE `Player_AUD` ADD COLUMN `battlemonSlotEquipment_id` BIGINT NULL',
    'SELECT 1'
);
PREPARE battlemon_migration FROM @statement;
EXECUTE battlemon_migration;
DEALLOCATE PREPARE battlemon_migration;

SET @statement = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'REVINFO'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.KEY_COLUMN_USAGE
        WHERE TABLE_SCHEMA = @schema_name
          AND TABLE_NAME = 'BattlemonSlotEquipment_AUD'
          AND COLUMN_NAME = 'REV'
          AND REFERENCED_TABLE_NAME = 'REVINFO'
    ),
    'ALTER TABLE `BattlemonSlotEquipment_AUD` ADD CONSTRAINT `FK_BattlemonSlotEquipment_AUD_REV` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`)',
    'SELECT 1'
);
PREPARE battlemon_migration FROM @statement;
EXECUTE battlemon_migration;
DEALLOCATE PREPARE battlemon_migration;

SELECT RELEASE_LOCK(@migration_lock_name) INTO @migration_lock_released;
