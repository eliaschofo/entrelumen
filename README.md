# ENTRELUMEN — The Living Atlas / El Atlas Vivo

An original **Minecraft 1.21.1 · NeoForge** kitchen sink about recovering lost knowledge and reconnecting a fractured world. Engineering, magic, nature, exploration, logistics and everyday life all contribute to the **Ark of Horizons**.

**In development. There is no approved public release yet.** The 150–200 hour first ending, final extra-large scale, visual quality and performance targets remain subject to playtesting and measurement.

## The pack

- Six directed acts, independent team campaigns and unrestricted gifts. Materials can be shared; receiving them does not complete the story.
- Broad early quality of life, purposeful automation and staged resource farming without universal EMC conversion.
- Original English and Spanish quests, tutorials and narrative.
- Six complementary Ark modules with recoverable commissioning and no offline decay.
- A Minecraft-scale pixel-art identity: restrained shapes and palettes, reviewed inside the game. Current unreviewed art and technical fixtures remain drafts.

The current prototype has **178 client / 143 server dependencies plus the companion mod**, **171 bilingual quests in seven chapters** and **42 campaign milestones**. Construction deliveries span all six acts and reach an explicit first narrative ending. This is substantially below the final reference-pack scale and does not establish a complete survival playthrough.

The assembled Ark offers a material repair workshop, a library that separates compound enchanted books conservatively, and temporary expedition lodging that preserves your previous home. Visitors can use these services without advancing their story. Nature, exploration, logistics and postgame masteries are unfinished. The latest building family adds functional glazing, shutters, fences, lighting, measurement and material-conversion tools; selected recipes and block behaviors passed dedicated-server checks. See the [current runtime evidence](docs/verification/habitation-restart-runtime.json) and [remaining acceptance requirements](docs/verification/acceptance.json).

## Performance and release

The target machine is an i7-8750H, GTX 1070 and 16 GB system RAM, at 1080p, 10 render chunks, 6 simulation chunks, no shaders and at most 8 GB Java heap. **These are test targets, not proven requirements or a performance guarantee.** Representative moving routes, industrial bases, terrain generation, a two-hour session and six-player co-op remain unverified. Collector validation and idle server diagnostics do not replace those workloads.

The updated dedicated server starts and saves cleanly. The updated clients have not been launched under the current no-Computer-Use constraint. Release also requires the natural first-hour playtest, full campaign and pacing tests, final visual review, upgrade/restoration checks, approved CurseForge files, a verified public installation and the weekly issue-review acceptance. Follow the [execution contract](docs/delivery/spec.md) and [implementation plan](docs/delivery/plan.json).

## Español

ENTRELUMEN es un kitchen sink original para **Minecraft 1.21.1 · NeoForge**, sobre recuperar conocimientos y reconstruir una red de mundos. Ingeniería, magia, naturaleza, exploración, logística y vida cotidiana contribuyen al **Arca de los Horizontes**.

**En desarrollo; todavía no hay una versión pública aprobada.** La campaña de 150–200 horas hasta el primer final, la escala extra grande, el arte y el rendimiento requieren pruebas reales.

El prototipo tiene **178 dependencias de cliente y 143 de servidor, más el mod propio**, **171 quests bilingües en siete capítulos** y **42 hitos de campaña**. Las entregas recorren seis actos y llegan a un primer final narrativo. El progreso pertenece al equipo; los regalos son libres y recibir materiales no completa la historia. Hay QoL desde el comienzo, automatización y granjas de recursos por etapas, sin conversión universal por EMC. Todavía falta alcanzar la escala final y verificar el recorrido completo en supervivencia.

El Arca ensamblada ofrece un taller que repara con materiales, una biblioteca que separa libros compuestos conservando sus encantamientos y un alojamiento temporal que guarda tu hogar anterior. Los visitantes pueden usarlos sin adelantar su historia. Naturaleza, exploración, logística y las maestrías siguen pendientes. La última familia de construcción incorpora vidrios funcionales, postigos, cercos, iluminación, medición y conversión de materiales; sus casos nativos seleccionados tienen [evidencia de servidor](docs/verification/habitation-restart-runtime.json).

La meta de rendimiento usa i7-8750H, GTX 1070 y 16 GB de RAM, a 1080p, 10 chunks de renderizado, 6 de simulación, sin shaders y hasta 8 GB para Java. **Aún no está acreditada.** Faltan recorridos representativos, bases industriales, generación de terreno, dos horas de estabilidad y cooperativo de seis jugadores. El servidor actualizado inicia y guarda correctamente; el cliente actualizado sigue sin abrirse mientras trabajamos sin Computer Use. Las maquetas visuales y las mediciones aisladas no son aceptación final.

## Source and project layout / Código y estructura

| Directory | Contents / Contenido |
| --- | --- |
| `companion/` | NeoForge mod, campaign authority and Ark services / mod, campaña y servicios del Arca |
| `catalog/` | Pinned dependencies, roles, provenance and licenses / dependencias, funciones, procedencia y licencias |
| `content/` | Original quest and narrative sources / quests e historia originales |
| `pack/` | Configuration, KubeJS, quests and resource overrides / configuración y recursos |
| `tools/` | Reproducible generation, installation and checks / generación, instalación y verificación |
| `docs/` | Design, execution contract, evidence and publication draft / diseño, contrato, evidencia y borrador de publicación |

Original content is **source available** under [LICENSE](LICENSE): private use and modification are permitted; public redistribution requires permission except for its limited platform rights. Dependencies retain their own licenses.

El contenido original tiene **código visible** bajo [LICENSE](LICENSE): permite uso y modificaciones privadas; la redistribución pública requiere permiso salvo los derechos limitados de plataforma allí descritos. Los mods ajenos conservan sus licencias.
