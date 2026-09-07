-- Persist displayed Battlemon levels 1..250 from client LevelExp_Pet.xml.
-- TINYINT signed wraps at 127; emblem BattleMonLevel 10/30/50 and the
-- 250-row client table require values above that. Safe to run repeatedly.
SET @schema_name = DATABASE();

SET @statement = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @schema_name
          AND TABLE_NAME = 'Pet'
          AND COLUMN_NAME = 'level'
          AND COLUMN_TYPE LIKE '%unsigned%'
    ),
    'SELECT 1',
    'ALTER TABLE `Pet` MODIFY COLUMN `level` TINYINT UNSIGNED NULL'
);
PREPARE pet_level_unsigned FROM @statement;
EXECUTE pet_level_unsigned;
DEALLOCATE PREPARE pet_level_unsigned;

SET @statement = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = @schema_name
          AND TABLE_NAME = 'Pet_AUD'
    ) AND EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @schema_name
          AND TABLE_NAME = 'Pet_AUD'
          AND COLUMN_NAME = 'level'
          AND COLUMN_TYPE NOT LIKE '%unsigned%'
    ),
    'ALTER TABLE `Pet_AUD` MODIFY COLUMN `level` TINYINT UNSIGNED NULL',
    'SELECT 1'
);
PREPARE pet_aud_level_unsigned FROM @statement;
EXECUTE pet_aud_level_unsigned;
DEALLOCATE PREPARE pet_aud_level_unsigned;
