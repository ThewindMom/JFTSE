CREATE TABLE IF NOT EXISTS MatchResult (
    resultId VARCHAR(36) NOT NULL,
    claimToken CHAR(36) NOT NULL,
    created DATETIME(6) NOT NULL,
    PRIMARY KEY (resultId)
) ENGINE=InnoDB;

SET @addClaimToken = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'MatchResult'
       AND COLUMN_NAME = 'claimToken') = 0,
    'ALTER TABLE MatchResult ADD COLUMN claimToken CHAR(36) NULL AFTER resultId',
    'SELECT 1'
);
PREPARE addClaimTokenStatement FROM @addClaimToken;
EXECUTE addClaimTokenStatement;
DEALLOCATE PREPARE addClaimTokenStatement;
UPDATE MatchResult SET claimToken = UUID() WHERE claimToken IS NULL;
ALTER TABLE MatchResult
    MODIFY COLUMN claimToken CHAR(36) NOT NULL;
