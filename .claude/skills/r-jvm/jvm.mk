# The Gradle targets of a project: wrapper, jvm-tooling, and
# podman-socket. A project includes this file after project.mk or
# sdlc.mk, from the installed skill, and does not keep a copy of the
# targets in its Makefile.
#
#     include .claude/skills/r-project-setup/project.mk
#     include .claude/skills/r-jvm/jvm.mk
#
# A project that sets JDK_IMAGE, GRADLE_VERSION, GRADLE_IMAGE,
# GRADLE_RUN, PODMAN_SOCKET, or MAVEN_MIRROR before the include keeps its
# value. A project that sets its own GRADLE_RUN adds $(MAVEN_MIRROR_ARGS)
# to it.
#
# MAVEN_MIRROR is empty by default, and Gradle then reads Maven Central.
# If it is a URL, Gradle reads each Maven Central artifact from that URL.
# Use it when Maven Central answers HTTP 429, for example
#   make test MAVEN_MIRROR=https://maven-central.storage-download.googleapis.com/maven2/
# $(GRADLE) and $(GRADLE_PODMAN) stay in the Makefile of the project,
# because each project picks its own Gradle image and its own tasks.
#
# A new project, or a project with no wrapper yet, runs
#   make jvm-tooling
# once. It writes the wrapper, the build files, and the config files,
# and formats the project. NAME sets the project name in settings.gradle.
# A file already in the project stays as it is. Commit gradlew,
# gradlew.bat, and gradle/wrapper/.
#
# A Gradle task that starts containers runs through $(GRADLE_PODMAN) and
# needs the Podman API socket of this user. make podman-socket starts it.
#
# GNU Make 3.81. See project.mk for the rules of a recipe.

ifndef JVM_MK
JVM_MK := 1

# The folder of this file, read before another file is included.
JVM_MK_DIR := $(dir $(abspath $(lastword $(MAKEFILE_LIST))))

# NAME, the project name of a new project, comes from tools.mk: the
# repository name of the remote origin, else the directory name.
# make jvm-tooling writes it to settings.gradle.
JDK_IMAGE ?= docker.io/library/eclipse-temurin:25-jdk
GRADLE_VERSION ?= 9.1.0
GRADLE_IMAGE ?= docker.io/library/gradle:$(GRADLE_VERSION)-jdk25
MAVEN_MIRROR ?=
# The init script maven-mirror.gradle beside this file does the change.
MAVEN_MIRROR_ARGS = $(if $(strip $(MAVEN_MIRROR)),-e MAVEN_MIRROR=$(strip $(MAVEN_MIRROR)) \
	-v "$(JVM_MK_DIR)maven-mirror.gradle":/gradle/init.d/r-maven-mirror.gradle:ro)
GRADLE_RUN ?= mkdir -p "$$HOME/.gradle" && podman run --rm $(PODMAN_NET_ARGS) $(MAVEN_MIRROR_ARGS) -e CI -e GRADLE_USER_HOME=/gradle \
	-v "$$HOME/.gradle":/gradle:Z -v "$(CURDIR)":/work:Z -w /work
# On a Mac, podman info names the socket inside the Podman machine, where
# the container that runs Gradle runs.
PODMAN_SOCKET ?= $$(podman info --format '{{.Host.RemoteSocket.Path}}')
# Host mode (TOOLS=host of podman-python.mk): Gradle runs from the JDK on
# PATH. GRADLE_RUN drops the image name that follows it, so
# $(GRADLE_RUN) $(JDK_IMAGE) ./gradlew runs ./gradlew. GRADLE_PODMAN
# needs no socket, because a container that Gradle starts uses the
# engine of the host. These replace the values that the project set.
ifeq ($(TOOLS),host)
GRADLE_RUN = env MAVEN_MIRROR="$(strip $(MAVEN_MIRROR))" sh -c 'shift; exec "$$@" $(if $(strip $(MAVEN_MIRROR)),--init-script "$(JVM_MK_DIR)maven-mirror.gradle")' gradle-host
GRADLE_PODMAN = $(GRADLE_RUN) $(JDK_IMAGE) ./gradlew --console=plain
endif

