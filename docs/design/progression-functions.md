# Progresión: Marco por infusión, funciones y resonador de vetas

Lote del 24 de septiembre de 2026, rama `feature/progression`. Aplica las «Respuestas de Elias (24 de septiembre)» de [reference-packs](../research/reference-packs.md): el Marco de Calibración sale de la historia y se copia en el infusor metalúrgico, Ultimine pasa a ser un curio propio de seis tiers, el MekaSuit y las armaduras cuánticas quedan en el acto VI y tres drops de jefes entran en los módulos del Arca. Suma los gates de la propuesta que encajan con esas respuestas, en una sola familia, «funciones».

## Marco de Calibración

**Sin receta de mesa.** La receta de mesa vieja (dos chapas de hierro de Create, dos alambres de cobre de IE y una lente en bruto) ya no existe. `entrelumen:integration/precision_bench` es ahora una infusión metalúrgica nativa de Mekanism 10.7:

| Entrada | Infusión | Salida | Máquina |
|---|---|---|---|
| 1 `entrelumen:raw_lens` (2 vidrios y 1 cobre en la mesa) | 40 de `mekanism:redstone` (4 polvos de redstone) | 1 Marco | infusor metalúrgico o fábrica de infusión |

La lente es el hilo de la historia (lente en bruto → Marco) y la redstone es la infusión más barata del infusor: cuesta lo mismo que la receta vieja y se automatiza sola.

**El primer Marco sale de la historia.** Primera señal (`first_signal`), el cierre del acto I, entrega ahora **dos Marcos** además del núcleo de señal. Usa el mismo mecanismo de recompensa de proyectos que entrega el Atlas, la lente y los altares: una vez por campaña, al completar la entrega. El campo nuevo `extraRewards` de `projects.json` lo admite (ver `companion/INTEGRATION.md`).

- **Por qué dos.** Uno arma el infusor. El otro es de repuesto: Banco de precisión (`precision_bench`, acto II) consume un Marco, y en el acto II también piden uno la matriz viva, la placa solar básica de New Age, la estantería del cartógrafo, las macetas con tolva y el portal minero de JAMD. Con uno solo, entregarlo o gastarlo dejaba al equipo sin infusor y sin forma de hacer más.
- **Por qué ahí.** Primera señal abre el acto II, el del taller. El Marco llega justo cuando el Atlas manda al Taller hundido.
- **Guía.** El texto de la quest de Primera señal dice para qué es cada ítem. El de Banco de precisión pide armar el infusor con un Marco e infundir la copia que se entrega. El Marco tiene una línea de tooltip: «La medida de Terra. Un infusor metalúrgico lo copia con una lente en bruto y redstone».

**El infusor pide un Marco.** La receta nativa (`I#I / ROR / I#I`: hierro, horno, redstone, osmio) lleva el Marco en el centro, en lugar del osmio: `I#I / RMR / I#I`. El Marco es el núcleo calibrado de la máquina y el dibujo sigue simétrico (regla 3 del playtest, abajo). El infusor es la entrada a Mekanism: circuitos básicos y todas las aleaciones salen de ahí.

**Ciclos y trabas.** El infusor es la única puerta que usa un componente que su propia adquisición necesita: el Marco se hace en el infusor y el infusor pide un Marco. Es un arranque deliberado, que funciona porque la historia da los primeros.

- `tools/generate_family_balance.py` rechaza cualquier gate cuyo resultado esté en la clausura de insumos de su componente o en la lista «no gatear» de la propuesta (`PROTECTED`). El infusor está en esa lista y pasa sólo como excepción declarada (`BOOTSTRAP`), y únicamente mientras `projects.json` entregue al menos dos Marcos.
- `tools/generate_integration_recipes.py --check` también exige esos dos Marcos, y que el Marco sea la única receta infundida.
- Los demás usos del Marco no cierran ciclos: el Regulador de Energía (III) pide circuitos básicos y el Bus del Arca (V) aleación atómica, que salen del infusor ya construido. Ninguna receta que sirve para fabricar el Marco o el infusor pide un Marco.
- **Traba posible:** un equipo que gaste los dos Marcos sin armar el infusor queda sin forma de hacer más. Los textos lo evitan. Si pasa, un operador puede dar uno con `/give @p entrelumen:calibration_frame`, y en multijugador otro equipo puede regalarlo (los regalos valen). No se agregó un comando propio.

