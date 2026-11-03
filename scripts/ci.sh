#!/usr/bin/env bash
#
# The project's CI, run locally: a clean build with every test.
#
#   scripts/ci.sh
#
# Run by .githooks/pre-push before every push; enable the hooks once with:
#   git config core.hooksPath .githooks
#
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")/.."   # the project root
. scripts/externals.sh   # setup done? (offers it otherwise); the externals first
running_instance "the build"
take_build_lock
exec mvn -q clean package
