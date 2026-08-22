#!/bin/bash
set -euo pipefail

public_host="${JFTSE_PUBLIC_HOST:-127.0.0.1}"

until mysqladmin ping --silent --host=mysql-db-server --user=jftse --password=jftse; do
    echo "Waiting for MySQL..."
    sleep 2
done

mysql --host=mysql-db-server --user=jftse --password=jftse fantasytennis <<SQL
UPDATE GameServer SET host='${public_host}' WHERE id IN (1, 2, 3, 4);
UPDATE GameServer SET port=5897 WHERE id=4;
SQL

echo "Advertising JFTSE game services at ${public_host}"
exec /usr/bin/supervisord -c /etc/supervisor/conf.d/supervisord.conf
