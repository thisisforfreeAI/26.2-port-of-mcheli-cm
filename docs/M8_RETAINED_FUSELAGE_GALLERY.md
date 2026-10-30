# Plane rear fuselages stay attached

Unedited native Minecraft screenshots using the production plane models and renderer. These
are held client damage/wreck states, not actual crash recordings. Each damaged plane has its
rear structural component at zero health. The wreck images also retain the existing wing-loss
stage; the engine section and rear fuselage remain part of the main aircraft.

| Aircraft | Rear damage while alive | Late wreck stage |
| --- | --- | --- |
| F-22A | ![F-22 damaged rear stays attached](screens/m8-retained-fuselage/f22a-tail-damage.png) | ![F-22 wreck retains rear](screens/m8-retained-fuselage/f22a-wreck.png) |
| B-2A | ![B-2 damaged rear stays attached](screens/m8-retained-fuselage/b-2a-tail-damage.png) | ![B-2 wreck retains center and rear](screens/m8-retained-fuselage/b-2a-wreck.png) |
| F-35A | ![F-35 damaged rear stays attached](screens/m8-retained-fuselage/f-35a-tail-damage.png) | ![F-35 wreck retains rear](screens/m8-retained-fuselage/f-35a-wreck.png) |
| A-10 | ![A-10 damaged rear stays attached](screens/m8-retained-fuselage/a-10-tail-damage.png) | ![A-10 wreck retains rear](screens/m8-retained-fuselage/a-10-wreck.png) |

The correction applies to every plane through the common breakup builder. Bounded smaller
rudder/elevator panel damage remains; whole-rear separation is retired. Vulkan and OpenGL
checks cover damage present at spawn, a new live-damage transition and the late wreck stage.

[Implementation and scope](M8_RETAINED_FUSELAGE.md).
