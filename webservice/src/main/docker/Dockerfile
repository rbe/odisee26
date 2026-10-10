FROM ubuntu:26.04 AS packages

ENV TZ=Europe/Berlin \
    LANG=en_US.UTF-8 \
    ODISEE_HOME=/home/odisee \
    JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64

# Headless Writer and the service JRE. Calc and Impress are not installed.
# Docs, package indexes, and Writer files that conversion does not read are
# removed in this same layer so the runtime stage does not contain them.
# entrypoint.sh uses a 1g heap, so the non-compressed CDS archive stays unused.
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        openjdk-21-jre-headless \
        libreoffice-writer \
        fonts-liberation \
        ca-certificates \
    && rm -f /usr/lib/jvm/java-21-openjdk-*/lib/server/classes_nocoops.jsa \
    && rm -rf /usr/lib/jvm/java-21-openjdk-*/man \
        /var/lib/apt/lists/* \
        /var/cache/apt/* \
        /var/cache/debconf/* \
        /var/log/apt/* \
        /usr/share/doc \
        /usr/share/man \
        /usr/share/info \
        /usr/share/lintian \
        /usr/share/linda \
        /usr/share/icons \
        /usr/share/coreutils/locales \
        /usr/lib/libreoffice/share/gallery \
        /usr/lib/libreoffice/share/template \
        /usr/lib/libreoffice/share/wizards \
        /usr/lib/libreoffice/program/wizards \
        /usr/lib/libreoffice/CREDITS.fodt \
    && rm -f /usr/share/libreoffice/share/config/images_*.zip \
    && rm -rf /tmp/* /var/tmp/* \
    && groupadd odisee \
    && useradd -g odisee -m -d ${ODISEE_HOME} -s /bin/bash odisee

COPY webservice*.jar ${ODISEE_HOME}/application.jar

WORKDIR ${ODISEE_HOME}
RUN mkdir -p var/deploy/fonts var/document var/image var/merge var/request var/template var/tmp
COPY LICENSE.txt .
COPY bin/* bin/
COPY etc/* etc/
COPY shell_profile .bash_profile
COPY entrypoint.sh .

RUN find ${ODISEE_HOME} -print0 | xargs -0 chown odisee:odisee \
    && find ${ODISEE_HOME} -type d -print0 | xargs -0 chmod 750 \
    && find ${ODISEE_HOME} -type f -print0 | xargs -0 chmod 440 \
    && find ${ODISEE_HOME} -type f -name \*.sh -print0 | xargs -0 chmod 550 \
    && find ${ODISEE_HOME}/bin -type f -print0 | xargs -0 chmod 550

FROM packages

LABEL maintainer="Ralf Bensmann <ralf@art-of-coding.eu>"
LABEL vendor="Art of Coding UG"
LABEL project="Odisee"

ARG projectVersion=LocalBuild

USER odisee:odisee

VOLUME ["${ODISEE_HOME}/var"]
EXPOSE 8080
CMD ["/home/odisee/entrypoint.sh"]
