# ENTRELUMEN — The Living Atlas / El Atlas Vivo

An original Minecraft 1.21.1 NeoForge kitchen sink about recovering lost knowledge and reconnecting a fractured world. Technology, magic, nature and exploration contribute to a six-act campaign and the Ark of Horizons.

**Development status:** implementation in progress. This repository is not yet a playable release. Performance targets and the 150–200 hour campaign are design goals until measured in playtests.

The current prototype contains 155 bilingual quests across six chapters, a server-authoritative team campaign, an Atlas interface, a functional compass survey station, a pinned 140-mod client / 110-mod server dependency selection, and candidate original item/block artwork. Construction deliveries now span all six acts, with 42 campaign milestones and an explicit first narrative ending. The six module recipes passed full-pack crafting checks with native mod items; the earlier Act II–V recipe checks remain documented. This remains far below the agreed extra-large final scale. Full campaign playthrough, survival pacing, final artwork, performance and release acceptance remain open; see the [Act VI route](docs/design/act-six-route.md), [Act V evidence](docs/verification/act-five-runtime.json) and [earlier integration evidence](docs/verification/integration-slice.md).

The 85 missing-item addon loot errors have been resolved. Server checks preserved all 295 available native tables and verified six representative block drops before and after reload. A final startup without test instrumentation contained zero ERROR lines; existing warnings and performance acceptance remain separate. See [loot compatibility evidence](docs/verification/optional-loot-runtime.json).

The Ark controller accepts capped partial batches for all six modules and preserves contributions through team changes and saving. Its final activation requires the campaign's recorded End journey, completed module projects, six commissioning batches and the physical modules. Activation records the ending once and consumes nothing further; later inspection replays it. Complete module benefits and postgame mastery systems remain unfinished. See the [Act VI route](docs/design/act-six-route.md) and [earlier Ark batch evidence](docs/verification/ark-batches-runtime.json).

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

El prototipo incluye 155 quests bilingües en seis capítulos, campaña por equipo, interfaz del Atlas, estación para marcar brújulas, 140 dependencias de cliente y 110 de servidor, y arte propio en revisión. Las entregas abarcan los seis actos, con 42 hitos y un primer final narrativo explícito. Las seis recetas de módulos pasaron pruebas de crafteo con los mods reales; las verificaciones anteriores de los actos II–V conservan su evidencia. Todavía está lejos de la escala final acordada. El recorrido completo en supervivencia, su duración, el rendimiento y la publicación siguen pendientes. Todo el contenido propio debe tener una identidad visual hermosa y coherente de pixel art a escala Minecraft, comprobada dentro del juego. Las maquetas técnicas y los conceptos sin revisión siguen siendo borradores.

El controlador del Arca acepta lotes parciales y conserva las entregas. La activación final exige el viaje al End registrado, los seis proyectos, sus seis lotes de puesta en marcha y los módulos físicos presentes. Registra el final una sola vez, sin consumir nada más; inspeccionarlo permite releerlo. Los beneficios completos de cada módulo y las maestrías del posgame siguen pendientes.

Se corrigieron los 85 errores de loot de addons. Las 295 tablas disponibles conservaron su contenido original y seis bloques pasaron pruebas de drops antes y después de recargar. El arranque final sin herramientas temporales de prueba no tuvo líneas ERROR; esto no acredita todavía el rendimiento del pack. [Evidencia de compatibilidad](docs/verification/optional-loot-runtime.json).

Las quests, la historia y las ayudas propias se desarrollan en inglés y español. El seguimiento del trabajo distingue implementación, pruebas y publicación.
