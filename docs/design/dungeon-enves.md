# El Envés: el descenso generativo

Plano del controlador, 26 de septiembre de 2026. Pedido de Elias:

- una dungeon generativa como las de Diablo 2/3 o la de *The Other* en ATM10;
- pisos indestructibles y escaleras que hay que encontrar;
- un mapa que se va revelando;
- 4 o 5 niveles con un jefe al final;
- loot que escala con la dificultad y que obliga a equiparse: nada de «refined obsidian y listo»;
- pocos enemigos fuertes y divertidos, nada de tormentas de entidades ni spawners, y nada de basura en el inventario.

**Estado (27/9).** El motor está hecho: la dimensión, el intento con su ofrenda y su bolsa de caídas, el generador en Java, las plantillas de Osarios con marcadores, la colocación por ticks, las reglas y el mapa con niebla ([El motor](#el-motor)). La primera iteración del contenido también: ecos con afijos, campeones de escalera, santuarios, sellos en tres variantes, cerraduras de bóveda, loot con Apotheosis y esquirlas de luz agria, el Wither blanco y la ofrenda de estrella o esquirlas ([Contenido construido](#contenido-construido-279)). Cisternas, Fundición, Geodas y El Eclipse usan por ahora las salas de Osarios con una paleta provisoria; otro worker dibuja su arte.

- `art/dungeon/drlg.py` es el oráculo del generador y dibuja el plano de revisión;
- `art/dungeon/tiles.py` hace las salas y sus marcadores; `tools/export_enves_tiles.py` las escribe como NBT.

## La decisión: layout generativo sobre salas de autor

Es el método de Diablo II. Hay dos niveles y cada uno hace lo que mejor le sale:

| Qué | Cómo | Costo |
|---|---|---|
| Forma del piso | Un generador de grilla (11×11 celdas) crece como un caminante con memoria: sigue de largo casi siempre, a veces abre una rama y al final cierra algunos bucles. Después elige la salida lejos, en un callejón, y reparte los roles. | Un algoritmo chico, sin arte |
| Aspecto de cada celda | Una plantilla de 19×19×12 por tileset × rol × máscara de puertas × variante, hecha por código: forma de la sala (cuadrada con pilares, octógono, cruz), piso, zócalos, nichos, luz. Se generan las 15 máscaras, así que el juego nunca rota nada. | Un script por tileset |

Sale variado, porque cada descenso es otro, y bonito, porque cada sala está dirigida. Es barato de hacer: no hay un motor de tallado de voxels en tiempo de ejecución, sólo pegar plantillas. Un motor 100% procedural daría menos control del arte por más trabajo.

Invariantes del port a Java (`EnvesLayout`), probados en `EnvesLayoutTest` con 2000 semillas:
- todo el piso es alcanzable;
- la salida es un callejón lejano (≥ 80% de la distancia máxima que alcanzó el crecimiento, antes de las ramas de los sellos);
- la bóveda y el santuario quedan fuera del camino principal;
- cada sello queda en un callejón fuera del camino principal (el port no brota ramas desde un sello ya elegido; el prototipo sí, y ese sello dejaba de ser callejón);
- el piso k+1 empieza justo debajo de la escalera del piso k, que baja de verdad en espiral y atraviesa la losa.

## Estructura del descenso

| Piso | Tileset | Salas | Carácter |
|---|---|---|---|
| I | Osarios | ~26 | Toba y calcita, nichos con huesos y velas, criptas de pilares |
| II | Cisternas | ~31 | Canales de agua, caños de cobre, pasarelas |
| III | Fundición | ~36 | Piedra negra, basalto, canales de lava, cadenas |
| IV | Geodas | ~41 | Amatista, calcita, basalto liso, cristales que brillan |
| V | El Eclipse | fijo | Acceso corto, antesala del campeón y arena de 3×3 celdas; al fondo, la salida |

Roles por piso:
- **inicio:** la bajada por donde llegaste;
- **escalera:** oculta en un callejón lejano; sellada hasta prender los sellos;
- **sellos (Elias, 26/9: «más salas, más laberíntico, que tengas que sí o sí explorar»):** 2 en los pisos I–II y 3 en los III–IV, en callejones fuera del camino principal y lo más lejos posible entre sí. Si el piso no deja escondites, el generador brota una rama ciega nueva. Los sellos se ven en el mapa recién cuando los encontrás;
- **guardia:** la sala antes de la escalera, con un campeón;
- **encuentros:** alrededor del 40% de las salas;
- **santuario:** en el 70% de los pisos, una bendición temporal;
- **bóveda:** un callejón lateral cerrado por un acertijo;
- **salas quietas:** lore, ambiente y alguna trampa leve.

**Dimensión propia:** `entrelumen:enves`. Es vacía, sin cielo, sin clima y sin spawn natural. Cada descenso ocupa una parcela a 2048 bloques de las otras, con sus cinco pisos apilados. La parcela se libera diez minutos después de que sale el último del grupo. Un piso se genera recién cuando alguien llega a la guardia del anterior, repartido entre ticks (hasta 41 celdas de 4.332 bloques).

## Mapa con niebla

- JourneyMap y el mapa de FTB Chunks quedan apagados dentro del Envés ([Mapas](#mapas)). Si no, revelan el piso entero apenas cargan los chunks.
- En su lugar, un minimapa propio con estética de Atlas, en pergamino:
  - lo pisado se ve nítido;
  - lo que se asoma por una puerta, borroso;
  - el resto es niebla de tinta.
- La escalera sólo aparece cuando la encontrás.
- El servidor manda al grupo la grilla y lo explorado, apenas unos bytes.
- El mismo mapa, grande, se abre en la pantalla del Atlas.

## El motor

### Dimensión y parcelas

- `entrelumen:enves`: vacía, sin cielo, con techo, hora fija, luz ambiente baja, sin clima y 128 de alto. El bioma no tiene spawns; además no hay spawn natural ni de spawners.
- La parcela `n` está en `(n % 64, n / 64) × 2048`; un piso ocupa 11 × 19 = 209 bloques de lado. Se usa la parcela libre más baja.
- Los pisos se apilan cada 12 bloques: la losa del vestíbulo (piso 0) está en y = 88, la del piso I en 76 y la del V en 28.
- `EnvesData` (`data/entrelumen_enves.dat` del overworld) guarda la Escalera Sellada y cada intento: parcela, semilla, tier, bolsa, piso de cada integrante, lo explorado, los sellos prendidos y el estado de cada piso. Las plantas se redibujan de la semilla.

### La escalera

- Un anillo de 5 × 5 alrededor de un núcleo de 3 × 3, que da una vuelta por piso en sentido horario desde la esquina noroeste.
- Las esquinas son descansos y los tres bloques de cada lado, escalones que miran hacia arriba: cada lado baja 3 y la vuelta baja 12. Cada escalón tiene 3 bloques de aire encima, así que se sube y se baja caminando.
- La celda de la escalera tiene la boca en su losa: 4 bloques sellados con `reinforced_deepslate` hasta que arden los sellos y el piso de abajo está listo. La celda de inicio del piso siguiente, justo debajo, tiene el resto de la vuelta.
- Sobre el inicio del piso I hay un vestíbulo cerrado con el portal de vuelta.
- `EnvesGeometry.RING` y `ringY` son el contrato; lo verifican `EnvesContractTest` y un GameTest.

### Colocación

- Al pagar se colocan el vestíbulo y el piso I; el siguiente, cuando alguien llega a la guardia, a la escalera o prende todos los sellos.
- Celda por celda, del inicio hacia afuera, con 6 ms por tick. Los chunks cargan en segundo plano con un ticket por trabajo y las plantillas se leen fuera del hilo del servidor.
- Bajo cada celda va una capa de roca donde la losa está abierta, hasta que el piso de abajo la reemplaza.
- Al terminar un intento se borra su parcela, bloques y entidades, en franjas de capas por tick, y queda libre. Los contenedores se vacían antes de borrarlos: un cofre que se quita con `setBlock` tira lo que tiene, y uno sin abrir tira antes su loot, que quedaba flotando en la parcela para el intento siguiente. Las entidades se barren al empezar y otra vez al terminar, cuando ya cargaron las de todos los chunks de la parcela: llegan un poco después que el chunk, y un borrado rápido terminaba antes (el último barrido espera hasta 30 s de tiempo real, y el log dice cuántas entidades barrió). Un eco muerto en un chunk que dejó de andar nunca termina de morir: también se descarta si su intento ya no existe. Y lo que vuelve del disco a una parcela sin intento abierto (libre, terminada o todavía armándose) no vuelve a entrar al mundo: es algo que un borrado no alcanzó a ver, y el log lo cuenta.
- Si el servidor se apaga a mitad de un piso o de un borrado, lo retoma al arrancar.

### El intento

- La Escalera Sellada está en la plantilla de la ruina inicial (`tools/build_heliodor_ruin_start.py`, 15 × 22 × 17). `HeliodorRuins` la hunde según el marcador `entrelumen:ground`, vuelca los cimientos y vuelve a tallar lo que quedó bajo tierra.
- El corazón del sol se abre para siempre cuando alguien de un equipo en Frontier (acto III) se para encima o lo toca. Antes sólo contesta «El corazón del sol está frío. Todavía no te reconoce.». Se van las ocho celdas de afuera; el centro y el pedestal quedan.
- La puerta (3 × 4 bloques `entrelumen:enves_gate`) pide un equipo en Frontier:
  - sin intento: la ofrenda y la dificultad, cualquier tier desde Haven hasta el del equipo;
  - con intento abierto: entrar, al inicio del piso más hondo que alcanzó el grupo, o abandonarlo.
  El servidor revalida cada elección.
- La bolsa tiene 3 caídas por integrante conectado al pagar. Cada caída adentro resta una:
  - se conserva todo: `keepInventory` rige sólo para esa muerte, así que Curios, mochilas y tumbas se comportan igual;
  - se reaparece al inicio del piso donde te caíste;
  - la caída que vacía la bolsa termina el intento: todos vuelven a la antecámara y la puerta pide otra ofrenda.
- El intento también termina diez minutos después de que no queda nadie adentro, al abandonarlo o por un operador.
- Se sale por el portal del vestíbulo o, con el jefe muerto, por el de la arena.

### Reglas

- Toda la dimensión es una región de `StructureProtection`: no se rompe ni se pone nada, las explosiones y los mobs no rompen bloques y no se echan líquidos. El bypass de operadores (`/entrelumen admin protection bypass`) sigue igual.
- **Sin vuelo:**
  - se apagan `mayfly` y la elytra, y se baja a quien monta algo;
  - quien sube más de 1 s o flota más de 1,5 s en el aire vuelve a su último suelo. Así caen los jetpacks y el vuelo de cualquier mod. La levitación y la caída lenta no cuentan.
- **Sin atajos:**
  - las perlas, el chorus y los objetos del tag `entrelumen:enves_forbidden` (los pergaminos y la piedra de Waystones) no se usan, y sus teletransportes se cancelan;
  - nadie cruza de dimensión hacia o desde el Envés salvo por la puerta y los portales; los warps de Waystones también se frenan en su evento;
  - los comandos de `denied_commands` (`/home`, `/rtp`, `/back`, `/spawn`, `/tpa`...) no andan adentro. Ningún mod del pack registra `/home` ni `/rtp`: la lista cubre los que se agreguen.

### Mapas

- **FTB Chunks:** `pack/config/ftbchunks-world.snbt` pide la etapa `ftbchunks_mapping` para su mapa y su minimapa. El companion se la da a todos afuera y se la saca adentro; FTB Library la sincroniza.
- **JourneyMap:** en este pack corre sólo en el cliente, así que no le llegan permisos del servidor. Al entrar, el cliente del companion usa su API (`ClientAPI.INSTANCE`: `disableFeature` para cada tipo de mapa y `toggleMinimap`) y al salir la restaura (`FeatureManager.reset()`). Un cliente modificado podría saltearlo.
- **El mapa propio**, arriba a la izquierda, sobre el pergamino del mapa vanilla:
  - lo pisado se ve nítido, lo que asoma por una puerta es una mancha suave y el resto, niebla de tinta;
  - los íconos (llegada, escalera, sellos, bóveda, santuario, guardia, salida) aparecen sólo en salas pisadas;
  - el grupo se ve con los marcadores del mapa vanilla;
  - abajo: el piso, «Sellos 1/3» y «Caídas 4/6».
- El Atlas usado adentro abre el mismo mapa en grande, con la leyenda y un botón a sus páginas.
- El servidor manda a cada integrante lo conocido de su piso, sólo cuando cambia: las salas pisadas con puertas y rol, y las asomadas sin nada más.
- Referencias vistas: `textures/map/map_background.png` y `map/decorations/player.png` y `blue_marker.png` del cliente 1.21.1, y la paleta del libro del Atlas (`atlas_book.png`). Las texturas propias están en `assets/entrelumen/textures/gui/enves/` (`art/dungeon/fog_textures.py`).

### Contrato de marcadores

Son bloques de estructura en modo DATA dentro de las plantillas, con metadata `enves:<tipo>` o `enves:<tipo>:<argumento>` y posiciones locales (19 × 12 × 19; y = 0 es la losa). Al pegar la celda, el motor los vuelve aire y actúa:

| Marcador | Dónde | Cuántos | Qué hace el motor |
|---|---|---|---|
| `enves:arrival` | inicio: pies | 1 | llegada y reaparición del piso |
| `enves:stair_top` | escalera y vestíbulo: el descanso de arriba (pies) | 1 | — |
| `enves:stair_bottom` | inicio: el descanso de abajo (pies) | 1 | — |
| `enves:stair_seal` | escalera: la boca en la losa | 4 | sello hasta que la escalera abre |
| `enves:encounter[:champion]` | combate y guardia: pies | 4 (5 en la guardia, con el campeón en el centro) | gancho `Encounters` la primera vez que entra alguien del grupo |
| `enves:chest:<room\|vault\|boss>/<orientación>` | salas quietas, bóveda, portal | 0–1 | gancho `Chests`; por defecto un cofre de Lootr con la loot table configurada, los de sala con probabilidad |
| `enves:shrine` | santuario: el altar | 1 | gancho `Shrines`; por defecto un faro provisorio |
| `enves:vault_gate:<n\|e\|s\|w>` | bóveda: la puerta de 3 × 4 | 12 | gancho `Vaults`; por defecto barrotes de hierro |
| `enves:vault_mechanism` | bóveda: 2 bloques detrás de la puerta (pies) | 1 | gancho `Vaults` |
| `enves:seal` | sello: sobre el estrado | 1 | `entrelumen:enves_seal`, que se prende con un clic |
| `enves:boss_center` | centro de la arena: pies | 1 | gancho `Boss` cuando el piso V está listo, y `Encounters` al primer paso |
| `enves:exit_portal:<return\|victory>` | vestíbulo; celda del portal del piso V | 1 | `entrelumen:enves_portal`: el de vuelta siempre activo, el de victoria al caer el jefe |

Las plantillas son `structure/enves/<tileset>/<rol>_<puertas>_<variante>.nbt`, con las puertas en orden NESW (`nes`, `w`) y `x` sin puertas. Los roles son `start`, `exit`, `vestibule`, `guard`, `fight` y `quiet` (3 variantes: cripta, octógono y cruz; la quieta recta es pasillo), `shrine`, `vault`, `seal`, `arena`, `arena_center` y `portal`, con las 15 máscaras cada uno. `tools/export_enves_tiles.py` las rehace desde `tiles.py` y escribe el índice `enves/templates/osarios.json`; con `--check` verifica lo commiteado.

### Archivos de datos

- `data/entrelumen/enves/config.json`: `offering` (una lista de alternativas: `minecraft:nether_star` × 1 o `entrelumen:sour_light_shard` × 64; también acepta un solo `{item, count}`), `falls_per_member` (3), `abandon_minutes` (10), `stair_seal_block`, `vault_gate_block`, `room_chest_chance` (0,25), `chest_loot_table` (`entrelumen:enves/{kind}`; también acepta `{tier}` y `{floor}`), `denied_commands`, `tilesets` (uno por piso) y `ftb_chunks_map_stage`. Un datapack lo reemplaza; si no valida, sigue el anterior.
- `data/entrelumen/enves/tilesets/<id>.json`: `templates` (la carpeta de plantillas) y `palette` (cambios de bloque). Los cinco usan `osarios`; Cisternas, Fundición, Geodas y El Eclipse con paletas provisorias.
- `data/entrelumen/tags/item/enves_forbidden.json`: los objetos que no andan adentro.
- `pack/config/ftbchunks-world.snbt`: `require_game_stage: true`.

### Comandos

- `/entrelumen enves`: el intento del equipo. `/entrelumen enves giveup`: abandonarlo.
- Operadores, `/entrelumen admin enves`: `list`, `open <tier>` (sin ofrenda ni Frontier), `tp <piso>`, `seals` (prende los del piso), `reveal` (explora el piso), `boss` (despierta el portal de victoria), `end` y `seal open|close` (la Escalera Sellada).

### Ganchos del segundo worker

`EnvesHooks` recibe los reemplazos en el setup del mod:
- `Encounters.roomEntered`;
- `Chests.place`;
- `Shrines.place`;
- `Vaults.place`; para abrir, `EnvesPlacer.openVault`;
- `Seals.mayLight`, `lit` y `stairMayOpen` (la escalera abre con todos los sellos, el piso de abajo listo y este sí: el contenido lo usa para el campeón);
- `Boss.floorReady`; el jefe llama a `Enves.bossDefeated` al caer;
- `Lifecycle`: intento abierto, piso listo, caída, jefe vencido (`bossDefeated`, también desde `/entrelumen admin enves boss`) y fin con su motivo.

`EnvesHooks.reset()` vuelve a los ganchos simples del motor y `EnvesContent.install()` pone los del contenido; `EnvesContent.uninstall()` además frena lo que el contenido hace por tick (los GameTests del motor corren así). `EnvesHooks.forcing(...)` marca lo que hace un operador: `/entrelumen admin enves seals` prende los sellos sin preguntarle a su variante y dispensa al campeón del piso.

Cada gancho recibe un `EnvesHooks.Floor`: el nivel, el intento (tier, semilla, parcela, bolsa), la planta, la profundidad, el tileset y los marcadores de cualquier celda en coordenadas de mundo. `Enves.attemptAt(server, pos)` dice de qué intento es una posición.

### Pruebas

- JUnit: `EnvesLayoutTest` (invariantes, 2000 semillas), `EnvesRulesTest` (bolsa, tiers, abandono, escalera, vuelo, comandos, niebla) y `EnvesContractTest` (marcadores, plantillas, escalera, datos, parcelas).
- GameTests (`RuntimeGameTestsEnves`):
  - el sello abre sólo con Frontier y no spoilea;
  - la ofrenda abre un intento a la dificultad elegida;
  - la bolsa compartida, con reaparición al inicio del piso, inventario intacto y expulsión;
  - indestructible y sin atajos: perlas, chorus, cruces de dimensión y vuelo;
  - el piso siguiente llega con la guardia y la escalera atraviesa la losa.

## Encuentros

- **Sin spawners.** Cada sala de encuentro tiene puntos marcados y el grupo se arma la primera vez que alguien del equipo entra: 1 élite más 1–2 escoltas, o 2 élites. Una sala limpia queda limpia.
- **Pool de enemigos por tileset:** vanilla con equipo, más los enemigos medianos de L_Ender's Cataclysm y Mowzie's Mobs que ya están en el pack. Por ejemplo, en la Fundición un Ignited Revenant con dos escoltas.
- **Afijos de élite, al estilo de Diablo:** Veloz, Blindado, Vampírico, Ardiente, Perforante y Espectral. Hay uno en el piso I y hasta tres en el piso IV. El campeón de la guardia lleva tres, más escoltas.
- **Sin basura:** los enemigos del Envés sólo sueltan experiencia. Un élite tiene una chance chica de soltar una gema o material de rareza; un campeón suelta una pieza con afijos.

## Dificultad y loot

- **La dificultad es el World Tier de Apotheosis que ya fija la historia** (Frontier en el acto III, hasta Pinnacle en el VI). En la entrada se puede elegir un tier menor para farmear.
- **Vida y daño enemigos** escalan por tier y por piso.
- **Contra el tanque puro:** parte del daño atraviesa la armadura (`armor_pierce` y `armor_shred` de Apothic Attributes). Con armadura de obsidiana refinada sola no alcanza: hacen falta afijos, protección, sustain, daño o magia.
- **Loot:**
  - cofres de Lootr, por jugador;
  - piezas con afijos de Apotheosis cuya rareza sube con el tier y el piso;
  - gemas;
  - la bóveda paga más;
  - el cofre del jefe da tres piezas de la rareza alta del tier.
- **Propuesta:** un curio único por jefe, como los objetos que se persiguen en Diablo.

## Reglas del Envés

- **Todo indestructible.** No se rompe ni se pone nada. Las explosiones no rompen bloques.
- **Nada que saltee el laberinto:**
  - sin vuelo (jetpacks, vuelo de mods);
  - sin perlas ni chorus;
  - sin waystones, `/home` ni `/rtp`.
  Los techos de 9 bloques tampoco dejan volar.
- **Intento, caídas y muerte (Elias, 26/9):**
  - la puerta se abre con una ofrenda, y cada ofrenda es un intento: desde el 27/9, **una estrella del Nether o 64 esquirlas de luz agria** (antes, un bloque de netherita);
  - adentro se conserva el inventario y se reaparece al inicio del piso;
  - el grupo comparte una bolsa de caídas de 3 por integrante (dos jugadores, seis caídas), sin importar quién las gaste;
  - cuando la bolsa se vacía, todos vuelven afuera, la puerta se cierra y pide otra ofrenda.

## Jefe (Elias, 26/9)

Uno propio. Para la v1.0, y probablemente más allá, va un reemplazo provisorio: un **Wither blanco, luminoso**. El modelo propio queda para el futuro y no frena el lanzamiento.

- **Movimiento:** casi no vuela. Levita unos bloques sobre el piso y se desliza.
- **Ataques:**
  - calaveras;
  - una **embestida muy telegrafiada**: carga con aviso claro, marca en el piso el recorrido, embiste en línea recta y queda expuesto unos segundos.
- **Sin grifeo.**
- **Escalado:** vida y daño según el World Tier.
- **Loot:** el cofre del Envés con la rareza alta del tier.
- **Textura:** original, pintada sobre el UV del Wither.

## Acertijos y bóvedas

Reusan los mecanismos de las ruinas: braseros en orden, espejos, palancas, ofrendas y el orden de piedras. Regla de diseño de Elias (26/9): divertidos y no obvios; ni aburridos, ni cliché, ni excesivamente difíciles. La pista siempre está en la sala o en la de al lado, nunca en una wiki.

## Entrada (Elias, 26/9)

**La Escalera Sellada**, en la ruina inicial. Está desde el minuto uno y el Atlas no la sabe leer.
- Se abre con el World Tier Frontier (acto III).
- La puerta pide la ofrenda del intento.
- Se puede elegir cualquier tier hasta el actual.

## Plan de implementación

1. Dimensión, parcelas, ciclo de vida y persistencia; port del generador con los invariantes como tests.
2. Plantillas:
   - yo hago el arte de Cisternas, Fundición, Geodas, El Eclipse y las salas especiales;
   - un exportador escribe los NBT con marcadores (puntos de encuentro, cofres, santuario, mecanismo de bóveda, espiral).
3. Colocación por celdas repartida entre ticks; protección y reglas del Envés.
4. Encuentros con tablas por datapack, afijos y escalado; loot tables con Apotheosis y Lootr.
5. Minimapa con niebla, más la vista grande en el Atlas; apagar JourneyMap y FTB Chunks en la dimensión.
6. Piso del jefe, pool, salida, recompensas y logro.
7. Entrada y selección de dificultad, regla de muerte, GameTests y QA de pack completo.

Los puntos 1–3 y 5 son un worker; el 4 y el 6, otro, en paralelo, sobre la misma interfaz de marcadores. Hechos (26/9): 1, 2 (el exportador y los marcadores; falta el arte de los otros tilesets), 3, 5 y 7. El 27/9, el 4 y el 6 en su primera iteración ([Contenido construido](#contenido-construido-279)); falta el arte de los tilesets II–V y la QA del pack completo sobre ellos.

## Contenido propuesto (27/9, para que Elias lo dirija)

Los pisos ya tienen tema. Los enemigos salen de mods que ya están en el pack (IDs verificados en los JAR fijados) y cada uno aparece como **eco**: condensado por la luz agria, con nombre propio, aura pálida y afijos.

| Piso | Ambiente | Escoltas | Élites | Campeón de la escalera | Idea de combate |
|---|---|---|---|---|---|
| I · Osarios | toba, calcita, nichos de huesos | `minecraft:stray`, `minecraft:skeleton` con equipo | `cataclysm:draugr`, `cataclysm:elite_draugr` | `cataclysm:royal_draugr` | Aprender a pelear en salas chicas: escudos, arqueros detrás |
| II · Cisternas | canales de agua, caños de cobre, pasarelas | `minecraft:drowned` con tridente | `cataclysm:deepling_brute`, `deepling_angler`, `deepling_priest` | `cataclysm:coral_golem` | Agua que te frena y enemigos que la aprovechan |
| III · Fundición | piedra negra, canales de lava tras rejas, cadenas | `minecraft:blaze`, `minecraft:wither_skeleton` | `cataclysm:ignited_revenant`, `cataclysm:ignited_berserker` | `cataclysm:the_prowler` | Fuego y embestidas: resistencia al fuego obligatoria |
| IV · Geodas | amatista, calcita, cristales que brillan | `minecraft:vex` | `cataclysm:amethyst_crab`, `cataclysm:the_watcher` | `cataclysm:ender_golem` | Enemigos que teletransportan y proyectiles de cristal |
| V · El Eclipse | obsidiana, obsidiana llorosa, oro, un sol oscuro | ecos menores del jefe | — | — | El Wither blanco: embestida telegrafiada y calaveras |

- **Tesoro que camina:** un `mowziesmobs:grottol` (el topo que come cristales) aparece a veces en las Geodas y huye. Si lo agarrás, suelta gemas.
- **Afijos de élite** (1 en el piso I, hasta 3 en el IV):

  | Afijo | Efecto |
  |---|---|
  | Veloz | velocidad |
  | Blindado | armadura y empuje |
  | Vampírico | se cura pegando |
  | Ardiente | aura de fuego |
  | Perforante | parte del daño ignora la armadura |
  | Espectral | parpadea y reaparece a tu espalda |

- **Santuarios** (un bendición temporal hasta el final del piso):

  | Bendición | Efecto |
  |---|---|
  | Fervor | + daño |
  | Refugio | + resistencia |
  | Presteza | velocidad y prisa |
  | Claridad | revela el mapa del piso entero |
  | Fortuna | + rareza del loot del piso |

- **Sellos**, con tres variantes para que no sean todos iguales:
  - vencer al guardián que lo custodia;
  - sostener el círculo 20 segundos mientras llegan ecos;
  - un acertijo chico de la familia de las ruinas: braseros, espejos o palancas.
- **Loot:** piezas con afijos de Apotheosis por tier y piso, y gemas. Además, **esquirlas de luz agria** como moneda del Envés, para decidir en qué se gastan (los curios únicos del jefe, por ejemplo).

## Decisiones de Elias (27/9)

- **Primera iteración completa** con el contenido propuesto: pisos, ecos, afijos, santuarios, sellos, bóvedas, loot y el Wither blanco. Elias dirige recién sobre algo tangible, así que se construye entero y se le muestra.
- **Ofrenda:** pasa de un bloque de netherite a **una estrella del Nether** por intento, o, en su lugar, **una cantidad de esquirlas de luz agria**. La cantidad va en `config.json` y se calibra para que la estrella sea sobre todo el pago de las primeras bajadas: una bajada completa deja más esquirlas de las que pide la puerta.
- **Esquirlas de luz agria:** la moneda del Envés. Más adelante se van a canjear en Solsticio, así que está bien que sobren. El canje no entra en esta iteración.
- **Nombres provisorios:** el Envés y el Wither blanco (EN «the Envés», «White Wither»). Los curios únicos del jefe quedan fuera de esta iteración.

## Contenido construido (27/9)

La primera iteración entera del contenido propuesto, en la rama `feature/enves-content`. Todo se enchufa en los ganchos del motor (`EnvesContent.install()`); los números viven en datos que un datapack puede reemplazar:

| Archivo | Qué tiene |
|---|---|
| `data/entrelumen/enves/balance.json` (`EnvesBalance`) | escalado por World Tier y por piso, base de cada rol, afijos por piso, rareza y pureza del loot, sellos, acertijos y jefe |
| `data/entrelumen/enves/encounters/<tileset>.json` (`EnvesEchoTables`) | los ecos de cada piso: escoltas, élites, campeón y tesoro |
| `data/entrelumen/loot_table/enves/*.json` | cofres (`room`, `vault`, `boss`) y caídas de ecos (`echo_elite`, `echo_guardian`, `echo_champion`, `grottol`) |
| `data/entrelumen/enves/config.json` | la ofrenda: estrella del Nether o esquirlas |

Un archivo que no valida deja el anterior y lo dice en el log. `balance.json` y `EnvesBalance.DEFAULTS` dicen lo mismo, y un test lo comprueba.

### Ecos

Un eco es un mob del pack condensado por la luz agria (`EnvesEchoes`):
- **Nombre:** «Eco de Draugr», y en los élites sus afijos: «Eco de Draugr — Veloz, Vampírico». El color cambia por rol: gris claro las escoltas, amarillo agrio los élites, verde agua los guardianes y oro los campeones. Los élites, guardianes y campeones muestran el nombre siempre.
- **Aura pálida:** partículas del color de la luz agria dos veces por segundo cerca de un jugador, y una chispa extra en los que tienen afijos.
- **Escalado:** la vida base del eco (su entrada en la tabla, o la del rol) por el multiplicador del tier del intento y el del piso. Todo el daño que hace (golpes, proyectiles, hechizos, AoE) se multiplica por su factor: el de la entrada o el del rol, por el del tier y el del piso. Además suma la armadura del tier.
- **El tier del intento manda, no el del jugador:** a los ecos no les llegan los aumentos de tier de Apotheosis (se marca `apotheosis:tier_augments_applied`) ni el `FinalizeSpawnEvent`, así que ningún mod los convierte en sus élites o invasores. Un jugador de Pinnacle que farmea un intento de Frontier pelea ecos de Frontier.
- **Sin basura:** las caídas propias del mob y su equipo se descartan. Suelta sólo la tabla de su rol: las escoltas, nada (sólo experiencia); los élites, guardianes y campeones, esquirlas y algo más.
- Persisten, no juntan cosas del piso y no se lastiman entre ellos. Un eco cuyo intento terminó o ya no existe se descarta solo (uno que un borrado no alcanzó a ver, o que vuelve del disco después).

**Por piso** (IDs verificados en los JAR fijados: `ModEntities` de L_Ender's Cataclysm 3.33 y el registro de Mowzie's Mobs 1.8.2). Vida base a Frontier en el piso I, antes del multiplicador del piso; «daño ×» multiplica el daño propio del mob. El respaldo vanilla sólo se usa si el mod falta:

| Piso | Escoltas (peso) | Élites: vida, daño ×, peso, respaldo | Campeón: vida, daño × (respaldo: wither skeleton) |
|---|---|---|---|
| I · Osarios | esqueleto con arco y casco de malla (3), stray con arco (2) | `cataclysm:draugr` 60, ×1,2, 3, zombie; `elite_draugr` 75, ×1,15, 2, husk | `cataclysm:royal_draugr` 180, ×1,4 |
| II · Cisternas | drowned con tridente | `deepling_brute` 85, ×1,1, 3, zombie; `deepling_angler` 60, ×1,1, 2, drowned; `deepling_priest` 55, ×1,2, 2, bruja | `cataclysm:coral_golem` 220, ×1,1 |
| III · Fundición | wither skeleton (3), blaze (2) | `ignited_berserker` 80, ×1,0, 3, vindicador; `ignited_revenant` 90, ×1,0, 2, blaze | `cataclysm:the_prowler` 240, ×0,9 |
| IV · Geodas | vex | `the_watcher` 50, ×1,2, 3, vex; `amethyst_crab` 110, ×0,8, 2, zombie | `cataclysm:ender_golem` 260, ×1,0 |
| V · El Eclipse | «Eco menor»: wither skeleton con espada de piedra | — | — (el Wither blanco) |

El tesoro de las Geodas es `mowziesmobs:grottol`: aparece en el 20% de las salas de combate del piso IV, huye y se entierra en el piso (la losa de las Geodas es basalto liso, que puede cavar). Sólo lo lastima un pico que rompa diamante; si lo agarrás, suelta dos gemas y 2–4 esquirlas. Al aparecer avisa: «Un grottol sale del cristal y huye. Sólo un pico le quiebra el caparazón.»

### Números de combate

| World Tier | Vida × | Daño × | Armadura + | Rareza del loot en el piso I (común, poco común, rara, épica, mítica) | Techo | Pureza base |
|---|---|---|---|---|---|---|
| Haven | 0,75 | 0,75 | 0 | 60, 36, 4, 0, 0 | rara | `chipped` |
| Frontier | 1,0 | 1,0 | 2 | 29, 60, 10, 1, 0 | épica | `chipped` |
| Ascent | 1,6 | 1,35 | 4 | 10, 30, 50, 10, 0 | épica | `flawed` |
| Summit | 2,5 | 1,8 | 8 | 0, 12, 29, 54, 5 | mítica | `normal` |
| Pinnacle | 3,8 | 2,4 | 12 | 0, 0, 10, 65, 25 | mítica | `flawless` |

Los pesos de rareza son los propios de Apotheosis para cada tier. El techo nunca se pasa, y es lo que da el cofre del jefe. La pureza sube por pasos (`cracked`, `chipped`, `flawed`, `normal`, `flawless`, `perfect`), hasta `perfect`.

| Piso | Vida × | Daño × | Chance de subir una rareza | Pureza + |
|---|---|---|---|---|
| I | 1,0 | 1,0 | 0 | 0 |
| II | 1,15 | 1,08 | 25% | 0 |
| III | 1,3 | 1,16 | 50% | +1 |
| IV | 1,5 | 1,25 | 75% | +1 |
| V | 1,7 | 1,35 | 100% | +2 |

| Rol | Vida base | Daño × | Experiencia | Afijos |
|---|---|---|---|---|
| Escolta | 24 | 1,0 | 6 | 0 |
| Élite | 70 (las tablas dan la suya) | 1,15 | 20 | I: 1 · II: 1–2 · III: 2 · IV: 2–3 |
| Guardián de sello | 110 | 1,25 | 35 | los del élite + 1, hasta 3 |
| Campeón | 200 (las tablas dan la suya) | 1,35 | 60 | 3 |
| Wither blanco | 600 | 1,0 | 300 | — |

La experiencia se multiplica por la vida × del tier. Ejemplos:
- Un élite de Frontier tiene entre 55 y 110 de vida en el piso I y 399 en el IV a Pinnacle.
- El Royal Draugr de Frontier tiene 180 de vida y pega 7 por golpe; a Pinnacle, 684 y 17.
- El Prowler del piso III tiene 312 de vida a Frontier y pega 15; a Pinnacle, 1.186 y 35.
- El Ender Golem del piso IV tiene 390 de vida a Frontier y 1.482 a Pinnacle.

### Afijos de élite

| Afijo | Qué hace |
|---|---|
| Veloz | +40% de velocidad (y de vuelo, en los que vuelan) |
| Blindado | +8 de armadura, +4 de dureza y +0,6 de resistencia al empuje |
| Vampírico | se cura el 35% del daño que hace |
| Ardiente | inmune al fuego; cada segundo prende fuego 3 s a todo jugador a 3 bloques, y sus golpes también prenden. La resistencia al fuego lo anula |
| Perforante | la armadura frena la mitad de lo que frenaría de sus golpes |
| Espectral | cada 5–8 s, si su blanco está a más de 2,5 bloques, elige un lugar libre unos 2 bloques detrás de él en el mismo piso; ese lugar sisea y brilla 0,6 s, y reaparece ahí |

Los afijos no se repiten en un eco. Los números están en `EnvesAffix`.

### Encuentros

- **Sala de combate:** la primera vez que alguien del grupo entra, se llenan sus puntos marcados, empezando por los más lejanos a quien entró: un élite y una escolta (45%), un élite y dos escoltas (30%) o dos élites (25%).
- **El piso V** no tiene salas de combate: su antesala (la guardia) trae tres ecos menores, y la arena, el jefe.
- **Guardia y campeón de la escalera:** el campeón, con tres afijos, en el centro de la guardia, y dos escoltas en las esquinas (tres desde el piso III). **La escalera no abre hasta que cae**, aunque ardan todos los sellos. Si todos arden y él sigue en pie, el grupo recibe un aviso. Nunca aparece dos veces: si desaparece sin morir (un comando, otro mod), `/entrelumen admin enves seals` lo dispensa. En un intento anterior al contenido, con la guardia ya pisada, aparece al entrar. Si ni el campeón ni su respaldo existen en el pack, la escalera no lo espera.
- Todo sale de la semilla del intento: un reinicio arma lo mismo.

### Loot

Entradas propias, resueltas en el momento: la de Apotheosis por reflexión, y sin Apotheosis equipo encantado vanilla, esmeraldas y materiales vanilla, así que las tablas cargan igual. El tier y el piso salen de dónde se tira el loot: el cofre o el eco que cayó. Por eso el loot de un intento de Frontier es de Frontier aunque lo abra alguien de Pinnacle.
- `entrelumen:enves_gear` es una pieza con afijos de Apotheosis. La rareza es la del tier, sube un paso con la chance del piso (y con la de Fortuna, 60%) y suma `rarity_bonus` pasos, sin pasar el techo del tier; con `top`, es el techo. La pieza base la elige Apotheosis entre las del tier.
- `entrelumen:enves_gem` es una gema de Apotheosis cuya pureza sale del tier, el piso y `purity_bonus`, nunca menor que la mínima de la gema.
- `entrelumen:enves_material` es el material de rescate de una rareza tirada igual.
- `entrelumen:enves_floor_bonus` suma `per_floor` × (piso − 1) a la pila, con la fracción al azar.
- `entrelumen:enves_floor` es una condición: pisos `min`..`max`.

| Tabla | Qué da |
|---|---|
| `enves/room` (25% de las salas quietas con marcador, Lootr) | 60% una pieza; 1–2 esquirlas + 0,35 por piso; 20% una gema; 30% un material; 50% un consumible (manzana dorada, curación II, resistencia al fuego larga, frascos de experiencia, flechas) |
| `enves/vault` (Lootr) | dos piezas una rareza más arriba; 3–5 esquirlas + 0,5 por piso; una gema de pureza +1; un material +1; 75% un consumible |
| `enves/boss` (Lootr, aparece al caer el jefe) | tres piezas de la rareza techo del tier; 12–16 esquirlas; dos gemas de pureza +1; dos materiales +2 |
| `enves/echo_elite` | 1–2 esquirlas + 0,25 por piso; 8% una gema; 10% un material |
| `enves/echo_guardian` | 2–3 esquirlas + 0,25 por piso; 35% una gema; 25% un material |
| `enves/echo_champion` | 3–4 esquirlas + 1 por piso; una pieza una rareza más arriba; 50% una gema; 50% un material |
| `enves/grottol` | dos gemas; 2–4 esquirlas |

Los cofres son de Lootr, uno por jugador. El del jefe no está hasta que el Wither blanco cae, así nadie lo saquea pasándole por al lado.

### Esquirlas de luz agria y la ofrenda

- `entrelumen:sour_light_shard` («Esquirla de luz agria»): la moneda del Envés, apilable a 64 y de rareza poco común. Tooltip: «Luz que se agrió en la fusión fallida, condensada por el Envés.» y «La moneda del Envés: su puerta acepta esquirlas en lugar de una estrella del Nether.» El arte es el del 16×16 de `art/build_art.py`.
- **La ofrenda:** una estrella del Nether **o 64 esquirlas**. La pantalla de la puerta muestra las dos; el jugador elige cuál paga, empezando por la primera que lleva, y el servidor lo vuelve a juzgar.
- **Calibración** (`EnvesContentDataTest`, 200 descensos, con el grupo pasando por el 70% de las salas y resolviendo tres de cada cuatro bóvedas):

| Hasta | Esquirlas esperadas |
|---|---|
| El piso I | 19 |
| Los pisos I–III | 80 |
| El descenso completo, con el jefe | 141 |

Morir en el piso I no paga la próxima puerta. Llegar a la Fundición la paga. Un descenso completo deja más del doble, y el sobrante es para el canje futuro en Solsticio. La estrella queda como el precio de los primeros descensos. En grupo, cada jugador tiene su propio loot de los cofres de Lootr, así que las esquirlas de cofre se multiplican; las de ecos se comparten.

### Santuarios

El santuario del piso tiene un altar (`entrelumen:enves_shrine`, provisorio sobre el ancla de reaparición) con una bendición que sale de la semilla. El primer toque bendice a todo el grupo hasta el final del piso, y el altar se apaga:

| Bendición | Efecto, mientras estés en ese piso |
|---|---|
| Fervor | +25% a todo el daño que hacés |
| Refugio | 25% menos de todo el daño que recibís |
| Presteza | Velocidad II y Prisa II |
| Claridad | el mapa entero del piso, escalera y sellos incluidos |
| Fortuna | el loot del piso sube una rareza más con 60% de chance |

Las partículas del altar tienen el color de su bendición, así se adivina antes de tocarlo. El aviso al grupo dice qué hace.

### Sellos

Las tres variantes se reparten barajadas por piso: dos sellos nunca comparten variante y tres muestran las tres. Un operador con `/entrelumen admin enves seals` las saltea.
- **Guardián:** al pisar la sala, un eco guardián (de los élites del piso, con un afijo más, hasta 3) se para entre la puerta y el sello. El sello no prende hasta que cae; después se prende con un toque.
- **Círculo:** tocar el sello lo despierta. Hay que tener a alguien del grupo a 4,5 bloques del sello durante 20 s.
  - La barra de jefe «Sosteniendo el sello» muestra el avance.
  - El anillo de partículas brilla mientras alguien lo sostiene.
  - Llegan tres oleadas por las bahías de la sala: dos escoltas, un élite y dos escoltas (una escolta más desde el piso III).
  - Vacío, el avance baja el doble de rápido de lo que sube; vacío 15 s, se enfría y hay que volver a tocar el sello. Las oleadas que ya llegaron no se repiten.
- **Acertijo chico:** la sala es del sello, así que se arma sobre sus pedestales:
  - **Braseros:** cuatro braseros en los pedestales diagonales. Al entrar, y cada vez que tocás el sello, el sello los hace destellar en un orden, cada uno con su nota. Hay que repetirlo: 3 toques en el piso I, 4 en el II y 5 en el III y el IV. Un error los apaga.
  - **Palancas:** cuatro palancas en los pedestales de los ejes y cuatro lámparas en los diagonales. Cada palanca invierte algunas lámparas; se prenden las cuatro. Nunca se resuelve con una sola palanca.
  - **Espejos:** una fuente de luz agria y espejos de dos posiciones sobre una grilla de 5 × 5 alrededor del sello, con 2 vueltas en los pisos I–II y 3 en los III–IV, más dos o tres espejos que no llevan a nada. La luz se ve en partículas mientras rebota; cuando llega al sello, se prende.

  Si otro tileset no deja lugar para el acertijo sorteado, se prueba el siguiente; si no entra ninguno, el sello pasa a ser de círculo.

### Bóvedas

La puerta de 3 × 4 de la bóveda es la cerradura misma:
- la fila de abajo tiene las piezas;
- las del medio son barrotes, para ver adentro;
- la de arriba es el dintel o las lámparas.

Lo que pide está detrás de los barrotes o en el umbral de enfrente. Al resolverla, `EnvesPlacer.openVault` la abre entera.

| Cerradura | Peso | Cómo es |
|---|---|---|
| Piedras de glifo | 3 | Detrás de los barrotes, sobre pedestales de calcita, tres glifos (las caras cinceladas del cuarzo, la arenisca roja, la toba y la pizarra profunda) dicen un código. Las tres piedras de la puerta giran con un clic y abren cuando lo dicen **al revés**. Copiarlo tal cual no abre, y la cerradura avisa: «El Envés copia todo al revés». Nunca es un palíndromo. |
| Braseros | 3 | La puerta recuerda un orden y lo toca al acercarte y cada vez que tocás los barrotes. Hay que prender los tres braseros en ese orden: 3, 4, 5 y 5 toques por piso. |
| Palancas | 3 | Seis palancas en las paredes del umbral y tres lámparas en el dintel. Cada palanca invierte una o dos lámparas; hay que prender las tres, y nunca alcanza con una sola. |
| Ofrenda | 1 | Un brasero frío pide 3, 4, 5 o 6 esquirlas según el piso. Paga con la moneda del Envés; la bóveda paga más. |

Los espejos se quedaron en los sellos: el umbral de una bóveda no tiene lugar para una grilla de luz. Si otro tileset no deja lugar para los glifos, la cerradura pasa a braseros; si faltan paredes para las palancas, a glifos.

### El Wither blanco

`entrelumen:white_wither`, provisorio que queda en la v1.0:
- **Cuerpo y textura:** es el cuerpo del Wither vanilla con la textura de marfil y oro de `art/authoring/draw_white_wither.py`. El brillo se dibuja a plena luz y late durante el aviso de la embestida. Las calaveras (`entrelumen:sour_skull`) usan el mismo marfil.
- **Aparición:** se condensa al pisar el centro de la arena del piso V. Pasa 3 s sin recibir daño mientras sube, con la barra de jefe blanca con muescas «Wither blanco».
- **Casi no vuela:** flota a 2,5 bloques del piso y se desliza (0,16 bloques por tick) alrededor de su blanco a unos 9 bloques, sin alejarse más de 24 del centro de la arena.
- **Calaveras:** tres, una por cabeza, cada 2 s (1,3 s bajo la mitad de la vida). Hacen 8 × el factor del tier y el piso y marchitan 4 s. No rompen nada: ni bloques ni lo que un proyectil pueda romper.
- **Embestida muy telegrafiada**, cada 8 s (5,6 s bajo la mitad de la vida) si su blanco está a 5–26 bloques y lo ve:
  - baja a 1,2 bloques, tiembla, zumba y marca en el piso con polvo dorado la línea que va a barrer, cortada donde la pare una pared, durante 1,8 s;
  - la barre a 21 bloques por segundo, golpea una vez a cada jugador de la línea con 14 × el factor y lo tira de costado;
  - queda expuesto en el piso 3,5 s, recibiendo un 50% más de daño.
- **Bajo la mitad de la vida** llama una vez a dos ecos menores en los bordes de la arena.
- **Sin grifeo:** su IA propia nunca rompe bloques y no tiene el nacimiento explosivo del vanilla. No tiene la armadura del vanilla, así que las flechas siempre cuentan. No suelta la estrella del Nether.
- **Al caer** llama a `Enves.bossDefeated`: se despierta el portal de victoria y aparece el cofre del jefe de Lootr (`entrelumen:enves/boss`) en el marcador del portal. Si desaparece sin morir, vuelve cuando alguien del grupo pisa la arena y el centro lleva tres segundos cargado sin él: un chunk recién cargado muestra sus entidades un momento después, y así nunca hay dos jefes.

| Tier | Vida | Calavera | Embestida |
|---|---|---|---|
| Haven | 765 | 8,1 | 14,2 |
| Frontier | 1.020 | 10,8 | 18,9 |
| Ascent | 1.632 | 14,6 | 25,5 |
| Summit | 2.550 | 19,4 | 34,0 |
| Pinnacle | 3.876 | 25,9 | 45,4 |

(daño antes de armadura)

### Cómo afinar

- **Dureza:** `tiers.*.health|damage|armor` para toda una dificultad, `floors[i]` para un piso, `roles.*` para un tipo de eco y `health`/`damage` de una entrada de tabla para un mob.
- **Afijos:** `affixes.elite` da `[mín, máx]` por piso, del I al V; `affixes.champion` fija los del campeón.
- **Loot:**
  - `tiers.*.rarity` y `top` para las rarezas;
  - `floors[i].rarity_step` y `purity` para lo que suma cada piso;
  - `loot.fortune_step` para Fortuna;
  - las tablas `loot_table/enves/*` para las cantidades.
- **La ofrenda:** `offering` de `config.json`. Si cambian las cantidades de esquirlas, `EnvesContentDataTest` vuelve a medir la calibración y falla si un descenso completo deja menos de 1,5 veces la puerta o más de 3,5, si el piso I la paga o si los pisos I–III no la pagan.
- **Sellos y acertijos:** `seals.circle_seconds|circle_radius|circle_grace_seconds`; `puzzles.echo_length|mirror_turns|offering_shards` por piso.
- **El jefe:** `boss.skull_damage|charge_damage|telegraph_ticks|exposed_ticks|exposed_bonus|hover|charge_cooldown`. El aviso nunca baja de 10 ticks.
- **Enemigos:** `encounters/<tileset>.json`; un ID que falta usa su `fallback`, y sin respaldo el eco se saltea.

### Pruebas

- **JUnit:**
  - `EnvesBalanceTest`: escalado, afijos por piso, rareza con techo, pureza, bonus por piso, validación, y que `balance.json` coincida con el código;
  - `EnvesPuzzleRulesTest`: variantes de sellos, cerraduras y bendiciones; braseros; glifos al revés; palancas que piden dos o más; espejos resolubles que nunca empiezan resueltos; ofrendas;
  - `EnvesContentDataTest`: tablas de ecos contra la propuesta con IDs verificados y respaldos, tablas de loot, calibración de la ofrenda, y las claves EN/ES que el código usa, con sus placeholders;
  - `EnvesContractTest`: la ofrenda nueva y sus rechazos.
- **GameTests aislados** (`RuntimeGameTestsEnvesContent`, sin los mods del pack, con respaldos vanilla):
  - una sala llena de ecos escalados que sueltan sólo lo suyo;
  - cada afijo;
  - cada bendición y que se apague fuera de su piso (Fervor y Refugio contra el mismo golpe sin bendición, así los modificadores propios del pack quedan de los dos lados);
  - los tres sellos del piso III;
  - las cuatro cerraduras, incluido que copiar los glifos tal cual no abre;
  - el campeón que cierra la escalera;
  - las dos ofrendas;
  - que las siete tablas carguen y paguen;
  - el Wither blanco sin grifeo, flotando bajo, con la embestida avisada, golpeando y expuesto;
  - su aparición en la arena, el portal y el cofre al caer;
  - que un intento terminado no deje loot ni ecos en su parcela: el cofre de la bóveda se va sin tirar lo suyo y los ecos, vivos o caídos, con él.

  Los casos del motor (`RuntimeGameTestsEnves`) corren con los ganchos simples (`@BeforeBatch`). Los jugadores de prueba no tienen cliente: sus chunks se cargan a mano (`arrived`) y la armadura que se ponen aplica sus modificadores a mano (`wear`), porque el tick de entidad viva de un jugador lo dispara la conexión.
- **GameTests de pack completo** (`EnvesContentFullpackGameTests`):
  - cada eco de las tablas es un mob vivo de su mod;
  - la sala del piso I trae draugr de Cataclysm sin los aumentos de Apotheosis;
  - el cofre del jefe a Frontier da tres piezas épicas y dos gemas de Apotheosis;
  - el cofre que aparece al caer el jefe es de Lootr.
- **Corrida del 27/9** sobre `main` 6bef925 (con los tilesets II–V):
  - GameTests aislados: los 132 del mod pasan, los del contenido incluidos;
  - QA de pack completo (274 JAR de servidor, `-Xmx4G`): pasan los 20 casos del Envés, los del contenido corridos con los mobs y el loot reales y los del motor, sin watchdog. Fervor se midió primero contra un golpe fijo y falló: el pack le saca a un golpe de jugador contra una oveja un 29% por su cuenta, así que ahora se compara contra el mismo golpe sin bendición;
  - al terminar, ni el mundo de los GameTests ni el del QA tienen ítems sueltos en el Envés (antes del arreglo del borrado, cada parcela reutilizada guardaba el loot de los cofres del intento anterior).

### Para una segunda pasada

- Ver en el juego lo que ningún test ve:
  - cómo se sienten los números y los afijos;
  - si los modelos provisorios (brasero, espejo, piedras de glifo, altar) se leen bien;
  - la lectura del aviso de la embestida;
  - si los mobs de Cataclysm con animación de muerte sueltan algo por fuera del evento de caídas.
- Arte propio del altar, el brasero y el espejo, y un modelo propio del jefe (hoy es el Wither vanilla repintado).
- Un curio único del jefe (fuera de esta iteración) y el canje de esquirlas en Solsticio.
- Los tilesets II–V ya tienen plantillas propias (rama `feature/enves-tilesets`, en `main`). Con ellas pasaron los GameTests de los sellos del piso III (Fundición) y del jefe en la arena del V (El Eclipse); las cerraduras se probaron en el piso I. Las cerraduras y acertijos revisan el lugar y cambian de tipo si el arte no los deja: falta mirarlos en juego en II y IV.

## Para decidir

- El nombre del descenso y el del jefe.
- Si hay curios únicos del jefe.
- La bolsa se cuenta con los integrantes conectados al pagar; los que entran después no suman caídas. ¿O cada integrante suma 3 al entrar por primera vez?
- El corazón del sol: se abren las ocho celdas de afuera y quedan el centro y el pedestal, porque el pedestal es la única fuente de brújulas de los que llegan tarde (docs/design/heliodor-compass.md). ¿Otro lugar para el pedestal?
- La escalera de la ruina inicial (arte de `sealed_stair()`) se baja caminando; para subir hay que saltar en las esquinas del anillo de 3 × 3. Su pie no llevaba a la antecámara: se le abrió una puerta y un piso.
- Los fosos de huesos del arte bajan un bloque bajo la losa, fuera de la plantilla de 12: hoy su fondo es la roca de abajo o el techo del piso siguiente.
