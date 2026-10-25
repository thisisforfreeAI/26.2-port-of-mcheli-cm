# Destruction effects follow-ups

Implement in order, with native in-game previews on three stock vehicles for each feature.
Keep the original crater and damage rules, preserve Claude's NTM work and target 60 FPS on
RTX 3060 8GB / Ryzen 7 7800X3D at 1080p. Hardware FPS needs target-PC verification.

| Step | Feature | Status |
| --- | --- | --- |
| 1 | Uneven damaged-engine smoke trails that stretch with movement | Complete |
| 2 | Coolant/oil leaks and temporary ground stains | Pending |
| 3 | Directional tumbling metal fragments | Pending |
| 4 | Flickering/failing damaged lights and local sparks | Pending |
| 5 | Wreck fires, embers and cooling smoke that die away | Pending |
| 6 | Localized blast soot, exposed metal and panel smoke | Pending |
| 7 | Rattling and detachable small authored panels | Pending |
| 8 | Progressive cracks on identifiable glazing | Pending |

All additions are cosmetic and respect the existing damage-details switch, distance and
quality controls. Avoid unbounded particles, repeated full-model scans, world lighting
updates and new gameplay entities. Temporary previews use controlled vehicle states;
images are native screenshots, never generated mockups.

## 1. Damaged engine trails

Smoke sheds only a small part of vehicle velocity, with uneven pulses, darker cores and
shorter-lived wisps. Maximum radius is charged to existing particle admission; no collision
queries are added. Native previews on M1A2/AH-64/F-35A and 301 local unit tests pass.

![F-35A engine trail](screens/m8-destruction-01-engine-trails.png)
