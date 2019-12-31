FROM opensuse/leap:15.2

LABEL maintainer="Ralf Bensmann <ralf@art-of-coding.eu>"
LABEL vendor="Art of Coding UG"
LABEL project="Odisee"

ARG ODISEE_HOME=/home/odisee
# LibreOffice Variant: still (stable) or fresh
ARG LO_VARIANT="still"
# Language: de en es fr it nl hu
ARG LO_LANG="de"
ARG zypper_update="zypper update --no-confirm"
ARG zypper_install="zypper install --no-confirm"

ENV TZ=Europe/Berlin
ENV LANG=en_US.UTF-8
ENV ODISEE_HOME=/home/odisee

COPY locale.conf /etc/
RUN ln -sf /usr/share/zoneinfo/${TZ} /etc/localtime

RUN ${zypper_update} \
    && ${zypper_install} which sudo \
    && ${zypper_install} libreoffice-writer libreoffice-writer-extensions \
    && ${zypper_install} libreoffice-l10n-${LO_LANG} \
    && ${zypper_install} myspell-de_DE \
    && ${zypper_install} fontconfig

RUN ${zypper_install} java-11-openjdk-headless
