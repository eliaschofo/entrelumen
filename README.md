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

## Running a dedicated server

`tools/build_server_pack.py` assembles a distributable server folder (it never writes into the repository or into the QA server, and it never starts anything):

```
python tools/build_server_pack.py --jar companion/build/libs/entrelumen-<version>.jar --output <new folder> --zip
```

`--output` is required on purpose: the folder is about 1.1 GB (twice with `--zip`, plus the libraries the NeoForge installer adds on first run), so pick a drive with room and not C:.

The folder holds the server-side dependency JARs (installed from the same lock as the client, `curate_pack.py --side server`), the companion JAR, `pack/` without its client-only paths (`resourcepacks/`, `kubejs/client_scripts/`, `kubejs/assets/`, `config/fancymenu/`, `config/defaultoptions/`, client display configs), `server.properties.template`, `eula.txt` (`eula=false`), `user_jvm_args.txt` (`-Xmx6G`) and the launchers `start.sh` / `start.bat`. With Java 21 or newer installed (the launchers stop with a message on an older Java), set `eula=true` after reading the Minecraft EULA and run the launcher: it downloads and verifies the NeoForge 21.1.249 installer on first run, copies the template to `server.properties` only if none exists, and starts the server.

Template defaults: `allow-flight=true`, `spawn-protection=0`, `simulation-distance=6`, `view-distance=10`, `max-players=10`, empty `server-ip` and `level-seed`, and `pvp=false` (this is a co-op pack; pass `--pvp true` to change it).

- **`allow-flight=true` is required.** Vanilla (`false`) kicks Immersive Aircraft pilots after about 160 ticks of level flight, and jetpacks hit the same check.
- `spawn-protection=0` is recommended: with the vanilla default only operators can build near the world spawn, which blocks the first-hour starter area.
- The launcher never overwrites an existing `server.properties`. To update a server that is already running: stop it and back up `world/`, build a new folder, then **replace** the server's `mods/` with the new one (delete or move the old `mods/` first; never copy over it, since an updated mod changes its file name, the old JAR stays beside the new one and NeoForge refuses to start with a duplicate mod ID) and replace `kubejs/server_scripts/` and `kubejs/data/` the same way, so scripts and data the pack dropped stop loading. Then copy `config/` and `defaultconfigs/` over the server's (your edits to a file the pack ships are overwritten) and edit `server.properties` by hand.
- Run the builder again on the same `--output` only for a pristine folder: the marker file lists what the builder wrote, and a folder that was used (`world/`, `server.properties`, `libraries/`, `logs/`, `ops.json`, `whitelist.json`, an accepted `eula.txt`) is refused. `--replace` overrides that and deletes the whole folder, world included. An existing `<output>.zip` is overwritten only if it is byte for byte the archive this tool last wrote there (its size and SHA-256 sit beside it in `<output>.zip.entrelumen.json`); a zip of a used folder carries the builder's marker too, so that marker alone never counts. A build that was interrupted is redone only if nothing in the folder shows it was started.
- `tools/runtime.py` and `tools/sync_pack.py` are the isolated local QA path, not the server pack.

## Español

ENTRELUMEN es un kitchen sink original para **Minecraft 1.21.1 · NeoForge**, sobre recuperar conocimientos y reconstruir una red de mundos. Ingeniería, magia, naturaleza, exploración, logística y vida cotidiana contribuyen al **Arca de los Horizontes**.

**En desarrollo; todavía no hay una versión pública aprobada.** La campaña de 150–200 horas hasta el primer final, la escala extra grande, el arte y el rendimiento requieren pruebas reales.

El prototipo tiene **178 dependencias de cliente y 143 de servidor, más el mod propio**, **171 quests bilingües en siete capítulos** y **42 hitos de campaña**. Las entregas recorren seis actos y llegan a un primer final narrativo. El progreso pertenece al equipo; los regalos son libres y recibir materiales no completa la historia. Hay QoL desde el comienzo, automatización y granjas de recursos por etapas, sin conversión universal por EMC. Todavía falta alcanzar la escala final y verificar el recorrido completo en supervivencia.

El Arca ensamblada ofrece un taller que repara con materiales, una biblioteca que separa libros compuestos conservando sus encantamientos y un alojamiento temporal que guarda tu hogar anterior. Los visitantes pueden usarlos sin adelantar su historia. Naturaleza, exploración, logística y las maestrías siguen pendientes. La última familia de construcción incorpora vidrios funcionales, postigos, cercos, iluminación, medición y conversión de materiales; sus casos nativos seleccionados tienen [evidencia de servidor](docs/verification/habitation-restart-runtime.json).

