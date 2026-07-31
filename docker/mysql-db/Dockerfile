FROM mysql@sha256:6e0014cdd88092545557dee5e9eb7e1a3c84c9a14ad2418d5f2231e930967a38

COPY sql/create/create_fantasytennis.sql /docker-entrypoint-initdb.d/1.sql
COPY sql/insert/account.sql /docker-entrypoint-initdb.d/2.sql
COPY sql/insert/gameservertype.sql /docker-entrypoint-initdb.d/3.sql
COPY sql/insert/gameserver.sql /docker-entrypoint-initdb.d/4.sql
