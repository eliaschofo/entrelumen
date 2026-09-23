# ENTRELUMEN — The Living Atlas / El Atlas Vivo

An original Minecraft 1.21.1 NeoForge kitchen sink about recovering lost knowledge and reconnecting a fractured world. Technology, magic, nature and exploration contribute to a six-act campaign and the Ark of Horizons.

**Development status:** implementation in progress. This repository is not yet a playable release. Performance targets and the 150–200 hour campaign are design goals until measured in playtests.

The current prototype contains 128 bilingual quests across five chapters, a server-authoritative team campaign, an Atlas interface, a functional compass survey station, a pinned 140-mod client / 110-mod server dependency selection, and candidate original item/block artwork. Twenty embedded-server GameTests cover campaign and network service behavior, including deliveries through Act V. The five opening projects have also been delivered through the client interface using supplied QA materials. The dedicated server loaded all 128 quests and 28 campaign milestones. All three Act V recipes passed real crafting checks, including four returned bowls for the habitation contract, following the fourteen Act II–IV checks. This remains far below the agreed extra-large final scale. Full campaign, survival pacing, visual, compatibility and performance acceptance remain open; see [Act V evidence](docs/verification/act-five-runtime.json) and [earlier integration evidence](docs/verification/integration-slice.md).

The Ark controller now accepts capped partial batches for all six modules, preserves contributions through team changes and saving, and rejects completed-step replay. This transaction slice passes 22 unit tests and 18 embedded-server tests; the ending, final module experiences and client visual review remain pending. See [Ark batch evidence](docs/verification/ark-batches-runtime.json).

## Design commitments
- Six directed acts with independent team campaigns and unrestricted item trading.
- Extensive early quality-of-life tools and approachable, purposeful automation.
- Original English and Spanish quests, tutorials and narrative.
- Staged resource farms without universal EMC conversion.
- Six complementary Ark modules, recoverable commissioning and no offline decay.
- Target: 16 GB system RAM, at most 8 GB Java heap, no default shaders.
- Beautiful original content with a coherent Minecraft-scale pixel-art identity, reviewed inside the game. Technical fixtures and unreviewed concept art are not final content.

## Project layout
- `companion/`: the NeoForge integration mod and campaign domain.
- `catalog/`: curated dependency inventory and provenance.
- `content/`: original quest and narrative sources.
- `pack/`: original configuration, KubeJS, quests and resource overrides.
- `tools/`: reproducible generation, installation and validation.
- `docs/delivery/`: the acceptance contract and verified progress.

Dependencies retain their own licenses. Original pack content is source available under [LICENSE](LICENSE); public redistribution or repackaging requires permission except for the limited platform rights described there.

## Español
ENTRELUMEN es un kitchen sink original sobre recuperar conocimientos y reconstruir una red de mundos. Su campaña combina tecnología, magia, naturaleza, exploración y construcción, con progreso por equipo e intercambio libre.

**Estado:** implementación en curso; todavía no es una versión jugable publicada. Las metas de rendimiento y duración requieren mediciones y pruebas reales.

El prototipo incluye 128 quests bilingües en cinco capítulos, campaña por equipo, interfaz del Atlas, estación para marcar brújulas, 140 dependencias de cliente y 110 de servidor, y arte propio en revisión. Se completaron los cinco proyectos iniciales desde la interfaz usando suministros de QA. El servidor cargó las 128 quests y los 28 hitos. Las tres recetas del acto V pasaron las pruebas de crafteo, con cuatro cuencos devueltos en el contrato de habitación, además de las catorce verificadas para los actos II–IV. Todavía está lejos de la escala final acordada. La campaña completa, su balance en supervivencia, el rendimiento y la publicación siguen pendientes. Todo el contenido propio debe tener una identidad visual hermosa y coherente de pixel art a escala Minecraft, comprobada dentro del juego. Las maquetas técnicas y los conceptos sin revisión siguen siendo borradores.

El controlador del Arca ya acepta lotes parciales para sus seis módulos, conserva las entregas y rechaza repeticiones de pasos terminados. Las pruebas de servidor pasaron; el final narrativo, las experiencias completas de los módulos y su revisión visual siguen pendientes.

Las quests, la historia y las ayudas propias se desarrollan en inglés y español. El seguimiento del trabajo distingue implementación, pruebas y publicación.
