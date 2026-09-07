SET @ddl = IF(
    EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'Guild'
          AND COLUMN_NAME = 'castleAccessLimit'
    ),
    'DO 0',
    'ALTER TABLE `Guild` ADD COLUMN `castleAccessLimit` TINYINT NOT NULL DEFAULT 2'
);
PREPARE statement FROM @ddl;
EXECUTE statement;
DEALLOCATE PREPARE statement;

UPDATE `Guild`
SET `castleAccessLimit` = 2
WHERE `castleAccessLimit` IS NULL;

ALTER TABLE `Guild`
    MODIFY COLUMN `castleAccessLimit` TINYINT NOT NULL DEFAULT 2;

SET @ddl = IF(
    EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'Guild'
          AND COLUMN_NAME = 'castleAdmissionFee'
    ),
    'DO 0',
    'ALTER TABLE `Guild` ADD COLUMN `castleAdmissionFee` INT NOT NULL DEFAULT 0'
);
PREPARE statement FROM @ddl;
EXECUTE statement;
DEALLOCATE PREPARE statement;

UPDATE `Guild`
SET `castleAdmissionFee` = 0
WHERE `castleAdmissionFee` IS NULL;

ALTER TABLE `Guild`
    MODIFY COLUMN `castleAdmissionFee` INT NOT NULL DEFAULT 0;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'Guild_AUD'
    ) OR EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'Guild_AUD'
          AND COLUMN_NAME = 'castleAccessLimit'
    ),
    'DO 0',
    'ALTER TABLE `Guild_AUD` ADD COLUMN `castleAccessLimit` TINYINT NULL'
);
PREPARE statement FROM @ddl;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'Guild_AUD'
    ) OR EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'Guild_AUD'
          AND COLUMN_NAME = 'castleAdmissionFee'
    ),
    'DO 0',
    'ALTER TABLE `Guild_AUD` ADD COLUMN `castleAdmissionFee` INT NULL'
);
PREPARE statement FROM @ddl;
EXECUTE statement;
DEALLOCATE PREPARE statement;
