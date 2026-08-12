CREATE TABLE IF NOT EXISTS `TournamentDefinition` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `created` DATETIME(6) DEFAULT NULL,
    `modified` DATETIME(6) DEFAULT NULL,
    `title` VARCHAR(100) NOT NULL,
    `entryType` TINYINT NOT NULL,
    `gameMode` TINYINT NOT NULL,
    `status` TINYINT NOT NULL,
    `capacity` INT NOT NULL,
    `finalSize` INT NOT NULL,
    `rewardProductIndex` INT NOT NULL,
    `rewardQuantity` INT NOT NULL,
    `applicationStart` DATETIME(6) NOT NULL,
    `applicationEnd` DATETIME(6) NOT NULL,
    `qualifyingStart` DATETIME(6) NOT NULL,
    `finalStart` DATETIME(6) NOT NULL,
    `tournamentEnd` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_tournament_title` UNIQUE (`title`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `TournamentEnrollment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `created` DATETIME(6) DEFAULT NULL,
    `modified` DATETIME(6) DEFAULT NULL,
    `tournamentId` BIGINT NOT NULL,
    `playerId` BIGINT NOT NULL,
    `playerName` VARCHAR(30) NOT NULL,
    `seed` INT NOT NULL,
    `state` TINYINT NOT NULL,
    `qualifiedAt` DATETIME(6) DEFAULT NULL,
    `eliminatedAt` DATETIME(6) DEFAULT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_tournament_enrollment_player` UNIQUE (`tournamentId`, `playerId`),
    CONSTRAINT `uk_tournament_enrollment_seed` UNIQUE (`tournamentId`, `seed`),
    CONSTRAINT `fk_tournament_enrollment_tournament` FOREIGN KEY (`tournamentId`) REFERENCES `TournamentDefinition` (`id`) ON DELETE CASCADE,
    INDEX `idx_tournament_enrollment_seed` (`tournamentId`, `seed`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `TournamentMatch` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `created` DATETIME(6) DEFAULT NULL,
    `modified` DATETIME(6) DEFAULT NULL,
    `tournamentId` BIGINT NOT NULL,
    `stage` TINYINT NOT NULL,
    `roundNumber` INT NOT NULL,
    `slotNumber` INT NOT NULL,
    `playerOneId` BIGINT DEFAULT NULL,
    `playerTwoId` BIGINT DEFAULT NULL,
    `winnerPlayerId` BIGINT DEFAULT NULL,
    `status` TINYINT NOT NULL,
    `roomId` SMALLINT DEFAULT NULL,
    `gameSessionId` INT DEFAULT NULL,
    `startedAt` DATETIME(6) DEFAULT NULL,
    `completedAt` DATETIME(6) DEFAULT NULL,
    `version` BIGINT DEFAULT 0 NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_tournament_match_slot` UNIQUE (`tournamentId`, `stage`, `roundNumber`, `slotNumber`),
    CONSTRAINT `uk_tournament_match_room` UNIQUE (`roomId`),
    CONSTRAINT `uk_tournament_match_session` UNIQUE (`gameSessionId`),
    CONSTRAINT `fk_tournament_match_tournament` FOREIGN KEY (`tournamentId`) REFERENCES `TournamentDefinition` (`id`) ON DELETE CASCADE,
    INDEX `idx_tournament_match_player_one` (`tournamentId`, `playerOneId`, `status`),
    INDEX `idx_tournament_match_player_two` (`tournamentId`, `playerTwoId`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `TournamentSettlement` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `created` DATETIME(6) DEFAULT NULL,
    `modified` DATETIME(6) DEFAULT NULL,
    `tournamentId` BIGINT NOT NULL,
    `playerId` BIGINT NOT NULL,
    `placeNumber` INT NOT NULL,
    `productIndex` INT NOT NULL,
    `quantity` INT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_tournament_settlement_prize` UNIQUE (`tournamentId`, `playerId`, `placeNumber`, `productIndex`),
    CONSTRAINT `fk_tournament_settlement_tournament` FOREIGN KEY (`tournamentId`) REFERENCES `TournamentDefinition` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
