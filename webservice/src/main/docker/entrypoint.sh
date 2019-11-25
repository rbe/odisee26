#!/usr/bin/env bash

#set -o nounset
#set -o errexit

ls -lR

ODISEE_HOME=/home/odisee
. ${ODISEE_HOME}/etc/odienv
odictl -q start
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
