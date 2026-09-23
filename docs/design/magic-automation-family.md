# Magia y automatización: ocho puentes útiles

`catalog/families/magic-automation.json` fija ocho JARs de contenido, sus IDs de proyecto/archivo CurseForge, nombre exacto y SHA-256. El lote ocupa 8451360 bytes comprimidos. Siete proceden de la instancia de referencia consultada en modo lectura; sus SHA-1 coinciden con la metadata CurseForge. AE2 Import/Export Card 1.5.0 procede del archivo oficial 8033654 y queda fijado por SHA-256. Su permiso de redistribución no se infiere del de otra versión: el catálogo conserva el dato desconocido. Los ocho JARs declaran licencia. No se copió ningún script, quest, asset o configuración de otro pack ni se incluyó un JAR en el repositorio.

Los actos son ocasiones sugeridas para enseñar una herramienta; no bloquean posesión, receta, uso ni regalos. Ars Nouveau, Create y Occultism ya están disponibles en II; AE2 aparece en III. El nuevo lote permite pasar del taller de hechizos a equipos que comparten trabajo con las máquinas sin abrir un segundo almacén digital.

| Mod y archivo fijado | Introducción y uso tardío | Función distinta y referencia upstream |
| --- | --- | --- |
| `ars_elemental` · `ars_elemental-1.21.1-0.7.10.1.jar` | II: escuelas y focos; IV-VI: especialización opcional de expedición | Varía efectos y equipo de Ars; [Ars Elemental](https://www.curseforge.com/minecraft/mc-mods/ars-elemental) |
| `ars_creo` · `ars_creo-1.21.1-5.4.0.jar` | II-III: torretas sobre contraptions; IV: mecanismos móviles abastecidos de Source | Integra jars y torretas Ars con Create; [Ars Creo](https://www.curseforge.com/minecraft/mc-mods/ars-creo) |
| `ars_technica` · `ars_technica-1.21.1-2.7.6.jar` | III: glyphs de procesado manual; IV-V: pequeñas partidas de campo y ajuste de máquinas | Convierte operaciones tipo Create en hechizos, además de un Source Motor; [Ars Technica](https://www.curseforge.com/minecraft/mc-mods/ars-technica) |
| `ars_ocultas` · `ars_ocultas-1.21.1-2.6.1.jar` | III: espíritus en containment jars; IV: célula ritual alimentada por logística | Los espíritus aceptan items por capacidad y procesan en jars; [Ars Ocultas](https://www.curseforge.com/minecraft/mc-mods/ars-ocultas) |
| `ars_additions` · `ars_additions-1.21.1-21.3.0.jar` | II-III: glyphs y Warp Index; IV: automatización de enchanting | Wixie Enchanting Apparatus usa el sistema Ars existente; [Ars Additions](https://www.curseforge.com/minecraft/mc-mods/ars-additions) |
| `ars_controle` · `ars_controle-1.21.1-1.6.15.jar` | III: filtros y sensor de estabilidad; IV: control redstone de talleres | Enlaces remotos y sensor permiten apagar granjas según la carga del mundo; [Ars Controle](https://www.curseforge.com/minecraft/mc-mods/ars-controle) |
| `arseng` · `arseng-2.1.1-beta.jar` | IV: celdas de Source; V-VI: medir pedidos del módulo mágico del Arca | Source como recurso ME y acceptor que convierte Source a energía AE; [Ars Énergistique](https://www.curseforge.com/minecraft/mc-mods/ars-energistique) |
| `ae2importexportcard` · `ae2importexportcard-1.21.1-1.5.0.jar` | III: reposición de inventario; IV-VI: equipos de expedición abastecidos desde ME | Tarjetas de importación/exportación del terminal inalámbrico; [AE2 Import Export Card](https://www.curseforge.com/minecraft/mc-mods/ae2-import-export-card) |

## Cierre de dependencias y procedencia

La tabla siguiente complementa los ocho pins del JSON: `proyecto / archivo` son IDs CurseForge, y los rangos son los requisitos efectivos declarados dentro del JAR, omitiendo Minecraft/NeoForge. Los proyectos obligatorios de CurseForge también se cotejaron. Ningún ID externo nuevo queda sin cubrir por `catalog/curated.json`.

| ID | Proyecto / archivo | Dependencias obligatorias | Licencia declarada en JAR |
| --- | --- | --- | --- |
| `ars_elemental` | 561470 / 8399862 | `ars_nouveau` ≥ 1.21.1-5.12 | LGPL-3.0 |
| `ars_creo` | 575698 / 8139067 | `ars_nouveau` ≥ 5.0.0; `create` ≥ 6.0.9 | LGPL-3.0 |
| `ars_technica` | 1096161 / 7642730 | `ars_nouveau` ≥ 1.21.1-5.11; `create` ≥ 6.0.8; Curios indicado por [el autor](https://www.curseforge.com/minecraft/mc-mods/ars-technica) | LGPL-3.0 |
| `ars_ocultas` | 907843 / 8586886 | `ars_nouveau` [5.3, 6); `occultism` ≥ 1.220.4 | LGPL-3.0 |
| `ars_additions` | 974408 / 7646325 | `ars_nouveau` ≥ 5.0.0; `curios` ≥ 1.21-9.0.0 | LGPL-3.0 |
| `ars_controle` | 1061812 / 7534518 | `ars_nouveau` ≥ 5.10.6; `curios` ≥ 1.21-9.0.0 | LGPL-3.0 |
| `arseng` | 905641 / 6203425 | `ae2` ≥ 19; `ars_nouveau` ≥ 5.2 | LGPL-3.0 |
| `ae2importexportcard` | 982512 / 8033654 | `ae2` ≥ 19.2 | MIT |

El lock vigente aporta `ars_nouveau` 5.13.1, `create` 6.0.10, `occultism` 1.224.4, `ae2` 19.2.17 y `curios` 9.5.1. Ars Elemental embebe Sauce 0.0.47.92; Ars Technica embebe Sauce 0.0.16.46; la tarjeta AE2 embebe GrandPower 3.0.2. Son JarJar internos, no proyectos de contenido adicionales. Ambas versiones embebidas de Sauce resolvieron en el arranque dedicado normal y QA; su interacción funcional aún requiere prueba. La ficha pública de [Ars Creo](https://www.curseforge.com/minecraft/mc-mods/ars-creo) y la de [Ars Technica](https://www.curseforge.com/minecraft/mc-mods/ars-technica) dicen GPL-3.0, mientras sus `neoforge.mods.toml` internos dicen LGPL-3.0; para créditos de publicación conviene consignar GPL-3.0 de manera conservadora y revisar con el autor o el archivo de la versión distribuida si se precisa una afirmación jurídica exacta. El repositorio sólo guarda referencias a archivos oficiales de CurseForge.

## Balance e integración pendiente

Se conservan todas las recetas nativas. No hay evidencia de una salida o coste excesivo que justifique reemplazar IDs, serializers, remainders o resultados. Ars Creo y Ars Technica comparten una vía de Source hacia potencia cinética, pero el primero sirve a contraptions y el segundo a glyphs de procesado; medir ambos generadores evitará una ruta ilimitada de SU. El Source Acceptor de Ars Énergistique cambia Source por energía AE, no por FE; aun así hay que comprobar costes y posible ciclo con máquinas que regeneren Source. Su único archivo local 1.21.1 está marcado beta por [CurseForge](https://www.curseforge.com/minecraft/mc-mods/ars-energistique/files/6203425).

Ars Additions incluye Warp Index, acceso remoto a Source y un ritual de carga de chunks que [declara desactivado por defecto](https://www.curseforge.com/minecraft/mc-mods/ars-additions); Ars Controle añade Scryer's Linkage y portales. Esas rutas pueden alterar costes de viaje o mantener talleres activos. Confirmar defaults, límites de distancia, uso con claims y comportamiento de chunks antes de cambiar configuración. Ars Ocultas debe probarse con hoppers, tuberías y starbuncles alimentando jars sin duplicar materiales. La tarjeta AE2 debe verificarse con un terminal inalámbrico real, filtros y crafting card, sin extraer más de lo pedido ni saturar el inventario.

El integrador debe cargar los ocho JARs junto al lock actual en cliente y dedicado, comprobar registro de recetas en JEI/EMI y funcionamiento de al menos un flujo Ars→Create, Ars→Occultism y Ars→AE2, y medir TPS/MSPT, memoria y energía con mecanismos activos. Luego puede decidir un ajuste concreto que preserve el tipo de receta, remainders, outputs y uso de objetos regalados. El hash y el cierre estático de dependencias por sí solos no prueban arranque, compatibilidad ni balance.

Se descartaron `ars_elemancy` por más equipo elemental sobre la misma especialización; `ars_unification` por ampliar transmutaciones sin una economía revisada; `AppliedFlux` porque las celdas FE de RFTools Power ya cubren almacenamiento de energía; `Applied Enhancements` porque modifica planificación y rutas AE2 de forma amplia sin ensayo de regresión; y `occultism_kubejs` porque no agrega una mecánica jugable hasta tener una integración KubeJS concreta. No se agregaron para inflar el conteo.

## Corrección del candidato de tarjetas AE2

El primer arranque dedicado detectó que 1.6.0 declara `AbstractContainerScreenAccessor` dentro de los mixins comunes e intenta cargar una pantalla de cliente en servidor. El servidor llegó a iniciar y guardar, pero conservó esa línea ERROR. El archivo oficial 1.9.0 también declara ese accessor común; actualizar al último archivo no resuelve la causa. Se eligió [1.5.0 oficial](https://www.curseforge.com/minecraft/mc-mods/ae2-import-export-card/files/8033654), cuyo mixin de pantalla está sólo en `client` y no contiene aquel accessor. Conserva importación/exportación; no incluye la ampliación de capacidad de filtros de 1.6.0. No se parcheó ni silenció ningún JAR. El archivo rechazado quedó fuera de mods y conservado en el respaldo privado de esta prueba. La verificación dedicada y el alcance de cliente pendiente quedan en [la evidencia de integración](../verification/magic-cooking-teams-runtime.json).

Con el archivo seleccionado, tanto QA como el arranque/guardado normal del dedicado terminaron sin líneas ERROR. El autocrafteo AE2 existente completó dos ciclos con sus cuencos devueltos. La configuración nativa generada confirma que la receta del ritual de chunkloading de Ars Additions sigue desactivada. Esto cubre carga y una regresión AE2; los flujos mágicos activos, las tarjetas inalámbricas, el cliente y su rendimiento siguen pendientes.


Los tres flujos acotados se ejecutaron después en el dedicado: Source Jar→Source Motor→eje Create, Foliot contenido→trituración nativa y Source Jar→relay→Source Acceptor→almacenamiento ME. Los recursos fueron finitos, con parada/corte y ausencia de duplicación según el caso. La primera observación de energía AE2 leyó un índice cacheado antes de actualizarse; se corrigió el test, no el addon. Ver [evidencia](../verification/ark-workshop-magic-runtime.json). No acredita generación autosostenida de Source, todas las máquinas, rendimiento industrial ni interacción de cliente.
