# Habitation: expedition lodging

The assembled Ark offers a personal temporary respawn while retaining the player's previous home. Use the existing Habitation block with a bed in the main hand to check in; crouch-use a Habitation block with a bed to check out. The bed is not consumed, and checkout restores the previous spawn tuple without moving the living player. Visitors can use the service without completing or changing campaign progress.

Check-in requires one complete physical Ark in loaded chunks and a dimension where native beds may set spawn. After check-in, only the reserved Habitation block and a safe exit are required: dismantling another module does not cancel an existing stay. There is no background tick, upkeep, chunk ticket or forced loading of neighboring chunks. A second reservation requires ending the first. Repeated check-in at the same location is harmless.

The native non-forced block respawn API supplies the arrival position. Its bounded search rejects obstructed, dangerous or fluid-filled exits rather than using a permissive fallback. If the lodging is lost, the original bed, anchor or world spawn supplies native fallback behavior. A later bed, anchor or command-chosen spawn takes priority over the reservation. Logout, dimension travel and team membership changes do not independently end a stay.

The reservation and previous dimension, optional position, angle and forced flag live together in the player's native persisted NBT, alongside the ordinary player save. Native cloning copies that namespace. Spawn setters are cancelable: successful state changes must be confirmed before discarding the backup. Canceled restoration retains a recoverable reservation, including through cloning; another chosen home must never be overwritten by a retry. The personal journal is read-only.

## Native reference and mod boundaries

Before implementation, the native red bed texture was extracted from `G:/curseforge/Install/versions/1.21.1/1.21.1.jar`, entry `assets/minecraft/textures/entity/bed/red.png`, and visually inspected at `work/ark-habitation-20260923/minecraft-red-bed.png` outside the repository. It is a 64×64 texture atlas: red blanket, white pillow and restrained wooden structure. Its interaction and material language inform this service; no texture, item or custom screen is introduced. Existing empty-hand journal reading and crouched empty-hand commissioning deposits remain separate gestures.

Minecraft 1.21.1/NeoForge's block respawn extension, `DismountHelper`, cancelable spawn setter, `PlayerRespawnPositionEvent` and post-respawn player event implement the behavior. The module status screen ([ark-field-journals.md](ark-field-journals.md)) shows the lodging state in one row, with the bilingual instructions and personal status on hover. Rendered acceptance remains pending while Computer Use is disabled.

Pinned Comforts supplies sleeping bags/hammocks without moving home; Waystones transports living players. This service instead remembers a temporary respawn reservation. Comforts can cancel the native spawn setter, so actual player getters must confirm changes. Tombstone may consult the current respawn for its home tablet or exceptional grave placement: its native policy remains in control, and identical grave locations are not promised. No inventory, drop, grave or death event policy is replaced.

## Verification boundary

Native isolated tests and loaded-pack tests must cover deliberate interaction, conservation, independent campaign state, restoration, new-bed precedence, persistence, invalid exits and cancelable setters. A real process restart separately checks FTB team campaigns and Ark deposits. These cases do not establish survival pacing, client rendering, every third-party respawn handler or final performance.
