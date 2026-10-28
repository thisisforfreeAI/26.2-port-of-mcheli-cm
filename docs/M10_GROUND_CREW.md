# M10c: ground crew and rearming

Part of the M10 plan (gameplay). Vehicles can be serviced on the ground: a repair pad fixes,
refuels and rearms a vehicle parked on it, and a new ammo cart carries a limited stock of rounds.

## Repair pad

- New block `Repair Pad` (a 1/16-high tile). Crafting table: iron, yellow dye, smooth stone and
  redstone (`IYI / SRS / IYI`) make 9. The plan named the drafting table; it only makes vehicles,
  so the pad uses the crafting table.
- A pad is a full 3x3 of tiles. A vehicle counts when its position is over the middle tile.
- Once a second, a vehicle on a pad with its throttle at 0 and moving slower than 0.1 blocks a tick:
  - repairs `groundCrew.padRepairHp` HP (default 10), components worst first through the M9 path;
  - with a fuel supply vehicle within `groundCrew.padSupplyRange` (default 8) of the pad's centre,
    takes a twentieth of its tank (at least 30), under the truck's own fuel rules;
  - with an ammo supply vehicle there, rearms its emptiest weapon by a tenth of its load, paid from
    the supplier's stock if it has one.
- A supply vehicle with a stock restocks a tenth of it a second while on a pad, moving or not.
- `groundCrew.enabled` (server config) turns all of it off.

## Ammo cart

- New stock vehicle `ammo_cart` (Vehicles tab), crafted at the drafting table: iron, a chest,
  gunpowder and planks. It reuses the ammo box's model and texture for now.
- 600 rounds of stock, an 8-block reach. Once a second it rearms one weapon of each vehicle in
  reach, the emptiest first (a tenth of that weapon's load), and pays for every round.
- Its seat is a loader's (M10b): with someone in it, it rearms twice a second.
- It can be towed with a chain like other ground vehicles.
- Any addon supply vehicle can carry a stock with `AmmoSupplyStock: rounds` (YAML) or
  `AmmoSupplyStock = rounds` (TXT). Without it, supply works exactly as upstream (the stock ammo box
  and fuel truck are unchanged).

## HUD and range rings

- While the ridden vehicle is being repaired, rearmed, refuelled or restocked, a line at the right
  below the component strip says `REPAIR REARM REFUEL RESTOCK` (each for 2.5 seconds after the last
  service, including the stock trucks' and ammo box's own supply). A stocked vehicle's riders see
  `STOCK n/max`.
- While riding any vehicle, fuel and ammo supply vehicles within 64 blocks draw their reach on the
  ground as a ring of green sparkles once a second.

## Saves and upgrades

- A cart's stock saves with it (`AcAmmoStock`) and syncs to clients.
- The stock content revision is now 5, so existing installs re-extract the stock pack and get the
  ammo cart.

## Tests

- `GroundCrewTest` (unit): the stock cart's stock, reach, loader seat and recipe; the ammo box stays
  endless; `AmmoSupplyStock` survives TXT to YAML; the HUD words.
- `ground_crew_pad_services` (GameTest): an AH-64 on a pad is repaired 10 HP a second (components
  too), refuelled and rearmed by a supplier 5 blocks off whose own reach is half a block, the
  supplier's stock pays, the HUD flags rise; nothing with the setting off, with the engine running
  or off the pad, and the flags clear.
- `ground_crew_ammo_cart_stock` (GameTest): the cart rearms exactly one weapon a second and pays for
  it, twice with a loader aboard, nothing when empty, and restocks on a pad.
- Updated counts: 19 stock vehicles, 116 vehicle recipes, 120 content items, 24 fixed items.

## Not tested

No client was run: the HUD line, the sparkle rings and the pad texture are unseen. Driving onto a
pad with a real pilot is untested; the GameTests place the vehicle.
