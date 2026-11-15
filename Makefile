# Odisee frontend. This file is the how-to. `make` and `make help` list every target.
# Run make from the repository root. sdlc.mk is the shared pipeline makefile.
# The Makefile is the interface. A laptop and CI run the same targets.
# Podman is the only container engine.
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
# GitHub image and zip:
# The workflow is CI, `.github/workflows/ci.yml`. Each job runs the make target with its name.
# The published version is the nearest vMAJOR.MINOR.PATCH tag, without the v, plus the Actions run number.
# A tag v2.6.0 and run 42 publish 2.6.0-42. The shape is 2.6.0-<run>. A missing tag fails the publish. The workflow does not create a git tag.
# A push of the tag does not publish. After tagging, run publish-container or publish-github from the Actions tab.
# The default branch pushes that version and latest.
# A manual run from any other ref pushes the version only. latest is not moved by a branch run, and cleanup deletes nothing.
# make pull VERSION=2.6.0-42
# make run VERSION=2.6.0-42
# make pull is podman pull ghcr.io/rbe/odisee26:$(VERSION).
# make run is podman run -it -p 8080:8080 ghcr.io/rbe/odisee26:$(VERSION).
# VERSION is required. An example is 2.6.0-42.
# The image copies named files. It excludes .claude/, AGENTS.md, and docs/.
#
# JUnit:
# make test runs the unit tests. It does not start a container.
# The Gradle line is ./gradlew :webservice:test :clients:client-java:test :clients:client-javafx:test :oxt:test :webservice:integrationTest :test
# make test-office runs :webservice:libreOfficeTest.
# make test-minio runs :webservice:minioTest.
# make test-keycloak runs :webservice:keycloakTest.
# make test-java runs :clients:client-java:javaClientOfficeTest.
# make ci runs setup, build, test, and report. It does not publish and it does not run test-java.
# The JUnit targets test-office, test-minio, test-keycloak, and test-java start their own containers.
# They do not use the compose stack from make stack.
#
# Stop and remove:
# make stop runs ./gradlew :webservice:stopOdisee.
# It stops odisee-lo-test (startLibreOffice), odisee-minio-test (startMinio),
# and odisee-keycloak-test (startKeycloak), and it stops the compose project odisee
# (services odisee, minio, and keycloak).
# make rm runs ./gradlew :webservice:removeOdiseeContainers.
# It removes the test containers with podman rm -f and removes the stack with
# podman compose down. A missing container is success. The compose volumes stay.
# make clean-images runs ./gradlew :webservice:cleanOdiseeImages.
# That removes the containers first, then the images those commands built or pulled:
# odisee-libreoffice-test:local, alpine/minio:RELEASE.2025-10-15T17-29-55Z,
# quay.io/keycloak/keycloak:26.8.0, and the service image startStack builds
# (odisee/odisee and odisee/webservice at the project version).
# A missing image is success. Other images on the machine stay.
#
# setup:
# make setup resolves Gradle dependencies. It installs OpenJDK 21 when java is not 21.
# On a Mac the Podman binary is /opt/homebrew/bin/podman or /usr/local/bin/podman.
# Gradle checks PATH and then those two paths (gradle/podman-executable.gradle.kts).
# macOS /bin/bash is 3.2. odictl re-execs Homebrew bash
# (/opt/homebrew/bin/bash or /usr/local/bin/bash). Otherwise brew install bash.
# brew bundle installs the root Brewfile: bash, flock, openjdk@21, and podman.

.DEFAULT_GOAL := help

IMAGE ?= ghcr.io/rbe/odisee26
PACKAGE ?= rbe/odisee26
KEEP_VERSIONS ?= 2
SDLC_JOBS ?= setup build test test-java publish-container publish-github
SDLC_CI ?= setup build test report
HELP_LEAD ?= Odisee. The Makefile is the interface. A laptop and CI run the same targets.
CI_IMAGE ?= localhost/odisee26:ci
# Host JDK 21. Recorded under Divergences in docs/pipeline-rules.md.
GRADLE ?= ./gradlew --no-daemon --console=plain

include sdlc.mk

NAME ?= press
ODICTL := webservice/src/main/container/bin/odictl
export CLIENT_ID
export CLIENT_SECRET

.PHONY: ensure-jdk ensure-podman login stack user token test-office test-minio test-keycloak test-java pull rm clean-images

