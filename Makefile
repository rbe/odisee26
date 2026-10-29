# Odisee frontend. This file is the how-to. `make` and `make help` list every target.
# Run make from the repository root.
#
# Local stack:
#   make stack
#   make user
#   make token
#   POST Authorization: Bearer to http://127.0.0.1:8080/odisee/document/generate
#
# `make stack` runs `./gradlew :webservice:startStack`.
# It builds the local image and starts odisee on 8080, MinIO on 9000, and Keycloak on 8180.
# IntelliJ can run the stack Gradle task (`startStack`) from the Odisee group.
# HTTP files in webservice/src/test/resources/intellij/ POST to localhost:8080 and do not send a bearer token.
#
# `make user` runs webservice/src/main/container/bin/odictl user.
# NAME defaults to press.
# It sets ODISEE_KEYCLOAK_ISSUER=http://127.0.0.1:8180/realms/odisee,
# ODISEE_KEYCLOAK_ADMIN=admin, and ODISEE_KEYCLOAK_ADMIN_PASSWORD=admin.
# It prints the client id and secret (client_id and client_secret).
# Pass those to make token as CLIENT_ID and CLIENT_SECRET.
# odictl writes var/user/NAME and etc/oidc under ODISEE_HOME.
# ODISEE_HOME defaults to $HOME/.local/share/odisee.
# This file does not contain a secret.
#
# `make token` reads CLIENT_ID and CLIENT_SECRET from the environment.
# A missing value fails with one line.
# The request is:
#   curl -s -X POST http://127.0.0.1:8180/realms/odisee/protocol/openid-connect/token \
#     -d grant_type=client_credentials \
#     -d client_id=$CLIENT_ID \
#     -d client_secret=$CLIENT_SECRET
#
# GitHub image:
# Actions → Build, test, and package → Run workflow → pick the feature branch.
# The branch must contain current master.
# The tag is 2.6.0-<run> from the Actions run number.
# make pull VERSION=2.6.0-<run>
# make run VERSION=2.6.0-<run>
# latest is not moved by a branch run.
# make pull is podman pull ghcr.io/rbe/odisee26:$(VERSION).
# make run is podman run -it -p 8080:8080 ghcr.io/rbe/odisee26:$(VERSION).
# VERSION is required. An example is 2.6.0-42.
#
# JUnit:
# make test runs ./gradlew :webservice:test. It does not start a container.
# make test-office runs :webservice:libreOfficeTest.
# make test-minio runs :webservice:minioTest.
# make test-keycloak runs :webservice:keycloakTest.
# make test-java runs :clients:client-java:javaClientOfficeTest.
# The JUnit targets start their own containers.
# They do not use the compose stack from make stack.
#
# setup:
# make setup checks that podman is on PATH.
# On a Mac the binary is /opt/homebrew/bin/podman or /usr/local/bin/podman.
# Gradle checks PATH and then those two paths (gradle/podman-executable.gradle.kts).
# macOS /bin/bash is 3.2. odictl re-execs Homebrew bash
# (/opt/homebrew/bin/bash or /usr/local/bin/bash). Otherwise brew install bash.
# brew bundle installs the root Brewfile: bash, flock, openjdk@21, and podman.

.DEFAULT_GOAL := help

NAME ?= press
ODICTL := webservice/src/main/container/bin/odictl
export CLIENT_ID
export CLIENT_SECRET

.PHONY: setup stack user token test test-office test-minio test-keycloak test-java pull run help

setup: ## First. Check that podman is on PATH. On a Mac: /opt/homebrew/bin/podman or /usr/local/bin/podman
	@echo 'macOS /bin/bash is 3.2. odictl re-execs Homebrew bash. Otherwise brew install bash.'
	@set -eu; \
	bin=""; \
	if command -v podman >/dev/null 2>&1; then \
		bin=$$(command -v podman); \
	elif [ "$$(uname -s)" = Darwin ]; then \
		for candidate in /opt/homebrew/bin/podman /usr/local/bin/podman; do \
			if [ -x "$$candidate" ]; then bin=$$candidate; break; fi; \
		done; \
	fi; \
	if [ -z "$$bin" ]; then \
		echo 'podman is not on PATH. On a Mac it is /opt/homebrew/bin/podman or /usr/local/bin/podman.' >&2; \
		exit 1; \
	fi; \
	echo "podman: $$bin"

stack: ## Local stack. Build the image and start odisee on 8080, MinIO on 9000, and Keycloak on 8180
	./gradlew :webservice:startStack

user: ## After stack. Run odictl user. NAME defaults to press. Prints the client id and secret
	ODISEE_KEYCLOAK_ISSUER=http://127.0.0.1:8180/realms/odisee \
	ODISEE_KEYCLOAK_ADMIN=admin \
	ODISEE_KEYCLOAK_ADMIN_PASSWORD=admin \
	$(ODICTL) user "$(NAME)"

token: ## After user. Request a client-credentials token. Set CLIENT_ID and CLIENT_SECRET
	$(if $(and $(CLIENT_ID),$(CLIENT_SECRET)),,$(error CLIENT_ID and CLIENT_SECRET are required))
	curl -s -X POST http://127.0.0.1:8180/realms/odisee/protocol/openid-connect/token \
		-d grant_type=client_credentials \
		-d "client_id=$${CLIENT_ID}" \
		-d "client_secret=$${CLIENT_SECRET}"

test: ## Unit tests. Does not start a container
	./gradlew :webservice:test

test-office: ## LibreOffice tests 1-3. Starts its own container, not the compose stack
	./gradlew :webservice:libreOfficeTest

test-minio: ## MinIO store test. Starts its own container, not the compose stack
	./gradlew :webservice:minioTest

test-keycloak: ## Keycloak client-credentials test. Starts its own container, not the compose stack
	./gradlew :webservice:keycloakTest

test-java: ## Java client office proof. Starts its own containers, not the compose stack
	./gradlew :clients:client-java:javaClientOfficeTest

pull: ## Published GitHub image. VERSION is required, for example 2.6.0-42
	$(if $(VERSION),,$(error VERSION is required, for example 2.6.0-42))
	podman pull "ghcr.io/rbe/odisee26:$(VERSION)"

run: ## Run that image on port 8080. VERSION is required, for example 2.6.0-42
	$(if $(VERSION),,$(error VERSION is required, for example 2.6.0-42))
	podman run -it -p 8080:8080 "ghcr.io/rbe/odisee26:$(VERSION)"

help: ## Print every target and when to use it
	@awk 'BEGIN {FS = ":.*## "} /^[a-zA-Z0-9_-]+:.*## / {printf "  %-16s %s\n", $$1, $$2}' $(MAKEFILE_LIST)
