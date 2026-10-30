# Keep plane rear fuselages attached

The generic breakup previously cut the rear 27% of every plane's body off as one slab.
This could carry away the engine section and both fins, and create an especially unnatural
rear-center fragment on flying wings such as the B-2.

Whole-rear separation is now disabled globally for planes, including addon aircraft. Live
damage, saved component damage and late/old wreck stages all retain the rear fuselage. Both
the airframe mesh builder and renderer reject the old whole-tail removal bit; the legacy
staged-body builder also returns an empty detached-tail section. Tail-only structural damage
keeps the original body mesh.

Wing breakup and supported small control-panel damage remain available. The rear geometry
template is retained to identify bounded rudder/elevator panels; it is never emitted as a
whole fuselage fragment. Component health/save values, flight, collision, weapons and repair
behavior are unchanged. Existing burn/crumple artwork, wreck placement and NTM integration
are retained. This adds no rendering pass, particle budget or configuration setting.

Regression checks exercise every eligible bundled plane body, B-2 planform/wing cuts, surface
conservation and tail-only damage preserving the source mesh. Native examples use held client
component states on F-22, B-2, F-35 and A-10 models, including tail damage already present at
spawn and late wreck stages; these are geometry previews, not captures of actual crashes.

[Native examples](M8_RETAINED_FUSELAGE_GALLERY.md).

Validation: all 558 unit tests pass, including the stock-model fracture audit and retained-rear
surface checks. The four-plane native fixtures pass on Vulkan and OpenGL, with separate checks
for pre-existing rear damage, newly applied rear damage and late wreck geometry. Only the
existing wing-removal bit is present in the wreck mesh; no whole-tail fragment is emitted.
