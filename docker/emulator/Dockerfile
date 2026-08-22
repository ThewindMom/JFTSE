FROM debian:12-slim

ENV JAVA_HOME=/opt/java/jdk-21
ENV PATH=$PATH:${JAVA_HOME}/bin

WORKDIR /opt/jftse

COPY docker/emulator/build.sh build.sh
COPY commons commons
COPY commons-proto commons-proto
COPY entities entities
COPY packet-generator packet-generator
COPY server-core server-core
COPY auth-server auth-server
COPY game-server game-server
COPY chat-server chat-server
COPY relay-server relay-server
COPY ac-server ac-server
COPY pom.xml pom.xml

RUN apt-get update && apt-get install -y vim wget curl tar procps openssl supervisor maven default-mysql-client

COPY docker/emulator/supervisord.conf /etc/supervisor/conf.d/supervisord.conf
COPY docker/emulator/entrypoint.sh /usr/local/bin/jftse-entrypoint

RUN chmod +x build.sh /usr/local/bin/jftse-entrypoint
RUN ./build.sh

RUN sed -i "s/jdbc:mysql:\/\/localhost:3306/jdbc:mysql:\/\/mysql-db-server:3306/g" auth-server/src/main/resources/application.properties
RUN sed -i "s/jdbc:mysql:\/\/localhost:3306/jdbc:mysql:\/\/mysql-db-server:3306/g" game-server/src/main/resources/application.properties
RUN sed -i "s/jdbc:mysql:\/\/localhost:3306/jdbc:mysql:\/\/mysql-db-server:3306/g" chat-server/src/main/resources/application.properties
RUN sed -i "s/jdbc:mysql:\/\/localhost:3306/jdbc:mysql:\/\/mysql-db-server:3306/g" relay-server/src/main/resources/application.properties
RUN sed -i "s/jdbc:mysql:\/\/localhost:3306/jdbc:mysql:\/\/mysql-db-server:3306/g" ac-server/src/main/resources/application.properties

RUN sed -i "s/spring.rabbitmq.host=localhost/spring.rabbitmq.host=rabbitmq-server/g" auth-server/src/main/resources/application.properties
RUN sed -i "s/spring.rabbitmq.host=localhost/spring.rabbitmq.host=rabbitmq-server/g" game-server/src/main/resources/application.properties
RUN sed -i "s/spring.rabbitmq.host=localhost/spring.rabbitmq.host=rabbitmq-server/g" chat-server/src/main/resources/application.properties
RUN sed -i "s/spring.rabbitmq.host=localhost/spring.rabbitmq.host=rabbitmq-server/g" relay-server/src/main/resources/application.properties

RUN mvn clean install

ENTRYPOINT ["/usr/local/bin/jftse-entrypoint"]
