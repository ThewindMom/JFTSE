# JFTSE orb lifecycle

`.agents/setup` installs the reusable toolchain and Maven cache.
`.agents/resume` only checks that those tools are still on PATH.

Setup builds and installs the Maven reactor with tests skipped, then downloads the
JUnit platform provider. It repeats Maven resolution on warm runs so changed POMs
refresh the cache. Compilation errors stop setup and prevent a refreshed snapshot.
The temporary Maven settings normalize the parent POM's Central repository ID without
changing user settings or rewriting cached artifact metadata.

These scripts prepare server development. They do not install the proprietary game
client, Wine, or untracked local tools. Commit and push the lifecycle files to the
project's remote branch before expecting new orbs to use them.

## System packages

Debian 12 does not ship JDK 21 or MySQL 8. Setup installs:

- `ca-certificates` `curl` `gnupg` `tar` `unzip` `git`
- `temurin-21-jdk` from Adoptium
- Maven 3.9.4 under `/opt/maven` (README allows Maven >= 3.6.3)
- `docker-ce` `docker-ce-cli` `containerd.io` `docker-buildx-plugin` `docker-compose-plugin`

Do not install `mariadb-server` or Debian `rabbitmq-server`. Those are not MySQL 8.0 or RabbitMQ 3.12.

## Runtime

Unit tests are plain JUnit and do not need a database.

The emulator runtime uses MySQL 8.0 and RabbitMQ 3.12 from `docker/docker-compose.yml`.
Start them with `amp orb services ensure` after setup. That file starts `dockerd`, then one
`backing-services` process (`.agents/wait-docker`) that waits up to 30s for `sudo docker info`
and runs one compose project named `jftse-orb`. No portal. Do not point at a developer host's
published 3306 or 5672 services.

Database images are built on first service startup, not during setup. The service
commands have not been exercised in a real orb. Debian-container verification covers
setup, login-shell activation, resume, and offline Maven tests, but not nested Docker.
