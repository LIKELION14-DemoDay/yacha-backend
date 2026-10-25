#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -ne 2 || ! "$1" =~ ^[[:xdigit:]]{40}$ || ! "$2" =~ ^[a-z0-9][a-z0-9.-]*$ ]]; then
  echo 'Usage: deploy-jar.sh <40-character commit SHA> <private S3 bucket>' >&2
  exit 2
fi

sha="$1"
bucket="$2"
root=/home/ec2-user/yacha-backend
image="yacha-backend:${sha}"
key="ci-jars/yacha-backend/${sha}"
cd "$root"

# The caller holds /var/lock/yacha-deploy.lock across git sync and this script.
stage="$(mktemp -d /tmp/yacha-build-XXXXXX)"
trap 'rm -rf "$stage"' EXIT

command -v aws >/dev/null
command -v docker >/dev/null
aws s3 cp "s3://${bucket}/${key}/app.jar" "$stage/app.jar" --only-show-errors
aws s3 cp "s3://${bucket}/${key}/app.jar.sha256" "$stage/app.jar.sha256" --only-show-errors
(cd "$stage" && sha256sum -c app.jar.sha256)

cp "$root/Dockerfile.runtime" "$stage/Dockerfile"
docker build --tag "$image" "$stage"

# Validate compose and production env before changing the running application.
APP_IMAGE="$image" docker compose -f docker-compose.prod.yml config -q

previous_image_id="$(docker inspect --format '{{.Image}}' yacha-backend 2>/dev/null || true)"
rollback_tag="yacha-backend:rollback-${sha}"
if [[ -n "$previous_image_id" ]]; then
  docker tag "$previous_image_id" "$rollback_tag"
fi

rollback() {
  if [[ -z "$previous_image_id" ]]; then
    echo 'Rollback unavailable: no previous application container/image' >&2
    return 1
  fi

  echo 'Rolling back to the previous application image...' >&2
  if ! APP_IMAGE="$rollback_tag" docker compose -f docker-compose.prod.yml up -d --no-deps --no-build app; then
    echo 'Rollback failed: Docker Compose could not start the previous image' >&2
    return 1
  fi

  # A successful Compose command alone does not guarantee a healthy rollback.
  for i in $(seq 1 30); do
    restored_id="$(docker inspect --format '{{.Image}}' yacha-backend 2>/dev/null || true)"
    if [[ "$restored_id" == "$previous_image_id" ]] &&
       curl -fsS --max-time 3 http://127.0.0.1:8080/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
      echo 'Rollback succeeded: previous application is healthy' >&2
      return 0
    fi
    sleep 5
  done

  echo 'Rollback container did not pass the health check' >&2
  docker logs --tail 100 yacha-backend >&2 || true
  return 1
}

if ! APP_IMAGE="$image" docker compose -f docker-compose.prod.yml up -d --no-deps --no-build app; then
  rollback || echo 'WARNING: rollback did not restore a healthy app' >&2
  exit 1
fi

expected_id="$(docker image inspect --format '{{.Id}}' "$image")"
actual_id="$(docker inspect --format '{{.Image}}' yacha-backend 2>/dev/null || true)"
if [[ "$expected_id" != "$actual_id" ]]; then
  echo 'Container was not started from the intended image' >&2
  rollback || echo 'WARNING: rollback did not restore a healthy app' >&2
  exit 1
fi

healthy=false
for i in $(seq 1 60); do
  if curl -fsS --max-time 3 http://127.0.0.1:8080/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
    healthy=true
    break
  fi
  sleep 5
done

if [[ "$healthy" != true ]]; then
  echo 'Health check failed; recent application logs:' >&2
  docker logs --tail 100 yacha-backend >&2 || true
  rollback || echo 'WARNING: rollback did not restore a healthy app' >&2
  exit 1
fi

# Remove older application image tags only after the new app is confirmed healthy.
# Keep the active app image and its immediate rollback image. Never prune volumes.
while IFS= read -r tag; do
  [[ -n "$tag" ]] || continue
  [[ "$tag" == "$image" || "$tag" == "$rollback_tag" ]] && continue
  tag_id="$(docker image inspect --format '{{.Id}}' "$tag" 2>/dev/null || true)"
  [[ -n "$tag_id" ]] || continue
  [[ "$tag_id" == "$expected_id" || "$tag_id" == "$previous_image_id" ]] && continue
  docker image rm "$tag" >/dev/null 2>&1 || true
done < <(docker image ls --format '{{.Repository}}:{{.Tag}}' | grep -E '^yacha-backend:(rollback-)?[[:xdigit:]]{40}$' || true)

docker image prune -f >/dev/null 2>&1 || true

echo "Application healthy. Deployed commit: ${sha}"
