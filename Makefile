# Odisee frontend. This file is the how-to. `make` and `make help` list every target.
# Run make from the repository root. .claude/skills/r-github-pipeline/sdlc.mk is the shared pipeline makefile.
# APM installs it from rbe/ralfskills (apm.yml, apm.lock.yaml).
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
# A push to the default branch publishes only when the repository variable AUTO_PUBLISH is true.
# The default branch pushes that version and latest.
# A manual run from any other ref pushes the version only. latest is not moved by a branch run, and cleanup deletes nothing.
# make pull VERSION=2.6.0-42
# make run VERSION=2.6.0-42
# make pull is podman pull ghcr.io/rbe/odisee26:$(VERSION).
# make run is podman run -it -p 8080:8080 ghcr.io/rbe/odisee26:$(VERSION).
# VERSION is required. An example is 2.6.0-42.
# The image copies named files. It excludes .claude/, CLAUDE.md, and docs/.
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
# make setup resolves Gradle dependencies. Gradle runs in Podman with JDK 21 (gradle/Containerfile).
# The targets that start containers from Gradle run Gradle in that same image with the Podman socket of the host. The host needs no JDK.
# On a Mac the Podman binary is /opt/homebrew/bin/podman or /usr/local/bin/podman.
# Gradle checks PATH and then those two paths (gradle/podman-executable.gradle.kts).
# macOS /bin/bash is 3.2. odictl re-execs Homebrew bash
# (/opt/homebrew/bin/bash or /usr/local/bin/bash). Otherwise brew install bash.

.DEFAULT_GOAL := help

IMAGE ?= ghcr.io/rbe/odisee26
PACKAGE ?= rbe/odisee26
KEEP_VERSIONS ?= 2
SDLC_JOBS ?= setup build test test-java publish-container publish-github
SDLC_CI ?= setup build test report
HELP_LEAD ?= Odisee. The Makefile is the interface. A laptop and CI run the same targets.
CI_IMAGE ?= localhost/odisee26:ci
OWNER ?= rbe
# setup, build, and test run the Gradle wrapper in Podman, in the image
# that `make gradle-image` builds from gradle/Containerfile (JDK 21, make,
# git, and the podman client). The work tree is mounted at its own path,
# so a path in a test is the same path on the host. The Gradle cache is
# $HOME/.gradle on the host. The image tag holds the checksum of
# gradle/Containerfile, so a changed Containerfile builds a new image.
JDK_IMAGE ?= docker.io/library/eclipse-temurin:21-jdk
# jvm.mk would default GRADLE_VERSION to another version. This project uses the wrapper version below.
GRADLE_VERSION ?= 8.14.5
GRADLE_IMAGE ?= localhost/odisee26-gradle:21-$(shell cksum < gradle/Containerfile | cut -d ' ' -f 1)
# Extra podman run arguments of the Gradle run, such as -e for a proxy.
GRADLE_RUN_ARGS ?=
GRADLE_RUN = mkdir -p "$(GRADLE_CACHE)" && podman run --rm --init
GRADLE_ARGS = $(GRADLE_RUN_ARGS) $(MAVEN_MIRROR_ARGS) -e CI -e GITHUB_RUN_NUMBER \
	-e GRADLE_USER_HOME=/gradle -v "$(GRADLE_CACHE)":/gradle:Z -v "$(CURDIR)":"$(CURDIR)":Z -w "$(CURDIR)" \
	$(GRADLE_IMAGE) ./gradlew --no-daemon --console=plain
GRADLE = $(GRADLE_RUN) $(PODMAN_NET_ARGS) $(GRADLE_ARGS)
# Host mode (TOOLS=host of r-project-setup): Gradle runs from the JDK on
# PATH, for a machine without Podman, such as a cloud session. In host
# mode GRADLE_RUN drops the image name that follows it.
ifeq ($(TOOLS),host)
GRADLE = $(GRADLE_RUN) $(GRADLE_IMAGE) ./gradlew --no-daemon --console=plain
endif
# The targets that start containers from Gradle (test-java, test-office,
# test-minio, test-keycloak, stack, stop, rm, clean-images) run the same
# image with the Podman socket of the host and the host network. The
# podman client in the image starts the containers through that socket,
# and Gradle connects to their published ports on 127.0.0.1. On a Mac,
# podman info names the socket inside the Podman machine, where the
# container runs. A Podman client on a Mac can name it with the prefix
# unix://, and sed removes that prefix, because podman run -v takes a path.
# make podman-socket (jvm.mk) starts the socket when it is off.
PODMAN_SOCKET = $$(podman info --format '{{.Host.RemoteSocket.Path}}' | sed 's|^unix://||')
GRADLE_PODMAN = $(GRADLE_RUN) --network host --security-opt label=disable \
	-e CONTAINER_HOST=unix:///run/podman/podman.sock -v "$(PODMAN_SOCKET)":/run/podman/podman.sock \
	$(GRADLE_ARGS)

