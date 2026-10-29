# M8 flyby audio

Engine loops retain their original mono sound samples and authored sound range. Enhanced /
Polished effects add smoothed Doppler from relative motion in three dimensions, modest
rearward emphasis for jets and a smooth fade at the range boundary. Initial observations,
large source/listener teleports and own riders use neutral Doppler. Helicopters keep their
rotor character; this does not replace their recordings with synthesized jet noise.

`effects.flybyAudio` (default on) controls these refinements and incoming-projectile cues.
Classic / the opt-out retain the old engine pitch behavior. Regardless of style, engine
updaters select the nearest 12 / 24 / 32 audible vehicles at Low / Balanced / High detail,
with rider priority and small selection hysteresis. Effect pressure reduces these caps.
Silent, destroyed, removed and out-of-range sources do not retain engine updaters.

Conventional rockets and guided missiles get a short mono air-rush on close incoming passes.
Cannon-class bullets get a narrower whizz. These are project-owned procedural recordings,
reproducible with `scripts/generate-flyby-audio.py`, not recordings from NTM or another mod.
They follow the projectile in 3D, fade quickly and stop when it disappears. Existing firing
and impact sounds continue normally. This is a restrained near-miss accent rather than a
continuous alarm or physically exact supersonic bullet simulation.

Admission uses the existing engine-audio entity scan: no extra world scan or network payload.
At most 256 projectiles are considered per tick, 128 tracks retained, four cues playing,
and one cue starts every five ticks. A stable pair of observations is needed; only passes
predicted within six ticks and within 12 blocks (rocket) / five blocks (shell) qualify.
Own shots, underwater shots, bombs, torpedoes, dispensers, markers and either NTM flag are
excluded. A conventional missile's `disableSmoke` setting does not disable its sound.
World changes, logout and effect disable clear the owned sound instances.

Native fixtures use real aircraft and weapon definitions on controlled client trajectories;
they do not run AI combat or change flight/projectile physics. Screenshots show those aircraft,
not a visible representation of audio. The local silent OpenAL device validates sound resource
loading, pitch/volume and admission, but cannot establish the quality of the audible mix.
Human listening and the 60 FPS / 1080p RTX 3060 / Ryzen 7800X3D acceptance check remain necessary.

Validation: 492 combined-main local unit tests passed. Three Vulkan aircraft passes assert
active engine instances as well as higher approach / lower receding pitch. Eleven OpenGL
cases cover a vertical pass, own pilot, disabled / Classic, real Hydra / GAU-8 / smoke-disabled
AGM-158 cues, both NTM flags and own-shot exclusions, 128 projectiles, and 64-aircraft
selection at Low / Balanced. Incoming cases assert active sound instances; staggered load
reaches four admitted cues before a live-disable check clears all owned projectile state.
The fixture explicitly enables master volume rather than inheriting the preview world's settings.

[Native aircraft examples and cue files](M8_FLYBY_GALLERY.md).
