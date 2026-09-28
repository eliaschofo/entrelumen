# Selección de mods por ida y vuelta con Elias

> **Renumeración de actos, 24/9:** el Arca pasó al acto V y Solsticio es el VI ([act-renumbering.md](act-renumbering.md)). El escalonado de las rondas 1 a 4 va por componente de acto y no cambia: el Bus del Arca y el Motor de Renovación ya eran del V, y ninguna receta usaba componentes del acto 6. Psi pasó entero al III.

Desde el 24 de septiembre de 2026 no hay un número objetivo de mods: cada mod tiene que contar, tener sentido y no arruinar el rendimiento. El controlador propone por tipo; Elias elige. Antes de instalar, cada elegido se verifica: versión para NeoForge 1.21.1 (21.1.249), dependencias, conflictos, costo de rendimiento y escalonado por actos.

## Ronda 1 (24/9)

| Tipo | Elegidos por Elias | Notas |
|---|---|---|
| Tecnología | **GregTech** si existe para 1.21.1 NeoForge; si no, **Modern Industrialization**. También **Oritech** y **Ender IO** | Se revierte la exclusión anterior de Oritech, Ender IO y MI por solapamiento: ahora se integran con propósito. |
| Magia | **Botania**, **Blood Magic**, **Mahou Tsukai** | Botania encaja con la estética de jardines de Solsticio. |
| Exploración y dimensiones | **Ad Astra**, **Deep Aether**, **Eternal Starlight**, **The Undergarden** | Deep Aether amplía el Aether, clave en la historia (Sun Spirit). Se revierte el descarte anterior de Undergarden. |
| Criaturas y fauna | **Naturalist**, **Friends & Foes**, **Creeper Overhaul + Enderman Overhaul**, **Mowzie's Mobs** | |

Pedidos previos del mismo día, a cargo del worker del contenido luminoso: **Silent Gear**, con el Lingote Luminoso como material, y un **mod de luz dinámica** compatible con Sodium.

## Ronda 2 (24/9)

| Tipo | Elegidos por Elias | Notas |
|---|---|---|
| Almacenamiento y logística | **Refined Storage**, opcional para quien lo prefiera; **Mekanism Covers** y **Mekanistic Routers** | RS convive con AE2 como alternativa, sin reemplazarlo; recetas y escalonado equivalentes. Se revierte la exclusión anterior. |
| Granja y comida | **Pam's HarvestCraft Crops**, **addons de Farmer's Delight** (Delights temáticos) y **Croptopia** | Evitar cultivos duplicados con Almost Unified y tags comunes. |
| Construcción y decoración | **Create: Deco** y **Steam 'n' Rails**; **Chisel & Bits** o su equivalente para 1.21.1; **más muebles** (Another Furniture o similar) | Chisel & Bits es pesado: medir. |
| Visual y ambiente | Ninguno | Se mantiene el look vanilla. |

## Ronda 3 (24/9)

| Tipo | Elegidos por Elias | Notas |
|---|---|---|
| QoL | **Carry On** | Levantar cofres y animales; revisar el bloqueo de entidades y bloques sensibles (spawners, altares, bloques del Arca, ruinas protegidas). |
| Transporte | **Create: Steam 'n' Rails** | Ya elegido en decoración; también cumple como transporte. |
| Aventura y loot | **Repurposed Structures** | Medir el costo de worldgen. |
| Jefes y combate | Ninguno | |

## Ronda 4 (24/9)

| Tipo | Elegidos por Elias | Notas |
|---|---|---|
| Magia | **Psi** | Hechizos programables; encaja con Terra, la inventora. |
| Tecnología | **Create: New Age** | «Es RECONTRA Heliodor»: la tecnología solar característica de Heliodor (paneles solares, electricidad, generadores y bobinas), escalonada con los componentes de acto. |
| Cocina automatizada | **Create: Central Kitchen** y **Create Slice & Dice** | Automatizan Farmer's Delight con Create; revisar que no se pisen entre sí ni con lo que ya hay. |
| Estructuras | **Dungeons and Taverns** | Estructuras livianas; medir la generación del Overworld, que ya había subido un 72% con la ronda anterior. |
| Transporte | Ninguno | Botania, Blood Magic y Steam 'n' Rails quedan afuera. |

## Descargas

El 24/9 Elias autorizó bajar de las fuentes oficiales (CurseForge y Modrinth) los mods que eligió en estas rondas, aunque no estén en ninguna instancia local: «no estamos copiando, estamos tomando inspiración, no importa si están o no en otro pack, bajalos porque te lo pedí». Por separado aprobó LambDynamicLights 4.8.11+1.21.1 desde Modrinth. Para la ronda 4 autorizó lo mismo: los mods elegidos y sus dependencias obligatorias, desde Modrinth o CurseForge, con los hashes verificados, todo gratis y sin login. Cada JAR se verifica por hash y se fija en el catálogo con su fuente.

## Resultado de la integración (rondas 1 a 3)

Rama `feature/mods-r123`, 24/9. Familia `catalog/families/mod-pingpong.json`: 29 mods elegidos y 4 librerías. El lock pasa de 275 cliente / 234 servidor a **308 / 266**; ninguna entrada previa cambió. Los JAR nuevos viven en `E:/Elias/Codex/Entrelumen-ssd/catalog-downloads` (el `catalog/downloads` de este worktree es un junction ahí) y `catalog/local-paths.json` los nombra con esa ruta.

Fuentes: 15 descargas del CDN oficial de Modrinth con SHA-1 y SHA-512 de la API verificados; Croptopia del CDN oficial de CurseForge, con el tamaño del registro oficial verificado (CurseForge no publica hash y el archivo no está en Modrinth); 17 de la instancia de referencia ATM10 con el SHA-1 de CurseForge verificado y, cuando los mismos bytes están en Modrinth, también su SHA-512. Nada pidió pago ni login. `tools/curate_pack.py` ahora acepta pins de Modrinth (`"provider": "modrinth"`, proyecto, versión, URL del CDN y los dos hashes) y `--check` vuelve a verificar SHA-1 y SHA-512.

### Entró

| Mod | Versión | Fuente | Acto | Escalonado o integración |
|---|---|---|---|---|
| Modern Industrialization | 2.5.6 | CF 405388/8597392 (ATM10) | II-V | Reemplazo de GregTech (ver abajo). Reactor nuclear con Bus del Arca (V); circuitos cuánticos con Motor de Renovación (V); cantera eléctrica con Lente Espectral (IV); pechera gravitatoria con Carta de Horizonte (IV); jetpack diésel con Regulador de Potencia (III). Sin replicador ni estrella del Nether por implosión. Sin guía al entrar ni al reaparecer. |
| Oritech | 1.2.11 | CF 1030830/8754466 (ATM10) | III-V | Jetpacks (III), exo-jetpack (IV), taladro profundo (IV), controlador de spawner (IV), bombas (IV), acelerador de partículas (V). Sin estrella del Nether por colisión de partículas. |
| Ender IO | 8.2.11-beta | CF 64578/8192838 (ATM10) | II-V | Ancla y bastón de viaje (III), spawner con energía (IV), capacitor octádico (V). |
| Mahou Tsukai | 1.36.27 | CF 342543/8212674 (ATM10) | III-IV | Progresión nativa. |
| Ad Astra | 1.16.19 | CF 635042/8758526 (ATM10) | V | El banco NASA pide una aleación atómica (acto V) desde la auditoría de recetas (71254d3): los cohetes y los planetas se abren en el acto V. |
| Deep Aether | 1.1.5.1 | Modrinth gcHIih5B/MSW5emg8 | IV | Amplía el Aether por su mismo portal; requiere el Aether 1.5.10 del lock. |
| Eternal Starlight | 0.9.0 | CF 1080592/8668881 (ATM10) | IV | El Orbe de la Profecía pide una Carta de Horizonte (acto del observatorio). |
| The Undergarden | 0.9.6 | CF 379849/7862546 (ATM10) | III | El catalizador pide una Matriz de Enrutamiento. |
| Naturalist | 2.0.4 | Modrinth F8BQNPWX/RzdhM6Zg | I | Fauna; los bichos chicos no cruzan portales. |
| Friends & Foes | 4.0.27 | Modrinth BOCJKD49/zeGwtTNo | I | Nativo. |
| Creeper Overhaul | 4.0.6 | CF 561625/6051279 (ATM10) | I | Nativo. |
| Enderman Overhaul | 2.0.3 | CF 574409/7661859 (ATM10) | I-III | Nativo. |
| Mowzie's Mobs | 1.8.2 | Modrinth BFbX9xcm/xgAXTl17 | II-IV | Minijefes opcionales; no cierran actos. |
| Refined Storage | 2.0.9 | CF 243076/8211701 (ATM10) | III | Alternativa a AE2: el controlador pide Matriz de Enrutamiento y el autocrafter un Núcleo de Manipulación, la misma entrada que AE2 en el acto III. Con la integración de Mekanism (paridad con Applied Mekanistics) y la de EMI (sólo cliente). |
| Mekanism Covers | 1.3-BETA | CF 1119874/6071684 (ATM10) | III | Nativo. |
| Mekanistic Routers | 1.2.0 | CF 1148201/7511369 (ATM10) | III | Nativo. |
| Pam's HarvestCraft 2 Crops | 1.0.9 | CF 361385/8064883 (ATM10) | I | Completa Food Core y Trees, que ya estaban. |
| Croptopia | 4.2.4 | CF 415438/7958876 (CDN de CurseForge) | I | Requiere EpheroLib. |
| Twilight's Flavor & Delight | 3.2.2 | Modrinth d6cSefpO/HNXR3CwJ | IV | Comida del Bosque Crepuscular. |
| End's Delight | 2.6.1 | Modrinth yHN0njMr/YTApg6Hl | IV | Comida del End. |
| My Nether's Delight | 1.10.4.1 | Modrinth O53VhQoZ/SGFJBL5q | II | Comida del Nether. |
| Aether's Delight | 0.1.4.2 | Modrinth XUztKPS9/l6fgFjUf | IV | Comida del Aether. |
| Create Deco | 2.1.3 | Modrinth sMvUb4Rb/qrcMVoBD | II | Nativo. |
| LittleTiles | 1.6.0-pre228 | Modrinth RCRxC1tD/GAeVlVcY | I | Equivalente de Chisels & Bits para 1.21.1; medido aparte. |
| Another Furniture | 4.0.3 | Modrinth ulloLmqG/cugGiMKt | I | Nativo. |
| Carry On | 2.2.6 | Modrinth joEfVgkn/PV8oLZ1q | QoL | Lista negra (ver abajo). |
| Repurposed Structures | 7.5.22 | CF 368293/8688394 (ATM10) | I-IV | Loot por Lootr; costo de worldgen medido. |

Librerías agregadas: CreativeCore 2.13.46 (LittleTiles), EpheroLib 1.2.0 (Croptopia), Common Storage Lib 0.0.10 y Resourceful Config 3.0.11 (Ad Astra, Creeper y Enderman Overhaul). Los Delights elegidos son los de las dimensiones con ruina o jefe en la historia (Aether, Twilight, Nether, End); Undergarden Delight y Brewin' and Chewin' quedaron afuera para no inflar.

### Quedó afuera

| Pedido | Motivo y evidencia | Alternativa |
|---|---|---|
| GregTech CEu Modern | La única versión para NeoForge 1.21.1 es 7.0.2 beta (julio de 2025; las 7.x siguientes salieron sólo para 1.20.1). En servidor dedicado no carga ni siquiera sola: `Attempted to load class net/minecraft/client/multiplayer/ClientLevel for invalid dist DEDICATED_SERVER` en `CommonInit.init` (prueba con GTCEu como único mod). | **Modern Industrialization**, el reemplazo que eligió Elias; ya entró. |
| Botania | Sin archivo para 1.21.1 en CurseForge (último: 1.20.1-456, 17/9/2026) ni en Modrinth. | Nature's Aura y Ars Nouveau, que ya están. |
| Blood Magic | Sin archivo para 1.21.1 (último: 1.20.1 3.3.8, 26/7/2026). | EvilCraft, que ya está, o el addon Blood Mages de Iron's Spells. |
| Chisels & Bits | 21.1.33 existe, pero su librería Saecularia Caudices viene anidada en tres niveles y su núcleo no carga; el mixin falla y además tumba la carga de Immersive Engineering. | **LittleTiles**, el equivalente; ya entró. |
| Create: Steam 'n' Rails | La versión oficial llega hasta 1.20.1. Para 1.21.1 sólo hay un port no oficial (PoppyBlossom, 0.3.0-beta.2 en Modrinth). | Decide Elias: ese port no oficial en beta, o seguir sin él. |

### Integración

