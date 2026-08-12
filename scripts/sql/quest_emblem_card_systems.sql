-- Quest, Emblem, and Card systems schema migration.
--
-- Run against an existing JFTSE database:
--   mysql <connection options> fantasytennis < scripts/sql/quest_emblem_card_systems.sql
--
-- The migration is idempotent and can be run more than once. EmblemQuest.xml is
-- a seed source: `auth-server -import` inserts missing quest IDs but deliberately
-- preserves existing database definitions and rewards for external management.

DELIMITER //

DROP PROCEDURE IF EXISTS qec_add_column//
CREATE PROCEDURE qec_add_column(
    IN target_table VARCHAR(64),
    IN target_column VARCHAR(64),
    IN column_definition VARCHAR(255)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = target_table
          AND COLUMN_NAME = target_column
    ) THEN
        SET @qec_sql = CONCAT(
            'ALTER TABLE `', target_table, '` ADD COLUMN `', target_column, '` ',
            column_definition
        );
        PREPARE qec_statement FROM @qec_sql;
        EXECUTE qec_statement;
        DEALLOCATE PREPARE qec_statement;
    END IF;
END//

DELIMITER ;

CALL qec_add_column('PlayerStatistic', 'perfectGames', 'INT DEFAULT NULL');
CALL qec_add_column('PlayerStatistic', 'fishesCaught', 'INT DEFAULT NULL');
CALL qec_add_column('PlayerStatistic', 'fruitsCollected', 'INT DEFAULT NULL');
CALL qec_add_column('PlayerStatistic_AUD', 'perfectGames', 'INT DEFAULT NULL');
CALL qec_add_column('PlayerStatistic_AUD', 'fishesCaught', 'INT DEFAULT NULL');
CALL qec_add_column('PlayerStatistic_AUD', 'fruitsCollected', 'INT DEFAULT NULL');

DROP PROCEDURE qec_add_column;

