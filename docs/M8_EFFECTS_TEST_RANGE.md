# M8 effects test range

Use **ESC → MC Heli Effects → Test range**. Select a weapon, surface and lighting, then
Enter range. Entry is available to the singleplayer owner or a server operator. Dismount first.
The separate flat dimension provides four reserved pads, spaced 256 blocks apart.

Choose **Fire demo** to launch the actual Hydra 70, AGM-158, Mk 82 or GBU-57 through the existing
weapon factory and projectile code. Watch in spectator mode and move freely around the impact.
The comparison uses the weapon's existing damage, terrain destruction, smoke and explosion
settings. It does not approximate an impact with a manually spawned explosion. The AGM uses
its existing straight-flight mode. NTM payloads, destruct weapons and arbitrary weapon names
are excluded, including addon replacements that carry NTM flags.

Sand, soil/grass, stone and water pads expose the existing material-specific effects. Day,
dusk and night use the range's own clock and timeline. Your normal world's clock, weather and
terrain are unchanged. Range lighting is shared among range visitors. Reset pad removes the
session's remaining projectiles, clears its viewer's old particles/scorch marks and restores
the target area. A preparing indicator disables firing until the terrain is ready. A visible
cooldown respects at least the weapon's authored delay between demonstrations. Fire waits
for the server to confirm completion, even when the client clock runs ahead during a reset.

Detail, Classic/Polished and Current M8/Modern controls change the existing client settings.
They remain selected when you return. The optional/default vanilla explosion-particle setting
and the other full effects options continue to apply. The displayed FPS comes from the actual
client frame counter; it is not inferred from particle counts. Reset activity is preparation,
so compare frame rate after the pad is ready and during the same weapon/lighting sequence.

**Return** restores your saved dimension, location, view, game mode and ability flags/speeds.
Your inventory is not replaced. Logout/login recovery also restores an interrupted visit.
`/mcheli_range return` provides the same return path; `/mcheli_range enter` opens the default
comparison under the same permission check. If the saved dimension is no longer available,
the return path uses the overworld spawn. Minecraft may show its normal confirmation the
first time a world loads the new custom dimension.

Terrain reset clears a 49×49 area at Y 0…31, then rebuilds supported material at Y −12…−1 from
the bottom up. Across all four sessions it changes at most 2,048 blocks per server tick.
Reset does not allocate a list of every position. Projectile cleanup follows the session's
launcher identity, including its child projectiles; it does not delete unrelated entities.
The temporary spectator pads do not retain dropped terrain items, so repeated tests do not
accumulate item entities. Ordinary dimensions and player inventories keep their normal drops.
Idle sessions do not scan entities or reset terrain. No additional explosion particles are
admitted beyond the existing graphics settings. Original gameplay fire rates and craters,
Claude's vehicle art/AI, and NTM integration are unchanged.

[Native impact gallery](M8_EFFECTS_TEST_RANGE_GALLERY.md) and
[machine-readable checks](performance/m8-realism/effects-test-range.json) accompany this feature.
528 local unit tests pass after integration with the latest main, including reset-footprint bounds, payloads, independent clock data
and regressions for a fast client clock and notifications during pad preparation. Four native
Vulkan and three OpenGL scenarios pass, covering all four weapons, all surfaces, day/dusk/night,
Classic/Modern, the real UI at 320×240 and original position/mode restoration. Both runs reach
the 2,048 reset-operation bound and keep the fixture's frozen original-world clock unchanged.
Native testing uses one concurrent session; the global four-session limit remains enforced
in the server scheduler. An additional Vulkan entry/impact/return check passes on that combined source.
A GameTest checks the operator/non-operator boundary with a connected player, including the
server's rejection messages.

Software Mesa preview results do not establish 60 FPS on the target RTX 3060 / 7800X3D at 1080p.
