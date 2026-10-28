# Aircraft fracture interiors: native examples

Unedited 1280×720 Vulkan captures of held aircraft using production component damage and rendering.
These are controlled client scenes, not combat screenshots. Each plane loses its left wing, is repaired,
then loses its tail; the wreck view follows, then a complete repair.

The added details are small: exposed spars and hanging cables sit at the cut skin. Dark inset
cross-sections appear only where the source mesh supplies closed contours. Open wing contours on
the F-35A and F-15E retain their openings rather than acquiring a fabricated wall. Existing fuel
spray, smoke and fire remain visible. [Implementation and limits](M8_FRACTURE_INTERIORS.md).

## F-35A

Left-wing loss: overall silhouette

![F-35A — Left-wing loss: overall silhouette](screens/m8-fracture-interiors/m8-fracture-f-35a-wing-overview.png)

Wing cut: exposed spars and cables

![F-35A — Wing cut: exposed spars and cables](screens/m8-fracture-interiors/m8-fracture-f-35a-wing-close.png)

Tail cut: recessed interior

![F-35A — Tail cut: recessed interior](screens/m8-fracture-interiors/m8-fracture-f-35a-tail-close.png)

Wreck stage with existing fire

![F-35A — Wreck stage with existing fire](screens/m8-fracture-interiors/m8-fracture-f-35a-wreck-close.png)

Repaired aircraft

![F-35A — Repaired aircraft](screens/m8-fracture-interiors/m8-fracture-f-35a-repaired.png)

## F-15E

Left-wing loss: overall silhouette

![F-15E — Left-wing loss: overall silhouette](screens/m8-fracture-interiors/m8-fracture-f-15e-wing-overview.png)

Wing cut: exposed spars and cables

![F-15E — Wing cut: exposed spars and cables](screens/m8-fracture-interiors/m8-fracture-f-15e-wing-close.png)

Tail cut: recessed interior

![F-15E — Tail cut: recessed interior](screens/m8-fracture-interiors/m8-fracture-f-15e-tail-close.png)

Wreck stage with existing fire

![F-15E — Wreck stage with existing fire](screens/m8-fracture-interiors/m8-fracture-f-15e-wreck-close.png)

Repaired aircraft

![F-15E — Repaired aircraft](screens/m8-fracture-interiors/m8-fracture-f-15e-repaired.png)

## A-10

Left-wing loss: overall silhouette

![A-10 — Left-wing loss: overall silhouette](screens/m8-fracture-interiors/m8-fracture-a-10-wing-overview.png)

Wing cut: exposed spars and cables

![A-10 — Wing cut: exposed spars and cables](screens/m8-fracture-interiors/m8-fracture-a-10-wing-close.png)

Tail cut: recessed interior

![A-10 — Tail cut: recessed interior](screens/m8-fracture-interiors/m8-fracture-a-10-tail-close.png)

Wreck stage with existing fire

![A-10 — Wreck stage with existing fire](screens/m8-fracture-interiors/m8-fracture-a-10-wreck-close.png)

Repaired aircraft

![A-10 — Repaired aircraft](screens/m8-fracture-interiors/m8-fracture-a-10-repaired.png)

## Classic comparison

Left-wing loss: overall silhouette

![Classic comparison — Left-wing loss: overall silhouette](screens/m8-fracture-interiors/m8-fracture-classic-wing-overview.png)

## Damage details disabled

Left-wing loss: overall silhouette

![Damage details disabled — Left-wing loss: overall silhouette](screens/m8-fracture-interiors/m8-fracture-details-off-wing-overview.png)

## 32-aircraft shared load

Left-wing loss: overall silhouette

![32-aircraft shared load — Left-wing loss: overall silhouette](screens/m8-fracture-interiors/m8-fracture-shared-load-wing-overview.png)

Software Mesa validation checks rendering and bounds; it does not establish target-PC 60 FPS.
