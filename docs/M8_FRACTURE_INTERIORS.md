# Richer aircraft fracture interiors

Severed wings and tails gain small uneven exposed spars, short hanging cables and dark inset
cross-sections where the source mesh forms a closed contour. The surviving aircraft skin and
its texture coordinates retain the existing breakup silhouette. These are cosmetic details,
not new collision parts or component damage rules.

The new geometry uses Enhanced quality, Polished style, Vehicle damage details and Vehicle
destruction sequences. Detail draws stop beyond 40 blocks and in far/thermal views. Classic
and disabled damage details retain their existing presentation. Repairs restore the original
structural sections and clear their fracture details.

## Geometry and lighting

Cut boundaries come from the existing clipped body mesh. A bounded contour graph welds points
at 1 mm and accepts only closed degree-two components. Ear clipping preserves concave outlines
and separate components; open or over-complex boundaries do not acquire an invented solid cap.
The cap sits 2.5 cm inside the surviving section. Five small spars and two short segmented cables
follow the cut's upper skin, with irregular lengths rather than a uniform row of beams.

Ordinary iron-block texture geometry supplies muted metal, dark cavity and cable colors. These
details use the existing ambient vehicle lighting; there is no new glow, shader or light source.
Unsupported roots fall back to their existing small spars.

## Cost and validation

The existing breakup loader remains one worker, four pending builds and 32 cached results.
Each cut graph is limited to 128 nodes and 256 edges; all three cuts together stay below 3,072
new vertices. Drawing shares the existing vehicle-detail frame allowance: 4,096 / 12,288 /
24,576 vertices at Low / Balanced / High, reduced under adaptive pressure. Caps, spars and
cables compete with the other overlays for that same allowance. No extra particles, entities,
world queries, collision rays or persistent per-aircraft geometry caches are added.

Unit checks exercise concave and disconnected contours, open/complex fallback, finite bounded
geometry and source immutability. [Native comparisons](M8_FRACTURE_INTERIORS_GALLERY.md) show
held component damage, wrecks and repairs on the F-35A, F-15E and A-10, plus style/settings and
shared-load checks. These are controlled client scenes, not combat recordings. Software Mesa
validation does not measure the 60 FPS target on RTX 3060 8 GB / Ryzen 7800X3D at 1080p.

Original craters, gameplay damage, flight handling and Claude's NTM Next integration remain unchanged.
