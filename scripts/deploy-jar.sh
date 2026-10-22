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

# Validate compose/env before touching the running application.
APP_IMAGE="$image" docker compose -f docker-compose.prod.yml config -q

previous_image_id="$(docker inspect --format '{{.Image}}' yacha-backend 2>/dev/null || true)"
rollback_tag="yacha-backend:rollback-${sha}"
if [[ -n "$previous_image_id" ]]; then
  docker tag "$previous_image_id" "$rollback_tag"
fi

rollback() {
  if [[ -n "$previous_image_id" ]]; then
    echo 'Rolling back to the previously running application image...' >&2
    APP_IMAGE="$rollback_tag" docker compose -f docker-compose.prod.yml up -d --no-deps --no-build app || true
  fi
}

if ! APP_IMAGE="$image" docker compose -f docker-compose.prod.yml up -d --no-deps --no-build app; then
  rollback
  exit 1
fi

expected_id="$(docker image inspect --format '{{.Id}}' "$image")"
actual_id="$(docker inspect --format '{{.Image}}' yacha-backend)"
if [[ "$expected_id" != "$actual_id" ]]; then
  echo 'Container was not started from the intended image' >&2
  rollback
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
  rollback
  exit 1
fi

echo "Application healthy. Deployed commit: ${sha}"
