FROM maven@sha256:2b4496088e7b80ae10a8c9f74e574ea21380325a006ec684532ad6bad5bc7273

WORKDIR /opt/jftse

COPY commons commons
COPY commons-proto commons-proto
COPY entities entities
COPY server-core server-core
COPY auth-server auth-server
COPY game-server game-server
COPY chat-server chat-server
COPY relay-server relay-server
COPY ac-server ac-server
COPY packet-generator packet-generator
COPY pom.xml pom.xml

RUN apt-get update \
    && apt-get install -y --no-install-recommends supervisor=4.2.5-1ubuntu0.1 \
    && rm -rf /var/lib/apt/lists/*

COPY docker/emulator/supervisord.conf /etc/supervisor/conf.d/supervisord.conf

RUN mvn clean install

CMD ["/usr/bin/supervisord", "-c", "/etc/supervisor/conf.d/supervisord.conf"]
