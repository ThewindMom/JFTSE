-- Adds the persistent cursor used by lazy Battlemon hunger and energy decay.
-- Existing pets intentionally start at their first authoritative access after
-- deployment instead of receiving retroactive decay. Safe to run repeatedly.
SET @schema_name = DATABASE();

SET @statement = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @schema_name
          AND TABLE_NAME = 'Pet'
          AND COLUMN_NAME = 'lifecycleUpdatedAt'
    ),
    'SELECT 1',
    'ALTER TABLE `Pet` ADD COLUMN `lifecycleUpdatedAt` DATETIME(6) NULL AFTER `alive`'
);
PREPARE pet_lifecycle FROM @statement;
EXECUTE pet_lifecycle;
DEALLOCATE PREPARE pet_lifecycle;

SET @statement = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = @schema_name
          AND TABLE_NAME = 'Pet_AUD'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @schema_name
          AND TABLE_NAME = 'Pet_AUD'
          AND COLUMN_NAME = 'lifecycleUpdatedAt'
    ),
    'ALTER TABLE `Pet_AUD` ADD COLUMN `lifecycleUpdatedAt` DATETIME(6) NULL AFTER `alive`',
    'SELECT 1'
);
PREPARE pet_aud_lifecycle FROM @statement;
EXECUTE pet_aud_lifecycle;
DEALLOCATE PREPARE pet_aud_lifecycle;
