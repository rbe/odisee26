#!/usr/bin/env bash

#set -o nounset
#set -o errexit

ls -lR

ODISEE_HOME=/home/odisee
. ${ODISEE_HOME}/etc/odienv
# LibreOffice only. The JVM stays in the foreground below so the container
# lifecycle follows the service. `odictl -q start` is the bare-metal command
# and would launch application.jar a second time.
odictl -q start-inst
sleep 5
echo
echo "Running Office instances:"
echo
ps ax | grep soffice | grep -v grep
echo

java \
    -Xms1g -Xmx1g \
    -Djava.security.egd=file:/dev/./urandom \
    -jar ${ODISEE_HOME}/application.jar

tail -f /dev/null

exit 0