ensure-jdk:
	@set -eu; \
	if ! java -version 2>&1 | grep -Eq 'version "21[.]'; then \
		if command -v sudo >/dev/null 2>&1 && command -v apt-get >/dev/null 2>&1; then \
			sudo apt-get update; \
			sudo DEBIAN_FRONTEND=noninteractive apt-get install -y openjdk-21-jdk; \
		else \
			echo "JDK 21 is required. Set JAVA_HOME to a JDK 21." >&2; \
			exit 1; \
		fi; \
	fi; \
	mkdir -p build; \
	: > build/jdk21.env; \
	for home in /usr/lib/jvm/java-21-openjdk-amd64 /usr/lib/jvm/java-21-openjdk-arm64 /usr/lib/jvm/java-21-openjdk; do \
		if [ -x "$$home/bin/java" ]; then \
			printf 'export JAVA_HOME=%s\nexport PATH="%s/bin:$$PATH"\n' "$$home" "$$home" > build/jdk21.env; \
			break; \
		fi; \
	done

ensure-podman:
	@set -eu; \
	pkgs="$${PODMAN_PACKAGES:-podman}"; \
	if ! command -v podman >/dev/null 2>&1; then \
		if command -v sudo >/dev/null 2>&1 && command -v apt-get >/dev/null 2>&1; then \
			sudo apt-get update; \
			sudo DEBIAN_FRONTEND=noninteractive apt-get install -y $$pkgs; \
		else \
			echo "podman is required on PATH." >&2; \
			exit 1; \
		fi; \
	fi; \
	mkdir -p "$$HOME/.config/containers"; \
	printf '%s\n' '[engine]' 'cgroup_manager = "cgroupfs"' 'events_logger = "file"' > "$$HOME/.config/containers/containers.conf"

setup: ## Match CI: resolve Gradle dependencies. On a Mac, Podman is /opt/homebrew/bin/podman or /usr/local/bin/podman
	@$(call SDLC_LOG,setup,_setup)

build: ## Match CI: zip the Linux runtime and stage the tested boot JAR
	@$(call SDLC_LOG,build,_build)

test: ## Match CI: unit tests. Does not start a container
	@$(call SDLC_LOG,test,_test)

test-java: ## Match CI: Java client office proof. Starts its own containers, not the compose stack
	@$(call SDLC_LOG,test-java,_test-java)

test-office: ## LibreOffice tests 1-3. Starts its own container, not the compose stack
	./gradlew :webservice:libreOfficeTest

test-minio: ## MinIO store test. Starts its own container, not the compose stack
	./gradlew :webservice:minioTest

test-keycloak: ## Keycloak client-credentials test. Starts its own container, not the compose stack
	./gradlew :webservice:keycloakTest

login: ## Match CI: log in to ghcr.io with REGISTRY_USER and REGISTRY_TOKEN
	@set -eu; \
	test -n "$${REGISTRY_USER:-}" || { echo "REGISTRY_USER is required" >&2; exit 1; }; \
	test -n "$${REGISTRY_TOKEN:-}" || { echo "REGISTRY_TOKEN is required" >&2; exit 1; }; \
	printf '%s\n' "$$REGISTRY_TOKEN" | podman login ghcr.io --username "$$REGISTRY_USER" --password-stdin

image: ## Build the multi-arch service image from the tested JAR. No push
	@set -eu; \
	PODMAN_PACKAGES="podman qemu-user-binfmt" $(THIS_MAKE) --no-print-directory ensure-podman; \
	test -f build/dist/application.jar; \
	rm -rf build/image-context; \
	mkdir -p build/image-context; \
	cp -a webservice/src/main/container/. build/image-context/; \
	rm -rf build/image-context/var; \
	cp .containerignore build/image-context/.containerignore; \
	cp build/dist/application.jar build/image-context/webservice.jar; \
	version="$${IMAGE_VERSION:-LocalBuild}"; \
	revision="$${GITHUB_SHA:-}"; \
	if [ -z "$$revision" ]; then revision=$$(git rev-parse HEAD 2>/dev/null || printf '%s' unknown); fi; \
	cache_repo="$(IMAGE)/cache"; \
	if ! podman build --platform linux/amd64,linux/arm64 --manifest "$(CI_IMAGE)" --layers \
		--build-arg "projectVersion=$$version" \
		--label "org.opencontainers.image.source=https://github.com/$(PACKAGE)" \
		--label "org.opencontainers.image.version=$$version" \
		--label "org.opencontainers.image.revision=$$revision" \
		--cache-from "$$cache_repo" build/image-context; then \
		podman manifest rm "$(CI_IMAGE)" >/dev/null 2>&1 || true; \
		podman build --platform linux/amd64,linux/arm64 --manifest "$(CI_IMAGE)" --layers \
			--build-arg "projectVersion=$$version" \
			--label "org.opencontainers.image.source=https://github.com/$(PACKAGE)" \
			--label "org.opencontainers.image.version=$$version" \
			--label "org.opencontainers.image.revision=$$revision" \
			build/image-context; \
	fi

publish-container: ## Match CI: push the version, and latest on the default branch
	@$(call SDLC_LOG,publish-container,_publish-container)

publish-github: ## Match CI: release MAJOR.MINOR.PATCH-run with the Linux zip
	@$(call SDLC_LOG,publish-github,_publish-github)

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

