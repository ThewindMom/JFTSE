-- Enable shop product 515 and grant Special item 11 to the disposable test accounts.
-- Idempotent: re-running does not duplicate PlayerPocket rows.

UPDATE Product
SET enabled = 1,
    display = 6
WHERE productIndex = 515
  AND category = 'SPECIAL'
  AND item0 = 11;

INSERT INTO PlayerPocket (pocket_id, category, itemIndex, useType, itemCount, created, modified)
SELECT p.pocket_id,
       'SPECIAL',
       11,
       'N/A',
       1,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM Player p
JOIN Account a ON a.id = p.account_id
WHERE a.username IN ('test', 'test1', 'test2')
  AND NOT EXISTS (
        SELECT 1
        FROM PlayerPocket pp
        WHERE pp.pocket_id = p.pocket_id
          AND pp.category = 'SPECIAL'
          AND pp.itemIndex = 11
  );

UPDATE Pocket pk
JOIN Player p ON p.pocket_id = pk.id
JOIN Account a ON a.id = p.account_id
SET pk.belongings = (
        SELECT COUNT(*)
        FROM PlayerPocket pp
        WHERE pp.pocket_id = pk.id
    )
WHERE a.username IN ('test', 'test1', 'test2');
