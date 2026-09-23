# Arquitectura de asentamientos: tres piezas con función propia

El fragmento `catalog/families/settlement-architecture.json` selecciona **tres** mods de contenido, no los seis candidatos. Suman 2.460.883 bytes comprimidos; eso no es una medida de RAM o FPS. Los JARs son los archivos oficiales presentes en la instancia de referencia consultada sólo en lectura: sus SHA-1 locales coinciden con `minecraftinstance.json`, y el fragmento fija SHA-256, nombre, ID de proyecto y de archivo CurseForge. No se copió ningún JAR, asset, script ni quest al repositorio.

| Mod fijado | Introducción libre y utilidad tardía | Función distinta y archivo upstream |
| --- | --- | --- |
| `mcwfences` · `mcw-mcwfences-1.2.1-mc1.21.1neoforge.jar` | I: cercos, setos y portones para huertas y viviendas; IV-VI: delimitar rutas seguras entre talleres Create, observatorio y Arca | Cercos de madera y metal, setos y portones apilables; [archivo 7308338](https://www.curseforge.com/minecraft/mc-mods/macaws-fences-and-walls/files/7308338) |
| `mcwlights` · `mcw-lights-1.1.5-mc1.21.1neoforge.jar` | I-II: iluminación de acceso y hogares; IV-VI: jardines, balcones y salas habitadas | Luces de jardín, pared y techo, ventiladores con luz y candelabros; [archivo 7304075](https://www.curseforge.com/minecraft/mc-mods/macaws-lights-and-lamps/files/7304075) |
| `mcwwindows` · `mcw-mcwwindows-2.4.2-mc1.21.1neoforge.jar` | I-II: ventanales, persianas y postigos; IV-VI: observatorio y viviendas del Arca con líneas de vista regulables | Ventanas redimensionables, blinds y shutters con estados abiertos/cerrados; [archivo 7317672](https://www.curseforge.com/minecraft/mc-mods/macaws-windows/files/7317672) |

Ningún bloque decorativo es ingrediente obligatorio de campaña, condición para habitar el Arca o puerta de uso por acto. La elección de paleta queda en manos del jugador y un regalo puede usarse desde el inicio. Los tres mods deben cargarse en cliente y servidor: registran bloques y recetas en código común y ninguno declara lado cliente exclusivo. La clasificación inicial se infirió del JAR y ahora tiene arranque dedicado confirmado. El render en cliente con la familia instalada sigue pendiente.

## Por qué se detiene en tres

`Chipped` ya aporta 449 nombres de bloque con “Door” y 242 con “Trapdoor”; `Copycats+` agrega puertas plegadizas/corredizas y trampillas de material copiado, además de las formas de `FramedBlocks`. Macaw's Doors tiene portcullis y garage door propios, pero incorporar sus 269 nombres de bloque y 1.134 modelos sólo por esas excepciones no justifica el solapamiento inicial. Macaw's Trapdoors repite casi toda su familia visual en otras 187 variantes. Se descartan ambos por ahora, no se sustituyen recetas existentes.

`Rechiseled` ya aporta 156 nombres de pavimentos, `FramedBlocks` un path, y Bridges/Roofs resuelven pasos elevados y cubiertas. Los 315 nombres de Paths y Pavings multiplicarían superficies parecidas sin una función nueva clara. Por contraste, el pack actual sólo ofrece cercos generales de Framed/Copycats, pocos cerramientos en Supplementaries y ningún juego comparable de postigos/persianas operables. Chipped tiene muchas texturas de lámparas y Supplementaries faroles; Lights queda por sus luminarias de jardín/techo, ventiladores y candelabros, no por recolorear redstone lamps.

## Proveniencia, receta nativa y límites

Los JARs declaran `MIT` para Fences y `All Rights Reserved` para Lights/Windows en `META-INF/neoforge.mods.toml`; sus metadatos CurseForge no declaran dependencias adicionales. Las dependencias son referencias a archivos oficiales para instalación por la App, sin redistribuir sus binarios. Los tres registran respectivamente 180, 140 y 310 nombres de bloque, y 674, 373 y 1.561 modelos de bloque JSON. Son opciones de construcción, no proyectos ni quests separados. La carga de modelos y de ventanas translúcidas, las luminarias repetidas y la memoria cliente necesitan medición en poblados reales sobre el equipo objetivo; el tamaño del JAR y la prueba headless no establecen rendimiento.

Se conservan las recetas y loot tables nativas. Para GameTests de crafting/drop, conviene usar estos casos representativos y comprobar el `RecipeManager` cargado antes de colocar y romper los bloques:

| ID de receta/bloque | Ingredientes nativos en grilla | Salida | Drop nativo sin explosión |
| --- | --- | --- | --- |
| `mcwwindows:oak_window` | ` A /ABA/ A `: A=`mcwwindows:window_base`, B=`minecraft:oak_log`; la base sale x4 de ocho sticks y un glass | x4 | x1 por bloque, `oak_window` |
| `mcwwindows:oak_shutter` | `B/B/B`: tres `minecraft:oak_trapdoor` | x3 | x1, `oak_shutter` |
| `mcwfences:oak_picket_fence` | `CBC/CAC`: C=`minecraft:oak_log`, B=`minecraft:oak_planks`, A=`minecraft:stick` | x3 | x1, `oak_picket_fence`, con `survives_explosion` |
| `mcwfences:oak_curved_gate` | `B A/BAA`: B=`minecraft:oak_log`, A=`minecraft:oak_planks` | x4 | x1 **por pieza colocada**, `oak_curved_gate`, con `survives_explosion` |
| `mcwlights:thin_garden_light` | ` N / L / I `: iron nugget, glowstone dust, iron ingot | x1 | x1, `thin_garden_light` |
| `mcwlights:oak_ceiling_fan_light` | ` A /BCB/ D `: A=iron nugget, B=iron ingot, C=oak slab, D=glowstone dust | x1 | x1, `oak_ceiling_fan_light` |

`mcwfences:oak_curved_gate` usa `com.mcwfences.kikoz.objects.DoubleGate`. Su propiedad `fencepart=bottom|top` se calcula por otro gate igual arriba/abajo: **colocar una pieza no crea gratis otra**. Dos ítems apilados forman el portón alto; su loot table entrega un ítem por cada bloque roto y no lleva condición de mitad. Este caso merece un GameTest de dos colocaciones/dos drops, además del crafting x4. Las ventanas prueban los estados de postigo y geometría redimensionable; una prueba de receta/drop por sí sola no certifica render, interacción ni visibilidad.

Una comparación estática de los 623 `crafting_shaped`/`crafting_shapeless` de estos tres JARs contra 170 JARs instalados no halló IDs repetidos ni patrones con ingredientes idénticos (se consideró el espejo de patrones). Tampoco se halló colisión entre las seis recetas de la tabla. El chequeo no resuelve solapamientos semánticos entre tags, recetas generadas ni overrides de datapacks: el cierre requiere RecipeManager real tras instalar el lote. No hay conflicto concreto que justifique cambiar recetas, tipos, outputs o remainders.

El lote instalado pasó dos pruebas nativas: siete recetas elegidas por el RecipeManager global, colocación y apertura de postigos/portón, rotura real de dos piezas con exactamente dos ítems recuperados e iluminación de jardín que cambia de 15 a 0. El recibo acotado está en [arcane-building-runtime.json](../verification/arcane-building-runtime.json); no certifica todas las variantes, render ni rendimiento del cliente.