pull: ## Published GitHub image. VERSION is required, for example 2.6.0-42
	$(if $(VERSION),,$(error VERSION is required, for example 2.6.0-42))
	podman pull "ghcr.io/rbe/odisee26:$(VERSION)"

run: ## Run that image on port 8080. VERSION is required, for example 2.6.0-42
	$(if $(VERSION),,$(error VERSION is required, for example 2.6.0-42))
	podman run -it -p 8080:8080 "ghcr.io/rbe/odisee26:$(VERSION)"

stop: ## Stop the test containers and the compose stack. A missing container is success
	./gradlew :webservice:stopOdisee

rm: ## Remove those containers. A missing container is success
	./gradlew :webservice:removeOdiseeContainers

clean-images: ## Remove the Odisee images after the containers are gone
	./gradlew :webservice:cleanOdiseeImages

clean: ## Remove build/ and .gradle/
	rm -rf build .gradle

_setup:
	@set -eu; \
	$(THIS_MAKE) --no-print-directory ensure-jdk; \
	if [ -f build/jdk21.env ]; then . ./build/jdk21.env; fi; \
	$(GRADLE) --version; \
	$(GRADLE) :dependencies :webservice:dependencies :clients:client-java:dependencies :clients:client-javafx:dependencies :documentation:dependencies; \
	printf '# setup\n\nDependencies resolved into the Gradle cache. JDK 21.\n' > build/summaries/setup.md

_build:
	@set -eu; \
	$(THIS_MAKE) --no-print-directory ensure-jdk; \
	if [ -f build/jdk21.env ]; then . ./build/jdk21.env; fi; \
	$(GRADLE) packageDistribution; \
	mkdir -p build/dist; \
	jars=$$(ls webservice/build/libs/webservice-*.jar 2>/dev/null | grep -v -- '-plain.jar$$' || true); \
	test "$$(printf '%s\n' "$$jars" | grep -c .)" -eq 1; \
	cp "$$jars" build/dist/application.jar; \
	zips=$$(ls build/distributions/odisee-*-linux-x86_64.zip 2>/dev/null || true); \
	test "$$(printf '%s\n' "$$zips" | grep -c .)" -eq 1; \
	cp "$$zips" build/dist/odisee-linux-x86_64.zip; \
	printf '# build\n\nZip build/dist/odisee-linux-x86_64.zip\nJAR build/dist/application.jar\n' > build/summaries/build.md

_test:
	@set -eu; \
	$(THIS_MAKE) --no-print-directory ensure-jdk; \
	if [ -f build/jdk21.env ]; then . ./build/jdk21.env; fi; \
	$(GRADLE) :webservice:test :clients:client-java:test :clients:client-javafx:test :oxt:test :webservice:integrationTest :test jacocoTestReport; \
	printf '# test\n\nJUnit XML is under each project build/test-results. Coverage is in build/reports/jacoco.\n' > build/summaries/test.md

_test-java:
	@set -eu; \
	$(THIS_MAKE) --no-print-directory ensure-jdk; \
	if [ -f build/jdk21.env ]; then . ./build/jdk21.env; fi; \
	$(THIS_MAKE) --no-print-directory ensure-podman; \
	$(GRADLE) :clients:client-java:javaClientOfficeTest; \
	printf '# test-java\n\nJava client office proof.\n' > build/summaries/test-java.md

_publish-container:
	@set -eu; \
	version=$$($(THIS_MAKE) -s --no-print-directory version); \
	mkdir -p build; \
	printf '%s\n' "$$version" > build/publish-version.txt; \
	IMAGE_VERSION="$$version" $(THIS_MAKE) --no-print-directory image; \
	tags=$$($(THIS_MAKE) -s --no-print-directory publish-tags); \
	for tag in $$tags; do \
		podman manifest push --all "$(CI_IMAGE)" "$(IMAGE):$$tag"; \
	done; \
	shown=$$(printf '%s\n' "$$tags" | sed 's/^/- /'); \
	printf '# publish-container\n\nStatus: pushed\nImage: %s\nTags:\n%s\n' "$(IMAGE)" "$$shown" > build/summaries/publish-container.md

_publish-github:
	@set -eu; \
	test -f build/dist/odisee-linux-x86_64.zip; \
	version=$$($(THIS_MAKE) -s --no-print-directory version); \
	asset=build/dist/odisee-$$version-linux-x86_64.zip; \
	cp build/dist/odisee-linux-x86_64.zip "$$asset"; \
	if gh release view "$$version" >/dev/null 2>&1; then \
		gh release upload "$$version" "$$asset" --clobber; \
	else \
		gh release create "$$version" "$$asset" --title "$$version" \
			--notes "Odisee $$version." --target "$$(git rev-parse HEAD)"; \
	fi; \
	printf '# publish-github\n\nStatus: released\nRelease: %s\n' "$$version" > build/summaries/publish-github.md