## Resonador de vetas: Ultimine como curio

Seis ítems, `entrelumen:vein_resonator_1` a `_6`: «Resonador de vetas I–VI» / «Vein Resonator I–VI». Son el diapasón de Terra. Stack de 1; rareza común (I–II), poco común (III–IV), rara (V) y épica (VI, además resistente al fuego). Tooltip:

- lore gris: «El diapasón de Terra. Zumba mientras la veta sigue.» / «Terra's tuning fork. It hums for as long as the vein runs on.»;
- efecto en azul, como las líneas de atributos: «Ultimine: hasta 16 bloques» … «hasta 96 bloques».

### Cómo limita FTB Ultimine 2101.1.15

Leído del JAR fijado (`ftb-ultimine-neoforge-2101.1.15.jar`, con `javap`):

- `FTBUltimineServerConfig.getMaxBlocks(ServerPlayer)` devuelve `max(0, max_blocks + round(ftbultimine:max_blocks_modifier))`. `max_blocks` sale de `ftbultimine-server.snbt` (0 a 32 768) o, si FTB Ranks está instalado, del nodo `ftbultimine.max_blocks`. FTB Ranks no está en el pack.
- `FTBUltiminePlayerData.updateBlocks` no elige ningún bloque si el límite es 0 o menos. Sin bloques en caché, el evento de rotura deja pasar la rotura vanilla de un solo bloque. Lo mismo vale para las funciones de clic derecho (hacha, pala, azada, cosecha), que usan el mismo límite.
- También existen `FTBUltimine.setPermissionOverride(Predicate<Player>)` y el evento `RegisterRestrictionHandlerEvent` de la API. No hacen falta: el atributo alcanza, y nadie en el pack los usa.

### Implementación

- `pack/config/ftbultimine-server.snbt` fija `max_blocks: 0`. Sin resonador, Ultimine está apagado para todos.
- `VeinResonator` le pregunta a Curios, por reflexión y cada 10 ticks como el Brazo de Terra, cuál es el tier más alto puesto en un slot activo. Después ajusta un único modificador transitorio y aditivo, `entrelumen:vein_resonator`, sobre `ftbultimine:max_blocks_modifier`. Su valor es `16 × tier − max_blocks`, con `max_blocks` leído de la config de Ultimine (`UltimineCompat`, reflexión sobre miembros públicos). Así el límite queda exacto aunque un servidor suba `max_blocks` en su copia local, y queda en 0 sin resonador.
- **Slot:** `charm` de Curios. El tag `curios:charm` hace que Curios lo acepte y `data/entrelumen/curios/entities/vein_resonator.json` le asigna el slot al jugador aunque los otros diez mods que lo usan salgan del pack.
- **Dos resonadores no suman:** cuenta el mejor.
- **En un slot vanilla** (mano, inventario, armadura) no pasa nada: el ítem no tiene atributos propios.
- **Sin Curios** nadie puede ponérselo y Ultimine queda apagado, porque `max_blocks` es 0. **Sin FTB Ultimine** los resonadores son ítems sin efecto.
- **Límites conocidos:** al sacarse el resonador, el límite tarda hasta 10 ticks en volver a 0. Si alguien instala FTB Ranks con el nodo `ftbultimine.max_blocks`, ese nodo reemplaza la base y la compensación no lo lee. En ese caso hay que dejar el nodo sin definir.

### Recetas

Mesa con forma, simétricas de izquierda a derecha. Cada tier consume el anterior. El I es barato («que el de 16 no sea TAN difícil»). Después sube por acto hasta el VI, con Luminosidades sólo en el último.

