FROM ubuntu:26.04 AS packages

# Headless Writer only. Calc and Impress are not installed.
# Docs, package indexes, and Writer files that conversion does not read are
# removed in this same layer so the runtime stage does not contain them.
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        libreoffice-writer \
        fonts-liberation \
        ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
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
    && rm -rf /tmp/* /var/tmp/*

COPY entrypoint.sh /entrypoint.sh
RUN chmod 755 /entrypoint.sh

FROM packages

EXPOSE 2002
ENTRYPOINT ["/entrypoint.sh"]
