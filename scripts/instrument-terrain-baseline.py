#!/usr/bin/env python3
"""Opt-in counters for the PR #61 synchronous terrain fitter; use in a disposable baseline checkout."""
import argparse
import hashlib
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('checkout', help='Disposable checkout at fcd9eb73bd44e054b0487063127b13ca22151e10')
args = parser.parse_args()
BASELINE_SHA256 = '9c977b9a07b11aa4a100dc6e7f4c1c87d0a93a99199e7947dff8be42886e9842'

p = Path(args.checkout) / 'src/main/java/io/github/mcheliceport/client/effect/BlastMarks.java'
s = p.read_text()
if hashlib.sha256(s.encode()).hexdigest() != BASELINE_SHA256:
    raise SystemExit('Refusing: the target file is not the unmodified PR #61 baseline')
s = s.replace('    private static int flashQuads;','''    private static int flashQuads;
    private static int reads, rays, checks;
    private static long tickNanos;''');s=s.replace('        long now = level.getGameTime();\n        EffectBudget budget', '''        long started = System.nanoTime();
        reads = rays = checks = 0;
        long now = level.getGameTime();
        EffectBudget budget''',1);s=s.replace('                boolean removed = mark.tiles.removeIf(tile -> !level.hasChunkAt(tile.pos)','                boolean removed = mark.tiles.removeIf(tile -> { checks++; return !level.hasChunkAt(tile.pos)').replace('!level.getBlockState(tile.pos.above()).getCollisionShape(level, tile.pos.above()).isEmpty());','!level.getBlockState(tile.pos.above()).getCollisionShape(level, tile.pos.above()).isEmpty(); });');s=s.replace('                    BlockState state = level.getBlockState(pos);','                    reads++;\n                    BlockState state = level.getBlockState(pos);');s=s.replace('                        var hit = level.clip(', '                        rays++;\n                        var hit = level.clip(');s=s.replace('    private static Mark project(', '    public static int projectionReads() { return reads; }\n    public static int projectionRays() { return rays; }\n    public static int maintenanceChecks() { return checks; }\n    public static long tickNanos() { return tickNanos; }\n    public static int pendingCount() { return PENDING.size(); }\n    public static int activeCount() { return 0; }\n\n    private static Mark project(');s=s.replace('    }\n\n    public static int projectionReads()', '        tickNanos = System.nanoTime() - started;\n    }\n\n    public static int projectionReads()',1);p.write_text(s)

print('Instrumented synchronous terrain counters:', p)
