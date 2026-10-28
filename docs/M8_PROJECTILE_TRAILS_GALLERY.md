# Missile and rocket trails: native examples

Unedited 1280×720 captures of real weapon definitions/models on held trajectories through the
production client effects path. These are controlled previews, not combat recordings. OpenGL
launch views include A-10, F-35A and F-15E context; Vulkan flight views show the trails more closely.

Solid motors keep fuller billows and wisps. The Harpoon uses a thinner cruise-exhaust profile.
The stock AGM-158 disables smoke and stays smokeless, with a restrained motor point.
[Implementation, compatibility and bounds](M8_PROJECTILE_TRAILS.md).

## Hydra 70 — A-10 context

Launch context (OPENGL)

![Hydra 70 — A-10 context — Launch context](screens/m8-projectile-trails/m8-projectile-hydra70-launch.png)

Flight and expanding trail (VULKAN)

![Hydra 70 — A-10 context — Flight and expanding trail](screens/m8-projectile-trails/m8-projectile-hydra70-flight.png)

Closer flight view (VULKAN)

![Hydra 70 — A-10 context — Closer flight view](screens/m8-projectile-trails/m8-projectile-hydra70-late.png)

## AIM-9 — F-35A context

Launch context (OPENGL)

![AIM-9 — F-35A context — Launch context](screens/m8-projectile-trails/m8-projectile-aim9-launch.png)

Flight and expanding trail (VULKAN)

![AIM-9 — F-35A context — Flight and expanding trail](screens/m8-projectile-trails/m8-projectile-aim9-flight.png)

Closer flight view (VULKAN)

![AIM-9 — F-35A context — Closer flight view](screens/m8-projectile-trails/m8-projectile-aim9-late.png)

## AGM-84 Harpoon — F-15E context

Launch context (OPENGL)

![AGM-84 Harpoon — F-15E context — Launch context](screens/m8-projectile-trails/m8-projectile-agm84-launch.png)

Flight and expanding trail (VULKAN)

![AGM-84 Harpoon — F-15E context — Flight and expanding trail](screens/m8-projectile-trails/m8-projectile-agm84-flight.png)

Closer flight view (VULKAN)

![AGM-84 Harpoon — F-15E context — Closer flight view](screens/m8-projectile-trails/m8-projectile-agm84-late.png)

## AGM-158 — authored smokeless exhaust

Launch context (OPENGL)

![AGM-158 — authored smokeless exhaust — Launch context](screens/m8-projectile-trails/m8-projectile-agm158-smokeless-launch.png)

Closer flight view (VULKAN)

![AGM-158 — authored smokeless exhaust — Closer flight view](screens/m8-projectile-trails/m8-projectile-agm158-smokeless-late.png)

## Authored motor-window fixture

An isolated AIM-9 fixture uses start/end ages 10/90. It demonstrates cosmetic burnout;
the registered stock AIM-9 definition and its thrust/guidance are unchanged.

Motor active (VULKAN)

![Authored motor-window fixture — Motor active](screens/m8-projectile-trails/m8-projectile-authored-burnout-flight.png)

Motor extinguished; earlier smoke remains (VULKAN)

![Authored motor-window fixture — Motor extinguished; earlier smoke remains](screens/m8-projectile-trails/m8-projectile-authored-burnout-late.png)

## 32 missiles — Balanced

A deliberately dense held-trajectory load, with adaptive reduction disabled to check fixed limits.

Closer flight view (VULKAN)

![32 missiles — Balanced — Closer flight view](screens/m8-projectile-trails/m8-projectile-shared-load-late.png)

## 32 missiles — Low

A deliberately dense held-trajectory load, with adaptive reduction disabled to check fixed limits.

Closer flight view (VULKAN)

![32 missiles — Low — Closer flight view](screens/m8-projectile-trails/m8-projectile-low-load-late.png)

Both backends also check Classic, an isolated NTM-filter fixture, authored smoke disabling,
cutoff, drain and shared limits. No NTM integration or payload is invoked by the filter fixture.
Software Mesa checks do not measure target-PC 60 FPS / 1080p.
