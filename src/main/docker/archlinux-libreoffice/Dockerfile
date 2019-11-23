FROM archlinux:20191105

LABEL maintainer="Ralf Bensmann <ralf@art-of-coding.eu>"
LABEL vendor="Art of Coding UG"
LABEL project="Odisee"

# LibreOffice Variant: still (stable) or fresh
ARG LO_VARIANT="still"
# Language: de en es fr it nl hu
ARG LO_LANG="de"
ARG pacman="pacman --noconfirm"

ENV TZ=Europe/Berlin
ENV LANG=en_US.UTF-8
ENV ODISEE_HOME=/home/odisee

COPY locale.gen /etc/
RUN ln -sf /usr/share/zoneinfo/${TZ} /etc/localtime \
    && echo "LANG=${LANG}" >/etc/locale.conf \
    && locale-gen

RUN ${pacman} -Sy \
    && ${pacman} -S grep which procps-ng net-tools sudo \
    && ${pacman} -S gawk sed rsync \
    && ${pacman} -S fontconfig

RUN ${pacman} -S libreoffice-${LO_VARIANT} libreoffice-${LO_VARIANT}-${LO_LANG} \
    && ${pacman} -S hyphen hyphen-${LO_LANG} \
    && ${pacman} -S hunspell hunspell-${LO_LANG}

RUN ${pacman} -S jre11-openjdk
