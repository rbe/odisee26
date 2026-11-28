# DEPS_TOKEN gives git read access to private dependencies on GitHub,
# such as an npm package `github:OWNER/REPO#REF` or a pip `git+https` URL.
# One name holds on this machine, in the CI secret, and in Make:
#
#     DEPS_TOKEN=<token> make setup
#
# CI sets DEPS_TOKEN from the repository secret DEPS_TOKEN (r-github-pipeline).
# GitHub refuses a secret name that starts with GITHUB_, and the job token
# GITHUB_TOKEN cannot read another private repository.
#
# A project with private dependencies names them before the include:
#
#     DEPS_REPOS = OWNER/one OWNER/two
#
# Then setup stops before it starts when DEPS_TOKEN is empty, and names
# DEPS_REPOS. With TOOLS=host, git on the host may use its own login,
# so the check does not run. An empty DEPS_REPOS adds nothing.
#
# A tool container that runs git gets DEPS_TOKEN with $(DEPS_TOKEN_ENV)
# and starts its shell command with $(DEPS_GIT_AUTH). git then reads
# every github.com address, ssh or https, over HTTPS with the token. The
# token stays in variables of that shell and in no file. The image needs
# git and ca-certificates.
#
# container.mk passes DEPS_TOKEN to podman build as the build secret
# deps_token, which is in no layer of the image. references/makefile.md
# has the Containerfile lines that read it.
#
# GNU Make 3.81. See project.mk for the rules of a recipe.

ifndef DEPS_TOKEN_MK
DEPS_TOKEN_MK := 1

DEPS_REPOS ?=
DEPS_TOKEN ?=
DEPS_TOKEN_ENV = -e DEPS_TOKEN
DEPS_TOKEN_URL = https://x-access-token:$${DEPS_TOKEN}@github.com/
DEPS_GIT_AUTH = if [ -n "$${DEPS_TOKEN:-}" ]; then export GIT_CONFIG_COUNT=3 GIT_CONFIG_KEY_0="url.$(DEPS_TOKEN_URL).insteadOf" GIT_CONFIG_VALUE_0="ssh://git@github.com/" GIT_CONFIG_KEY_1="url.$(DEPS_TOKEN_URL).insteadOf" GIT_CONFIG_VALUE_1="git@github.com:" GIT_CONFIG_KEY_2="url.$(DEPS_TOKEN_URL).insteadOf" GIT_CONFIG_VALUE_2="https://github.com/"; fi
DEPS_TOKEN_COMMA := ,
DEPS_TOKEN_BUILD_ARGS = $(if $(DEPS_TOKEN),--secret id=deps_token$(DEPS_TOKEN_COMMA)env=DEPS_TOKEN)

.PHONY: deps-token

# An internal step of setup when DEPS_REPOS is set.
deps-token:
	@if [ -z "$${DEPS_TOKEN:-}" ]; then \
	  printf '%s\n' "deps-token: DEPS_TOKEN is empty. Set it to a token that reads $(DEPS_REPOS)." >&2; \
	  exit 1; \
	fi

ifneq ($(strip $(DEPS_REPOS)),)
ifneq ($(TOOLS),host)
setup: deps-token
endif
endif

endif