# The command variables of the Make contract. Each runs the real recipe
# below through SDLC_LOG, which writes build/logs and build/summaries.
SETUP_CMD = @$(call SDLC_LOG,setup,_setup)
BUILD_CMD = @$(call SDLC_LOG,build,_build)
TEST_CMD = @$(call SDLC_LOG,test,_test)
# The gates. Gradle runs in Podman. Spotless runs google-java-format on the Java files and
# leaves Groovy and Kotlin files alone. Checkstyle is the linter of Java and CodeNarc is the linter of Groovy.
FORMAT_CMD = @$(THIS_MAKE) --no-print-directory gradle-image && $(GRADLE) spotlessCheck
FORMAT_FIX_CMD = @$(THIS_MAKE) --no-print-directory gradle-image && $(GRADLE) spotlessApply
LINT_CMD = @$(THIS_MAKE) --no-print-directory gradle-image && $(GRADLE) checkstyleMain checkstyleTest checkstyleIntegrationTest codenarcMain codenarcTest codenarcIntegrationTest
# The one integration entry. It starts its own containers through the Podman socket.
INTEGRATION_CMD = @$(THIS_MAKE) --no-print-directory test-java
# JaCoCo writes one execution file for each project. The coverage map names the tests that cover
# each source file. jacoco_repo_paths.py of r-project-report gives the XML report the paths of
# this multi-module build. diff-cover reads that report and writes build/coverage/diff.md.
COVERAGE_CMD = set -eu; \
	$(THIS_MAKE) --no-print-directory gradle-image; \
	$(GRADLE) jacocoDiffReport; \
	files=$$(find . -path '*/build/jacoco/test.exec' -not -path './apm_modules/*' | sed 's|^\./||' | sort | tr '\n' ' '); \
	$(PYTHON) .claude/skills/r-project-report/scripts/jacoco_cover_map.py $$files; \
	$(PYTHON) .claude/skills/r-project-report/scripts/jacoco_repo_paths.py build/reports/jacoco/diff/jacocoDiff.xml build/reports/jacoco/diff/jacocoDiffRepo.xml; \
	REPORT=build/reports/jacoco/diff/jacocoDiffRepo.xml $(DIFF_COVER)
TEST_CHANGED_CMD = chosen=$$($(PYTHON) .claude/skills/r-project-report/scripts/changed_tests.py --print) || exit 1; \
	chosen=$$(printf '%s' "$$chosen" | tr '\n' ' '); \
	if [ -z "$$chosen" ]; then printf '%s\n' "test-changed: no test covers the changed files. make test runs the full suite."; \
	else printf 'test-changed: %s\n' "$$chosen"; $(THIS_MAKE) --no-print-directory gradle-image; args=""; for name in $$chosen; do args="$$args --tests $$name"; done; $(GRADLE) :webservice:test :clients:client-java:test :clients:client-javafx:test :oxt:test $$args; fi
# login comes from the contract: podman login ghcr.io with REGISTRY_USER and REGISTRY_TOKEN.
PUBLISH_IMAGE = 1
# The image is built from a context that holds the tested JAR. container.mk
# builds it for one platform in the build job and for PLATFORMS when it publishes.
# The Containerfile is copied into the context, so CONTAINERFILE names that copy.
IMAGE_CONTEXT = build/image-context
CONTAINERFILE = build/image-context/Containerfile
IMAGE_PREPARE = rm -rf build/image-context; \
	mkdir -p build/image-context; \
	cp -a webservice/src/main/container/. build/image-context/; \
	rm -rf build/image-context/var; \
	cp .containerignore build/image-context/.containerignore; \
	test -f build/dist/application.jar; \
	cp build/dist/application.jar build/image-context/webservice.jar
IMAGE_BUILD_ARGS = --layers --build-arg projectVersion=$${version:-LocalBuild}
# NAME is the Keycloak user of `make user`. sdlc.mk would default it to the
# repository name, so the GitHub repository is set here.
NAME ?= press
GH_REPO ?= $(PACKAGE)

include .claude/skills/r-github-pipeline/sdlc.mk
# wrapper, jvm-tooling, and podman-socket. GRADLE_RUN, GRADLE_IMAGE, JDK_IMAGE, and PODMAN_SOCKET above win over its defaults.
include .claude/skills/r-jvm/jvm.mk

ODICTL := webservice/src/main/container/bin/odictl
export CLIENT_ID
export CLIENT_SECRET

.PHONY: ensure-podman gradle-image stack user token test-office test-minio test-keycloak test-java pull rm clean-images

