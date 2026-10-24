# Explosion contrast follow-up

The opening impact should remain readable before dense smoke fills the frame. This follow-up
keeps the Minecraft sprite style and existing weapon families, ground dust, debris and wind.

## Presentation

- The existing 2–3 tick additive flash starts near white and cools toward orange. Its charged
  radius and optional-glow admission remain unchanged; there is no extra flash quad or bloom.
- Ground-level support smoke starts after 2–4 ticks; upper billows after 5–11 ticks. Particles
  are admitted at birth, so this does not introduce a deferred particle queue.
- Dense billows use 72–92% of the profile radius; edge wisps use 42–54% and lower opacity.
  Varied offsets break up the cloud outline. Existing shorter wisp lifetimes remain.
- Fire pockets are smaller and more separated. An independent contraction curve, bounded
  between 84% and 100% of the charged size, varies their rolling motion while hot. Each
  cools into ordinary smoke. Shared wind, weapon heading, bomb spread, incendiary persistence,
  fast material-specific ground dust and debris remain in effect.

Primary request caps, per-tick admission, live particle limits, terrain reads and fragment
caps are unchanged. Staging can add at most two hidden ticks within the existing 128-tick
cloud lifetime ceiling; visible core lifetime settings are not raised. This is a composition/timing pass,
not a particle-count increase. Rolling fire adds bounded arithmetic to existing quads.

## Audio

McHeli explosions now send a dedicated positional sound packet only when `isPlaySound` is
true. It replaces their previous generic server sound, preventing a duplicate immediate boom.
The audible radius remains 64 blocks. Enhanced presentation plays the existing vanilla
explosion asset at a sharper pitch, then a quieter, deeper second voice for power >=3.
Large blasts separate the tail by four ticks instead of two. This reuses stock audio; it is
not a newly recorded crack/rumble sample or an acoustic occlusion simulation.

Arrival is rounded to ticks using 343 blocks per second, capped at four ticks within the
64-block radius. Classic retains one immediate voice with the original pitch distribution.
All modes admit at most four opening impacts per client tick and play at most four total
voices per client tick; the waiting queue holds at most 32. Opening impacts take priority over
optional rumble tails, which can be displaced or expire during sustained combat.
Pending sounds clear on world changes/disconnect and pause with the client world.

Network protocol is **30**: clients and servers need the same updated build. The visual
explosion payload format, damage, terrain destruction, projectile physics and bullet glow
are unchanged.

## Original contrast validation

267 JUnit tests pass, including staged smoke/coverage, size/phase bounds, audio timing and
sound-packet round trips. Both native backends passed the seven-weapon comparisons and every audio queue drain
check, including 80-request bursts per weapon. Capture requests at ages 1/3/6/20/50 produced
34 Vulkan and 34 OpenGL 1080p frames (a slow frame may coalesce adjacent requests).
Per-tick ground reads stayed <=96 and fragment tracking <=24. Captures and exact weapon
profiles are indexed in [the compact results](performance/m8-realism/blast-contrast.json).
Audio ran through OpenAL null output: scheduling/drain is verified, subjective listening is not. Local builds exclude the optional uncached JEI API; repository dependencies remain
unchanged. Software rendering cannot certify the RTX 3060 / 7800X3D / 1080p 60 FPS goal.

## Overlapping explosion audio refinement

The original combined admission counter could spend all four slots on two opening impacts
and their two tails, dropping the third impact. Birth-time caps also did not bound the number
of voices whose different propagation delays converged on one playback tick.

The bounded scheduler now reserves admission for four opening impacts per tick and gives
those due impacts priority over optional tails at playback. An incoming impact can replace
a tail in a full queue. Four voices is the playback ceiling, including repeated drains in
the same tick. Tails more than two ticks late and impacts more than four ticks late expire
to prevent an audible backlog after a client stall. World changes clear both entries and
credits. Sound instances are constructed only when actually played. Queue scans are bounded
by 32 entries and four playback choices per client tick.

Five additional tests cover four-impact volleys, saturated tail eviction, primary-only
saturation, sustained bursts, stale expiration and reset behavior. Total: 272 JUnit tests
with zero failures, errors or skips. Native Vulkan 1080p passed seven weapon scenes, sixteen consecutive ticks of 80 audio
requests per weapon, and an eight-tick Classic burst. All queue bounds and drain assertions
passed. [Compact results](performance/m8-realism/audio-priority.json) record this runtime check;
OpenAL null output does not establish subjective listening acceptance.
This refinement retains protocol 30, sound assets/pitches, visual effects and the 60 FPS goal.
