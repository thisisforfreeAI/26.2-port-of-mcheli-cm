# M8 aircraft customization

Open a vehicle's inventory and choose **Appearance**. Cycle through that definition's existing
liveries, then Apply or Cancel. Repainting requires the same nearby, live vehicle menu, an idle
engine and little movement. The server checks the existing texture-name list and team access;
a client cannot submit an arbitrary texture path or repaint a different vehicle.

The F-35A, F-15E and A-10 additionally support optional three-digit identifiers (blank removes
them), a chevron, star or bars, pale/low-visibility/olive paint, and clean/service/worn paint.
These markings occupy explicitly fitted areas of the intact nose. Wear adds sparse paint
chips around that area; it is not a replacement weathered texture for the whole airframe.
Other vehicles retain the livery picker and show that these marking layouts are unavailable.
No new livery artwork or replacements for Claude's broken-section/wreck assets are included.

Selections synchronize through one packed cosmetic integer, and save as `M8Appearance` beside
the existing `TextureName`. Old saves default to no new marks or wear. Invalid saved cosmetic
bits safely restore that default. Identifiers are numbers only; there are no free-text or
external-image uploads. Changing controls sends nothing until Apply. Opening the appearance
panel preserves a pending command-field edit through the existing operator-checked save path.

Marks follow the original body's extracted pose and actual fitted triangles. The model loader
prepares five small patches on each side once, preserving gaps. A shared original pixel atlas
contains digits, symbols and paint chips. All marks use ambient lighting and no emissive glow.
Destroyed, thermal and distant views skip them, preserving the separate damage/wreck rendering.
The client `effects.aircraftCustomization` option defaults on for Enhanced / Polished effects;
liveries continue to work with the earlier presentation as well.

Rendering uses one atlas batch per nearby vehicle, a 96-block range and a shared per-view
allowance of 2,048 / 6,144 / 12,288 vertices at Low / Balanced / High, reduced under pressure.
A weak cache holds at most 32 original body groups. Numbers change atlas coordinates during
submission; selecting a new identifier does not generate or cache a new model or texture.
No additional entity scans, particles, physics or weapon changes are introduced.

[Native examples](M8_AIRCRAFT_CUSTOMIZATION_GALLERY.md) and
[machine-readable checks](performance/m8-realism/aircraft-customization.json) record coverage.
519 local unit tests pass. Three Vulkan scenes and four OpenGL checks pass, including
Classic/off, shared load and the real menu request at 320×240 GUI size. The load admits
4,602 marking vertices within the Balanced 6,144 allowance. A GameTest also covers server
menu validation and save/load persistence. The vehicle captures are controlled model views; the separate menu test uses a real server
vehicle, menu, request and synchronized result. Target-PC 60 FPS acceptance remains open.