CREATE TABLE IF NOT EXISTS ItemCard (
    id BIGINT NOT NULL AUTO_INCREMENT,
    abilityGrade INT DEFAULT NULL,
    abilityPower INT DEFAULT NULL,
    itemIndex INT NOT NULL,
    itemType VARCHAR(255) DEFAULT NULL,
    name VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_card_item_index (itemIndex)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS EmblemQuestDefinition (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conditionTarget1 VARCHAR(255) DEFAULT NULL,
    conditionTarget2 VARCHAR(255) DEFAULT NULL,
    conditionTarget3 VARCHAR(255) DEFAULT NULL,
    conditionTarget4 VARCHAR(255) DEFAULT NULL,
    conditionType1 VARCHAR(255) DEFAULT NULL,
    conditionType2 VARCHAR(255) DEFAULT NULL,
    conditionType3 VARCHAR(255) DEFAULT NULL,
    conditionType4 VARCHAR(255) DEFAULT NULL,
    emblemGrade INT DEFAULT NULL,
    enabled BIT(1) DEFAULT NULL,
    event INT DEFAULT NULL,
    gameMode VARCHAR(255) DEFAULT NULL,
    icon VARCHAR(255) DEFAULT NULL,
    itemRewardRepeat BIT(1) DEFAULT NULL,
    levelRestriction INT DEFAULT NULL,
    name VARCHAR(255) DEFAULT NULL,
    prerequisites VARCHAR(255) DEFAULT NULL,
    questIndex INT NOT NULL,
    questNameLabel VARCHAR(255) DEFAULT NULL,
    questRepeat BIT(1) DEFAULT NULL,
    requiredItem1 INT DEFAULT NULL,
    requiredItem2 INT DEFAULT NULL,
    requiredItem3 INT DEFAULT NULL,
    requiredItem4 INT DEFAULT NULL,
    requiredQuantity1 INT DEFAULT NULL,
    requiredQuantity2 INT DEFAULT NULL,
    requiredQuantity3 INT DEFAULT NULL,
    requiredQuantity4 INT DEFAULT NULL,
    rewardExp INT DEFAULT NULL,
    rewardGold INT DEFAULT NULL,
    successConditionLabel VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_emblem_quest_definition_index (questIndex)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS EmblemQuestReward (
    id BIGINT NOT NULL AUTO_INCREMENT,
    playerType TINYINT DEFAULT NULL,
    productIndex INT DEFAULT NULL,
    quantityMax INT DEFAULT NULL,
    quantityMin INT DEFAULT NULL,
    rewardSlot TINYINT DEFAULT NULL,
    definition_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_emblem_quest_reward (definition_id, playerType, rewardSlot),
    CONSTRAINT fk_emblem_quest_reward_definition
        FOREIGN KEY (definition_id) REFERENCES EmblemQuestDefinition (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS PlayerEmblemEquipment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    modified TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    slot1 SMALLINT DEFAULT NULL,
    slot2 SMALLINT DEFAULT NULL,
    slot3 SMALLINT DEFAULT NULL,
    slot4 SMALLINT DEFAULT NULL,
    player_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_player_emblem_equipment_player (player_id),
    CONSTRAINT fk_player_emblem_equipment_player
        FOREIGN KEY (player_id) REFERENCES Player (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS PlayerEmblemEquipment_AUD (
    id BIGINT NOT NULL,
    REV INT NOT NULL,
    REVTYPE TINYINT DEFAULT NULL,
    created TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    modified TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    slot1 SMALLINT DEFAULT NULL,
    slot2 SMALLINT DEFAULT NULL,
    slot3 SMALLINT DEFAULT NULL,
    slot4 SMALLINT DEFAULT NULL,
    player_id BIGINT DEFAULT NULL,
    PRIMARY KEY (id, REV),
    KEY idx_player_emblem_equipment_aud_rev (REV),
    CONSTRAINT fk_player_emblem_equipment_aud_rev
        FOREIGN KEY (REV) REFERENCES REVINFO (REV)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS PlayerEmblemQuest (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    modified TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    baseline1 INT DEFAULT NULL,
    baseline2 INT DEFAULT NULL,
    baseline3 INT DEFAULT NULL,
    baseline4 INT DEFAULT NULL,
    completionCount INT DEFAULT NULL,
    progress1 INT DEFAULT NULL,
    progress2 INT DEFAULT NULL,
    progress3 INT DEFAULT NULL,
    progress4 INT DEFAULT NULL,
    status VARCHAR(255) NOT NULL,
    definition_id BIGINT NOT NULL,
    player_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_player_emblem_quest (player_id, definition_id),
    KEY idx_player_emblem_quest_definition (definition_id),
    CONSTRAINT fk_player_emblem_quest_definition
        FOREIGN KEY (definition_id) REFERENCES EmblemQuestDefinition (id),
    CONSTRAINT fk_player_emblem_quest_player
        FOREIGN KEY (player_id) REFERENCES Player (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS PlayerEmblemQuest_AUD (
    id BIGINT NOT NULL,
    REV INT NOT NULL,
    REVTYPE TINYINT DEFAULT NULL,
    created TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    modified TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    baseline1 INT DEFAULT NULL,
    baseline2 INT DEFAULT NULL,
    baseline3 INT DEFAULT NULL,
    baseline4 INT DEFAULT NULL,
    completionCount INT DEFAULT NULL,
    progress1 INT DEFAULT NULL,
    progress2 INT DEFAULT NULL,
    progress3 INT DEFAULT NULL,
    progress4 INT DEFAULT NULL,
    status VARCHAR(255) DEFAULT NULL,
    player_id BIGINT DEFAULT NULL,
    PRIMARY KEY (id, REV),
    KEY idx_player_emblem_quest_aud_rev (REV),
    CONSTRAINT fk_player_emblem_quest_aud_rev
        FOREIGN KEY (REV) REFERENCES REVINFO (REV)
) ENGINE=InnoDB;
