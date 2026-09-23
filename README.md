# ENTRELUMEN — The Living Atlas / El Atlas Vivo

An original Minecraft 1.21.1 NeoForge kitchen sink about recovering lost knowledge and reconnecting a fractured world. Technology, magic, nature and exploration contribute to a six-act campaign and the Ark of Horizons.

**Development status:** implementation in progress. This repository is not yet a playable release. Performance targets and the 150–200 hour campaign are design goals until measured in playtests.

The current prototype contains 171 bilingual quests across seven chapters, a server-authoritative team campaign, an Atlas interface, a functional compass survey station, a pinned 147-mod client / 116-mod server dependency selection, and candidate original item/block artwork. Construction deliveries now span all six acts, with 42 campaign milestones and an explicit first narrative ending. The six module recipes passed full-pack crafting checks with native mod items; the earlier Act II–V recipe checks remain documented. This remains far below the agreed extra-large final scale. Full campaign playthrough, survival pacing, final artwork, performance and release acceptance remain open; see the [Act VI route](docs/design/act-six-route.md), [Act V evidence](docs/verification/act-five-runtime.json) and [earlier integration evidence](docs/verification/integration-slice.md).

The 85 missing-item addon loot errors have been resolved. Server checks preserved all 295 available native tables and verified six representative block drops before and after reload. A final startup without test instrumentation contained zero ERROR lines; existing warnings and performance acceptance remain separate. See [loot compatibility evidence](docs/verification/optional-loot-runtime.json).

The Ark controller accepts capped partial batches for all six modules and preserves contributions through team changes and saving. Its final activation requires the campaign's recorded End journey, completed module projects, six commissioning batches and the physical modules. Activation records the ending once and consumes nothing further; later inspection replays it. The engineering module now diagnoses the current team's prerequisites, active batch and nearby structure without consuming resources or changing progress; its completed-state messages were checked in both languages and saving preserved the campaign. Complete module benefits and postgame mastery systems remain unfinished. See the [engineering evidence](docs/verification/engineering-runtime.json), [Act VI route](docs/design/act-six-route.md) and [earlier Ark batch evidence](docs/verification/ark-batches-runtime.json).

Act VI has now been checked in the real client in English and Spanish. Loading a previously stranded save repairs all 42 campaign quest mirrors while preserving the authoritative campaign and prior completion timestamps. The dedicated full-pack checks passed 25/25 with a temporary QA watchdog extension after the four-reload test sequence hit the normal watchdog. Its aggregate delay does not measure an ordinary production reload. See the [Act VI runtime evidence](docs/verification/act-six-runtime.json).

The logistics module provides another local entry point for the same recoverable Ark batches. Its two new targeted full-pack checks passed with the normal watchdog, including exact partial consumption, surplus retention and rejection of a foreign team, ambiguous controller or invalid interaction. Its completed-state messages and both modules' item hints rendered in English and Spanish. The redundant completed-state deposit hint has now been corrected and checked in both client languages. See [logistics evidence](docs/verification/logistics-runtime.json) and the [follow-up journal session](docs/verification/journals-runtime.json).

The other four modules have distinct team journals presented in Minecraft's native read-only book screen. The installed client renders the exploration record across three complete pages in English and Spanish; arcana uses two English pages. Arrow navigation, Tab/Enter, closing and unchanged campaign data after saving were checked. Physical Page Up/Page Down remain unverified. See [book-reader evidence](docs/verification/book-journals-runtime.json). Crouched empty-main-hand use now deposits only that module's current commissioning batch. Five targeted full-pack tests passed with the normal watchdog; the arcane completed-state gesture and updated quest rendered in both languages, while normal use still opened its book. See [local-deposit evidence](docs/verification/journal-deposits-runtime.json). The [first-hour survival protocol](docs/verification/first-hour-playtest.md) is ready, but the natural-acquisition playtest has not been run.

## Design commitments

The integrated performance collector has one [real, untrimmed validation run](docs/verification/benchmarks/capture-36af6196-00dc-4423-8abf-229e7824881d/README.md): 117.34 mean FPS, 73.55 FPS 1% low and 15.17 ms tick p95 at 1080p. This stationary creative fixture is **not pack performance acceptance**; representative machines, routes, long sessions and co-op remain unmeasured.

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