La meta de rendimiento usa i7-8750H, GTX 1070 y 16 GB de RAM, a 1080p, 10 chunks de renderizado, 6 de simulación, sin shaders y hasta 8 GB para Java. **Aún no está acreditada.** Faltan recorridos representativos, bases industriales, generación de terreno, dos horas de estabilidad y cooperativo de seis jugadores. El servidor actualizado inicia y guarda correctamente; el cliente actualizado sigue sin abrirse mientras trabajamos sin Computer Use. Las maquetas visuales y las mediciones aisladas no son aceptación final.

## Servidor dedicado

`tools/build_server_pack.py` arma una carpeta de servidor distribuible (no escribe en el repositorio ni en el servidor de QA, y no arranca nada):

```
python tools/build_server_pack.py --jar companion/build/libs/entrelumen-<versión>.jar --output <carpeta nueva> --zip
```

`--output` es obligatorio a propósito: la carpeta pesa cerca de 1,1 GB (el doble con `--zip`, más las librerías que el instalador de NeoForge suma en la primera ejecución), así que elegí un disco con lugar y no C:.

La carpeta trae los JAR de servidor (instalados desde el mismo lock que el cliente, `curate_pack.py --side server`), el JAR del mod propio, `pack/` sin sus rutas de cliente (`resourcepacks/`, `kubejs/client_scripts/`, `kubejs/assets/`, `config/fancymenu/`, `config/defaultoptions/`, configuraciones de pantalla del cliente), `server.properties.template`, `eula.txt` (`eula=false`), `user_jvm_args.txt` (`-Xmx6G`) y los lanzadores `start.sh` / `start.bat`. Con Java 21 o superior instalado (los lanzadores se detienen con un aviso si la versión es anterior), leé el EULA de Minecraft, poné `eula=true` y ejecutá el lanzador: la primera vez descarga y verifica el instalador de NeoForge 21.1.249, copia la plantilla a `server.properties` sólo si no existe y arranca el servidor.

Valores de la plantilla: `allow-flight=true`, `spawn-protection=0`, `simulation-distance=6`, `view-distance=10`, `max-players=10`, `server-ip` y `level-seed` vacíos, y `pvp=false` (es un pack cooperativo; con `--pvp true` se cambia).

- **`allow-flight=true` es obligatorio.** Con el valor de vanilla (`false`) el servidor expulsa a quien pilotea un Immersive Aircraft tras unos 160 ticks de vuelo nivelado, y los jetpacks chocan con la misma regla.
- `spawn-protection=0` es lo recomendado: con el valor de vanilla sólo los operadores construyen cerca del spawn y eso traba la zona de las primeras horas.
- El lanzador nunca pisa un `server.properties` existente. Para actualizar un servidor que ya está andando: detenelo y hacé una copia de `world/`, armá una carpeta nueva y **reemplazá** el `mods/` del servidor por el nuevo (borrá o mové antes el `mods/` viejo; nunca copies encima, porque un mod actualizado cambia el nombre de su archivo, el JAR viejo queda al lado del nuevo y NeoForge no arranca con un ID de mod repetido). Reemplazá de la misma forma `kubejs/server_scripts/` y `kubejs/data/`, así lo que el pack sacó deja de cargarse. Después copiá `config/` y `defaultconfigs/` encima de los del servidor (lo que cambiaste en un archivo que trae el pack se pisa) y editá el `server.properties` a mano.
- Volvé a correr el armador sobre el mismo `--output` sólo si la carpeta está intacta: el archivo marcador lista lo que escribió el armador, y una carpeta que ya se usó (`world/`, `server.properties`, `libraries/`, `logs/`, `ops.json`, `whitelist.json`, un `eula.txt` aceptado) se rechaza. `--replace` lo fuerza y borra la carpeta entera, mundo incluido. Un `<output>.zip` existente sólo se sobrescribe si es, byte por byte, el archivo que esta herramienta escribió ahí la última vez (su tamaño y su SHA-256 quedan al lado, en `<output>.zip.entrelumen.json`); un zip de una carpeta usada también lleva el marcador del armador, así que ese marcador solo nunca alcanza. Un armado que se cortó se rehace sólo si nada en la carpeta muestra que se arrancó.
- `tools/runtime.py` y `tools/sync_pack.py` son el camino local de QA aislado, no el server pack.

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
