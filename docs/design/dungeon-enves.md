# El Envés: el descenso generativo

Plano del controlador, 26 de septiembre de 2026. Pedido de Elias:

- una dungeon generativa como las de Diablo 2/3 o la de *The Other* en ATM10;
- pisos indestructibles y escaleras que hay que encontrar;
- un mapa que se va revelando;
- 4 o 5 niveles con un jefe al final;
- loot que escala con la dificultad y que obliga a equiparse: nada de «refined obsidian y listo»;
- pocos enemigos fuertes y divertidos, nada de tormentas de entidades ni spawners, y nada de basura en el inventario.

**Estado (26/9).** El motor está hecho: la dimensión, el intento con su ofrenda y su bolsa de caídas, el generador en Java, las plantillas de Osarios con marcadores, la colocación por ticks, las reglas y el mapa con niebla ([El motor](#el-motor)). Falta lo del segundo worker: encuentros, afijos, loot, santuarios, acertijos de bóveda y el jefe, que se enchufan en los ganchos del motor. Cisternas, Fundición, Geodas y El Eclipse usan por ahora las salas de Osarios con una paleta provisoria.

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
- Al terminar un intento se borra su parcela, bloques y entidades, en franjas de capas por tick, y queda libre.

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

- `data/entrelumen/enves/config.json`: `offering` (`minecraft:netherite_block` × 1), `falls_per_member` (3), `abandon_minutes` (10), `stair_seal_block`, `vault_gate_block`, `room_chest_chance` (0,25), `chest_loot_table` (`entrelumen:enves/{kind}`; también acepta `{tier}` y `{floor}`), `denied_commands`, `tilesets` (uno por piso) y `ftb_chunks_map_stage`. Un datapack lo reemplaza; si no valida, sigue el anterior.
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
- `Seals.mayLight` y `lit`;
- `Boss.floorReady`; el jefe llama a `Enves.bossDefeated` al caer;
- `Lifecycle`: intento abierto, piso listo, caída y fin con su motivo.

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
  - la puerta se abre con una ofrenda de **1 bloque de netherita**, y cada ofrenda es un intento;
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

Los puntos 1–3 y 5 son un worker; el 4 y el 6, otro, en paralelo, sobre la misma interfaz de marcadores. Hechos (26/9): 1, 2 (el exportador y los marcadores; falta el arte de los otros tilesets), 3, 5 y 7. Pendientes: 4 y 6.

## Para decidir

- El nombre del descenso y el del jefe.
- Si hay curios únicos del jefe.
- La bolsa se cuenta con los integrantes conectados al pagar; los que entran después no suman caídas. ¿O cada integrante suma 3 al entrar por primera vez?
- El corazón del sol: se abren las ocho celdas de afuera y quedan el centro y el pedestal, porque el pedestal es la única fuente de brújulas de los que llegan tarde (docs/design/heliodor-compass.md). ¿Otro lugar para el pedestal?
- La escalera de la ruina inicial (arte de `sealed_stair()`) se baja caminando; para subir hay que saltar en las esquinas del anillo de 3 × 3. Su pie no llevaba a la antecámara: se le abrió una puerta y un piso.
- Los fosos de huesos del arte bajan un bloque bajo la losa, fuera de la plantilla de 12: hoy su fondo es la roca de abajo o el techo del piso siguiente.