El prototipo incluye 171 quests bilingües en siete capítulos, campaña por equipo, interfaz del Atlas, estación para marcar brújulas, 147 dependencias de cliente y 116 de servidor, y arte propio en revisión. Las entregas abarcan los seis actos, con 42 hitos y un primer final narrativo explícito. Las seis recetas de módulos pasaron pruebas de crafteo con los mods reales; las verificaciones anteriores de los actos II–V conservan su evidencia. Todavía está lejos de la escala final acordada. El recorrido completo en supervivencia, su duración, el rendimiento y la publicación siguen pendientes. Todo el contenido propio debe tener una identidad visual hermosa y coherente de pixel art a escala Minecraft, comprobada dentro del juego. Las maquetas técnicas y los conceptos sin revisión siguen siendo borradores.

El controlador del Arca acepta lotes parciales y conserva las entregas. La activación final exige el viaje al End registrado, los seis proyectos, sus seis lotes de puesta en marcha y los módulos físicos presentes. Registra el final una sola vez, sin consumir nada más; inspeccionarlo permite releerlo. El módulo de ingeniería ya diagnostica requisitos, lote activo y estructura cercana del equipo sin consumir recursos ni modificar el progreso. Sus mensajes de campaña terminada se comprobaron en ambos idiomas y el guardado conservó la campaña. Los beneficios completos de cada módulo y las maestrías del posgame siguen pendientes. [Evidencia de ingeniería](docs/verification/engineering-runtime.json).

El acto VI se comprobó dentro de Minecraft en inglés y español. Al cargar una partida que tenía las quests trabadas, se recuperaron los 42 hitos reflejados sin alterar la campaña ni las fechas de finalización anteriores. Pasaron los 25 controles con una ampliación temporal del watchdog de QA, después de que la secuencia de cuatro recargas activara el límite normal. Su atraso acumulado no mide una recarga ordinaria de producción. [Evidencia del acto VI](docs/verification/act-six-runtime.json).

El módulo logístico ofrece otro punto local para entregar los mismos lotes recuperables. Sus dos pruebas nuevas con el pack completo pasaron con el límite normal del watchdog: consumo parcial exacto, sobrantes conservados y rechazo de otro equipo, controlador ambiguo o interacción inválida. Los mensajes de campaña terminada y las ayudas de ingeniería y logística se vieron en ambos idiomas. La instrucción redundante de entrega al completar los lotes ya se corrigió y comprobó en ambos idiomas. [Evidencia de logística](docs/verification/logistics-runtime.json) y [sesión posterior de diarios](docs/verification/journals-runtime.json).

Los otros cuatro módulos tienen diarios distintos en el lector nativo de libros de Minecraft. Exploración se comprobó en tres páginas completas en español e inglés; arcana, en dos páginas inglesas. Funcionan las flechas, Tab/Enter y el cierre; guardar conservó la campaña. Falta comprobar Page Up/Page Down con teclado físico. [Evidencia del lector](docs/verification/book-journals-runtime.json). Usarlos agachado con la mano principal vacía ahora entrega sólo el lote vigente de ese módulo. Pasaron cinco pruebas dirigidas con el pack completo y el watchdog normal; el gesto arcano con lote completado y su quest actualizada se comprobaron en ambos idiomas. El uso normal sigue abriendo el libro. [Evidencia de depósitos locales](docs/verification/journal-deposits-runtime.json). Está listo el [protocolo de supervivencia de la primera hora](docs/verification/first-hour-playtest.md), pero todavía falta ejecutar ese recorrido consiguiendo los materiales al jugar.

Se corrigieron los 85 errores de loot de addons. Las 295 tablas disponibles conservaron su contenido original y seis bloques pasaron pruebas de drops antes y después de recargar. El arranque final sin herramientas temporales de prueba no tuvo líneas ERROR; esto no acredita todavía el rendimiento del pack. [Evidencia de compatibilidad](docs/verification/optional-loot-runtime.json).

Las quests, la historia y las ayudas propias se desarrollan en inglés y español. El seguimiento del trabajo distingue implementación, pruebas y publicación.

La primera validación real del colector midió 117,34 FPS de media, 73,55 FPS de 1% low y 15,17 ms por tick en p95 a 1080p. Es un escenario creativo quieto; **no acredita el rendimiento del pack**. Los archivos originales, las limitaciones y la reproducción están en la [evidencia de captura](docs/verification/benchmarks/capture-36af6196-00dc-4423-8abf-229e7824881d/README.md).
