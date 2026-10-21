#!/usr/bin/env bash

#set -o nounset
#set -o errexit

# ODISEE_HOME and the Keycloak variables come from the container environment.
# odictl is on PATH. This process does not source a profile.
: "${ODISEE_HOME:=/home/odisee}"
export ODISEE_HOME
# LibreOffice only. The JVM stays in the foreground below so the container
# lifecycle follows the service. `odictl -q start` is the bare-metal command
# and would launch application.jar a second time.
# ODISEE_SHELL=1 backgrounds that same JVM and execs bash for `podman run -it`.
odictl -q start-inst
sleep 5
echo
echo "Running Office instances:"
echo
ps ax | grep soffice | grep -v grep
echo

if [ "${ODISEE_SHELL:-}" = "1" ]; then
    java \
        -Xms1g -Xmx1g \
        -Djava.security.egd=file:/dev/./urandom \
        -jar ${ODISEE_HOME}/application.jar &
    ODISEE_JVM_PID=$!
    export ODISEE_JVM_PID
    # --rcfile installs the EXIT and TERM traps in the shell that becomes PID 1.
    # A login shell is not used. podman exec is the shell when this variable is unset.
    rc=$(mktemp)
    cat > "${rc}" << 'EOF'
stop_odisee_shell() {
    trap - EXIT TERM
    if [ -n "${ODISEE_JVM_PID:-}" ]; then
        kill "${ODISEE_JVM_PID}" 2>/dev/null || true
        wait "${ODISEE_JVM_PID}" 2>/dev/null || true
    fi
    if [ -n "${ODISEE_HOME:-}" ]; then
        local pid args
        while read -r pid args; do
            case "${args}" in
                *soffice*"${ODISEE_HOME}"*|*oosplash*"${ODISEE_HOME}"*)
                    if [ "${pid}" != "$$" ]; then
                        kill "${pid}" 2>/dev/null || true
                    fi
                    ;;
            esac
        done < <(ps ax -o pid=,args= 2>/dev/null || true)
    fi
    exit 0
}
trap stop_odisee_shell EXIT TERM
EOF
    exec bash --rcfile "${rc}" -i
fi

java \
    -Xms1g -Xmx1g \
    -Djava.security.egd=file:/dev/./urandom \
    -jar ${ODISEE_HOME}/application.jar

exit 0
