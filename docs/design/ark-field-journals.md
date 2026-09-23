# Ark field journals: arcana, nature, exploration and habitation

Four placed modules offer a read-only field journal through an empty-main-hand use. The server reads the current player's team campaign, checks the clicked block, reach and spectator mode, and reports native chat lines. It never grants items, consumes supplies, records a journey, advances a batch or activates the ending. Material possession is not evidence. The existing `Campaigns.Campaign.completed` observations, `ArkCommissioning.STEPS` and current partial deposit map remain authoritative; no second ledger or offline work is introduced.

Each journal has a distinct narrative at zero, partial and complete evidence, followed by exact recorded/pending projects. The arcane journal reads `spectral_archive`, `sealed_memory` and `atlas_voices`. Nature reads `nursery_protocol`, `pollinator_treaty` and `renewal_engine`. Exploration separates the `horizon_survey` project from team journeys `aether_arrival`, `twilight_arrival`, `bumblezone_arrival` and `end_arrival`; crafted samples never appear as visited routes. Habitation reads `travellers_table`, `travelling_pantry` and `settlement_supply`. Every journal separately reports whether its own module project was delivered.

The phase line distinguishes an upcoming, current or completed commissioning batch. An upcoming batch shows its static future cost without claiming stored materials. The current batch shows credited and remaining quantities from the team's existing partial deposit map. A completed batch reports no pending materials. The final ending remains a separate recorded milestone. On interaction only, the existing bounded inverse controller scan may report a nearby controller, missing modules or unloaded/ambiguous space; it does not load chunks. The journal remains readable when no controller is present.

## Interaction reference

The actual controller's empty-hand interaction and native chat were inspected in `docs/verification/act-six-runtime.json`. `EngineeringModuleBlock` and `EngineeringDiagnostics` already provide the same on-demand block interaction and bounded physical scan. These journals reuse those controls and original module models. They create no screen, geometry, texture or icon. The current artwork remains a candidate under `DESIGN.md`, not accepted by this behavior change.

The tests cover milestone-dependent narrative, the current team's independent record, earlier/current/later batch projections and a repeated on-block read without mutation. Installed client and full-pack behavior are separate acceptance steps.