- **Escalonado** (`tools/generate_family_balance.py`, familia `pingpong`): 22 recetas nativas con un componente de acto en un solo casillero, 6 recetas quitadas (el replicador de MI, los gemelos de ensamblador de los controladores escalonados de MI y las estrellas del Nether de MI y Oritech: el Wither sigue siendo la única fuente) y 82 recetas upstream que no parsean en 1.21.1 desactivadas (compat de Ad Astra con Create, Mekanism e IE en formato viejo, compat de Croptopia con Botany Pots en formato viejo, reparaciones de Malum para Undergarden y un tinte de Create Deco). El script comprueba al cargar que cada receta escalonada tiene su componente: `loaded-ingredient-check`, 28 verificadas, 0 fallas.
- **Ad Astra, cofres** (28/9): la 1.16.19 trae el loot de sus cofres en la carpeta de 1.20 (`data/*/loot_tables/`), que 1.21 no lee, así que los cofres de sus estructuras en la Luna, Marte y Venus salían vacíos (59 en sus plantillas); la familia `pingpong` porta sus 6 tablas (5 de `ad_astra:chests/` y la heredada `minecraft:loot` de Venus) a `loot_table/` con el contenido del mod, y sólo los libros encantados nombran `#minecraft:on_random_loot`, el mismo sorteo que hacían en 1.20. `tools/check_loot_tables.py --structures adastra-` verifica que cada cofre nombre una tabla que 1.21 carga; falta abrirlos en un servidor.
- **Loot y estructuras que no cargaban** (28/9, `tools/check_loot_tables.py --structures`, que ahora también exige que cada pool nombre plantillas que existen y sólo cuenta lo que coloca una estructura que puede generarse): el pool de la mazmorra lunar de Ad Astra pide `library` y el JAR trae `libary.nbt`, así que el override del pool apunta a `libary` y la biblioteca (5 cofres) vuelve a salir; `ad_astra:venus_bullet` no tiene plantilla y cada inicio era una pieza vacía de 2×2×2 con terreno adaptado (`beard_thin`) y un hallazgo de `/locate`, sin error en el log, así que se apaga vaciando su tag de biomas `#ad_astra:has_structure/venus_bullet` (`replace: true`, sólo lo usa ella); el `tiny_skull` de Enderman Overhaul (todos los derechos reservados) no se copia: recibe una tabla nuestra escrita de cero, la de auto-drop de vanilla con `minecraft:survives_explosion`; los alias tiran una vez la tabla destino: Deep Aether `minecraft:chests/altar_camp` → `deep_aether:chests/dungeon/altar_camp`, Cataclysm `minecraft:chests/village_desert_house` → `minecraft:chests/village/village_desert_house`, Dungeons and Taverns `pots/pot_piglins` → `pot_piglin` y los barriles del escondite illager `raw_vegitarian`, `raw_trash`, `lesser_meat` y `raw_weaponry` → la tabla de los cofres del mismo cuarto (`vegitarian`, `trash`, `meat`, `weaponry`), Towns and Towers `village_leatherworker` → `village_tannery` (como sus otros talabarteros), su casa de abedul → `village_plains_house` y, en las ruinas del bosque, carnicero → `village_butcher`, biblioteca → `village_cartographer` (vanilla no tiene cofre de biblioteca) y templo → `village_temple`; quedan como están la casa grande de las ruinas del bosque de T&T, que pide `wythers:chests/village/forest_ruins_big_house` (Wythers no está en el pack), y los 7 `ruin_town_loot_chest` de Dungeons and Taverns, que no coloca ningún pool; ninguna tabla suma materiales de acto. Licencias: de Ad Astra se copian datos porque su Terrarium License v1 los deja en MIT (aviso en `THIRD_PARTY_NOTICES.md`); de Deep Aether, Cataclysm, Dungeons and Taverns y Towns and Towers no se copia nada, sólo alias, y `check_loot_tables.py --copies` falla si un archivo de `pack/kubejs/data` copia datos de un JAR con derechos reservados. Los 5 cofres de ruinas de los actos IV y V del companion sortean de `#minecraft:on_random_loot` y el check rechaza un `enchant_randomly` sin `options`. Falta verlo en un servidor.
- **Almost Unified**: config `crops` nueva con 69 nombres de cultivo duplicados (`c:crops/*` y `c:seeds/*`), prioridad Farmer's Delight, Croptopia y Pam's, y unificación de loot para que toda planta suelte el mismo ítem; `materials` suma MI, Oritech, Ender IO y Ad Astra a las prioridades. El lingote de biosteel de Oritech queda en `ignored_items` (27/9): Oritech lo etiqueta como `c:ingots/steel`, así que Almost Unified lo escondía y hacía que sus recetas entregaran acero de IE; no se podía fabricar y cuatro aumentos de Oritech que lo piden por nombre (vida, más vida, velocidad y respiración) quedaban imposibles. Sigue en el tag de acero, como lo diseñó Oritech. La receta de haunting de Create del tomate podrido y las semillas de avena y batata de Pam's (que son a la vez cultivo) quedan fuera porque rompían.
- **Carry On**: se conservan sus valores por defecto y se agregan todos los bloques y entidades de ENTRELUMEN, spawners, bóvedas, contenedores de Lootr, altares de jefes, cofres de mazmorra del Aether y piezas de multibloques o redes (AE2, MI, Oritech, conductos de Ender IO, carcasas de Mekanism). Las ruinas protegidas también quedan cubiertas: Carry On publica un `BreakEvent` antes de levantar y la protección lo cancela. Sólo se levantan bloques con inventario (`pickupAllBlocks = false`).
- **Deep Aether**: usa el portal, el acto y los jefes del Aether ya existente; suma su jefe (Eye of the Storm) y drops por victoria perfecta; Aether's Delight completa la comida.
- **Refined Storage frente a AE2**: misma entrada (acto III, componentes de logística); no reemplaza a AE2.
- **Teclas**: 24 atajos nuevos chocaban (V, R, M, G, H, Y, B, C, Z, el punto y las flechas); el preset los mueve a teclas libres o combinaciones con Alt, Ctrl o Shift. Auditoría simulada sin superposición en el mundo.
- **QA**: `ModPingpongFullpackGameTests` (lote cargado sin los rechazados, Carry On rechaza los bloques sensibles, un tomate maduro de Croptopia suelta tomate de Farmer's Delight); el bootstrap de QA también registra ahora las suites de brújula y jugabilidad.

### Rendimiento (servidor dedicado, antes y después)

Copias propias del servidor de QA en E: (`server-mods-r123-base` con el lock de `main`, `server-mods-r123` con este lock); `server-slice` y los perfiles de cliente no se tocaron. Heap de 6 GB, mundo nuevo con la semilla de QA en cada corrida, sin jugadores. El worldgen se mide forzando la carga de 256 chunks (16 tandas de 4×4) por dimensión; es generación síncrona, el peor caso: en juego la generación es asíncrona. La PC estaba compartida con otros workers: la carga del sistema figura en cada par.

| Medida | Antes | Después | Notas |
|---|---|---|---|
| Arranque, pared (par 1, carga 25-75%) | 262,8 s | 299,4 s | +14% |
| Carga total según ModernFix (par 1) | 274,7 s | 322,3 s | +17%; datapacks 1,24 → 1,63 min |
| CPU de la JVM hasta `Done` (par 2, carga 83-94%) | 713 s | 836 s | +17% |
| TPS en reposo | 20 | 20 | ambos pares |
| MSPT en reposo, mediana / p95 (par 1) | 0,4 / 0,7 ms | 0,7 / 1,5 ms | |
| Heap usado en reposo | 1,4-1,8 GB | 2,6-2,8 GB | +1,0-1,2 GB de 6 GB |
| Overworld, 256 chunks (par 1) | 40,3 s y 31,6 s | 69,4 s y 57,2 s | +72% y +81% |
| Aether, 256 chunks (par 1) | 17,9 s | 19,3 s | +8% con Deep Aether |
| Twilight Forest / Nether (par 1) | 31,7 s / 19,4 s | 32,2 s / 20,7 s | sin cambio apreciable |
| Undergarden / Eternal Starlight | — | 33,5 s / 73,5 s | Starlight bajó a 2,9 TPS durante la tanda forzada (tick máximo 10 s) |
| Luna / Marte (Ad Astra) | — | 42,2 s / 35,5 s | |

- **Repurposed Structures**: sin él (carga 33-44%) el Overworld tardó 59,6 s y 47,1 s, frente a 69,4 s y 57,2 s con él (carga 50-75%). Aporta del orden del 15-20% del tiempo de generación del Overworld, dentro del ruido de la máquina; la mayor parte del aumento viene del lote entero (minerales de MI y Oritech, cultivos silvestres, fauna).
- **LittleTiles** (medido aparte): sin él y con CreativeCore también afuera, el arranque tardó 252 s y el heap en reposo fue el mismo (2,6 GB). En servidor su costo no se distingue del ruido; su peso real es de render en el cliente, que no se midió.
- **Veredicto**: ningún mod hunde por sí solo el rendimiento del servidor, así que no se sacó ninguno. Lo más caro es la generación de Eternal Starlight (una dimensión del acto IV) y el aumento del Overworld en la primera exploración; conviene medirlo en cliente en una PC modesta.
- Errores en el log de un arranque limpio: de 1 a 3, todos upstream e inofensivos: el mapa de experiencia de Create Enchantment Industry nombra fluidos viejos de Reliquary y Ender IO (el override del pack ya los corrige; el archivo del mod igual se queja) y el tag `minecraft:rabbit_food` de Pam's trae un typo.

Recibos: `E:/Elias/Codex/Entrelumen-ssd/mods-r123-20260924` y [`docs/verification/mod-pingpong-runtime.json`](../verification/mod-pingpong-runtime.json).

### QA de pack completo

Servidor limpio nuevo (`server-mods-r123-qa`: sólo librerías, los 266 JAR del lock, los archivos de `pack/` y el JAR de QA), mundo nuevo, `-Dentrelumen.qa=true`, rama con `main` mergeado (brújula, jugabilidad, comercio y ciudad v6 de Solsticio). Las 113 GameTests registradas corrieron de a una, porque comparten datos de campaña.

- **101 pasan y 12 fallan; ninguna falla viene de este lote.**
  - Del lote: `pingpongbatchloadedwithoutrejectedbuilds`, `carryonrefusessensitiveblocks` y `unifiedcropsdropfarmersdelighttomatoes` pasan.
  - `fullpackfirstjoingiftsstaydisabled` pasa, así que la guía de MI quedó apagada.
  - `fullpackplayerarrivesemptyhanded` y `newplayerarriveswithanemptyinventory` fallan: un jugador nuevo recibe `ars_nouveau:starbuncle_plush`, `silentgear:material_book` y `silentgear:blueprint_package`.
  - `startruinisanchoredregisteredandplacedonce` falla: el punto de llegada no es seguro (x=4088).
  - `compassmovestothenextobjectivewhentheteammeetsthecondition` y `compasssearchesstayboundedandcooperative` fallan.
  - `satietyoverflowcountsbiteseatenfromblocks` falla porque necesita el bloque del fixture aislado.
  - `storytieronlyrisesandcannotbechosen` falla porque otras pruebas ya subieron los tiers.
  - **Atribución**: las mismas 7 fallan igual en el servidor de antes, con el lock de `main` y el mismo JAR de QA. Son de `main`, no del lote.
  - Las 4 de reinicio y backup (`preparelivebackupfixture`, `verifylogisticsrestartfixture`, `verifyrestoredlivebackup` y `verifyteamrestartafternewserverprocess`) necesitan otro proceso o un ZIP de backup; no aplican en una sola corrida.
  - `altarsrespectforeignftbchunksclaims` falló por tiempo con la máquina cargada y pasó al repetirla, igual que las dos de restauración de naturaleza, que en una corrida `runall` concurrente habían fallado por estado compartido.
- **Comercio de Solsticio**: las 16 tablas de tiendas y las 6 de nativos resuelven todos sus ítems y tags con el pack cargado (`solsticioshoptablesresolveinthefullpack`, que pasa también con el lock de `main`).
- **Ciudad de Solsticio**: todos los bloques de la paleta existen en el pack completo (`solsticiocitypaletteresolvesinthefullpack`, en los dos locks).
  - Colocación en el pack completo: 557.413 bloques de plantilla en 131 ticks, 2.584 ms colocando, peor tick de 121 ms y 35,9 s de pared.
  - Quedó con llegada, portal, 4 lotes y los puntos de Aurelia, Terra, Juan y Bodhi. El comercio pobló 66 aldeanos en 1,9 s, sin fallas.
  - Sin avisos de marcadores desconocidos.
- **Hallazgo de la brújula**: en la primera corrida (`test runall`, con mucha generación simultánea) el servidor se cayó por el watchdog. Un tick duró 60 s dentro de `CompassLocator$StructureJob.step`, que pide `level.getChunk(..., STRUCTURE_STARTS)` de forma síncrona en el hilo del servidor. Resuelto el 24 de septiembre en `fix/fullpack`: la búsqueda pide el chunk con un ticket propio y consulta el futuro en ticks siguientes, sin esperar en el hilo del servidor. Las otras fallas de esta QA, sus causas y la corrida en verde están en `docs/verification/fullpack-fixes-runtime.json`. Es código de la brújula: lo reporto a su dueño en lugar de tocarlo.
- Otros errores de log ajenos al lote: excepciones de ticks de entidades de hechizos atrapadas por el nivel (Ars Nouveau `EntityWallSpell`, Theurgy `FollowProjectile`, Iron's `ChainLightning`) durante una prueba de Ars.

### Pendiente para Elias

- Steam 'n' Rails: ¿el port no oficial para 1.21.1 o nada?
- Botania y Blood Magic no existen para 1.21.1; si se quiere algo parecido, decidir entre lo que ya está o un addon.
- Revisión en cliente: render de LittleTiles, Mowzie's y Eternal Starlight; las teclas nuevas y la sensación de la exploración inicial en una PC modesta (el Overworld genera un 70-80% más lento con el lote).
- Balance en juego: potencia de MI, Oritech y Ender IO frente a Mekanism; hechizos de Mahou Tsukai; loot de Repurposed Structures; la armadura cuántica de MI frente al equipo luminoso.
- Las quests y la brújula todavía no mencionan las dimensiones nuevas; en ellas la aguja queda gris.
- Recetas desactivadas que podrían reescribirse al formato 1.21 si se extrañan: las de Create, Mekanism e IE para los minerales de Ad Astra y las de Croptopia en Botany Pots.

## Resultado de la integración (ronda 4)

Rama `feature/mods-r4`, 24/9. Misma familia `catalog/families/mod-pingpong.json`: 5 mods elegidos, 1 librería nueva y 1 librería actualizada. El lock pasa de 308 cliente / 266 servidor a **314 / 272**. La única entrada previa que cambió es Create: Dragons Plus (ver abajo); el resto quedó idéntico byte a byte.

Fuentes:
- Los cinco mods y Kotlin for Forge vienen del CDN oficial de Modrinth, con SHA-1 y SHA-512 de la API de versiones verificados.
- Dragons Plus 1.11.9 viene del CDN de CurseForge: el tamaño coincide con el registro oficial, y los mismos bytes están en Modrinth con SHA-1 y SHA-512 verificados.
- Nada pidió pago ni login. Los JAR están en `E:/Elias/Codex/Entrelumen-ssd/catalog-downloads`.

### Entró

| Mod | Versión | Fuente | Acto | Escalonado o integración |
|---|---|---|---|---|
| Psi | 1.21.1-110 | Modrinth pOeA0exL/j9TFdTKC | III | Entero en el acto III (Elias, 24/9; antes el ensamblador era del II). El Ensamblador de CAD pide un Regulador de Energía (III): sin él no hay CAD ni hechizos. Los núcleos de psigema (hiperacelerado y radiativo) quedan nativos: el Ensamblador ya los cierra (`UPSTREAM` en `tools/generate_family_balance.py`, [recipe-design-rules](recipe-design-rules.md)). |
| Create: New Age | 1.2.0+mc1.21.1 | Modrinth FTeXqI9v/IwtuwMZy | II-IV | La tecnología de Heliodor; ver la tabla de abajo. |
| Create: Central Kitchen | 2.6.2 | Modrinth btq68HMO/whbguqT1 | II | Automatiza la olla de cocción, la sartén, la cocina, la tabla de cortar con brazo mecánico y los banquetes. |
| Create Slice & Dice | 4.3.4 | Modrinth GmjmRQ0A/D6mQaFRW | II | Rebanadora para las recetas de la tabla de cortar, aspersores y fertilizante líquido. |
| Dungeons and Taverns | v4.4.4 | Modrinth tpehi7ww/BYUUUeZA | I-IV | Tabernas, torres, criptas, campamentos y mazmorras chicas en el Overworld, el Nether y el End; su botín pasa por Lootr. |

Librerías:
- **Kotlin for Forge 5.12.0** (Modrinth ordsPcFz/uhJhCT7X): obligatoria para Slice & Dice.
  - Es un JAR que sólo trae librerías anidadas, sin `[[mods]]` propio.
  - `tools/curate_pack.py` ahora resuelve esos JAR por lo que proveen y los deja en los dos lados; antes los ignoraba.
- **Create: Dragons Plus 1.11.7b → 1.11.9** (CurseForge 1216624/8900055): Central Kitchen 2.6.2 (y también 2.6.1) exige 1.11.9 o superior.
  - La alternativa era Central Kitchen 2.6.0, que acepta 1.11.7b, pero trae el lag de servidor que corrigió 2.6.1: los Spouts sobre Depots revisaban recetas en cada tick.
  - 1.11.8 y 1.11.9 sólo agregan fichas agrupadas de Ponder y corrigen tablas de botín y una duplicación de baldes. Create: Enchantment Industry 2.5.3b acepta `[1.11.3,)` y carga sin quejas.
  - La familia declara el reemplazo (`replaces`), y `--check` rechaza el lock si 1.11.7b sigue en él.
  - 1.11.9 ya trae la condición `item_exists` en las tablas de botín de los tanques frágiles, así que la familia industrial dejó de pisarlas.

**Create no cambia: sigue en 6.0.10.**
- New Age pide `[6.0.9,6.1.0)`, Central Kitchen `[6.0.10,)` y Slice & Dice `[6.0.9,7.0.0)`.
- Slice & Dice trae anidado Ponder 1.0.87; el cargador conserva el 1.0.82 de Create (visto en el log).
- MixinExtras sale de NeoForge (0.5.3).

### Create: New Age, la tecnología de Heliodor

> **Actualización del 25 de septiembre** ([recipe-design-rules](recipe-design-rules.md)): el Acoplador pasó de la bobina a las escobillas de carbón (una por generador); la placa básica lleva una aleación infundida (II); la placa avanzada queda nativa; energizador y motor avanzados llevan aleación reforzada (III); energizador y motor reforzados y la barra de reactor, ironwood (IV); los núcleos de CAD de Psi quedan nativos. La tabla de abajo es la de la ronda 4.

New Age 1.2.0 no tiene paneles fotovoltaicos. Sus «paneles solares» son **placas de calentamiento solar**, que calientan las calderas de Create con la luz del sol. La electricidad sale de una **bobina generadora** que gira entre imanes y se recoge con **escobillas de carbón**.
- Los **energizadores** sobrecargan metales con esa electricidad.
- Los **motores** la vuelven a convertir en rotación.
- Hay un **reactor de torio** con barras, un aceptor de combustible y ventilaciones de calor.
- Las **farolas** (street light) se cargan con electricidad y alumbran según la luz del lugar.
- Genera torio y magnetita en el Overworld (ver las mediciones).

Las piezas clave llevan los componentes de acto que ya usa el pack; son materiales de Heliodor. El Marco de Calibración contiene la lente en bruto de la ruina; desde el 24 de septiembre no tiene receta de mesa y se copia en el infusor metalúrgico ([progression-functions](progression-functions.md)).

| Pieza | Acto | Componente | Por qué |
|---|---|---|---|
| Placa de calentamiento solar básica | II | Marco de Calibración (reemplaza el vidrio del centro) | La lente de Heliodor concentra el sol. |
| Bobina generadora | II | Acoplador de Energía (reemplaza un lingote de cobre) | Toda generación eléctrica pasa por una bobina. |
| Placa de calentamiento solar avanzada | III | Regulador de Energía | Más calor solar. |
| Energizador avanzado | III | Regulador de Energía | Sobrecarga más rápida. |
| Motor avanzado | III | Regulador de Energía (reemplaza una pepita de oro) | Segundo nivel de motor. |
| Energizador reforzado | IV | Lente Espectral | La luz concentrada: sobrecarga máxima. |
| Motor reforzado (ensamblaje mecánico) | IV | Lente Espectral (reemplaza un diamante) | Último nivel de motor y su extensión. |
| Barra de reactor (ensamblaje mecánico) | IV | Sello de Contención | Sin barras no hay fisión. |

- Los materiales temáticos ya estaban: New Age usa cobre en casi todo (8 lingotes en la bobina, alambre, tubos de calor, bloques de cobre). No se cambió ningún otro ingrediente, así que el balance nativo sigue igual.
- Se dejaron nativos:
  - Las piezas que dependen de una de las escalonadas: escobillas, cables, conectores y el energizador básico, que es una receta sin forma y se alimenta de la bobina.
  - Las farolas y los postes, que son decoración.
- También quedaron nativas dos recetas de New Age que conviene revisar en juego:
  - La manzana dorada encantada por ensamblaje secuenciado: 4 vueltas de 2.000.000 de energía cada una.
  - El frasco de experiencia energizado.
- El torio no lleva el tag `c:ores/thorium`. Así el minero dimensional de Occultism no lo produce y el reactor depende de la mina.

### Ganchos para las quests (New Age y Psi)

La reescritura de quests (`docs/design/quest-lore.md`, ya en `main`) nombra este escalonado en la línea de qué hacer. Estos son ganchos para la voz narrativa y las ruinas; no se escribió ninguna quest ni línea del Atlas.

- **Ruinas de Heliodor**
  - Taller hundido (II): una fila de placas solares rotas sobre una caldera fría. La misión «Volver a encender el taller» pide armar la placa básica con el Marco de Calibración y hacer hervir la caldera.
  - Invernadero-domo (III): las placas avanzadas en el vidrio del domo y las farolas de New Age a lo largo del camino. Relevar cuántas vuelven a encenderse de noche.
  - Observatorio (IV): el energizador reforzado con la Lente Espectral. Diamantes sobrecargados como «luz guardada».
  - Templo de la Luz Sagrada (IV): el reactor de torio, cuya barra pide el Sello de Contención, es el eco de la fusión. Jugaban con fuego. El corio fundido sirve como lore de lo que casi pasó.
- **Terra**
  - El Terraprisma «canaliza la luminosidad en distintas formas de energía», y New Age es eso mismo: luz del sol → calor → rotación → electricidad → luz. Sus misiones del acto VI pueden pedir la pieza que falta de un circuito eléctrico roto de Solsticio: una bobina, un alambre de oro sobrecargado o una farola.
  - Psi es su magia programable. El Ensamblador de CAD, con el Regulador de Energía del acto III, y un hechizo de luz programado como primera prueba.
- **La luz**
  - La farola de New Age consume energía según la luz del lugar. Sirve de metáfora de Solsticio: una ciudad que guarda luz.
  - La paleta de Solsticio podría sumar farolas y postes de New Age. Queda para el dueño de la paleta.
- La brújula y el Atlas todavía no mencionan nada de esto, y las ruinas de los actos todavía no están colocadas.

### Central Kitchen frente a Slice & Dice

No se pisan en código: ningún mixin comparte objetivo y ninguno crashea. Sí se superponen en dos tareas.

| Tarea | Central Kitchen | Slice & Dice | Qué se dejó |
|---|---|---|---|
| Recetas de la olla de cocción (92) | Automatiza la olla real: brazo mecánico, empaquetadores y quemador de blaze como fuente de calor. | «Cocción en cuenco» (basin cooking): copia cada receta de la olla como mezcla calentada. Con su configuración por defecto agregó 92 copias, una por receta. | Central Kitchen. `basin_cooking.enabled = false` en `pack/config/sliceanddice-common.toml`. |
| Recetas de la tabla de cortar (268) | Las convierte en recetas de sierra mecánica y de desplegador con cuchillo. | Rebanadora propia, que acepta cualquier herramienta. | Slice & Dice. `convertCuttingBoardRecipesToSawingRecipes` y `…ToDeployingRecipes = false` en `pack/config/create_central_kitchen-common.toml`. |

- **Ninguno sobra.** Central Kitchen es lo único que automatiza la olla, la sartén, la cocina y los banquetes. Slice & Dice aporta la rebanadora, los aspersores (riego, fertilizante, pociones y experiencia de CEI) y el fertilizante líquido: el lado de granja, que encaja con Juan.
- Con las dos cosas apagadas, cada receta aparece en EMI una sola vez más, en vez de tres o cuatro.
- Con lo que ya había:
  - Ningún otro mod del pack automatiza Farmer's Delight.
  - Slice & Dice reescribe la receta de Create de la bola de slime: acepta cualquier masa (`c:foods/dough`) en lugar de sólo la de Create. Es inofensivo.
  - Central Kitchen suma las fuentes de calor de Farmer's Delight a los calentadores pasivos de caldera de Create.
- La GameTest `kitchenautomationkeepsoneroutepertask` controla las dos cosas en el pack cargado.

### Otras integraciones

- **Almost Unified:** no aplica.
  - Los metales y gemas nuevos son únicos: psimetal, psigema, ébano e marfil de Psi; torio y magnetita de New Age.
  - El único cultivo nuevo tocado es la masa, que no es un material.
- **Recetas y datos que no cargaban**, resueltos con el generador (`--family pingpong4`):
  - Psi y PneumaticCraft publican su libro de Patchouli con el mismo ID (`patchouli:guide_book`) y uno pisaba al otro. El pack conserva el de PneumaticCraft ahí y copia el de Psi, sin cambios, a `psi:encyclopaedia_psionica`. Ahora se craftean los dos.
  - Dos logros ocultos de Dungeons and Taverns (`minecraft:wander_add_map` y `minecraft:give_quest_trader_trade`) nombran un padre `minecraft:root` que no existe en 1.21.1 y no cargaban; quedaron desactivados. El comerciante-misión de sus tabernas pierde ese intercambio extra. El resto de la estructura funciona.
- **Teclas:** la tecla maestra de Psi (`psimisc.keybind`) viene en C, la de guardar la barra rápida. El preset la pasa a Alt+C. Ninguno de los otros cuatro mods registra teclas. Auditoría simulada sin superposición en el mundo.
- **Regalos de primer ingreso:** ninguno de los cinco regala ítems.
  - Psi abre su libro con la tecla maestra sólo si no tenés un CAD en la mano; no lo regala.
  - Dungeons and Taverns no da nada al entrar.
  - `fullpackfirstjoingiftsstaydisabled` pasa. `fullpackplayerarrivesemptyhanded` sigue fallando en esta rama por los regalos de Ars Nouveau y Silent Gear que arregla `fix/fullpack` (ver la QA).
- **Dungeons and Taverns pesa más de lo que parece:** 97 estructuras y 34 conjuntos.
  - Pisa sólo dos archivos vainilla: el conjunto de las mansiones del bosque, que ahora comparten lugar con su mansión illager, y la aldea de taiga, que cambia de tag de biomas.
  - Agrega aldeas de jungla, pantano y abedul. Sus 17 tablas de botín en el espacio `minecraft` tienen nombres propios; no pisan tablas vainilla.
  - Corre una función cada 5 ticks que recorre rayos y esqueletos wither cargados.
  - Sus 13 encantamientos son sólo de tesoro.

### Rendimiento (servidor dedicado, antes y después)

- **Servidores:** `server-mods-r4-base` con el lock de `main` (ce7cdda, 266 JAR de servidor) y `server-mods-r4` con este lock (272). Son copias propias en E:, armadas con el esqueleto del servidor de QA de la ronda anterior; no se tocaron `server-slice`, los servidores de otros workers ni los perfiles de cliente.
- **Condiciones:** heap de 6 GB, mundo nuevo con la semilla de QA en cada corrida, sin jugadores, watchdog de 60 s. Las pruebas y el método de worldgen son los de las rondas 1 a 3, más el End: 256 chunks forzados en 16 tandas de 4×4 por prueba. Esa generación es síncrona, el peor caso.
- **Ruido:** la PC estuvo compartida con los servidores de QA de `fix/fullpack` durante casi toda la serie, con carga del sistema entre 33% y 97%. Por eso se corrieron 4 pares (base-después, después-base) y se dan medianas. Una corrida `base3` se perdió por el apagón y se repitió.

| Medida | Antes (mediana de 4) | Después (mediana de 4) | Notas |
|---|---|---|---|
| Arranque, pared | 254 s | 217 s | Rangos: 246-404 contra 206-266 s. Sin aumento. |
| Carga total según ModernFix | 276 s | 232 s | |
| CPU de la JVM hasta `Done` | 718 s | 709 s | La medida menos sensible a la carga: igual. |
| Carga de datapacks | 1,52 min | 1,12 min | |
| TPS en reposo | 20 | 20 | Sólo bajó a 17,8 (1 min) una corrida base con el sistema al 93-97%. |
| MSPT en reposo, mediana | 1,4 ms | 1,25 ms | p95 de 1,5 a 4,9 ms en ambos. |
| Heap usado en reposo | 2,0-2,8 GB | 1,9-2,8 GB | Sin diferencia apreciable. |
| Overworld, 512 chunks (dos cuadrados), CPU | 414 s | 419 s | +1%. Pared: 189 contra 147 s de mediana, ruido de ±50%. |
| Nether, 256 chunks, CPU | 76 s | 95 s | Mediana +25%, pero los pares van de −9% a +72%; ver D&T. |
| End, 256 chunks, CPU | 53 s | 56 s | |
| Líneas de error en un arranque limpio | 3 | 3 | Las mismas 3 de antes, todas upstream: los fluidos viejos de Reliquary y Ender IO en el mapa de CEI y el typo de Pam's. |

- **Dungeons and Taverns:** se midió aparte, sacando su JAR y con la misma carga, una corrida detrás de la otra.
  - Con D&T, el Overworld tardó 86 s de pared y 320 s de CPU; sin D&T, 115 s y 363 s. El Nether dio 65 contra 69 s de CPU.
  - Su costo de generación queda por debajo del ruido de esta PC, de ±15-20% en pares seguidos.
  - En reposo, su función cada 5 ticks no mueve el MSPT: 0,8 ms con D&T y 0,7 ms sin él.
- **Veredicto:** la ronda 4 no agrega costo medible en el arranque, en reposo ni en la generación del Overworld. El 72-81% que sumó la ronda anterior sigue siendo el costo de fondo.
  - New Age suma torio y magnetita, Slice & Dice nada, y D&T estructuras espaciadas.
  - Lo único que conviene mirar es el Nether en cliente, donde D&T suma fortalezas, puertos y torres.
- **Sin medir:** el render en cliente (los cables y farolas de New Age, las partículas de Psi) y una fábrica de Create cocinando en carga, que es donde pesan Central Kitchen y Slice & Dice.

Recibos: `E:/Elias/Codex/Entrelumen-ssd/mods-r4-20260924` y [`docs/verification/mod-pingpong-r4-runtime.json`](../verification/mod-pingpong-r4-runtime.json).

### QA de pack completo

- **Servidor y condiciones:** servidor limpio nuevo (`server-mods-r4-qa`: sólo librerías, los 272 JAR del lock, los archivos de `pack/` y el JAR de QA), mundo nuevo y `-Dentrelumen.qa=true`.
- **Rama probada:** la rama con `main` mergeado en 35f22ad, que incluye los arreglos de `fix/fullpack`, la reescritura de quests y la biblia actualizada.
- **Ejecución:** las 120 GameTests registradas corrieron de a una, porque comparten datos de campaña.

- **114 pasan y 6 fallan; ninguna falla viene del lote:**
  - `kitchenautomationkeepsoneroutepertask` era un falso positivo de la prueba. Contaba también una receta nativa de Farmer's Delight, la salsa de tomate por mezcla de Create, que comparte salida con la olla. Se corrigió para contar sólo las copias de Slice & Dice, y al repetirla pasa con 0 copias.
  - `altarsrespectforeignftbchunksclaims` falló por tiempo con la máquina cargada y pasó al repetirla, igual que en `fix/fullpack`.
  - Las 4 de reinicio y backup (`preparelivebackupfixture`, `verifylogisticsrestartfixture`, `verifyrestoredlivebackup` y `verifyteamrestartafternewserverprocess`) necesitan otro proceso o un ZIP de backup. No aplican en una sola corrida; `fix/fullpack` las verificó en JVM separadas.
- **Las cinco pruebas nuevas pasan:** `pingponground4batchloaded`, `heliodorsolarandpsipiecesneedactcomponents`, `pneumaticcraftandpsiguidebooksstaycraftable`, `dungeonsandtavernsstructuresregistered` y `kitchenautomationkeepsoneroutepertask`, esta última en la repetición.
- **Las 7 fallas de `main` que quedaban de la ronda anterior ahora pasan con el lote puesto:**
  - el jugador nuevo sin ítems (`fullpackplayerarrivesemptyhanded` y `newplayerarriveswithanemptyinventory`);
  - la ruina de inicio;
  - las dos de la brújula;
  - la saciedad de bloques;
  - el tier de la historia.
- **También pasan:** el lote anterior (`pingpongbatchloadedwithoutrejectedbuilds`, Carry On, cultivos unificados), el comercio y la paleta de Solsticio, y `datapackreloadisatomicandrejectscycles`.
- **Guía de primer ingreso:** `tools/audit_first_join.py` (de `fix/fullpack`) sobre los 314 JAR no encuentra ningún regalo de los cinco mods nuevos.
  - D&T tiene logros con recompensa de función, pero se disparan al comerciar, al interactuar con un comerciante o al recibir daño, nunca al entrar.
  - Los otros aciertos son vocabulario de librerías (`startWithValue`, `startWith`).
- **Log:**
  - Los mismos 3 errores upstream de siempre en cada carga de datapacks.
  - La queja de Almost Unified por `stella_arcanum` al recargar, que es anterior a este lote.
  - Excepciones de entidades de hechizos de Ars Nouveau (`resolveEmitter` nulo), atrapadas por el nivel durante una prueba de Ars, como en la ronda anterior.
  - Ninguna línea de los mods nuevos.
- **Pruebas de Python** del repo, sobre la rama mergeada: 31 comandos, todos en 0.
  - La lista de `fix/fullpack`.
  - `curate_pack --check` en cliente y servidor.
  - Todas las familias de `generate_family_balance --check` y los demás generadores con `--check`.
  - La auditoría de teclas y `unittest discover`.
  - El acompañante compila (`jar`, `qaJar` y `test`).

Recibo: [`docs/verification/mod-pingpong-r4-runtime.json`](../verification/mod-pingpong-r4-runtime.json).

### Quedó afuera

| Qué | Motivo |
|---|---|
| Botania, Blood Magic, Steam 'n' Rails y todo transporte | Elias los dejó afuera en esta ronda. |
| Create: Central Kitchen 2.6.0 | Aceptaba Dragons Plus 1.11.7b, pero tiene el lag de servidor que corrige 2.6.1. Se eligió 2.6.2 con Dragons Plus 1.11.9. |
| Kotlin for Forge de la instancia ATM10 | Son otros bytes que los del Modrinth oficial. Se usó el de Modrinth. |
| Cocción en cuenco de Slice & Dice; conversiones de la tabla de cortar a sierra y desplegador de Central Kitchen | Duplican una ruta que el otro mod ya cubre; están apagadas por configuración. |
| Dos logros ocultos de Dungeons and Taverns | No cargan en 1.21.1 (padre inexistente); están desactivados. |

### Pendiente para Elias

- **Cocina:** ¿te sirve la división (olla con Central Kitchen, corte con Slice & Dice)? Si preferís la cocción en cuenco o la sierra de Create, se invierte con un cambio de config.
- **New Age en juego:**
  - Potencia del generador frente a Create: Crafts & Additions, Mekanism e Immersive Engineering, que ya están.
  - Las recetas de manzana dorada encantada y de experiencia energizadas.
  - El reactor de torio frente al de MI.
- ~~**Psi:** ¿el Ensamblador de CAD en el acto II está bien, o preferís abrirlo en el III junto a Mahou Tsukai?~~ Elias, 24/9: Psi entero en el III. El ensamblador pide un Regulador de Energía (`feature/acts-renumber`).
- **Revisión en cliente:**
  - Render de los cables y farolas de New Age y de las partículas de Psi.
  - La tecla Alt+C de Psi.
  - El Nether con las estructuras de D&T en una PC modesta.
- **Quests y ruinas:** los ganchos de arriba son propuestas. Las ruinas de los actos todavía no están colocadas.
- **Estructuras de D&T y ruinas:** `startruinisanchoredregisteredandplacedonce` pasa, pero no se buscó a propósito si alguna estructura de D&T puede generarse cerca de la ruina de inicio.

## FTB Filter System y FTB XMod Compat (27/9)

Rama `feature/ftb-filter-system`. El 27/9 Elias aprobó FTB Filter System («Sí, agregalo») para que el libro tenga tareas de «cualquiera de estos ítems». Ese mismo día aprobó FTB XMod Compat, porque sin él FTB Quests no usa los filtros.

La familia es `catalog/families/quest-filters.json`: dos mods de infraestructura y ninguna librería nueva, porque Architectury y FTB Library ya estaban. El lock pasa de 314 cliente / 272 servidor a **316 / 274**, y ninguna entrada previa cambió.

| Mod | Versión | Fuente | SHA-256 |
|---|---|---|---|
| FTB Filter System | 21.1.4 | CurseForge 943925/7429011 | `b8700e8bfd9c78b09bc7819cb28a6318b20d2bf68969bc6894a0bfa39581a99b` |
| FTB XMod Compat | 21.1.11 | CurseForge 889915/8653466 | `0bfa6513c51b697a81ab2afc1b25983d3e39fe4a33ec334b57e6384b25a17450` |

- **Origen.** Los dos JAR vienen de la instancia de referencia ATM10 8.1, donde van junto a las mismas FTB Quests 2101.1.34 y FTB Library 2101.1.35 del lock. El SHA-1 de CurseForge coincide, y FTB publica el mismo SHA-1 en su Maven.
- **FTB Filter System.** 21.1.4 es su última versión para 1.21.1; las 21.11 y 26.1 son para otras versiones de Minecraft.
- **FTB XMod Compat.** 21.1.12 declara las mismas dependencias que 21.1.11. Se fijó 21.1.11, el par que usa ATM10.

### Por qué hacen falta los dos

Leído de los JAR descompilados con Vineflower 1.10.1, fuera del repo, en `E:/Elias/Codex/Entrelumen-ssd/filters-20260927/decomp`:

- **FTB Quests no trae adaptadores.** Su `integration/item_filtering/ItemMatchingSystem` viene vacío. Una tarea de ítem le pregunta si su ítem es un filtro; si ningún adaptador lo reclama, compara ítem contra ítem, y la tarea acepta sólo el filtro mismo.
- **FTB Filter System no sabe nada de FTB Quests.** El adaptador lo registra XMod Compat: `ftbquests/filtering/FFSSetup` llama a `FTBQuestsAPI.registerFilterAdapter` y resuelve cada caso con `FTBFilterSystemAPI` (`isFilterItem`, `doesFilterMatch`, `parseFilter`).
- **El filtro.** Es el ítem `ftbfiltersystem:smart_filter`, con la expresión en el componente de texto `ftbfiltersystem:filter` (`registry/ModDataComponents`).
- **La sintaxis.** Es la de `util/FilterParser`: `tipo(argumento)`, con los tipos del mod sin espacio de nombres (`item`, `item_tag`, `or`, `and`, `not`…).
  - La raíz (`RootFilter`) combina sus términos con AND, así que «cualquiera de estos» va dentro de `or(...)`: `or(item(minecraft:oak_log)item_tag(minecraft:logs))`.
  - Un `item(...)` que no existe hace fallar la expresión entera (`ItemFilter.fromString`), y el filtro no acepta nada. Un tag vacío, en cambio, sólo no aporta ítems.
- El formato en las cadenas y las reglas del motor están en [content/sectors/README.md](../../content/sectors/README.md#tarea-de-cualquiera-de-estos).

### Qué más prende XMod Compat con los mods del pack

El libro no usa nada de esto hoy. Queda anotado para revisarlo en el cliente.

- **JEI:**
  - Suma dos categorías. «Quests» lista las quests que dan un ítem de recompensa, sólo las que el jugador ya puede empezar y que se pueden buscar. «Loot crates» muestra qué trae cada caja de loot y con qué peso, incluidas las cajas de las cumbres y los encargos.
  - FTB Quests pasa a usar JEI como visor de recetas: un clic en una tarea de un solo ítem abre su receta.
  - La tecla de marcador de JEI marca ítems desde el libro. Para eso XMod trae dos mixins obligatorios a las clases de marcadores de JEI (`BookmarkListAccessor` y `BookmarkOverlayAccessor`). Son del lado del cliente, y JEI no se instala en el servidor. ATM10 8.1 lleva este mismo JEI (19.50.0.414) con XMod 21.1.11, pero en nuestro cliente todavía no se probaron.
- **KubeJS:** eventos y el objeto `FTBQuests` para scripts (tareas y recompensas propias, quest empezada o completada), y eventos de FTB Chunks, FTB Teams y FTB Filter System. Ningún script del pack los usa.
- **Waystones:** los waystones que el jugador descubrió aparecen en el mapa de FTB Chunks. En el Envés no hay mapa ni waystones.
- **GuideME y Patchouli:** los enlaces `show_docs:` de las quests pueden abrir sus guías.
- **Jade:** muestra las barreras de quest, que el pack no coloca.
- **Etapas de juego:**
  - Por defecto, XMod les pasa las etapas de FTB Library a KubeJS.
  - `pack/config/ftbxmodcompat.snbt` fija `stage_selector: "vanilla"`. Así siguen siendo las etiquetas de entidad de FTB Library, las que usa el companion para sacar el mapa de FTB Chunks dentro del Envés ([dungeon-enves](dungeon-enves.md)).
  - El valor está anotado en [qol-defaults](../qol-defaults.md).
- **El resto no aplica:** permisos (FTB Ranks, LuckPerms), monedas (SG Economy), Game Stages, REI y FTB Essentials no están en el pack, y XMod no hace nada con ellos.

### Verificación

- **Estática, sobre `origin/main` 2c4a3fc más esta rama:**
  - `curate_pack --check`, en cliente (316) y en servidor (274).
  - `test_curate_families`.
  - `check_keybinds`: ninguno de los dos mods registra teclas.
  - `generate_quests --check`: con las 59 cadenas actuales, que no usan `any`, el libro sale igual byte a byte.
  - Los 31 pasos de Python de `verify.yml`, entre ellos `test_sector_book`, `test_generate_quests` y `test_quest_book`.
  - `check_guides`: las 44 guías y las 59 cadenas, sin errores.
  - `check_guides` contra los registros reales, sobre una copia de Create · Complementos con tareas `any`: la versión válida pasa. Marca un ítem que no existe, un tag que nadie define y un tag que existe pero no tiene ningún ítem del pack (`#c:ingots/cobalt`; hay 35 así).
- **En un servidor: pendiente.** Hasta el cierre de la rama siempre había otro servidor de QA con el candado de la máquina (`qa-server.lock`) o menos de 5 GB de RAM libre.
  - El chequeo quedó listo en `E:/Elias/Codex/Entrelumen-ssd/filters-20260927/scripts/run_runtime_qa.sh`. Hace un solo intento: si falta algo, se detiene y dice por qué.
  - Arma un servidor desechable con los 274 JAR y el libro del pack, y agrega tres tareas `any` de prueba en Create · Complementos: dos alternativas con un tag, tres ítems sueltos, y un encargo que consume un tag.
  - Un comando de KubeJS le pide cada tarea a FTB Quests por el objeto `FTBQuests` de XMod Compat. Comprueba que el ítem sea el filtro con la expresión compilada, que el adaptador de FTB Filter System lo reclame, y qué ítems acepta y rechaza cada tarea. También revisa que una tarea común y la llave arcana (que compara por componente) sigan igual.
  - Además corre la prueba del mapa del Envés y cuatro pruebas de quests del companion.

### Pendiente

- Correr el chequeo en servidor cuando haya lugar.
- **En el cliente:**
  - la lista de ítems válidos que abre un clic en una tarea `any`;
  - las categorías de JEI;
  - el clic que abre la receta;
  - los waystones en el mapa de FTB Chunks.
- **Cadenas que esquivaron el «cualquiera de estos»** con un logro en lugar de los ítems, o pidiendo un ítem por varios: su conversión a `any` la programa el controlador.

## Botines de jefe y la puerta de Starlight (27/9)

Arreglos que encontraron los que escriben las quests. La regla del pack sigue siendo que el Wither es la única fuente de estrellas del Nether, y ahora las estrellas también pagan la puerta del Envés.

### Botines de jefe

Familia nueva `boss_drops` de `tools/generate_family_balance.py` (script `entrelumen_boss_drops_balance.js`):

| Fuente | Qué hacía | Cambio |
|---|---|---|
| Theurgy, incubación | La licuefacción convierte una estrella o un huevo de dragón en varios azufres, y la incubación rearmaba cada uno. | Salen `theurgy:incubation/nether_star` y `theurgy:incubation/dragon_egg`. La licuefacción y la calcinación siguen consumiendo estrellas. |
| RFTools Utility, generador de criaturas | Un Wither por 0,1 estrella de materia y 20.000 FE, y el Wither suelta una estrella entera; un Dragón del End por 100.000 FE, sin pelea del End pero con unos 64 de polvo de draconio de Draconic Evolution. | Salen `rftoolsutility:minecraft_wither` y `rftoolsutility:minecraft_ender_dragon`: ninguna máquina hace jefes. |
| Oritech, controlador de spawner | Atrapa cualquier mob que lo pisa salvo el Dragón: un Wither costaba unas decenas de almas. | `#c:bosses` se suma a `oritech:spawner_blacklist`, con un archivo que se fusiona con el de Oritech. |
| Bumblezone, la Reina | Pagaba estrellas por jalea real, y la abeja real de Productive Bees hace jalea sin fin. | La estrella sale de las recompensas por frasco y por cubo o bloque. |
| EvilCraft, espíritus encerrados | El Horno de espíritus cocinaba el espíritu de un Wither encerrado y daba estrellas (fijo en el código), y el Reanimador convertía espíritus de jefes en huevos generadores. La lista negra de espíritus sólo tenía al dragón. | `pack/config/evilcraft-common.toml` suma a `entityBlacklist` todos los jefes: `#c:bosses` de los JAR fijados y del pack, los de Twilight, el Wither, el dragón, la Luz Agria del Envés (`entrelumen:white_wither`) y, desde el 29/9, el Afrit y el Marid desatados de Occultism. Ningún espíritu de jefe se puede encerrar (27/9). |
| Jefes que su mod no etiqueta | El Wildfire de Friends&Foes (el blaze real, única fuente de fragmentos de corona), el Asechador de Deeper and Darker, el Guardián del Caos de Draconic Evolution y, por decisión de Elias del 29/9, el Afrit desatado (`occultism:afrit_wild`) y el Marid desatado (`occultism:marid_unbound`) de Occultism no estaban en `#c:bosses`: las máquinas que rechazan esa etiqueta podían atraparlos o generarlos. | `pack/kubejs/data/c/tags/entity_type/bosses.json` los suma a `#c:bosses` (se fusiona, `replace: false`) y EvilCraft también los lista (28/9; Afrit y Marid, 29/9). El Asechador muestra barra de jefe (`ServerBossEvent`) y el Guardián, la de su pelea (`ShieldedServerBossInfo`); el Wildfire no tiene barra, pero es jefe de las quests. Sólo los desatados: el Afrit y el Marid ligados (`occultism:afrit` y `occultism:marid`, los familiares) no cuentan. |

Ender IO (frascos de alma y spawner motorizado), Industrial Foregoing (herramienta de captura, y con ella el duplicador) y Apothic Spawners (captura y huevos) ya rechazan `#c:bosses`, y la rienda de ender tampoco toma jefes. La trampa de jefes de Forbidden Arcanus los captura para soltarlos y pelearlos, y Silent Gear sólo parte y rearma estrellas: ninguna fuente nueva.

Desde el 28/9, `tools/test_family_balance.py` exige que cada quest con rol de jefe y tarea de matar, en `content/sectors` o `content/guides`, pida una criatura de `#c:bosses` (NeoForge, los JAR fijados, el companion y el pack), salvo una lista explícita con el motivo de cada una, y que la lista de EvilCraft cubra todo `#c:bosses`. Fuera del Wildfire, que se sumó por pedido (jefe de las quests sin barra de jefe), una criatura entra a `#c:bosses` sólo si muestra barra de jefe o su mod la marca como jefe. Elias decidió el 29/9: el Afrit y el Marid desatados de Occultism entran a `#c:bosses`; quedan afuera y permitidos a las máquinas, con la razón «Elias 29/9: farmable», el Warden, el hombre lobo de EvilCraft, los tres asaltantes de IE (Fusilier, Commando y Bulwark), la araña de hielo de Iron's Spells y el Permafrost de Eternal Starlight. La lista de permitidos de `test_family_balance.py` (`UNTAGGED_QUEST_BOSSES`) lleva esa razón en cada uno.

Decisiones de Elias (27/9) sobre lo que quedaba abierto:

- **Hostile Neural Networks: queda.** El modelo del Wither se entrena matando Withers y simula estrellas con FE y matrices de predicción: es la granja de estrellas del acto IV que describe la guía de jefes, y el Wither sigue siendo la fuente.
- **Occultism, campo de batalla dimensional: queda.** Clona un Wither capturado con una gema trinidad, que es de juego tardío, y lo mata a cambio de datura (`battlefield/minecraft/wither`): también ahí el Wither sigue siendo la fuente.
- **Huevos de dragón: quedan las demás fuentes.** El modelo del dragón de HNN, el campo de batalla (uno de cada cuatro), la nucleosíntesis de Mekanism (un huevo y 4 mB de antimateria) y la Reina (cubo o bloque de jalea); Draconic Evolution además deja un huevo cada vez que se mata al dragón. Sólo salió la incubación de Theurgy.
- **El dragón del generador de RFTools: sale**, por la misma regla que el Wither: ninguna máquina hace jefes. Sin la pelea del End no dejaba corazón ni huevo, pero era una granja barata de polvo de draconio (unos 64 por dragón, de Draconic Evolution).

### La puerta de Starlight

Eternal Starlight es del acto IV: la receta del Orbe de la Profecía lleva una Carta de horizontes (familia `pingpong`). Pero el Guardián, cuyas ruinas salen en todo el Overworld, soltaba un Orbe en la primera victoria, también a quien ya mató al Dragón, y vendía más por una moneda de plata, que cuesta dos lingotes de hierro o una esmeralda. Los cristales estelares crecen sólo dentro de Starlight, así que sacar el Orbe a secas cerraría la dimensión:

- La primera victoria suelta, en lugar del Orbe, los cuatro fragmentos de cristal estelar azul que pide la receta escalonada (`swapped_loot`; el generador cuenta los fragmentos en la receta editada).
- El trueque del Orbe pasa a cuatro fragmentos por la misma moneda. Los trueques del Guardián están en código (`GatekeeperTrades`), así que el script de la familia los cambia cuando la entidad entra al mundo (`EntityEvents.spawned`), sin Java en el companion.
- Las ruinas siguen como están: dan el marco del portal y la pelea, y el Orbe espera a la Carta.

### Verificación (27/9)

- `tools/generate_family_balance.py --check` pasa en las nueve familias, y `tools/test_family_balance.py` también (36 tests, 11 nuevos: los techos de Eterna, el ancla del Refugio, la forma de los aumentos, estos botines y la puerta de Starlight). Pasan además las 33 verificaciones de Python de CI, `check_guides.py` y el build del companion con sus 250 tests de JUnit.
- Servidor descartable con el pack completo (274 JARs de servidor, `-Xmx4G`) y una sonda de KubeJS:
  - las tres recetas quitadas no están, y la del zombi del generador de RFTools y la del Orbe sí;
  - la lista negra de Oritech toma al Wither y al Dragón, no al zombi;
  - ninguna recompensa de la Reina por frasco, cubo o bloque de jalea paga estrellas;
  - tres tiradas de primera victoria del Guardián dieron la Tablilla y cuatro fragmentos, nunca un Orbe;
  - un Guardián invocado vende cuatro fragmentos por una moneda donde vendía el Orbe (`offers-swapped`), con los demás trueques intactos;
  - la infusión de la rienda oculta pide 80 de Eterna, 85 de Quanta y 60 de Arcana.
- El primer arranque cortó la sonda por un método sobrecargado de Rhino; el segundo, ya corregido, terminó limpio. Los únicos errores del log son los conocidos de siempre, ninguno de estos archivos. El servidor se borró después.
- Pendiente: probarlo en un cliente (el trueque en la pantalla del Guardián, el texto de las quests) y una pelea real con el Guardián.

Seguimiento (27/9, rama `fix/balance-rftools-dragon`): sale `rftoolsutility:minecraft_ender_dragon`. `tools/test_family_balance.py` (`test_no_rftools_spawner_recipe_spawns_a_boss`) lee `c:bosses`, los dos jefes de NeoForge y los que sumen los mods fijados, recorre las recetas del generador de RFTools en los JAR y exige que toda receta de un jefe esté quitada. El servidor no hizo falta: la familia usa el mismo mecanismo de quitado que el Wither, y el arranque anterior ya lo había probado.

## Ronda 5 (27/9): censo de ATM10, FTB Evolution y Craftoria

Rama `feature/mods-r5`. Entran los 31 ADD del [censo](../research/mod-census.md), con las decisiones de Elias del 27/9: «sumar genuinamente todo lo que MEJORE la experiencia y robustezca al pack (no sumar por sumar)»; Neo Vitae sí, en los actos III-IV y con su dimensión; Iris sí, con los shaders apagados y sin shaderpack; Create Aeronautics a prueba, en su propio lote y medido. El controlador sumó Create Collision Fix y, desde la [búsqueda hacia afuera](../research/mod-outward.md), tres arreglos al lote 1, tres comodidades al lote 2 y Sanguine Neural Networks al de Neo Vitae. Un commit (o un grupo chico) por lote, para mergear de a uno.

**Qué versión se fija.** La que usa un pack de referencia (primero ATM10) cuando cumple las dependencias del lock: esos packs corren el mismo NeoForge 21.1 con cientos de mods, y su pin es evidencia de que carga. Un archivo oficial más nuevo entra sólo si arregla algo que necesitamos, y se dice por qué. Cada JAR sale de la fuente oficial:

- **Instancia ATM10 8.1** (sólo lectura): el SHA-1 de CurseForge de su `minecraftinstance.json` coincide con los bytes.
- **CDN de CurseForge** (`edge.forgecdn.net`): el tamaño coincide con el registro oficial del archivo. Cuando FTB Evolution 1.43.1 trae el mismo archivo, su manifiesto público da el mismo SHA-1.
- **CDN de Modrinth**: SHA-1 y SHA-512 de la API de versiones.
- En los tres casos, si los mismos bytes están en Modrinth, el pin guarda su SHA-512 y `--check` lo vuelve a verificar. Todo pin de CurseForge lleva los IDs de proyecto y archivo que necesita la exportación de la App.

**Relaciones de CurseForge.** `tools/curate_pack.py` suma `cfRelations` a las familias: un proyecto requerido por CurseForge puede darse por cumplido con un mod que el lock ya provee desde otro proyecto (`satisfiedBy`, como CC: Tweaked 1676502 para el 282001 que piden Advanced Peripherals y More Red CCT) o quedar exento para archivos nombrados cuando la relación es vieja y su `mods.toml` no la pide (`waivedFor`). `--check` rechaza un proveedor ausente y una exención que no nombra al archivo; `tools/test_curate_families.py` lo cubre.

### Lote 1 · Robustez y rendimiento

Familia `catalog/families/pingpong5-robustness.json`: 16 mods y 2 librerías. El lock pasa de 316 cliente / 274 servidor (con los filtros de quests) a **334 / 287**; ninguna entrada previa cambió.

| Mod | Versión | Fuente | Lado | Para qué |
|---|---|---|---|---|
| Entity Culling | 1.10.5 | CF 448233/8287097 (Craftoria) | cliente | No dibuja criaturas ni bloques con modelo ocultos tras paredes. |
| Structure Layout Optimizer | 1.0.12 | CF 1087831/7439136 (FTB) | ambos | Arma más rápido las estructuras por piezas (aldeas, mazmorras). |
| I'm Fast | 1.0.3 | CF 1111501/8747159 (ATM10) | ambos | El servidor deja de echar o frenar a quien se mueve rápido (jetpacks, élitros, aeronaves). |
| Packet Fixer | 3.3.1 | CF 689467/7221528 (Craftoria) | ambos | Sube el límite de los paquetes: una mochila llena ya no desconecta. |
| Load My F\*\*\*ing Tags | 1.1.1 | CF 656346/7084444 (ATM10) | ambos | Una entrada rota en un tag ya no vacía el tag entero. |
| Crash Assistant | 1.11.12 | CF 1154099/8636685 (ATM10) | cliente | Ventana de crash que nombra la causa probable y sube los logs sólo si el jugador lo pide. |
| Not Enough Recipe Book | 0.4.3 | CF 738663/6880047 (FTB) | ambos | Saca el libro de recetas vanilla; necesita OctoLib 0.6.2 (CF 916747/8040848, ATM10). |
| NaNny | 1.0.1 | CF 634392/5728615 (FTB) | ambos | Cancela el daño que no es un número, que deja a un jugador inmortal o lo mata. |
| Better Compatibility Checker | 21.1.8 | CF 551894/7404415 (ATM10) | ambos | La lista de servidores muestra si el servidor corre la misma versión del pack. |
| Sodium Extra | 0.9.3 | CF 447673/8403576 (ATM10) | cliente | Más opciones de video para PCs modestas. |
| Compact Machines Preview Fixer | 1.1.0 | CF 1548811/8133423 (FTB; no está en Modrinth) | cliente | Tapa la fuga de memoria de la vista previa de salas de Compact Machines. |
| Draconic Evolution Render Patcher | 2.0.0 | CF 1383702/8277268 (FTB) | cliente | Núcleo de energía, reactor e inyectores de Draconic se dibujan bien con Sodium. |
| Create Collision Fix | 1.0.0 | Modrinth j20TJ3QZ/oIRUjqoI | ambos | Evita el crash de Create 6.0.10 por choque de contraptions (`mf.axis` nulo) que deja al servidor reiniciándose en bucle. |
| Mekanism Pipez Fix | 1.0.1 (beta) | CF 1233861/7661970 | ambos | Los caños de Pipez siguen alimentando multibloques de Mekanism después de reiniciar: un reactor de fisión ya no se queda sin agua. |
| Neruina | 3.3.3 | CF 851046/8451084, con Configurable 3.5.2 (CF 1092048/8438541) | ambos | Congela la entidad, máquina o ítem que falla en cada tick en vez de tumbar el servidor, y avisa dónde. |
| Async Locator Refined | 1.6.0 | CF 1331921/8501111 | ambos | Mapas del tesoro, delfines y `/locate` buscan fuera del hilo del servidor. |

Todos salvo Compact Machines Preview Fixer tienen los mismos bytes en Modrinth, y el pin guarda su SHA-512.

- **Versiones que no son las más nuevas.**
  - Entity Culling 1.11.x arregla que NeoForge no usara la caja de render de los bloques con modelo grande, pero es una serie de seis días con tres arreglos seguidos y un hilo de culling reescrito. Queda 1.10.5, la de Craftoria. Hay que mirar en cliente que no desaparezcan renderizadores grandes (controlador del Arca, altares, núcleo de Draconic); si pasa, van a su lista blanca.
  - Sodium Extra 0.9.4 sólo corrige la niebla y traducciones; queda 0.9.3, la de ATM10.
- **Create Collision Fix** exige Create `[6.0.10]` exacto. **Se saca cuando Create pase a 6.0.11**, que trae el arreglo oficial (PR #10301); con 6.0.11 el cargador se niega a arrancar y lo nombra. La GameTest `pingponground5robustnessloaded` también lo avisa.
- **Configuración:**
  - `pack/config/bcc-common.toml`: ENTRELUMEN 0.1.0. La versión es la `mod_version` del companion y se sube con cada release; la GameTest compara las dos.
  - `pack/config/crash_assistant/config.toml`: sólo el nombre del pack. El enlace de ayuda queda en el de NeoForge hasta que ENTRELUMEN tenga un canal público de soporte.
  - El resto, por defecto. Not Enough Recipe Book queda en su modo `TOGGLE`: el botón del libro muestra u oculta la barra de fabricables de EMI y el servidor no otorga ni guarda recetas. Ningún script, quest ni código del pack usa el desbloqueo de recetas (búsqueda en el repo).
  - Neruina, por defecto: umbral de 10 excepciones, avisos para todos y comandos para operadores. En la QA, una línea de Neruina cuenta como error.
- **Compact Machines Preview Fixer** apunta a `MachineRoomScreen` y a la cámara de Gander, que existen en Compact Machines 7.0.81; sus mixins son `@Pseudo` y no exigen el objetivo. FTB Evolution lo usa con el mismo 7.0.81, y Draconic Render Patcher con el mismo Draconic 3.1.4.632 y Sodium 0.8.13.
- **Escalonado:** ninguno; no agregan ítems.
- **Teclas:** sólo Entity Culling registra dos, sin asignar (`key.entityculling.toggle` y `toggleBoxes`); quedan en `tools/keybind_contexts.json`.
- **GameTests:** `pingponground5robustnessloaded` (los de servidor cargados, los de cliente fuera del servidor dedicado, Create en la versión que parchea el hotfix, BCC con el nombre y la versión del pack) y `nannycancelsnandamage` (un cerdo que recibe daño NaN conserva la vida).

### Lote 2 · Información y comodidad

Familia `catalog/families/pingpong5-information.json`: 11 mods y 3 librerías. El lock pasa a **348 / 296**; ninguna entrada previa cambió.

| Mod | Versión | Fuente | Lado | Para qué |
|---|---|---|---|---|
| EMI Loot | 0.7.9 | CF 681783/7417271 (Craftoria) | ambos | EMI muestra qué sueltan criaturas, bloques y cofres; necesita Fzzy Config 0.7.6 (CF 1005914/7568897, ATM10). |
| EMI Ores | 1.3 | CF 974009/8254306 (Craftoria) | ambos | EMI muestra en qué alturas y biomas sale cada mineral. |
| RightClickHarvest | 4.6.1 | CF 452834/7508749 (FTB) | ambos | Clic derecho sobre un cultivo maduro: cosecha y replanta. Necesita JamLib 1.3.6 (CF 623764/7766752, FTB). |
| AE2: Crafting Tree | 1.1.1 | CF 1086241/7182163 (ATM10; no está en Modrinth) | ambos | El estado de un pedido de AE2 muestra el árbol entero y los patrones que faltan. |
| Bridging Mod | 2.6.2 | CF 533942/6269728 (ATM10) | cliente | Coloca bloques hacia afuera desde el borde en que estás parado. Necesita YetAnotherConfigLib 3.8.2 (CF 667299/7437845, ATM10), también de cliente. |
| WITS | 1.3.1 | CF 909375/8412915 (ATM10) | ambos | `/wits` dice en qué estructura estás parado. |
| Bad Wither No Cookie | 3.20.4 | CF 261251/8135209 (ATM10) | cliente | Los sonidos del Wither y del Dragón se oyen sólo cerca. |
| Yeetus Experimentus | 87.0.0 | CF 635427/5444189 (ATM10) | cliente | Sin el aviso de «ajustes experimentales» al crear o abrir un mundo. |
| Chunky | 1.4.23 | CF 485681/6383261 | ambos | Pregenera el mundo antes de abrir un servidor. |
| Dynamic FPS | 3.11.4 | CF 335493/7546938 | cliente | Baja FPS y volumen con la ventana en segundo plano. |
| Ping Wheel | 1.12.2 (beta) | CF 734339/7996932 | ambos | Marca un lugar o una criatura para tu grupo de FTB Teams (Mouse 5). |

Todos tienen los mismos bytes en Modrinth salvo AE2: Crafting Tree, que no está ahí.

- **FindMe queda afuera** (ver «Quedó afuera»): el servidor busca en todo contenedor dentro del radio y deja sacar ítems sin abrirlo ni preguntarle a FTB Chunks (`PositionRequestMessage` y `PullItemRequestMessage` de 3.3.4, leídos con `javap`). Su config sólo tiene el radio.
- **RightClickHarvest respeta los reclamos:** en NeoForge publica un `BreakEvent` antes de cada cosecha y un `EntityPlaceEvent` antes de replantar (`RightClickHarvestPlatformImpl`), y FTB Chunks los cancela en un reclamo ajeno. Config por defecto: sin azada obligatoria, sin costo de hambre ni de experiencia. Los cultivos de Mystical Agriculture son `CropBlock`, así que se cosechan igual que al romperlos.
- **Yeetus Experimentus hace falta:** `WorldDimensions.checkStability` (servidor 1.21.1 parchado por NeoForge 21.1.249) marca como experimental toda dimensión que no es vanilla, y el pack tiene más de diez. Los tres packs de referencia lo traen. Falta verlo en cliente.
- **EMI Loot no muestra el botín de la campaña.** No tiene un ajuste por tabla, pero nombra cada receta `emi_loot:/<categoría>/<espacio>/<ruta>` (`ChestLootRecipe.getId` y sus hermanas). El filtro de datos de EMI (`pack/kubejs/assets/emi/recipe/filters/entrelumen_hidden_loot.json`) oculta toda tabla `entrelumen:*`: la del taller de Terra, las de bloques y las del Envés (`entrelumen:enves/<tipo>`), que entran con su rama.
  - **Las cajas de loot de las quests no son tablas de botín:** son tablas de recompensas de FTB Quests. Las muestra FTB XMod Compat (ya en `main`) con la categoría de JEI `ftbquests:loot_crate`, con pesos, incluidas las cajas de las cumbres y los encargos. EMI la importa con el mismo ID (`JemiCategory`). El mismo filtro la oculta en EMI, y `RecipeViewerEvents.removeCategories` la oculta en JEI (`pack/kubejs/client_scripts/entrelumen_recipe_viewer.js`). La categoría «Quests» de XMod queda: sólo lista quests que el jugador ya puede empezar.
  - `tools/check_recipe_design.py` suma la regla 6, que prueba el filtro contra IDs de ejemplo de las cinco categorías: oculta los de `entrelumen` y no los de otros mods. También exige la categoría en EMI y en JEI.
- **Dynamic FPS** (`pack/config/dynamic_fps.json`, parcial: el mod guarda sólo lo que difiere de sus valores): desenfocado a 15 FPS (de fábrica, 1), sin el modo inactivo por batería, sin el indicador de batería y sin descargar las librerías nativas de batería.
- **Ping Wheel:** queda en `AUTO`. Un grupo de FTB Teams ve sólo sus pings; un jugador sin grupo, todos. Trae traducción al español argentino.
- **Teclas:**
  - Bridging Mod venía en la coma, que ya usa Iron Jetpacks para bajar el empuje. El preset la pasa a **Alt+coma**.
  - Ping Wheel usa Mouse 5 para marcar y deja sin asignar su pantalla de ajustes.
  - FindMe tenía Y y el teclado numérico, pero quedó afuera.
  - Dynamic FPS trae dos teclas sin asignar.
  - `tools/check_keybinds.py`: 0 choques.
- **Chunky:** hay que probar que las ruinas salen igual en chunks pregenerados. La ruina de inicio se coloca en `ServerStartedEvent`, antes de cualquier `/chunky start`, y el resto de las ruinas todavía no está en `main`. La QA pregenera un cuadrado alrededor del inicio y vuelve a correr la prueba de la ruina.
- **GameTests:** `pingponground5informationloaded` (el lote cargado, los de cliente fuera del servidor dedicado, FindMe ausente) y `rightclickharvestrespectsforeignclaims`. En esta última, dos jugadores de prueba en un reclamo real de FTB Chunks: el dueño cosecha y su trigo vuelve a edad 0, y el trigo que toca el visitante sigue maduro.

### Lote 3 · Compat entre sistemas que ya tenemos

Familia `catalog/families/pingpong5-compat.json`: 5 mods y ninguna librería nueva. El lock pasa a **353 / 301**; ninguna entrada previa cambió.

| Mod | Versión | Fuente | Acto | Para qué |
|---|---|---|---|---|
| Apothic Category Compat | 2.0.2 | CF 1516278/8219980 (FTB) | I-V | Armas a distancia de Cataclysm, Twilight Forest y Undergarden entran en la categoría arco de Apotheosis (un mapa de datos). |
| Apotheosis x Iron's Spellbooks Compat | 2.2.1 | CF 1244863/8480849 (FTB y Craftoria; no está en Modrinth) | II-IV | Afijos y gemas de Apotheosis para el equipo de Iron's. |
| Polymorphic Energistics | 0.4.1 | CF 941096/5545923 (ATM10) | III | Elegir la salida cuando dos recetas chocan al codificar un patrón de AE2. |
| Ad-Astra: Giselle Addon | 8.1 | CF 714958/8676868 (ATM10) | V | Mejoras de espacio para la MekaSuit y la armadura de PneumaticCraft, cargador de combustible, sensor de cohete y mesa NASA automática. |
| Advanced Peripherals | 0.8.0a (alfa) | CF 431725/8666472 (ATM10 y FTB; no está en Modrinth) | III-V | Periféricos de ComputerCraft: puentes a AE2 y RS, detectores, gestor de inventario, chat, escáner geológico y tortugas autómatas. |

- **Versiones:**
  - Iron's Apothic se fija en 2.2.1, la que usan FTB Evolution y Craftoria con las mismas Apotheosis 8.7.0 e Iron's 3.16.3 del lock. Las 2.2.4 a 2.2.6 salieron en la última semana.
  - Giselle queda en 8.1 (ATM10, con Ad Astra 1.16.19); las 8.2 a 8.4 sólo están en CurseForge y no traen changelog.
  - Advanced Peripherals queda en 0.8.0a, la de ATM10 y FTB con CC: Tweaked; la 0.7.62b es beta de otra serie.
- **Relaciones de CurseForge** (`cfRelations` de la familia):
  - CC: Tweaked 282001, que piden Advanced Peripherals y More Red CCT, se cumple con el CC: Tweaked 1.120.2 del proyecto 1676502 que ya estaba: provee el mismo `computercraft` y su `mods.toml` sólo pide `[1.119.0,)`.
  - Botarium (704113) queda exento para Giselle 8.1: el `mods.toml` no lo pide (Ad Astra 1.16 pasó a Common Storage Lib) y ATM10 lo trae sin Botarium.
- **Escalonado** (familia `pingpong5compat` de `tools/generate_family_balance.py`, script `entrelumen_pingpong5compat_balance.js`):
  - **Se quitan** las cinco celdas de disco AE2 de Advanced Peripherals: de 1M a 256M bytes, con disquetes de ComputerCraft y procesadores. Saltearían MEGA Cells, la ruta del acto IV a las celdas grandes.
  - **También se quita** el controlador de chunks, que sólo sirve para armar la tortuga que carga chunks.
  - **Quedan nativos** (regla `UPSTREAM`): la mesa NASA automática de Giselle se hace con la mesa NASA, que ya lleva la aleación atómica (V). Los puentes ME y RS necesitan una red cuyo controlador ya pide la Matriz de Enrutamiento. El censo proponía la Matriz en el puente ME, pero la Matriz ya está en 7 de 8 y el puente no abre ninguna función.
  - Los módulos de MekaSuit de Giselle piden el traje espacial, y la mejora de oxígeno de PneumaticCraft también.
- **Config de Advanced Peripherals:**
  - `pack/config/Advancedperipherals/world.toml`: `givePlayerBookOnJoin = false`. Es el único regalo de primer ingreso de los lotes 1 a 3 (`tools/audit_first_join.py`); sin esto, el jugador nuevo ya no llegaba con las manos vacías.
  - `pack/defaultconfigs/Advancedperipherals/peripherals.toml` (se copia a cada mundo nuevo):
    - el detector de jugadores lee posiciones hasta 128 bloques (antes, infinito y entre dimensiones) y no informa estadísticas;
    - el chat alcanza 256 bloques y no puede adjuntar `run_command`;
    - la tortuga que carga chunks queda apagada, porque los chunks forzados pasan por FTB Chunks y sus límites.
- **Teclas:**
  - El atajo de las gafas inteligentes venía en G, la tecla fija de Curios. El preset lo deja sin asignar: sólo sirve con el módulo de atajos puesto en las gafas, y cada uno lo asigna al armarlo.
  - Ctrl izquierdo para ver descripciones se mantiene apretado y sólo actúa en pantallas.
- **Carry On:** `advancedperipherals:*` y `ad_astra_giselle_addon:*` quedan en la lista negra.
- **Afijos de hechizo de Iron's Apothic:** quedan nativos. Tienen 140 afijos y 23 gemas en su propio espacio; siguen la rareza de Apotheosis, que en el pack atan los World Tiers de la campaña. Hay que mirarlos en juego.
- **Apothic Category Compat:** su mapa de datos también nombra armas de mods que no tenemos (Alex's Caves, Alex's Mobs, Born in Chaos). El arranque dirá si NeoForge las ignora en silencio.
- **GameTests:**
  - `pingponground5compatloaded`: el lote cargado, sin recetas de las celdas ni del controlador, y los seis valores de config de Advanced Peripherals leídos del mod.
  - `carryonrefusesround5blocks`: la lista crece con cada lote.

### Lote 4 · Tecnología y redstone

Familia `catalog/families/pingpong5-tech.json`: 5 mods y 1 librería (Tesseract API), todo de la instancia ATM10. El lock pasa a **359 / 307**; ninguna entrada previa cambió.

| Mod | Versión | Fuente | Acto | Para qué |
|---|---|---|---|---|
| Extended Industrialization | 1.16.2 | CF 1068418/8708268 | II-V | Amplía Modern Industrialization: calderas y paneles solares, energía inalámbrica tesla, máquinas grandes, matriz de procesamiento, granjero, encadenador de máquinas, traje nano y herramientas eléctricas. Necesita Tesseract API 1.12.16 (CF 1067672/8708086). |
| Industrialization Overdrive | 1.12.2 | CF 1089065/8742295 (no está en Modrinth) | III-V | Constructor de multibloques de MI, matriz de procesamiento múltiple, horno de pirólisis y la herramienta Vajra. |
| Dyson Cube Project | 1.0.5 | CF 1345066/8082907 (no está en Modrinth) | V | Esfera de Dyson: se lanzan velas solares a órbita y un receptor cobra su energía. |
| More Red | 6.0.0.3 | CF 387638/5763286 | II-VI | Compuertas lógicas de redstone, cables de colores y agrupados, mesa de soldar. |
| More Red x CC:Tweaked Compat | 1.3.0 (beta) | CF 867286/6165477 | III-V | ComputerCraft lee y escribe los cables agrupados de More Red. |

- **Escalonado** (familia `pingpong5tech`, script `entrelumen_pingpong5tech_balance.js`):
  - **Tesla** (función «energía inalámbrica», acto III, como el enchufe de Flux y las celdas dimensionales): la bobina lleva la aleación reforzada en las dos esquinas libres de arriba (`ZLZ / EHE / BCB`) y la torre, en lugar de una de sus dos carcasas (`AZA / DHD / ACA`). Se quitan sus gemelos del ensamblador de MI y la conversión receptor → bobina, que salteaba el gate; la bobina se sigue convirtiendo en receptor. Los receptores y escotillas quedan nativos (`UPSTREAM`: sin transmisor no reciben nada).
  - **Esfera de Dyson** (función «reactor final», acto V): su receta es de hierro, cobre y diamante, así que sin gate la esfera salía en el acto I. El expulsor lleva la aleación atómica en lugar de una losa. El receptor queda nativo: sólo cobra velas ya lanzadas.
  - **Armadura nano cuántica de EI**: un empaquetador de MI suma pieza nano y mejora cuántica. Es el mismo salto que la armadura cuántica de MI (acto VI), así que lleva la misma Luminosidad de Habitabilidad como tercera entrada, en las cuatro piezas.
  - **La pechera gravitatoria nano de EI** se desempaca en la de MI, pero se empaqueta a partir de ella. El gate de la de MI (Carta de Horizonte) la declara como ruta alternativa: devuelve la pechera que ya existía.
  - **Nativos a propósito:** los paneles solares, las calderas solares, la matriz de procesamiento y las herramientas de EI siguen la escalera de circuitos de MI (analógico LV, electrónico MV, digital HV), que ya los ubica. Lo mismo vale para las piezas de Industrialization Overdrive, que piden de MV a EV (la Vajra, cryofluid y circuitos digitales), y para la lógica de More Red. El censo proponía componentes para los paneles LV/HV, el constructor, la matriz y la Vajra. Por las reglas del playtest (lo que va por docenas y los escalones internos de un mod no piden componentes) quedan con la escalera de MI.
  - `FUNCTION_MEMBERS` suma la bobina y la torre a la energía inalámbrica y el expulsor al reactor final; los tests lo comprueban.
- **Almost Unified:**
  - El polvo de netherita de EI entra en `c:dusts/netherite`, que ya se unifica (gana el de Mekanism).
  - El lingote de aleación roja de More Red se etiqueta `c:ingots/redstone_alloy` a propósito, igual que la aleación de Ender IO: las recetas de cada mod aceptan la del otro. No se unifica (`redstone_alloy` no está en la lista de materiales).
  - Ese cruce deja hacer en la mesa (cobre o hierro y 4 de redstone) la aleación que Ender IO pide para 13 recetas (conductos de redstone, filtros, cuba), sin pasar por su fundidora, que es una máquina temprana sin gate. Queda anotado; no rompe un acto.
- **Esfera de Dyson, balance** (config nativa): 20 FE/t por vela, hasta 50 millones de velas. Cada vela cuesta 3 cobres, 4 paneles de vidrio y 2 lapislázulis. Diez mil velas dan 200.000 FE/t, y la esfera no tiene tope práctico. Queda para probar en juego frente a Powah, los reactores y New Age antes de tocar la config.
- **Teclas:** EI trae cinco atajos del traje nano, sin asignar. Los demás no registran teclas.
- **Carry On:** `extended_industrialization:*`, `industrialization_overdrive:*`, `dysoncubeproject:*` y `morered:*`.
- **GameTests:** `pingponground5techloaded` controla:
  - el lote cargado;
  - la bobina y la torre con aleación reforzada y el expulsor con aleación atómica, en el gestor de recetas cargado;
  - las cuatro piezas nano cuánticas con la Luminosidad, leídas de la lista de entradas de MI;
  - que no queden las rutas quitadas.

  `carryonrefusesround5blocks` suma cinco bloques de este lote.

### Lote 5 · Neo Vitae

Familia `catalog/families/pingpong5-neovitae.json`: 2 mods, ninguna librería nueva. El lock pasa a **361 / 309**; ninguna entrada previa cambió.

| Mod | Versión | Fuente | Acto | Para qué |
|---|---|---|---|---|
| Neo Vitae | 1.1.28 | CF 1404763/8976888 (CDN; los mismos bytes en Modrinth) | III-IV | El sucesor de Blood Magic: el Ara Vitae y sus niveles, orbes, sigilos, runas, alquimia, la forja del fuego infernal, rituales y las mazmorras del Reino Demoníaco, su dimensión (`neovitae:dungeon`). |
| Sanguine Neural Networks | 2.0 | CF 1111092/8815455 (CDN; los mismos bytes en Modrinth) | IV | Sangre para el altar desde un modelo de datos de Hostile Neural Networks, sin granja de criaturas. Trae el sacrificador virtual y la estructura Suffering Inc. |

- **Versión.** ATM10 y FTB Evolution fijan Neo Vitae 1.1.15, y Craftoria una 1.1.2x. Entra 1.1.28 por arreglos que un servidor público necesita:
  - 1.1.26 y 1.1.27: la Linterna de Sangre ya no corre su chequeo de spawn durante la generación de chunks (tumbaba al servidor explorando), ya no crashea cuando otro mod hace aparecer animales cerca y ya no carga chunks de más;
  - 1.1.22: se cierra un duplicado de experiencia con los tomos, las redes de enrutamiento ya no cargan chunks remotos y un depósito grande ya no deja el saldo de sangre en negativo;
  - 1.1.16: las invocaciones del Sigilo de Nigromancia tienen un tope por jugador (10) y ya no pagan sangre muriendo en pinchos, y el Nexo del Tormento reparte botín de Apotheosis según el World Tier del dueño;
  - 1.1.28 sólo reparte más llaves de mazmorra en el Reino.
- **Escalonado** (familia `neovitae` de `tools/generate_family_balance.py`, script `entrelumen_neovitae_balance.js`):
  - **El Ara Vitae abre el mod entero** (acto III): el Núcleo de Propagación llena su hueco de arriba (`sZs / sfs / ggg`), como el ensamblador de Psi. Todo lo demás sale del altar: las tablillas, los orbes, la forja (pide una Tabula Rasa), las runas, los sigilos y la mesa de alquimia. `tools/check_recipe_design.py` lo anota como hito del Núcleo (fan-out 6).
  - **La piedra ritual maestra** (acto IV) lleva hierro de bosque (ironwood) en las dos esquinas de arriba (`ZsZ / scs / oso`). Todos los rituales la necesitan, incluidos los de mazmorra, que abren el Reino Demoníaco. La piedra ritual imperfecta (lluvia, resistencia y otros menores) queda con el altar.
  - **El sacrificador virtual** de Sanguine Neural Networks lleva ironwood en sus esquinas libres (`ZDZ / SOS / BCB`, acto IV). Suffering Inc. se arma con él.
- **El teleposer sale, con sus tres focos y la cadena del Sigilo de Teleposición** (el reactivo pide un teleposer). Intercambia bloques enteros, contenedores y spawners incluidos, en un cubo de hasta 7×7×7 que arranca arriba del teleposer (`x, z` en `[−r, r]`, `y` en `[1, 2r+1]`). Lo hace con `setBlock` en `Utils.swapLocations` y sólo mira un tag de bedrock y portales: ni `BreakEvent`, ni FTB Chunks, ni `StructureProtection`. Puesto junto al borde de un reclamo o de una ruina de Heliodor, se lleva lo de adentro. Puede volver si más adelante un gancho del companion le hace respetar reclamos y `StructureProtection`.
- **El resto respeta la protección.** Rituales, sigilos, cargas explosivas, el área de la Lex Vitae y el enrutador pasan por `BlockProtectionHelper`, que publica `BreakEvent` y `EntityPlaceEvent` con un jugador falso del dueño, así que FTB Chunks y `StructureProtection` pueden negarse. La captura de spawners con una gema de Spiritus ya la cubren la protección de uso de ítems de las ruinas y la de interacción de FTB Chunks.
- **Dimensión:** `neovitae:dungeon`, sólo por rituales. El mod no suma estructuras, biomas ni menas al Overworld (no trae modificadores de bioma ni sets de estructuras). Yeetus Experimentus (lote 2) evita el aviso experimental.
- **Almost Unified:** el salitre y el azufre de Neo Vitae entran en `c:dusts/saltpeter` y `c:dusts/sulfur`, que ya se unifican (gana Immersive Engineering por prioridad de mod). El hierro infernal (`hellforged`) es sólo de Neo Vitae.
- **Primer ingreso:** ningún regalo (`tools/audit_first_join.py`). El libro guía se fabrica con un libro y redstone.
- **Teclas:**
  - editar el HUD venía en H, que ya usa Eternal Starlight: el preset la deja sin asignar;
  - cambiar el modo de la Lex Vitae venía en el punto, que es el acelerador de Iron Jetpacks: pasa a **Shift+punto**, sólo con la Lex Vitae en la mano;
  - el Escudo de Sangre es el botón de usar con un orbe en la otra mano, como un escudo (compartido a propósito con `key.use`);
  - el rayo de la Lex Vitae y la guía vienen sin asignar;
  - `tools/check_keybinds.py`: 0 choques.
- **Carry On:** `neovitae:*` y `sanguine_networks:*`.
- **EMI Loot** muestra las tablas de Neo Vitae (cofres de mazmorra, criaturas): no son de la campaña.
- **GameTests:** `pingponground5neovitaeloaded` controla:
  - los dos mods cargados;
  - el altar con el Núcleo y la piedra maestra y el sacrificador con ironwood, en el gestor de recetas cargado;
  - las seis recetas del teleposer ausentes;
  - la dimensión `neovitae:dungeon` cargada.

  `carryonrefusesround5blocks` suma el altar, la piedra maestra, la forja y el sacrificador.
- **Ganchos para las quests** (cadena propia de Neo Vitae; no se tocó contenido de quests):
  1. Ara Vitae (`neovitae:ara_vitae`, acto III, con el Núcleo de Propagación).
  2. Orbe Novicius (`neovitae:blood_orb_weak`), Tabula Rasa (`neovitae:tabula_rasa`) y la primera runa (`neovitae:rune_blank`, `neovitae:rune_sacrifice`).
  3. Forja del fuego infernal (`neovitae:hellfire_forge`) y gema de Spiritus menor (`neovitae:spiritus_gem_petty`).
  4. Sigilos: adivinación, agua, lava y aire (`neovitae:sigil_divination`, `neovitae:sigil_water`, `neovitae:sigil_lava`, `neovitae:sigil_air`).
  5. Orbes Discipulus y Veneficus (`neovitae:blood_orb_apprentice`, `neovitae:blood_orb_magician`) y Tabula Animata (`neovitae:tabula_animata`).
  6. Acto IV: piedra ritual maestra (`neovitae:master_ritual_stone`, con ironwood), cristal de activación débil (`neovitae:activation_crystal_weak`) y adivinador ritual (`neovitae:ritual_diviner`).
  7. El Reino Demoníaco: la dimensión `neovitae:dungeon` por un ritual de mazmorra, demonita cruda (`neovitae:raw_demonite`) y lingote infernal (`neovitae:ingot_hellforged`).
  8. Orbe Magus (`neovitae:blood_orb_master`), Tabula Spiritus (`neovitae:tabula_spiritus`) y la Lex Vitae (`neovitae:lex_vitae`).
  9. Sanguine Neural Networks: sacrificador virtual (`sanguine_networks:virtual_sacrificer`) con un modelo de datos de HNN.
- **Pendiente en juego:** el HUD y el altar en un cliente, una bajada al Reino Demoníaco y lo que pesan las Linternas de Sangre.

### Lote 6 · Iris

Familia `catalog/families/pingpong5-shaders.json`: 1 mod de cliente. El lock pasa a **362 / 309**; el servidor no cambia.

| Mod | Versión | Fuente | Lado | Para qué |
|---|---|---|---|---|
| Iris | 1.8.14-beta.1 | CF 455508/8242804 (ATM10; los mismos bytes en Modrinth) | cliente | Carga shaders en formato OptiFine que agregue el jugador. |

- **Versión:** la de ATM10, que corre con el mismo Sodium 0.8.13 y Sodium Extra 0.9.3 del lock. El log de ATM10 en esta máquina la muestra cargando y apagada por falta de pack («Shaders are disabled because no valid shaderpack is selected»). Su `mods.toml` pide `[1.21,1.21.1)` de Minecraft, pero el mismo archivo carga en ATM10 sobre 1.21.1.
- **Apagado por defecto, sin shaderpack.** Iris lee `config/iris.properties` al cargar las opciones (`Options.load`) y prende los shaders salvo que diga `enableShaders=false`. Default Options copia `config/defaultoptions/extra/<ruta>` a `<ruta>` al construir `GameConfig`, antes de que ningún mod lea su config, y sólo si el archivo no existe (`ExtraDefaultOptionsHandler`, 21.1.8). Así el pack siembra una vez `enableShaders=false` y `disableUpdateMessage=true`. Quien prende shaders conserva esa elección cuando el pack se actualiza, algo que un `config/iris.properties` enviado directo pisaría.
  - `tools/generate_client_defaults.py` genera el archivo desde la sección `extra` del preset (`pack/config/entrelumen/client-preset.json`). Sólo acepta los archivos de su lista (`config/iris.properties`) y líneas `clave=valor` simples; `--check` lo compara y los tests cubren el valor y los rechazos.
  - Para usarlos: copiar un shaderpack a `shaderpacks/`, abrir Opciones → Video → Shader Packs, elegirlo y poner «Shaders: ON».
- **Teclas:** Iris traía recargar en R (el cinturón de herramientas), prender o apagar en K (el gestor de reclamos, tecla fija) y la pantalla de packs en O (la bolsa de Occultism). El preset deja las tres sin asignar; la pantalla sigue a mano en las opciones de video. El modo alambre viene sin asignar. `tools/check_keybinds.py`: 0 choques.
- **Escalonado, Carry On, GameTests:** no aplica (cliente, sin ítems ni bloques). Falta verlo en un cliente: el botón de Shader Packs, un pack de prueba prendido y apagado, y que el archivo sembrado no se pise al reiniciar.
