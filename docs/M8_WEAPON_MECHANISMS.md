# M8 weapon mechanisms

Enhanced / Polished effects refine existing conventional weapon presentation. The client
`effects.weaponMechanisms` option defaults on. Barrel rotation interpolates smoothly as the
existing gun spins up and down. Recoil has a sharper visual attack within the model's authored
travel. Supported bay doors visually open during the existing 15-tick weapon-switch wait;
server cooldowns, ammunition, shot timing, muzzle positions and vehicle handling stay unchanged.

Mixed conventional/NTM bays follow the selected occupied seat's weapon. They remember that
choice while closing. Selecting an NTM weapon keeps its original door curve. No NTM payload,
warhead bridge, explosion mode or weapon-selection logic is introduced.

Spent standard cases tumble, rebound once, settle, and resume falling if their supporting
block disappears. Known standard-mesh cases are capped to their cartridge family: the Apache's
M230 case is approximately 11 cm long rather than the former 35 cm. Smaller authored cases,
unknown addon sizes, custom cartridge models and their ejection settings are preserved.
Known closed-feed installations (GAU-8, M61, GAU-22 and the supported tank gun family) suppress
only automatic muzzle-case fallback; explicit authored cartridge events remain respected.
Cases use ambient lighting and receive no emissive glow.

The existing admission limits remain: at Balanced, eight case births per tick, 128 live cases,
96-block spawn/render range and the existing lifetime setting. Settled cases perform no
collision rays, checking support every ten ticks. Refined cases sharing a model render in one
batch, with lighting sampled every five ticks or on block changes. Visible spinning sets and
remembered bay curves each have 128 weakly owned entries and clear on world changes/logout.
Classic and the disabled option retain the earlier mechanism presentation.

[Native examples](M8_WEAPON_MECHANISMS_GALLERY.md) and
[machine-readable checks](performance/m8-realism/weapon-mechanisms.json) accompany this feature.
515 local unit tests, four Vulkan scenes and six OpenGL scenes pass, including load, support
removal, Classic/off and NTM-flag bypass checks. The optional NTM mod is absent locally; its
selected-bay classification is exercised with a fixture-only flag. The preview fixture replays replicated client shot bits on actual vehicle models; it does not
fire server projectiles, consume ammunition or create craters. Target-PC 60 FPS acceptance is
still outstanding.
