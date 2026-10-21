FROM ubuntu:26.04 AS packages

ENV TZ=Europe/Berlin \
    LANG=en_US.UTF-8 \
    ODISEE_HOME=/home/odisee \
    PATH="/home/odisee/.local/bin:${PATH}"

# Headless Writer and the service JRE. Calc and Impress are not installed.
# Docs, package indexes, and Writer files that conversion does not read are
# removed in this same layer so the runtime stage does not contain them.
# entrypoint.sh uses a 1g heap, so the non-compressed CDS archive stays unused.
# This layer stays cached. Files that change are copied below it.
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        openjdk-21-jre-headless \
        curl \
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
    && useradd -g odisee -m -d ${ODISEE_HOME} -s /bin/bash odisee \
    && chmod 750 ${ODISEE_HOME} \
    && mkdir -p -m 750 \
        ${ODISEE_HOME}/bin \
        ${ODISEE_HOME}/etc \
        ${ODISEE_HOME}/var/deploy/fonts \
        ${ODISEE_HOME}/var/document \
        ${ODISEE_HOME}/var/image \
        ${ODISEE_HOME}/var/merge \
        ${ODISEE_HOME}/var/request \
        ${ODISEE_HOME}/var/template \
        ${ODISEE_HOME}/var/tmp \
        ${ODISEE_HOME}/.local/bin \
    && chmod 750 \
        ${ODISEE_HOME}/var \
        ${ODISEE_HOME}/var/deploy \
        ${ODISEE_HOME}/.local \
    && chown -R odisee:odisee \
        ${ODISEE_HOME}/bin \
        ${ODISEE_HOME}/etc \
        ${ODISEE_HOME}/var \
        ${ODISEE_HOME}/.local \
    && ln -s ${ODISEE_HOME}/bin/odictl ${ODISEE_HOME}/.local/bin/odictl \
    && chown -h odisee:odisee ${ODISEE_HOME}/.local/bin/odictl

WORKDIR ${ODISEE_HOME}
COPY --chown=odisee:odisee --chmod=440 LICENSE.txt .
COPY --chown=odisee:odisee --chmod=550 bin/* bin/
COPY --chown=odisee:odisee --chmod=440 etc/* etc/
COPY --chown=odisee:odisee --chmod=550 entrypoint.sh .
COPY --chown=odisee:odisee --chmod=440 webservice*.jar application.jar

FROM packages

LABEL maintainer="Ralf Bensmann <ralf@art-of-coding.eu>"
LABEL vendor="Art of Coding UG"
LABEL project="Odisee"

ARG projectVersion=LocalBuild
LABEL org.opencontainers.image.source="https://github.com/rbe/odisee26"
LABEL org.opencontainers.image.version="${projectVersion}"

USER odisee:odisee

VOLUME ["${ODISEE_HOME}/var"]
EXPOSE 8080
CMD ["/home/odisee/entrypoint.sh"]
