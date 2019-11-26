FROM odisee/archlinux-libreoffice:20191105-still AS base

LABEL maintainer="Ralf Bensmann <ralf@art-of-coding.eu>"
LABEL vendor="Art of Coding UG"
LABEL project="Odisee"

ARG projectVersion=LocalBuild

ENV TZ=Europe/Berlin
ENV LANG=en_US.UTF-8
ENV ODISEE_HOME=/home/odisee

RUN groupadd odisee \
    && useradd -g odisee -m -d ${ODISEE_HOME} -s /bin/bash odisee

COPY webservice*.jar ${ODISEE_HOME}/application.jar

WORKDIR ${ODISEE_HOME}
RUN mkdir var \
    && mkdir var/deploy \
    && mkdir var/deploy/fonts \
    && mkdir var/document \
    && mkdir var/image \
    && mkdir var/merge \
    && mkdir var/request \
    && mkdir var/template \
    && mkdir var/tmp
COPY LICENSE.txt .
COPY bin/* bin/
COPY etc/* etc/
COPY xml/* etc/xml/
COPY image/* var/image/
COPY shell_profile .bash_profile
COPY entrypoint.sh .

RUN ls -lR \
    && find ${ODISEE_HOME} -print0 | xargs -0 chown odisee:odisee \
    && find ${ODISEE_HOME} -type d -print0 | xargs -0 chmod 750 \
    && find ${ODISEE_HOME} -type f -print0 | xargs -0 chmod 440 \
    && find ${ODISEE_HOME} -type f -name \*.sh -print0 | xargs -0 chmod 550 \
    && find ${ODISEE_HOME}/bin -type f -print0 | xargs -0 chmod 550 \
    && chmod 440 ${ODISEE_HOME}/bin/odilib

USER odisee:odisee

FROM base
WORKDIR ${ODISEE_HOME}
VOLUME ["${ODISEE_HOME}/var"]
EXPOSE 8080
CMD ${ODISEE_HOME}/entrypoint.sh