gradle-image: ## Build the Gradle toolchain image from gradle/Containerfile, once
ifeq ($(TOOLS),host)
	@true
else
	@set -eu; \
	if ! podman image exists "$(GRADLE_IMAGE)"; then \
	  podman build $(PODMAN_NET_ARGS) --build-arg JDK_IMAGE=$(JDK_IMAGE) -t "$(GRADLE_IMAGE)" -f gradle/Containerfile gradle; \
	fi
endif

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

# The recipes of setup, build, and test come from the Make contract and
# run SETUP_CMD, BUILD_CMD, and TEST_CMD.

test-java: ## Match CI: Java client office proof. Starts its own containers, not the compose stack
	@$(call SDLC_LOG,test-java,_test-java)

test-office: ## LibreOffice tests 1-3. Starts its own container, not the compose stack
	@$(THIS_MAKE) --no-print-directory gradle-image podman-socket
	$(GRADLE_PODMAN) :webservice:libreOfficeTest

test-minio: ## MinIO store test. Starts its own container, not the compose stack
	@$(THIS_MAKE) --no-print-directory gradle-image podman-socket
	$(GRADLE_PODMAN) :webservice:minioTest

test-keycloak: ## Keycloak client-credentials test. Starts its own container, not the compose stack
	@$(THIS_MAKE) --no-print-directory gradle-image podman-socket
	$(GRADLE_PODMAN) :webservice:keycloakTest

publish-github: ## Match CI: release MAJOR.MINOR.PATCH-run with the Linux zip
	@$(call SDLC_LOG,publish-github,_publish-github)

stack: ## Local stack. Build the image and start odisee on 8080, MinIO on 9000, and Keycloak on 8180
	@$(THIS_MAKE) --no-print-directory gradle-image podman-socket
	$(GRADLE_PODMAN) :webservice:startStack

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
	@$(THIS_MAKE) --no-print-directory gradle-image podman-socket
	$(GRADLE_PODMAN) :webservice:stopOdisee

rm: ## Remove those containers. A missing container is success
	@$(THIS_MAKE) --no-print-directory gradle-image podman-socket
	$(GRADLE_PODMAN) :webservice:removeOdiseeContainers

clean-images: ## Remove the Odisee images after the containers are gone
	@$(THIS_MAKE) --no-print-directory gradle-image podman-socket
	$(GRADLE_PODMAN) :webservice:cleanOdiseeImages

clean: ## Remove build/ and .gradle/
	rm -rf build .gradle

_setup:
	@set -eu; \
	$(THIS_MAKE) --no-print-directory gradle-image; \
	$(GRADLE) --version; \
	$(GRADLE) :dependencies :webservice:dependencies :clients:client-java:dependencies :clients:client-javafx:dependencies :documentation:dependencies; \
	printf '# setup\n\nDependencies resolved into the Gradle cache. JDK 21 in Podman.\n' > build/summaries/setup.md

_build:
	@set -eu; \
	$(THIS_MAKE) --no-print-directory gradle-image; \
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
	$(THIS_MAKE) --no-print-directory gradle-image; \
	$(GRADLE) :webservice:test :clients:client-java:test :clients:client-javafx:test :oxt:test :webservice:integrationTest :test jacocoTestReport; \
	printf '# test\n\nJUnit XML is under each project build/test-results. Coverage is in build/reports/jacoco.\n' > build/summaries/test.md

_test-java:
	@set -eu; \
	$(THIS_MAKE) --no-print-directory ensure-podman; \
	$(THIS_MAKE) --no-print-directory gradle-image podman-socket; \
	$(GRADLE_PODMAN) :clients:client-java:javaClientOfficeTest; \
	printf '# test-java\n\nJava client office proof.\n' > build/summaries/test-java.md

_publish-github:
	@set -eu; \
	test -f build/dist/odisee-linux-x86_64.zip; \
	version=$$($(THIS_MAKE) -s --no-print-directory version); \
	asset=build/dist/odisee-$$version-linux-x86_64.zip; \
	cp build/dist/odisee-linux-x86_64.zip "$$asset"; \
	$(THIS_MAKE) --no-print-directory gh-image; \
	if $(GH) release view "$$version" -R "$(PACKAGE)" >/dev/null 2>&1; then \
		$(GH) release upload "$$version" "$$asset" -R "$(PACKAGE)" --clobber; \
	else \
		$(GH) release create "$$version" "$$asset" -R "$(PACKAGE)" --title "$$version" \
			--notes "Odisee $$version." --target "$$(git rev-parse HEAD)"; \
	fi; \
	printf '# publish-github\n\nStatus: released\nRelease: %s\n' "$$version" > build/summaries/publish-github.md