| Tier | Bloques | Acto | Receta |
|---|---|---|---|
| I | 16 | I | 4 lingotes de cobre, 1 fragmento de amatista, 1 lente en bruto (`C C / CAC / _L_`) |
| II | 32 | II | Resonador I, 1 Marco de Calibración, 2 lingotes de latón de Create, 1 aleación infundida de Mekanism |
| III | 48 | III | Resonador II, 1 Regulador de Energía, 2 diamantes, 1 procesador de ingeniería de AE2 |
| IV | 64 | IV | Resonador III, 1 Lente Espectral, 2 gemas de zanita del Aether, 1 lingote de ironwood del Twilight Forest |
| V | 80 | V | Resonador IV, 1 Bus del Arca, 2 aleaciones atómicas de Mekanism, 1 lingote celeste de Nature's Aura |
| VI | 96 | VI | Resonador V, 1 Luminosidad de Exploración, 2 estrellas del Nether, 1 Luminosidad de Ingeniería |

Tiers II a VI siguen el mismo dibujo: `_T_ / SRS / _B_`. El componente del acto va arriba, el par de materiales de mod a los costados del resonador anterior y el tercer material abajo. El III lleva el Regulador porque la minería en área es una función del Regulador (Mining Gadgets, taladro infinito de Industrial Foregoing).

### Arte

Los seis íconos los dibujó el controlador (`art/authoring/draw_resonators.py`: un diapasón simétrico por tier, con la gema completa en la base). Las grillas pasaron de `resonator_<n>` a `vein_resonator_<n>` y `art/build_art.py` las registra, con procedencia, modelos y texturas en el acompañante y en el resource pack.

## MekaSuit y armaduras cuánticas: acto VI

Decisión de Elias: van en el acto VI, y el Lingote Luminoso sigue siendo el mejor equipo. El catálogo trae dos armaduras cuánticas, la de Advanced AE 1.6.12 y la de Modern Industrialization 2.5.6; se gatean las dos. **Material de gate: una Luminosidad por pieza**, que sólo se consigue en Solsticio. Cada mod toma la Luminosidad de su disciplina:

| Armadura | Receta nativa | Cambio | Luminosidad |
|---|---|---|---|
| MekaSuit (casco, pechera, pantalones, botas) | `PCP / P#P / AEA` en la mesa | la Luminosidad corona la pieza en lugar del circuito definitivo (C): `PLP / P#P / AEA` | Ingeniería: el traje del ingeniero |
| Cuántica de Advanced AE (4 piezas) | `PWP / PNP / AQA` en la mesa | la Luminosidad es el enlace, en lugar del punto de acceso inalámbrico (W): `PLP / PNP / AQA` | Logística: armadura conectada a la red ME |
| Cuántica de Modern Industrialization (4 piezas) | empaquetador: pieza de netherita + mejora cuántica | tercera entrada del empaquetador, que admite tres | Habitabilidad: un refugio que se lleva puesto |

Son 12 Luminosidades en total, 4 por disciplina. Mientras los aldeanos nativos de Solsticio no intercambien Luminosidades (lo arma el worker del acto VI), estas armaduras no se pueden fabricar, igual que el equipo luminoso y los creativos. Las unidades de vuelo del MekaSuit no se gatean: sólo sirven dentro del traje, que ya es del VI.

## Drops de jefes en el Arca (acto V)

Cada drop reemplaza una unidad de un insumo que ya estaba, así que ningún módulo pide más ítems que antes. La receta de mesa y la entrega del Atlas siguen iguales entre sí.

