# Entrelumen companion

Java 21, NeoForge 21.1.249. Build with `./gradlew build`; dependencies resolve from official FTB and Architectury Maven repositories. The MDK template license is preserved in TEMPLATE_LICENSE.txt. Original code is governed by the repository license.

The server owns campaign SavedData. The atlas and `/entrelumen status` expose current team status. Read INTEGRATION.md for content IDs and project schema. First-act recipes are included. Acts II–VI requirements remain development values pending pack integration and balance; compilation cannot certify final gameplay.

Tests cover campaign isolation, founder snapshots, idempotent delivery, failed consumption, archival, resumable Ark phases and SavedData restart/schema handling. Dedicated runtime, client rendering, multiplayer migration and balancing require integration verification.
