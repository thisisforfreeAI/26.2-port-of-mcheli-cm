# Tank cook-off correction examples

Original 1280×720 Vulkan captures of real server-owned tanks. The spectator never mounts them and
receives its ammo counts through the normal network path. The fixture fills ammo on the server and
invokes the existing server destruction API; these are controlled deaths, not weapon-impact captures.

## M1A2 pressure panels

| Opening above the bustle | Panels ejected; turret retained |
| --- | --- |
| ![M1A2 pressure panels opening](screens/m8-tank-cookoff/m8-tank-cookoff-m1a2-opening.png) | ![M1A2 venting through the open bustle](screens/m8-tank-cookoff/m8-tank-cookoff-m1a2-venting.png) |

![M1A2 open compartment aftermath](screens/m8-tank-cookoff/m8-tank-cookoff-m1a2-aftermath.png)

The pressure roof behind the crew hatches opens independently. It does not eject the protected
turret design. The remaining turret roof, crew openings and rear rack retain their original geometry.

## T-90 complete turret

| Before the cook-off stage | Complete turret and gun separated |
| --- | --- |
| ![T-90 before turret separation](screens/m8-tank-cookoff/m8-tank-cookoff-t-90-before.png) | ![T-90 turret in flight](screens/m8-tank-cookoff/m8-tank-cookoff-t-90-separated.png) |

![T-90 turret after landing](screens/m8-tank-cookoff/m8-tank-cookoff-t-90-landed.png)

## T84 complete turret

| Before the cook-off stage | Complete posed turret separated |
| --- | --- |
| ![T84 before turret separation](screens/m8-tank-cookoff/m8-tank-cookoff-t84-before.png) | ![T84 turret in flight](screens/m8-tank-cookoff/m8-tank-cookoff-t84-separated.png) |

![T84 turret after landing](screens/m8-tank-cookoff/m8-tank-cookoff-t84-landed.png)

Both carousel examples use ordinary UUID seeds that failed the former one-in-eight random gate.

## Full debris budget

| Pressure panels remain raised | Turret remains visibly dislodged |
| --- | --- |
| ![M1A2 lids open with full fragment slots](screens/m8-tank-cookoff/m8-tank-cookoff-busy-m1a2-opening.png) | ![T-90 budget fallback](screens/m8-tank-cookoff/m8-tank-cookoff-busy-t90-separated.png) |

These two diagnostic scenes deliberately occupy the eight fragment slots with tiny airborne pieces
far outside the camera. The visible panels/assembly remain on screen instead of disappearing when
admission fails. [Implementation, eligibility and limits](M8_TANK_COOKOFF_PARTS.md).
