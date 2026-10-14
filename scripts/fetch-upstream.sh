#!/usr/bin/env bash
# Clones the upstream McHeli CE sources (reference only, git-ignored) at the commit the port is based on.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p upstream
[ -d upstream/McHeliCE ] || git clone --filter=blob:none https://github.com/Warfactory-Offical/McHeliCE.git upstream/McHeliCE
git -C upstream/McHeliCE checkout -q cbaff7f30e549ff75e485388c24c74a4316c2228
[ -d upstream/McHeliCE.wiki ] || git clone https://github.com/Warfactory-Offical/McHeliCE.wiki.git upstream/McHeliCE.wiki
echo "Upstream ready in upstream/"