| Módulo | Antes | Ahora | Jefe |
|---|---|---|---|
| Arcano | 2 lentes espectrales, 2 sellos de contención, 2 lingotes de iesnium | … 1 lingote de iesnium y **1 estrella del Nether** | Wither: es la única fuente (las estrellas por implosión de MI y por partículas de Oritech ya estaban quitadas) |
| Exploración | 1 carta de horizontes, 1 lente espectral, 2 lingotes de steeleaf, 2 gemas de zanita | … 1 gema de zanita y **1 botella de aliento de dragón** | Dragón del End: se embotella durante la pelea; el módulo ya pedía la llegada al End |
| Naturaleza | 1 motor de renovación, 2 cápsulas de ecosistema, 2 matrices vivas | … 1 matriz viva y **1 esponja mojada** | Guardián anciano (la brújula del acto V apunta al monumento) |

El aliento de dragón devuelve la botella al fabricar en la mesa; la entrega del Atlas la consume entera.

## Familia «funciones»

`tools/generate_family_balance.py`, familia `functions`, genera `pack/kubejs/server_scripts/entrelumen_functions_balance.js`. Cada gate declara su función, y la tabla `FUNCTIONS` fija un componente por función. Un mod nuevo que sume una cantera hereda la puerta de las canteras. `FUNCTION_MEMBERS` enumera los gates de otras familias que pertenecen a cada función, y los tests comprueban que usen el mismo componente.

### Reglas del playtest de Elias (24 de septiembre)

El controlador pasó la devolución del primer playtest de recetas ([playtest-2026-09-24](playtest-2026-09-24.md)). Esta familia ya las cumple:

1. **Menos anidado.** Nada básico pide un componente que a su vez pide otro: los gates van en hitos (controladores, reactores, canteras, armaduras), y la entrada a Mekanism pide el Marco, que es de primer nivel.
2. **Menos abanico.** Se quedaron sólo las recetas clave que cierran una brecha o evitan que una función quede abierta por otro mod. El lote suma a lo sumo dos recetas a cada componente, y un test lo comprueba.
3. **El 3×3 es un dibujo.** Cada componente va en el centro o en el eje vertical, y la receta queda tan simétrica como era: el generador rechaza un gate que rompa la simetría. En máquinas, el componente reemplaza el núcleo (osmio del infusor, procesador del controlador, circuito del puerto). Sólo un gate dibujado puede reemplazar un ingrediente único, y la reversión se sigue comprobando exacta.

La comprobación de clausura es por ítem y no por mod: Mekanism y AE2 fabrican insumos de los componentes (circuitos, procesadores) y se gatean en su tope. Ningún gate de ninguna familia puede tocar la lista «no gatear» de la propuesta (prensa, deployer, pinza de cables, reconstructor, aparato de encantamiento, inscriptor, cámara de presión, caja UV, cristal de infusión, centrífuga, exprimidor, altares de espíritus, olla, etcétera), salvo el arranque del infusor.

### Aplicados (22 recetas más las 6 del resonador)

| Función | Componente | Acto | Receta y dibujo |
|---|---|---|---|
| Entrada a Mekanism | Marco de Calibración | II | infusor metalúrgico: el Marco en el centro (`I#I / RMR / I#I`) |
| Red de almacenamiento | Matriz de Enrutamiento | III | controlador ME de AE2: la Matriz en el centro, en lugar del procesador de ingeniería (`aba / bMb / aba`). Paridad con Refined Storage (brecha 1) |
| Teletransporte | Matriz de Enrutamiento | III | teletransportador de Mekanism: la Matriz arriba del núcleo (`CMC / XTX / CXC`). Los portátiles sólo llegan a estos bloques |
| Jetpack | Regulador de Energía | III | jetpack de Mekanism: el Regulador en la tobera (`SCS / ITI / _R_`) |
| Almacenamiento inteligente | Sensor de Inventario | III | matriz de discos QIO: el Sensor mira hacia afuera, en lugar del vidrio (`TST / C#C / TIT`). El Sensor por fin gatea algo (brecha 3) |
| Canteras | Lente Espectral | IV | Digital Miner: la Lente arriba (`ALA / SRS / TXT`); pozo dimensional de Occultism (ritual, de ahí trabajan los espíritus mineros); tarjeta de cantera de RFTools, que pasa del Regulador a la Lente (brecha 2) |
| Reactores | Sello de Contención | IV | puerto del reactor de fisión: el Sello en el corazón (`_F_ / FSF / _F_`) |
| Reactor final | Bus del Arca | V | controlador del reactor de fusión: el Bus en el eje de abajo (`CGC / FTF / FBF`) |
| Renovación | Motor de Renovación | V | puerto del SPS (antimateria): el Motor en el corazón (`_#_ / #M# / _#_`) |
| Armadura tope | una Luminosidad | VI | 12 piezas (ver arriba) |
| Minería en área | por tier | I–VI | resonadores de vetas |

