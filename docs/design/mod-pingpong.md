# Selección de mods por ida y vuelta con Elias

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

## Descargas

El 24/9 Elias autorizó bajar de las fuentes oficiales (CurseForge y Modrinth) los mods que eligió en estas rondas, aunque no estén en ninguna instancia local: «no estamos copiando, estamos tomando inspiración, no importa si están o no en otro pack, bajalos porque te lo pedí». Por separado aprobó LambDynamicLights 4.8.11+1.21.1 desde Modrinth. Cada JAR se verifica por hash y se fija en el catálogo con su fuente.

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
- **Hallazgo de la brújula**: en la primera corrida (`test runall`, con mucha generación simultánea) el servidor se cayó por el watchdog. Un tick duró 60 s dentro de `CompassLocator$StructureJob.step`, que pide `level.getChunk(..., STRUCTURE_STARTS)` de forma síncrona en el hilo del servidor. Es código de la brújula: lo reporto a su dueño en lugar de tocarlo.
- Otros errores de log ajenos al lote: excepciones de ticks de entidades de hechizos atrapadas por el nivel (Ars Nouveau `EntityWallSpell`, Theurgy `FollowProjectile`, Iron's `ChainLightning`) durante una prueba de Ars.

### Pendiente para Elias

- Steam 'n' Rails: ¿el port no oficial para 1.21.1 o nada?
- Botania y Blood Magic no existen para 1.21.1; si se quiere algo parecido, decidir entre lo que ya está o un addon.
- Revisión en cliente: render de LittleTiles, Mowzie's y Eternal Starlight; las teclas nuevas y la sensación de la exploración inicial en una PC modesta (el Overworld genera un 70-80% más lento con el lote).
- Balance en juego: potencia de MI, Oritech y Ender IO frente a Mekanism; hechizos de Mahou Tsukai; loot de Repurposed Structures; la armadura cuántica de MI frente al equipo luminoso.
- Las quests y la brújula todavía no mencionan las dimensiones nuevas; en ellas la aguja queda gris.
- Recetas desactivadas que podrían reescribirse al formato 1.21 si se extrañan: las de Create, Mekanism e IE para los minerales de Ad Astra y las de Croptopia en Botany Pots.
