#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -x ./gradlew ]]; then
  ./gradlew :app:bundleRelease
else
  gradle :app:bundleRelease
fi