### No aplicados

| Propuesta | Por qué no |
|---|---|
| Amuletos de starbuncle, wixie y drygmy de Ars Nouveau con la Matriz Viva | Regla 2 del playtest: tres recetas más para la Matriz sin cerrar ninguna brecha. |
| Drones de PneumaticCraft con el Núcleo de Manipulación | Regla 2: son cinco recetas (cada dron tiene la suya) para un solo componente. |
| Teletransportador portátil y entangloporter cuántico de Mekanism | Regla 2: el portátil sólo llega a teletransportadores, que ya están gateados; el entangloporter no estaba en la propuesta y ya pide aleación atómica (V). |
| Altar de despertar de Mystical Agriculture con el Motor de Renovación | Regla 2: el Motor ya cierra muchas recetas, y ésta no evita que otra función quede abierta. |
| Unidades de vuelo del MekaSuit (jetpack y modulador gravitatorio) con la Carta de Horizonte | La propuesta los condicionaba a que el MekaSuit no pasara al V. Pasó al VI: los módulos sólo sirven dentro del traje. |
| Armaduras tope con el Bus del Arca (V) | Reemplazado por la decisión de Elias: acto VI con Luminosidades. |
| Vitrina de trofeos en Solsticio | Elias todavía no respondió. |
| Controlador del reactor de fusión con el Motor de Renovación | La propuesta dejaba elegir: el Motor quedó para la antimateria (SPS) y la fusión va con el Bus, como el reactor nuclear de MI. |
| Otros miembros de las familias (cargadores de chunks, disassembler atómico y Meka-Tool) | No estaban en la propuesta. |

### Aumentadores de spawner (regla 4)

Cada aumentador canjea los materiales de Apotheosis (un polvo de gema y dos materiales de rareza) por el componente de acto, que ya llevaba: queda una medalla de cobre simétrica, `_K_ / SOS / _S_`, con el componente arriba, el ítem original de Apothic Spawners en el centro y tres láminas de cobre. Pasa de 9 ítems a 5 y el acto lo sigue marcando el componente. Se actualizaron [apotheosis-family](apotheosis-family.md) y las tres guías que describían la receta vieja.

### Para la auditoría de recetas

Lo que este lote tocó y todavía choca con el playtest, porque arreglarlo no era barato acá:

- **Recetas sin forma.** Las 21 recetas de componentes y módulos del Arca (`generate_integration_recipes.py`) siguen sin forma, incluidos los tres módulos que ahora llevan drops de jefes. Pasarlas a dibujos cambia el generador, las entregas y sus tests.
- **Tarjeta de cantera de RFTools.** Sólo cambió de componente; la Lente sigue en una esquina de un dibujo simétrico (`LPr / iMi / rSr`).
- **Abanico heredado.** La Matriz de Enrutamiento y el Regulador de Energía ya cerraban cerca de veinte recetas cada uno antes de este lote.

## Validación

(Ver abajo el recibo de QA.)

## Pendiente

- Ver los seis íconos de los resonadores en el inventario del cliente.
- Intercambios de Luminosidades en Solsticio (worker del acto VI): sin ellos no se fabrican el resonador VI ni las armaduras tope.
- Ver en un cliente la línea azul del tooltip, el Marco en JEI/EMI como infusión y el alcance de Ultimine con el resonador puesto.
