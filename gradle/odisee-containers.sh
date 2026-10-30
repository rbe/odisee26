#!/usr/bin/env bash
# Stop, remove, or delete the images of the Odisee test containers and the compose project.
# A missing container or image is success. This script does not remove any other image.
set -eu

if [ "$#" -lt 4 ]; then
  echo 'usage: odisee-containers.sh stop|rm|images PODMAN COMPOSE_DIR VERSION [IMAGE...]' >&2
  exit 1
fi

action=$1
podman=$2
compose_dir=$3
export VERSION=$4
shift 4

compose() {
  "$podman" compose -p odisee --project-directory "$compose_dir" -f "$compose_dir/compose.yaml" "$@"
}

# Containers started by startLibreOffice, startMinio, and startKeycloak.
test_containers=(
  odisee-lo-test
  odisee-minio-test
  odisee-keycloak-test
)

stop_container() {
  local name=$1
  if "$podman" container exists "$name"; then
    "$podman" stop "$name"
  fi
}

# podman rm -f exits non-zero when the name is already gone.
remove_container() {
  local name=$1
  if "$podman" rm -f "$name"; then
    return 0
  fi
  if "$podman" container exists "$name"; then
    echo "Could not remove container ${name}" >&2
    return 1
  fi
}

remove_image() {
  local image=$1
  if "$podman" image exists "$image"; then
    "$podman" rmi "$image"
  fi
}

case "$action" in
  stop)
    for name in "${test_containers[@]}"; do
      stop_container "$name"
    done
    compose stop
    ;;
  rm)
    for name in "${test_containers[@]}"; do
      remove_container "$name"
    done
    compose down
    ;;
  images)
    if [ "$#" -eq 0 ]; then
      echo 'images needs at least one image name' >&2
      exit 1
    fi
    for image in "$@"; do
      remove_image "$image"
    done
    ;;
  *)
    echo "Unknown action ${action}" >&2
    exit 1
    ;;
esac
