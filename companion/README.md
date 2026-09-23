# Entrelumen companion

Java 21, NeoForge 21.1.249. Build with `./gradlew build`; dependencies resolve from official FTB and Architectury Maven repositories. The MDK template license is preserved in TEMPLATE_LICENSE.txt. Original code is governed by the repository license.

The server owns campaign SavedData. The atlas and `/entrelumen status` expose current team status. Read INTEGRATION.md for content IDs and project schema. First-act recipes are included. Acts II–VI requirements remain development values pending pack integration and balance; compilation cannot certify final gameplay.

Tests cover campaign isolation, founder snapshots, idempotent delivery, failed consumption, archival, resumable Ark phases and SavedData restart/schema handling. Dedicated runtime, client rendering, multiplayer migration and balancing require integration verification.

`runGameTestServer` uses a development-only fixture for four external Act VI ingredients absent from its small FTB runtime. Its passes verify Entrelumen server behavior, not compatibility with the real cross-mod items. The separate `entrelumen-0.1.0-qa.jar` from `qaJar` includes GameTests and a failing preflight for the real Mekanism, Occultism, Twilight Forest and Aether items. Replace the normal JAR with it only in a full-pack QA server, start Java with `-Dentrelumen.qa=true`, then run `/test runall` as an operator. The log emits each result and a terminal summary; `/entrelumen_qa_results` reports expected, finished and failed tests with names. Run once per fresh server start. The fixture is absent from both JARs and normal client/server runs. Do not publish the QA JAR.
