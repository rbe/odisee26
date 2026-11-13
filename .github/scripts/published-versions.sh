#!/usr/bin/env bash
# Print the ids of published versions older than the current one and the one
# immediately before it. artifacts: id<TAB>name. images: id<TAB>tag,tag.
set -eu

mode=${1:?}
current=${2:?}

lines=()
while IFS= read -r line || [ -n "${line:-}" ]; do
  case "$line" in
    ''|'#'*) continue ;;
  esac
  lines+=("$line")
done

if [[ ! "$current" =~ ^([0-9]+\.[0-9]+\.[0-9]+)-([0-9]+)$ ]]; then
  exit 0
fi
base=${BASH_REMATCH[1]}
current_run=$((10#${BASH_REMATCH[2]}))
base_re=$(printf '%s' "$base" | sed 's/[].[^$*+?(){}|]/\\&/g')

if [ "${#lines[@]}" -eq 0 ]; then
  exit 0
fi

run_of_artifact() {
  local name=$1
  if [[ "$name" =~ ^odisee-${base_re}-([0-9]+)-linux-x86_64$ ]]; then
    printf '%s' "$((10#${BASH_REMATCH[1]}))"
    return 0
  fi
  return 1
}

runs_of_image() {
  local tags=$1 tag
  local IFS=','
  read -ra parts <<< "$tags"
  for tag in "${parts[@]}"; do
    tag=${tag//[[:space:]]/}
    if [[ "$tag" =~ ^${base_re}-([0-9]+)$ ]]; then
      printf '%s\n' "$((10#${BASH_REMATCH[1]}))"
    fi
  done
}

former=""
for line in "${lines[@]}"; do
  id=${line%%$'\t'*}
  payload=${line#*$'\t'}
  [ "$id" != "$line" ] || continue
  case "$mode" in
    artifacts)
      run=$(run_of_artifact "$payload") || true
      [ -n "${run:-}" ] || continue
      if [ "$run" -lt "$current_run" ] && { [ -z "$former" ] || [ "$run" -gt "$former" ]; }; then
        former=$run
      fi
      run=""
      ;;
    images)
      while IFS= read -r run; do
        [ -n "$run" ] || continue
        if [ "$run" -lt "$current_run" ] && { [ -z "$former" ] || [ "$run" -gt "$former" ]; }; then
          former=$run
        fi
      done <<EOF
$(runs_of_image "$payload")
EOF
      ;;
    *)
      printf 'unknown mode %s\n' "$mode" >&2
      exit 2
      ;;
  esac
done

kept() {
  local run=$1
  [ "$run" -ge "$current_run" ] && return 0
  [ -n "$former" ] && [ "$run" -eq "$former" ] && return 0
  return 1
}

for line in "${lines[@]}"; do
  id=${line%%$'\t'*}
  payload=${line#*$'\t'}
  [ "$id" != "$line" ] || continue
  [[ "$id" =~ ^[0-9]+$ ]] || continue
  case "$mode" in
    artifacts)
      run=$(run_of_artifact "$payload") || continue
      if ! kept "$run"; then
        printf '%s\n' "$id"
      fi
      ;;
    images)
      [ -n "$payload" ] || continue
      deletable=1
      saw=0
      IFS=',' read -ra parts <<< "$payload"
      for tag in "${parts[@]}"; do
        tag=${tag//[[:space:]]/}
        [ -n "$tag" ] || continue
        saw=1
        if [[ ! "$tag" =~ ^${base_re}-([0-9]+)$ ]]; then
          deletable=0
          break
        fi
        if kept "$((10#${BASH_REMATCH[1]}))"; then
          deletable=0
          break
        fi
      done
      if [ "$saw" -eq 1 ] && [ "$deletable" -eq 1 ]; then
        printf '%s\n' "$id"
      fi
      ;;
  esac
done
