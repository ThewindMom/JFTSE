#!/bin/bash

mkdir -p /opt/java
echo "Downloading openjdk-21..."
curl -LO https://download.java.net/java/GA/jdk21/fd2272bbf8e04c3dbaee13770090416c/35/GPL/openjdk-21_linux-x64_bin.tar.gz \
    && tar xf openjdk-21_linux-x64_bin.tar.gz \
    && mv jdk-21 /opt/java \
    && rm openjdk-21_linux-x64_bin.tar.gz
