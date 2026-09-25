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
| Ad Astra | 1.16.19 | CF 635042/8758526 (ATM10) | V | El banco NASA pide un Bus del Arca: los cohetes y los planetas se abren construyendo el Arca. |
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
- **Almost Unified**: config `crops` nueva con 69 nombres de cultivo duplicados (`c:crops/*` y `c:seeds/*`), prioridad Farmer's Delight, Croptopia y Pam's, y unificación de loot para que toda planta suelte el mismo ítem; `materials` suma MI, Oritech, Ender IO y Ad Astra a las prioridades. La receta de haunting de Create del tomate podrido y las semillas de avena y batata de Pam's (que son a la vez cultivo) quedan fuera porque rompían.
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
| Psi | 1.21.1-110 | Modrinth pOeA0exL/j9TFdTKC | III | Entero en el acto III (Elias, 24/9; antes el ensamblador era del II). El Ensamblador de CAD pide un Regulador de Energía (III): sin él no hay CAD ni hechizos. Los núcleos de psigema (hiperacelerado y radiativo) piden una Matriz de Distribución (III). |
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
