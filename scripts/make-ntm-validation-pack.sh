#!/bin/sh
# Builds an addon pack that gives six stock rockets an NTM Next payload, for checking the
# optional bridge in game (docs/M7_PLAN.md, "Optional NTM Next bridge"). Then start the
# server and client with -PwithHbm and fire the rockets of these vehicles:
#
#   AH-6 Hydra (hydra70_l)          chemical (chlorine)
#   AH-64 Hydra (hydra70)           fire (phosphorus)
#   Mi-28 S-8 (s-8)                 mini-nuke (SAFE preset)
#   MH-60L DAP Hydra (hydra70_10)   VNT (standard)
#   Tornado GR4 CRV7 (crv7)         mist (water)
#   W3 S-13 (s-13)                  nuke
#
# Usage: scripts/make-ntm-validation-pack.sh [--effect-only] [game dir relative to the repository, default run]
set -eu
cd "$(dirname "$0")/.."
effect_only=false
if [ "${1:-}" = "--effect-only" ]; then effect_only=true; shift; fi
game_dir=${1:-run}
src=src/main/resources/assets/mcheli/weapons
pack=$game_dir/mcheli_addons/ntm_validation
out=$pack/assets/mcheli/weapons
mkdir -p "$out"
cat > "$pack/pack.mcmeta" <<'JSON'
{"pack":{"pack_format":3,"description":"NTM Next validation payloads"},"addon":{"domain":"ntm_validation","version":"1"}}
JSON
add() {
    cp "$src/$1.yml" "$out/$1.yml"
    printf 'NTM:\n  PayloadType: %s\n  EffectOnly: %s\n%s\n' "$2" "$effect_only" "$3" >> "$out/$1.yml"
}
add hydra70_l CHEMICAL '  Chemical:
    Type: CHLORINE
    Count: 8
    Speed: 0.2'
add hydra70 FIRE '  Fire:
    FireType: PHOSPHORUS
    Count: 16
    Width: 2.0
    Height: 1.0
    AreaSpread: 4
    Lifetime: 200
    LifetimeVariance: 40
    Particles: true'
add s-8 MININUKE '  MiniNuke: SAFE'
add hydra70_10 VNT '  VNT:
    Preset: STANDARD
    Effect: STANDARD'
add crv7 MIST '  Mist:
    FluidType: WATER
    CloudCount: 6
    Width: 3.0
    Height: 2.0
    AreaSpread: 4
    Lifetime: 200
    LifetimeVariance: 40'
add s-13 NUKE ''
echo "Wrote $pack"
