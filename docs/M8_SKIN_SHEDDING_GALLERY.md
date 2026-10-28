# Aircraft skin shedding: native examples

Unedited 1280×720 Vulkan captures of controlled component damage on real vehicle models.
Small painted skin fragments tumble after a fresh cut, then fade. They are deliberately tiny;
the large wing/tail fragments, fuel spray and exposed cables are existing effects. Close views
make the small chips easier to inspect below and behind the cut. No image has been altered.

[Implementation and bounds](M8_SKIN_SHEDDING.md). The scenes are held client states, not combat recordings.

## F-35A

Wing breakup with the existing large fragment

![F-35A — Wing breakup with the existing large fragment](screens/m8-skin-shedding/m8-skin-f-35a-wing-overview.png)

Wing cut and small trailing skin pieces

![F-35A — Wing cut and small trailing skin pieces](screens/m8-skin-shedding/m8-skin-f-35a-wing-close.png)

Tail cut and shedding

![F-35A — Tail cut and shedding](screens/m8-skin-shedding/m8-skin-f-35a-tail-close.png)

Repaired aircraft

![F-35A — Repaired aircraft](screens/m8-skin-shedding/m8-skin-f-35a-repaired.png)

## F-15E

Wing breakup with the existing large fragment

![F-15E — Wing breakup with the existing large fragment](screens/m8-skin-shedding/m8-skin-f-15e-wing-overview.png)

Wing cut and small trailing skin pieces

![F-15E — Wing cut and small trailing skin pieces](screens/m8-skin-shedding/m8-skin-f-15e-wing-close.png)

Tail cut and shedding

![F-15E — Tail cut and shedding](screens/m8-skin-shedding/m8-skin-f-15e-tail-close.png)

Repaired aircraft

![F-15E — Repaired aircraft](screens/m8-skin-shedding/m8-skin-f-15e-repaired.png)

## A-10

Wing breakup with the existing large fragment

![A-10 — Wing breakup with the existing large fragment](screens/m8-skin-shedding/m8-skin-a-10-wing-overview.png)

Wing cut and small trailing skin pieces

![A-10 — Wing cut and small trailing skin pieces](screens/m8-skin-shedding/m8-skin-a-10-wing-close.png)

Tail cut and shedding

![A-10 — Tail cut and shedding](screens/m8-skin-shedding/m8-skin-a-10-tail-close.png)

Repaired aircraft

![A-10 — Repaired aircraft](screens/m8-skin-shedding/m8-skin-a-10-repaired.png)

## Fresh wreck breakup

Wing cut and small trailing skin pieces

![Fresh wreck breakup — Wing cut and small trailing skin pieces](screens/m8-skin-shedding/m8-skin-fresh-wreck-wing-close.png)

Tail cut and shedding

![Fresh wreck breakup — Tail cut and shedding](screens/m8-skin-shedding/m8-skin-fresh-wreck-tail-close.png)

## 32-aircraft shared load

Wing breakup with the existing large fragment

![32-aircraft shared load — Wing breakup with the existing large fragment](screens/m8-skin-shedding/m8-skin-shared-load-wing-overview.png)

Loaded damage and style re-enable produce no fresh skin pieces. Repairs and settings clear them.
All pieces share the existing eight-mesh limit. Software Mesa checks do not establish target-PC FPS.