.PHONY: wrapper jvm-tooling podman-socket

# help-group: Tools
wrapper: ## Write the Gradle wrapper in Podman. A new project runs this once
	$(GRADLE_RUN) $(GRADLE_IMAGE) gradle --console=plain wrapper --gradle-version $(GRADLE_VERSION)

# One target for the Gradle tooling, every step through Podman:
# 1. build.gradle, settings.gradle with the name NAME, and
#    config/checkstyle/checkstyle.xml from the r-jvm skill, and
#    .editorconfig from r-project-setup, each one when missing
# 2. the Gradle wrapper (make wrapper) when gradlew is missing. Gradle
#    needs the settings file of step 1 to write it.
# 3. build/ and .gradle/ in .gitignore, each line when missing
# 4. make format-fix, so make format passes from the first commit
# A file already in the project stays as it is, and a Kotlin build file
# (build.gradle.kts, settings.gradle.kts) counts as present. Set the
# group in the build file afterwards.
jvm-tooling: ## Set up the wrapper, Gradle files, Checkstyle, and EditorConfig, then format
	@set -eu; \
	if [ ! -f build.gradle ] && [ ! -f build.gradle.kts ]; then cp .claude/skills/r-jvm/templates/build.gradle build.gradle; fi; \
	if [ ! -f settings.gradle ] && [ ! -f settings.gradle.kts ]; then sed "s/'project-name'/'$(NAME)'/" .claude/skills/r-jvm/templates/settings.gradle > settings.gradle; fi; \
	if [ ! -f config/checkstyle/checkstyle.xml ]; then mkdir -p config/checkstyle; cp .claude/skills/r-jvm/templates/checkstyle.xml config/checkstyle/checkstyle.xml; fi; \
	if [ ! -f gradlew ]; then $(MAKE) --no-print-directory wrapper; fi; \
	if [ ! -f .editorconfig ]; then cp .claude/skills/r-project-setup/templates/editorconfig .editorconfig; fi; \
	touch .gitignore; \
	for line in build/ .gradle/; do \
	  if ! grep -qx "$$line" .gitignore; then printf '%s\n' "$$line" >> .gitignore; fi; \
	done; \
	$(MAKE) --no-print-directory format-fix

# A Podman that runs through a machine (ServiceIsRemote) has its socket
# already. Rootless Podman keeps its mount namespace apart, so the recipe
# checks that the socket is a socket there. It runs podman system migrate
# once when it is not.
podman-socket: ## Start the Podman API socket of this user when it is off. $(GRADLE_PODMAN) needs it
	@set -eu; \
	if [ "$$(podman info --format '{{.Host.ServiceIsRemote}}')" = true ]; then exit 0; fi; \
	sock=$(PODMAN_SOCKET); \
	if [ ! -S "$$sock" ] && command -v systemctl >/dev/null 2>&1; then \
		systemctl --user start podman.socket 2>/dev/null || true; \
	fi; \
	if [ ! -S "$$sock" ]; then \
		mkdir -p build "$$(dirname "$$sock")"; \
		nohup podman system service --time=0 "unix://$$sock" > build/podman-service.log 2>&1 & \
		for i in 1 2 3 4 5 6 7 8 9 10; do \
			if [ -S "$$sock" ]; then break; fi; \
			sleep 1; \
		done; \
	fi; \
	if [ ! -S "$$sock" ]; then \
		echo "The Podman socket $$sock did not open. See build/podman-service.log." >&2; \
		exit 1; \
	fi; \
	if [ "$$(id -u)" != 0 ] && ! podman unshare test -S "$$sock"; then \
		echo "podman-socket: $$sock is not in the mount namespace of rootless Podman. podman system migrate restarts its pause process." >&2; \
		podman system migrate; \
	fi; \
	if [ "$$(id -u)" != 0 ] && ! podman unshare test -S "$$sock"; then \
		echo "The Podman socket $$sock is not in the mount namespace of rootless Podman." >&2; \
		exit 1; \
	fi; \
	echo "podman-socket: $$sock"

# help is the default goal. This fragment does not take it.
.DEFAULT_GOAL := help

endif
