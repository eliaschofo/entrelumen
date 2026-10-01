# Libro de quests v3: cadenas, motor y dos ejemplares (26 de septiembre de 2026)

El libro v2 ([quest-book](quest-book.md)) resolvió la escala, la historia y el hub, pero las 93 guías seguían siendo listas de nodos con un texto largo cada una. La v3 las reemplaza de a poco por **cadenas** (sectores): capítulos con una entrada, un tronco de hitos, ramas, consejos, secretos, un encargo y una cumbre, dibujados con la forma de su tema. Esta primera fase trae el motor y los dos ejemplares que pidió Elias, Create y Ars Nouveau, ya a la escala de ATM10; el resto del pack sigue el [plan de cobertura](quest-coverage-plan.md).

- Catálogo de todo lo que FTB Quests 2101.1.34 puede expresar, con la decisión de cada elemento: [ftbquests-2101-features](../research/ftbquests-2101-features.md).
- Voz y reglas de texto: [quest-copy](quest-copy.md).
- Formato de un archivo de cadena: [content/sectors/README.md](../../content/sectors/README.md).

## Qué hay

| Pieza | Qué hace |
|---|---|
| `content/sectors/sector_*.json` | Una cadena por archivo: quests con rol, tareas, posición y texto en los dos idiomas; figuras, paneles, arte y enlaces; recompensas propias y tablas del capítulo |
| `tools/quest_engine.py` | Compila las cadenas: gramática de nodos, presets, texto enriquecido, imágenes, curvas, recompensas (las del rol y las propias), tablas del libro y del capítulo, tema y validación |
| `tools/quest_text.py`, `tools/quest_art.py` | Presentación v2 ([abajo](#presentación-v2-28-de-septiembre-de-2026)): marcas de jerarquía y fuentes del companion; el vocabulario de dibujo del lienzo, el dibujo que se completa con el progreso y los nodos de adorno |
| `tools/quest_v2.py` | Presentación v2 para guías y capítulos de historia ([abajo](#guías-e-historia-en-la-v2-30-de-septiembre)): el mismo texto y el mismo lienzo que las cadenas, con las reglas propias de la historia |
| `tools/quest_draft.py`, `tools/preview/` | El kit para pasar capítulos a la v2 ([abajo](#kit-para-pasar-capítulos-28-de-septiembre)): el borrador del texto y el renderer de vistas previas, de a uno por vez en la PC |
| `tools/generate_quests.py` | Arma el libro entero (historia, guías, cadenas, hub) y escribe `pack/config/ftbquests/quests` y los recursos del companion |
| `content/quest_book.json` | Motivos (colores y formas por tema), colores por rol, ritmo de recompensas, las 18 tablas y los nombres de las formas |
| `tools/build_quest_placeholders.py` | Texturas provisorias del arte pedido; nunca pisa un archivo que ya existe |
| `tools/test_sector_book.py`, `tools/check_guides.py` | Contratos de las cadenas; ítems, logros, estructuras, criaturas, teclas y nombres contra los JAR fijados; Almost Unified |
| `tools/mod_facts.py`, `tools/recipe_of.py` | Para redactar: ítems con nombre EN/ES, logros, recetas, estructuras y documentación de un mod, y las recetas nativas de cualquier ítem, leídos de los JAR fijados |
| `tools/format_sector.py` | Deja cada archivo de cadena en el mismo formato (una línea por figura e imagen, un bloque por quest), para que los cambios de varios redactores se lean bien |

Los IDs salen de claves semánticas: `stable_id("quest:" + clave)`. Reusar la clave de una guía conserva el progreso de quien ya la había completado.

Revisión del 1 de octubre de 2026 (lote quest-engine), lo que el motor hace solo:

- **IDs de tareas por contenido (F48).** Una quest de una sola tarea conserva `task:<clave>`. En una de varias, cada tarea toma `stable_id("task:<clave>:<tipo>:<objetivo>")`: el ítem con sus componentes, el filtro de un «any», o el logro, dimensión, bioma, estructura, criatura, objetivo observado o estadística; una tarea idéntica repetida suma `:2`, `:3`. La cantidad y el consumo no cuentan, así que subir una cantidad conserva el progreso. Una tarea puede fijar su ID con `"id"` (16 dígitos hex). `tools/task_ids.json` guarda la firma de cada ID; `generate_quests.py` se niega a que un ID existente pase a significar otro requisito salvo con `--accept-task-id-changes`.
- **Opcionales que traban (F27, decisión D7).** Si una quest obligatoria, no repetible y que pide todas sus dependencias depende de trabajo opcional (no un checkmark), ese prerrequisito de una cadena pasa a obligatorio, en cadena y entre capítulos, con su forma y su rol. En una guía o en la historia el mismo caso hace fallar la generación con las claves.
- **Ítems que se llevan puestos (F25).** Las tareas que no consumen un ítem de `tools/carried_items.json` salen como `entrelumen:carried_item` (campos `item` y `count`), que también cuenta armadura, mano secundaria y Curios. Una variante con componentes (el cinturón de nueve bolsillos) sigue siendo tarea de ítem de FTB: la del companion sólo mira el ID del ítem y se fabrica en el inventario, donde FTB la ve.
- **Notas automáticas.** Una quest que cita una tecla de `tools/unbound_keys.json` y no dice dónde asignarla suma «[note] This key ships unbound: set it in Controls.» / «Esta tecla viene sin asignar: asignala en Controles.» (F49); una tarea de ítems que no consume y pide dos o más suma «Carry all N at once…» / «Tené las N unidades encima a la vez…» (F40). Si empujan la primera página más allá de 330 caracteres, van en una página propia. `[key:key.emi.*]` se rechaza: son binds de EMI, no teclas (F50).
- **Logros que cargan (F2).** `check_guides.py` exige que cada tarea de logro exista como `data/<ns>/advancement/<ruta>.json` (singular) en un JAR fijado, vanilla, el companion, `pack/kubejs/data` o un datapack global, con ícono `"id"` y sin filtros `"tag"`.
- El aviso «Secreto encontrado» no es recompensa de equipo (F74): una muerte no tiene jugador al que dársela y FTB lo guardaba hasta el próximo ingreso.

## Estándar de una cadena

La gramática la impone el motor por rol: el contenido declara el rol y el motor pone forma, tamaño, preset, etiqueta de color, visibilidad y recompensa.

| Rol | Nodo | Qué es | Comportamiento | Recompensa |
|---|---|---|---|---|
| Entrada (`entry`) | hexágono 2 | La primera tarea real: nada de bienvenida | Sin dependencias en el capítulo; el capítulo se centra ahí | XP |
| Paso (`step`) | cuadrado 1 | El tronco: lo que hay que fabricar para seguir | — | XP |
| Hito (`milestone`) | hexágono 2, o la forma del motivo (engranaje en Create, octógono en Ars) | Una máquina que cambia cómo jugás | — | XP ×2 y un premio a elección |
| Rama (`side`) | diamante 1, opcional | Variantes, trucos y máquinas de nicho | Las ramas largas aparecen de a una (`hide_until_deps_complete`) | XP |
| Consejo (`tip`) | etiqueta `el_tag` 1, ícono 0,75 | Un dato de oficio con el prefijo dorado «» Pro tip:» / «» La posta:» | Checkmark, sin candado ni aviso | Nada |
| Nota (`info`) | círculo 0,75 | Contexto corto o una nota de Heliodor | Checkmark, sin candado ni aviso; puede esconder el texto hasta completarla | Nada |
| Secreto (`secret`) | estrella `el_star` 1 | Un logro divertido o raro del mod | Invisible hasta completarlo; sólo tareas que el servidor detecta solo | XP, un premio al azar y el aviso «Secreto encontrado» |
| Encargo (`bounty`) | sello `el_rosette` 1 | Entregar un lote al depósito común | Repetible cada 1200 s (un día de juego); consume lo entregado | Una caja del acto, con cantidad extra al azar |
| Jefe (`boss`) | escudo `el_shield` 2 | Una pelea del mod | Tarea de matar | XP ×3 y lo que daría una caja del acto |
| Cumbre (`capstone`) | sol `el_sunburst` 3 | El objetivo del capítulo | Progresión lineal y detalles ocultos hasta poder empezarla; puede revelar el arte de cierre | XP ×5, una caja del acto y una fanfarria |

Además, cada cadena:

- **Se engancha con la historia.** Enlaces (octógono 1) muestran las entregas del Atlas u otras cadenas que usan lo que el capítulo enseña: dos en cada cadena grande.
- **Tiene un dibujo.** Una figura con la forma del tema ubica los nodos y se dibuja detrás: el engranaje de 16 dientes en Create · Cinética, los rieles y el sol de energía en Create · Vías y energía, un tablero con un panel por complemento en Create · Complementos, el círculo de rituales con cuatro escuelas en Ars. Los paneles de rama llevan rótulo.
- **Tiene un motivo.** Colores de línea, de panel y de texto del panel por tema: cobre y latón en Create, violeta de gema fuente en Ars.
- **Cuenta lo justo.** Cada quest cumple [quest-copy](quest-copy.md): de una a tres oraciones, sin hablar de sí misma, con algún chiste en el subtítulo y consejos con prefijo.
- **Tamaño.** De 45 a 90 quests por capítulo (el círculo de Ars llega a 110); un mod más grande se parte por etapas, y sus complementos van a un capítulo propio. La cifra de ATM10 es sólo un piso de referencia: la profundidad la manda la complejidad del mod (ver abajo, [plan de cobertura](quest-coverage-plan.md)). Referencias (`research/quests/chapter-stats.json`): Create tiene 89 quests en ATM10, 102 en FTB Evolution y 109 en Craftoria; Ars Nouveau, 130, 57 y 91.
- **Mínimos que prueba `test_sector_book.py`:** una entrada, una cumbre y un encargo; al menos tres hitos, dos consejos y dos secretos; consejos y notas, no más de un cuarto del capítulo.

### Profundidad según el mod (Elias, 27/9)

La cantidad de nodos sigue la complejidad del mod, no la cifra de otro pack: Mekanism no tiene la misma profundidad que Just Dire Things, ni Create la de Iron Furnaces.

- **Ninguna faceta sin explorar.** Cada sistema del mod tiene al menos un nodo que lo muestre: paso, rama, consejo o nota. Sistema quiere decir máquinas, procesos, logística, energía, equipo, jefes o una mecánica rara. El índice del manual del mod (Ponder, Patchouli, GuideME, Modonomicon) y sus tipos de receta sirven de lista de facetas.
- **Sin obligar a fabricar todo.** Para completar la cadena alcanza el tronco: entrada, pasos, hitos y cumbre. Las ramas, los consejos y las notas son opcionales: enseñan y premian, pero no bloquean.
- **Un jugador nuevo sale sabiendo usar el mod.** En un mod complejo, el tronco enseña el camino y las ramas cubren cada variante, con el porqué y no sólo el qué.
- **Niveles**, con presupuesto orientativo. El nivel de cada cadena y sus facetas pendientes están en el [plan de cobertura](quest-coverage-plan.md).

| Nivel | Qué es | Ejemplos | Quests | Capítulos |
|---|---|---|---|---|
| S | Columna del pack: muchos sistemas que se encadenan | Create, Mekanism | 200–400 | 3–5 |
| A | Sistema grande con varias ramas | Occultism, Oritech | 100–200 | 2–3 |
| B | Mod mediano: un sistema con variantes | Just Dire Things, Powah | 40–100 | 1–2 |
| C | Mod chico o utilidad | Iron Furnaces, una mochila | 5–30 | dentro de una guía |

## Ejemplares

| Capítulo | Acto | Quests | Entrada · pasos · hitos · ramas · consejos · notas · secretos · encargo · jefe · cumbre | Dibujo |
|---|---|---|---|---|
| Create · Cinética (`sector_create_kinetics`) | I | 59 | 1 · 8 · 7 · 26 · 8 · 2 · 5 · 1 · 0 · 1 | Engranaje: el tronco gira en el aro de 16 dientes; tres paneles (cintas y embudos, caños y vapor, logística con criterio) |
| Create · Vías y energía (`sector_create_logistics`) | II | 51 | 1 · 17 · 10 · 9 · 8 · 1 · 3 · 1 · 0 · 1 | Una vía con durmientes de punta a punta, la red de paquetes en cadena y el sol de New Age |
| Create · Complementos (`sector_create_addons`) | II | 30 | 1 · 2 · 7 · 10 · 4 · 2 · 2 · 1 · 0 · 1 | Un tablero con ocho paneles: Enchantment Industry, Connected, Dragons Plus, Copycats y Deco, Hypertube, Farmer's Delight (Slice & Dice y Central Kitchen), Rechiseled y almacenamiento en movimiento |
| Ars Nouveau (`sector_ars_nouveau`) | II | 105 | 1 · 26 · 8 · 47 · 12 · 0 · 8 · 1 · 1 · 1 | Círculo de rituales: cuatro escuelas (hechicería, Fuente, rituales, criaturas) alrededor de la cumbre y cuatro constelaciones en los cuadrantes (armadura e hilos, herramientas del oficio, pociones y visiones, lo salvaje y los Wilden), con glifos del mod en el borde |
| Ars Nouveau · Complementos (`sector_ars_addons`) | III | 57 | 1 · 0 · 4 · 44 · 4 · 0 · 2 · 1 · 0 · 1 | Nueve pétalos alrededor de la Marca de maestría, uno por complemento: Ars Elemental (escuelas y equipo), Ars Technica, Ars Énergistique, Starbunclemania, Ars Ocultas y Creo, Ars Additions, Ars Controle y Not Enough Glyphs |

- Reemplazan a diez guías (cinco de Create y cinco de Ars, 230 quests). Se conservan 186 claves, así que ese progreso sigue valiendo; las 44 que se van eran bienvenidas, cierres que sólo resumían y notas sueltas.
- Después de ver Create · Cinética, Elias pidió igualar o superar a ATM10 en nodos y sin relleno: Create queda en 140 quests (ATM10: 89) y Ars en 162 (ATM10: 130), todas con hechos de los JAR fijados. Los 14 nodos de complementos de Ars se mudaron a su capítulo con sus IDs.
- Todo ítem pedido existe en los JAR fijados y sobrevive a Almost Unified (`check_guides.py`). Los secretos usan logros reales del mod; el jefe de Ars es la Quimera Wilden.
- En Ars, la escuela elemental es una rama exclusiva: se elige un foco de cuatro. La cumbre pide tres de las cuatro escuelas.

## Recompensas y cajas

Las recompensas siguen siendo un extra chico, por equipo y a mano, como en la v2. Ninguna da componentes de ENTRELUMEN ni salidas de recetas con puerta (`test_gated_outputs_never_reward`, `test_reward_tables_are_moderate_and_safe`).

- **XP por rol**, multiplicada por la XP de guía del acto (5, 8, 10, 12, 15, 15): entrada, paso, rama y secreto ×1; hito ×2; jefe ×3; cumbre ×5; consejos y notas, nada. Las cinco cadenas pagan 2.455 puntos (las diez guías que reemplazan pagaban 1.277). El libro entero da 17.654 puntos. **Decisión de Elias (27/9): se sube el tope.** `test_quest_book.py` lo fija en 80.000 desde el 28/9, para el libro completo de unas 6.500 quests. Los niveles de Powah van **agrupados por escalón**: un nodo por nivel, con varias recetas adentro. Mystical Agriculture y Productive Bees, no (Elias, 28/9): un nodo por semilla y por abeja, con premios que ayudan a avanzar (ver «Recompensas propias»).
- **18 tablas, tres por acto** (`reward_tables/`): la caja del acto (`crate_N`), un premio a elección (`choice_N`) y cosas sueltas (`supplies_N`). Las cajas se llaman de rescate, de taller, de correo, de expedición, del Arca y del Solsticio; dan 2 tiradas en los actos I a IV y 3 en V y VI, cada una con su color, y sólo la del Solsticio brilla. No caen de criaturas.
- **Dónde:** hito → a elección; secreto → al azar; jefe → el contenido de una caja; cumbre y encargo → la caja cerrada, que se abre con clic derecho.

### Recompensas propias (28/9)

Las cadenas de Mystical Agriculture y Productive Bees van a tener un nodo por semilla y por abeja, con premios que ayuden a avanzar: la segunda abeja de un tipo difícil para criar, genes perfectos al azar, semillas o esencia. Por eso, además de lo que paga el rol, una quest puede nombrar sus propias recompensas. El formato, con ejemplos, está en [content/sectors/README.md](../../content/sectors/README.md#recompensas-propias).

- **Ítems** (`rewards`), con componentes de datos, se suman al final de las recompensas del rol.
- **Tablas del capítulo** (`reward_tables`) que una quest sortea (`reward_table`) o deja elegir (`reward_choice`).
- **IDs estables:**
  - Cada ítem sale de la clave de la quest y del ítem, y cada referencia a una tabla, de la clave y del nombre de la tabla. Reordenar no entrega de nuevo lo ya reclamado.
  - Cada tabla usa `table_id("<capítulo>/<nombre>")` y se escribe en `reward_tables/<capítulo>__<nombre>.snbt`, después de las 18 del libro. Sus IDs y su orden no cambian (`test_sector_book.py` los fija).
- **Límites, como en las tablas del libro:** hasta 64 de un ítem y hasta 8 sorteos; nada de ENTRELUMEN ni de recetas con puerta. Una quest de sólo checkmark sigue sin pagar.
- **Lo que lee FTB Quests 2101.1.34**, descompilado del JAR fijado:
  - `ItemReward.readData` lee la pila con `QuestObjectBase.itemOrMissingFromNBT`. Si el compuesto no trae las claves viejas `Count` o `tag`, pasa entero a `ItemStack.parse` (`ItemStack.CODEC` de 1.21.1: `id`, `count` de 1 a 99, `components`). Si no parsea, FTB pone su ítem faltante. Un tipo de componente sin registrar o que no se guarda hace fallar la pila (`DataComponentPatch.PatchKey`).
  - `BaseQuestFile.loadRewardTableFile` carga cada `reward_tables/*.snbt` con su `id` y su `order_index`. `RewardTable.sanitizeFilename` sólo respeta nombres `[a-z0-9_]`, y por eso el nombre de la tabla va en minúsculas con `_`.
  - `RandomReward.claim` sortea `loot_size` veces con reposición. `ChoiceReward` deja elegir una entrada: ahí no cuentan ni el peso ni los sorteos.
  - FTB Library lee el JSON como SNBT: un entero es `int`, un decimal es `double` y una lista no puede mezclar tipos.
- **`check_guides.py`** revisa, contra los JAR fijados:
  - que cada ítem exista y sobreviva a Almost Unified, que las cantidades empiecen en 1 y que las tablas existan en el capítulo;
  - que cada tipo de componente esté registrado: los de vanilla por la clase `DataComponents` del JAR de 1.21.1, los de un mod por las clases que registran componentes en sus JAR, con caché fuera del repo. Con los 16 tipos que ya usan las tareas del libro no da falsos errores;
  - en Productive Bees, que el tipo de abeja de un huevo o de un gen de tipo esté definido y cargue en este pack, y que un gen tenga atributo, valor y pureza reales.

## Arte pedido

Todo lo que sigue es provisorio y geométrico (`build_quest_placeholders.py`), hasta que Elias lo dibuje. No se dibujó arte nuevo.

| Archivo | Tamaño | Para qué |
|---|---|---|
| `ftbquests:textures/shapes/el_sunburst/` | 128×128, tres capas (`background`, `outline`, `shape`) | Forma de las cumbres: un sol |
| `ftbquests:textures/shapes/el_star/` | 128×128, tres capas | Forma de los secretos: una estrella de cuatro puntas |
| `ftbquests:textures/shapes/el_rosette/` | 128×128, tres capas | Forma de los encargos: un sello |
| `ftbquests:textures/shapes/el_tag/` | 128×128, tres capas | Forma de los consejos: una etiqueta |
| `ftbquests:textures/shapes/el_shield/` | 128×128, tres capas | Forma de los jefes: un escudo |
| `entrelumen:textures/gui/quests/tip.png` | 16×16 | Ícono de los consejos (ícono del tema de `#entrelumen_tip`) |
| `entrelumen:textures/gui/quests/secret.png` | 16×16 | Ícono para un secreto sin ítem (hoy ninguno lo usa) |
| `entrelumen:textures/gui/quests/banner_create.png` | 192×48 (mejor 256×48: «Create · Complementos» no entra) | Placa detrás del título de los capítulos de Create |
| `entrelumen:textures/gui/quests/banner_ars.png` | 192×48 (mejor 256×48, por «Ars Nouveau · Complementos») | Placa detrás del título de los dos capítulos de Ars |
| `entrelumen:textures/gui/quests/diagram_crushing.png` | 96×48 | Diagrama en la cumbre de Create · Cinética: dos ruedas trituradoras |
| `entrelumen:textures/gui/quests/diagram_train.png` | 96×48 | Diagrama en la cumbre de Vías y energía: estación, señal y tren |
| `entrelumen:textures/gui/quests/diagram_glyphs.png` | 96×48 | Diagrama en la cumbre de Ars: forma + efecto + aumento |

Opcionales para más adelante: una textura de línea por motivo (`dependency_line_texture`), un fondo de libro y fondos grandes por cadena. `px.png` (4×4 blanco) no es arte: es el píxel que el motor estira y tiñe para paneles y líneas.

## Presentación v2 (28 de septiembre de 2026)

Pedido de Elias (28/9): el texto de las quests tiene que leerse mejor («está medio crudo y aparte está muy chico para leer») y los lienzos tienen que jugar más, como Apotheosis · Aventura, que pinta la montaña de World Tiers del propio JAR y hace subir la escalera de niveles por ella: constelaciones, cerca y lejos, dibujar con nodos, nada de grilla estricta. Después pidió romper las reglas mentales: cada elemento de FTB Quests es un grano de arena, no lo que dice su nombre.

### Qué deja hacer FTB Quests 2101.1.34

Leído del descompilado (`E:/Elias/Codex/Entrelumen-ssd/questbook-v3/decomp/src-ftbq` y `src-ftbl`, rutas relativas a `dev/ftb/mods/`) y comparado con el catálogo de [ftbquests-2101-features](../research/ftbquests-2101-features.md), que ya cubría colores, hover, clic, páginas e imágenes.

**Tamaño del texto: no hay perilla.**
- Cada párrafo de la descripción es un `TextField` a escala 1 con 9 px por renglón (`ftbquests/client/gui/quests/ViewQuestPanel.addDescriptionText`).
- `TextField` tiene `scale` (`ftblibrary/ui/TextField`), pero el panel nunca lo toca, ni en el título ni en el subtítulo.
- La configuración del cliente (`FTBQuestsClientConfig`) sólo escala el rastreador de quests fijadas (0,25 a 2) y el changelog. FTB Library no tiene ninguna.
- El tema sólo agranda el panel: `full_screen_quest` lo lleva a pantalla completa. El ancho sale de `min_width` de la quest o `default_min_width` del capítulo (320 en el pack), con un mínimo de 200. Más ancho no es letra más grande: son renglones más largos.
- Lo único que agranda la letra es la escala de GUI de Minecraft, que elige el jugador.

**Lo que sí se puede elegir es la fuente, el color y el peso.**
- Un componente JSON puede pedir cualquier fuente que el cliente tenga, y el companion puede traer fuentes nuevas. Una fuente bitmap con más `height` dibuja letras más grandes.
- Pero el renglón sigue midiendo 9 px, así que una letra de 16 px invade el renglón de arriba. Sirve para pocas letras sobre la línea base (un número, un símbolo) con una línea en blanco encima; no para párrafos.
- Negrita, cursiva, subrayado y color: sí. La descripción se dibuja sin sombra (`TextField` con banderas 0; `ftblibrary/ui/Theme.drawString`).

**Íconos dentro del renglón.** Minecraft 1.21.1 no tiene componentes de sprite. Una fuente bitmap sí puede asignar un carácter del área privada de Unicode a cualquier textura, incluida la del ítem en el JAR de su mod, por referencia. El glifo lleva el tooltip real del ítem.

**Imágenes en la descripción** (`{image:…}`, ya catalogadas):
- Una por párrafo. El párrafo no puede llevar texto: `findImageComponent` toma el primer hermano y el resto se pierde.
- El ícono puede ser una textura o `item:<id>`, que dibuja el ítem de verdad, modelo 3D incluido.
- Alineación izquierda, centro o derecha, `fit` al ancho, nota al pasar el mouse y clic.

**Divisores.** No hay componente; alcanza un renglón de `─`, que está en la hoja `ascii.png` de vanilla, o una imagen.

**El lienzo.** Una imagen de capítulo puede ser:
- una textura, propia o de cualquier mod;
- un sprite del atlas de bloques (`minecraft:block/water_still`, `mekanism:liquid/liquid`), que en el juego sigue animado;
- `item:<id>`, el ítem en 3D a cualquier tamaño y ángulo;
- un color.

Tiene tinte, alfa, rotación, orden, clic, nota al pasar el mouse y `dependency`: aparece recién al completar una quest (para quien no edita, `QuestPanel.addWidgets` ni crea el widget). El texto sobre una imagen (`text_on_image`) se escala al alto de la caja y gira con ella, con fuente JSON. Todo escala con el zoom del libro (de 4 a 28, 16 por defecto; a zoom 16 una celda son 28 px).

**Lo que no sobrevive: recortes y mosaicos.**
- `Icon.getIcon` acepta propiedades como `; u0= v0= u1= v1= tile_size=` (`ftblibrary/icon/ImageIcon.setProperties`), y dibujadas funcionarían.
- Pero la imagen viaja del servidor al cliente como `Icon.toString()` (`ChapterImage.writeNetData`), y `ImageIcon.toString()` devuelve sólo la ruta de la textura.
- Resultado: no hay recorte ni mosaico. Un patrón repetido son varias imágenes; una hoja de sprites no se puede recortar. Los sprites del atlas y `item:` sí llegan enteros.

**Notas al pasar el mouse: sólo con clic.** `ChapterImageButton.checkMouseOver` sólo reconoce el mouse sobre una imagen que tiene una acción de clic, salvo para quien edita el libro. Una imagen con nota y sin clic nunca le muestra la nota a un jugador. Nueve adornos del libro estaban así el 28/9. La v2 le da a esas notas el clic de la quest que las revela, y `check_guides.py` avisa de las que no tienen ni clic ni revelado.

**Quests sin tareas.** No sirven para encender cosas: FTB las completa recién cuando el jugador entra al mundo (`ServerQuestFile.checkQuestBookOnLogin`), no en vivo. Para que algo aparezca al avanzar, está la `dependency` de una imagen.

### Qué hace legible el texto en los packs de referencia

Medido sobre las descripciones de las tres instancias de [reference-packs](../research/reference-packs.md) (en inglés; ATM10 3.522, FTB Evolution 1.764 y Craftoria 1.002 quests). Son patrones descritos con palabras propias; no se copió nada.

- **ATM10: párrafo único y denso.** 1,2 párrafos por quest, de unos 188 caracteres. Color en el 70% y negrita en el 29%. Pocas líneas en blanco (7%). Imágenes en el 14%, casi todas capturas de multibloques armados.
- **FTB Evolution: párrafos cortos separados.** 2,6 párrafos por quest de unos 105 caracteres, con línea en blanco entre párrafos en el 76%. Los términos clave van en color en el 94%. En el 9%, una segunda página: el qué en la primera, el cómo en la segunda.
- **Craftoria: el más fácil de recorrer con la vista.** Tres párrafos de unos 79 caracteres. Listas en el 11%, algunas como fichas técnicas: una etiqueta de color por variante, viñetas de dos a cinco palabras y al final la imagen grande del bloque. Los colores marcan categorías (cosas, acciones, costos).

Lo que comparten las que se leen bien: una idea por párrafo, aire entre párrafos, el mismo color para la misma categoría, listas para lo que es paralelo (modos, usos, recetas), lo largo en la página 2 y una imagen para reconocer la cosa.

### Reglas v2 del texto

La voz no cambia ([quest-copy](quest-copy.md)); cambia la forma. La letra no puede crecer, así que crecen la jerarquía y el aire.

1. **Primero, una oración.** `[lead]` abre la quest con una oración corta en negrita blanca: lo que te llevás si leés un solo renglón. No repite el título. En un consejo, el primer párrafo es el `[tip]`.
2. **Después, poco y separado.** Hasta dos párrafos cortos o una lista de dos a cinco viñetas. Una idea por párrafo, dos renglones como mucho. Entre viñetas no hay línea en blanco; entre párrafos, sí.
3. **Viñetas con ícono.** `[li:mod:ítem]` pone el ícono del ítem (su textura plana, con su tooltip) en lugar del punto. Sin ícono, `[li]` pone un punto del color del motivo.
4. **Números que importan.** Van con `[hl|…]`, en el color del motivo. El número que resume la quest va con `[big|…]`, al doble de tamaño. `[big]` no abre página ni sigue a una viñeta, porque sube 7 px sobre el renglón de arriba. Uno o dos por quest, en las que tienen un número estrella (256 bloques, 32 lingotes, ×2).
5. **Llamadas, cada una en su párrafo.** `[tip]` («La posta:», con el farol del libro), `[careful]` («Ojo:», con la alerta de vanilla) y `[note]` («Dato:», con el ícono de información). La posta es un dato de oficio; el ojo, lo que rompe algo; el dato, un contexto que no es instrucción.
6. **Acciones como íconos.** `[icon:right_click]` y `[icon:click]` delante del verbo: el mouse de vanilla con el botón marcado.
7. **La página 2 es para la profundidad:** diagramas, tablas, submecánicas. El tope de 330 caracteres visibles en la primera página sigue igual.
8. **Todo ítem nombrado** va con `[item:…]` (color y tooltip), igual que antes. `{rule}` separa dos bloques con una línea tenue, pocas veces.

El tope, la paridad de idiomas y las frases prohibidas los sigue revisando el motor. `[lead]` primero, `[big]` bien ubicado y `{rule}` entre párrafos también son errores de compilación.

### Reglas v2 del lienzo

1. **Una escena, no un grafo.** Una pintura del propio mod (Apotheosis, el panorama del título de Create), referenciada desde su JAR. O un dibujo propio con texturas y sprites de los mods: un edificio en corte, un río, una mina.
2. **El tronco dibuja algo:** un eje, un río, una cinta, un caño, una escalera. Donde el dibujo reemplaza a la línea, la línea real se esconde (`hide_lines`), y el mouse la sigue mostrando.
3. **Distancias libres.** Las ramas van cerca de su padre, en grupos o constelaciones sueltas; no hay grilla.
4. **Sólo dos reglas duras.** Los nodos no se pisan (lo valida el motor) y las líneas se leen: cortas, sin atravesar grupos ajenos ni rótulos.
5. **Pocos rótulos y grandes:** escala 2 para las secciones y 3 o 4 para el título. Escala 1 sólo para notas.
6. **Se dibuja de a poco: decisión de Elias del 28/9.** Viendo el piloto dijo «me va eso de que se vaya dibujando de a poco, es novedoso y nadie lo tiene». Es regla de primera:
   - todo capítulo v2 arranca como un **boceto**: el marco de la escena, la entrada y unos pocos hitos, más el título;
   - el dibujo **se completa a medida que el jugador avanza**: los caminos, las piezas de la escena, la utilería y la luz llegan con la quest que los gana;
   - un camino que pasa por quests (`through`) crece tramo por tramo (`grow`): cada tramo aparece con la quest a la que llega. El río, el eje o el caño se van dibujando detrás del jugador;
   - el boceto es tiza tenue (`sketch`: la misma forma, clara y casi transparente, desde el principio) y la tinta entra encima con `reveal` o `grow`;
   - la luz, el humo, la grava y el sol de la cumbre siguen siendo el premio de los hitos;
   - `check_guides.py` avisa si más del 40% de las imágenes fuertes de un capítulo v2 (alfa mayor que 90, y todo ítem en 3D) se ven antes de completar una quest.
7. **Adornos (`decor`)**, de uno a tres por capítulo: juguetes sin premio que nunca cuentan como contenido.
8. **Por referencia, nunca copiado.** Las texturas de mods se leen de su JAR. `check_guides.py` exige que existan, que un sprite esté en el atlas de bloques y que un ícono de texto no sea animado.

### Motor

- **`tools/quest_text.py`:**
  - las marcas `[lead]`, `[li]`, `[li:…]`, `[icon:…]`, `[big|…]`, `[careful]`, `[note]` y `{rule}`;
  - las dos fuentes que `generate_quests.py` escribe en el companion. `entrelumen:quest_big` usa las hojas de vanilla al doble, sobre la línea base; `entrelumen:quest_icons` pone un glifo del área privada por textura usada;
  - `"presentation": 2` en un capítulo dibuja `[tip]` con el farol en vez de «» ». Los capítulos que no lo piden no cambian.
- **`tools/quest_art.py`:** diez tipos de arte, que salen del [README de las cadenas](../../content/sectors/README.md#arte-presentación-v2):
  - `picture`, `sprite`, `item`, `path` (color, textura o sprite en trozos casi cuadrados, puntos o ítems, curvas suaves), `mosaic`, `glow`, `scatter`, `frame`, `text` (rótulo girado) y `lettering` (una letra por posición a lo largo de un camino);
  - el rol `decor`: un checkmark opcional sin premio, aviso, candado ni líneas, de cualquier tamaño, que ningún total ni proporción cuenta (`is_counted`);
  - para la regla 6: `through` (un camino por las posiciones de quests), `grow` (cada tramo con la quest a la que llega, o una quest por segmento) y `sketch` (la copia tenue de un camino, una pintura, un sprite o un marco, visible desde el principio). Un ítem en 3D no se puede bocetar: FTB lo dibuja sin alfa.
- **Toques en el código existente:**
  - `quest_engine.py`: importa los dos módulos, suma sus marcas y delega en `compile_paragraph`, `compile_text`, `compile_sector` (decor) y `decorate_sector` (`art_images`);
  - `generate_quests.py`: escribe las fuentes y no cuenta los adornos;
  - `check_guides.py`: revisa texturas, sprites, ítems e íconos;
  - `content/quest_book.json`: colores (transparentes) del rol `decor`.
- **Dos límites que reportaron los redactores**, resueltos en el mismo módulo:
  - `line` y `panel` ignoraban `reveal` y `click` (`art_image` no les pasaba esos datos), y por eso se dibujaban con `px.png`. Ahora los respetan. Las 196 líneas con `reveal` que ya había (Logística, Psi, RFTools) aparecen, por fin, con su quest.
  - Los rótulos de los paneles de rama tenían escala 1 fija. Ahora toman `caption_scale`, `caption_tint` y `caption_bold` (escala 2 por defecto en un capítulo con `"presentation": 2`; `quest_art.caption_style`). Los rótulos de arte (`label`, `text`) ya aceptaban cualquier `scale`; la v2 suma `rotation` y fuentes.
- **Pruebas:** `tools/test_presentation.py` (23), que también corren dentro de `tools/test_sector_book.py`.

### Piloto: Create · Cinética y Mekanism · Básico

Las dos cadenas conservan todas sus claves, tareas, dependencias, datos y fuentes. Cambian la posición, el dibujo y la forma del texto: 153 quests reescritas en inglés y en español.

**Create · Cinética: el molino en la caverna.**
- La pintura del título de Create (sus cuatro caras, desde el JAR de Create) es el taller.
- Un río de agua animada, con orillas de pasto y flores, mueve la rueda hidráulica dibujada alrededor de su nodo.
- El eje de Create, con su propia textura, lleva el tronco en zigzag por la pintura. Pasa a latón recién cuando hacés latón.
- Cada hito prende un farol.
- Alrededor: las cintas bajo la prensa; la sala de calderas con el tanque y el motor en 3D, que largan vapor; el pozo inundado del buceo; los estratos de las piedras de Create; la pizarra de esquemas; la mesa de logística de latón; el estante de herramientas y golosinas.
- En la cumbre, las dos ruedas trituradoras dibujadas, que sueltan grava, y el sol.
- Juguetes: un farol que prende las lámparas de la pintura y un silbato que aparece con la caldera.

**Mekanism · Básico: la planta en corte.**
- Un edificio con un piso por nivel (básico, avanzado, élite, definitivo). Los pisos se iluminan al alcanzar su nivel.
- A la izquierda, la escalera de niveles, que baja al sótano de Mekanism Extras en la tierra.
- A la derecha, la escalera de menas ×2…×5, con el lodo sucio de Mekanism bajando animado.
- En el medio, la planta química manda un caño de color, animado, a la máquina de cada piso: oxígeno a ×3, cloruro a ×4, ácido a ×5. La torre de evaporación está armada con sus bloques.
- En la terraza, la cinta de carcasa de acero, las menas, el anexo de MoreMachine y dos chimeneas que humean.
- Juguete: el Robit.

Vistas previas, antes y después, en `E:/Elias/Codex/Entrelumen-ssd/presentation-v2/`. Las dibujó el renderer de `questbook-v3/tools/preview3.py` extendido, que desde el 28/9 vive en el repo como `tools/preview/preview_v2.py` ([kit](#kit-para-pasar-capítulos-28-de-septiembre)). Agrega:
- modelos 3D de ítems (elementos JSON y OBJ de NeoForge) para íconos y para imágenes `item:`;
- sprites, rótulos girados y cursiva;
- las fuentes del companion;
- el panel de la quest con la geometría de `ViewQuestPanel`, sin sombra y a escala de GUI 2 o 3.

### Cómo pasar los otros capítulos

**Qué cambiar en los otros ~70 capítulos:**
- **Texto:** la forma de arriba, quest por quest y sin tocar los datos:
  - `[lead]`;
  - las enumeraciones hechas listas;
  - íconos donde el ítem tiene textura plana;
  - `[careful]` para lo que hoy es una advertencia metida en un párrafo;
  - la profundidad en la página 2.
- **Lienzo:** una escena por familia en vez de una figura geométrica por capítulo. Los grupos pasan de paneles con esquinas a lugares de la escena, con rótulos de escala 2.

**Cómo hacerlo barato:**
1. **Por familia, no por capítulo.** Las cinco cadenas de Create comparten la caverna y el eje; las tres de Mekanism, el edificio en corte. La primera de cada familia paga el dibujo y las otras lo reusan, cambiando la parte que les toca.
2. **Pinturas que ya existen en los JAR fijados**, por referencia, en un barrido del 28/9 de texturas grandes fuera de bloques e ítems:
   - Aether: el panorama del título (6 caras de 1017 px) y su logo;
   - Ars Nouveau: el cuadro `painting/resting_drygmy`;
   - Forbidden & Arcanus: los fondos de investigación con estrellas;
   - Theurgy y Modonomicon: cielos nocturnos por capas, para los capítulos de magia con constelaciones;
   - Herbs and Harvest: la lámina de cultivos (1920×991);
   - los tutoriales de Apotheosis;
   - los cuadros de vanilla (`minecraft:textures/painting/…`), para cocina, exploración y construcción.
   
   Los mods técnicos sin pintura usan el edificio en corte, la placa o el río de Create.
3. **El texto con un borrador asistido:** `tools/quest_draft.py` ([kit](#kit-para-pasar-capítulos-28-de-septiembre)). Quien redacta revisa y corrige, y el costo baja a unos segundos por quest más el chiste.
4. **Orden: por dónde pasa un jugador nuevo.** Primero los actos I y II (Create · Vías y energía, Farmer's Delight, Ars, las guías de inicio), después el resto por familia. Cada tanda cierra con las vistas previas (`tools/preview/preview_v2.py`: el boceto, el avance, el final y los paneles a GUI 2) antes de mostrárselas a Elias.
5. **Lo que no cambia se queda:** claves, tareas, dependencias, recompensas y fuentes. Un capítulo sin `"presentation": 2` se ve igual que hoy, así que se puede pasar de a uno sin romper nada.

### Kit para pasar capítulos (28 de septiembre)

Elias aprobó la v2 y varios redactores pasan capítulos en paralelo. El kit baja el costo y la memoria que usan. La lista de pasos para cada redactor está en el [README de las cadenas](../../content/sectors/README.md#pasar-un-capítulo-a-la-v2).

**Borrador del texto: `tools/quest_draft.py content/sectors/sector_x.json`.** Reescribe sólo las listas `text`, inglés y español juntos, y deja claves, tareas, títulos, datos y fuentes como estaban. Por quest:
1. una oración de advertencia (Careful…, Never…, Don't…, explota, se destruye, se pierde, un `[warn|…]`) pasa a su propio `[careful]`, sin la palabra que ya dice la etiqueta. Una sobre el pack mismo («In this pack…», «Pack change:», una pieza que pide un material de acto) pasa a `[note]`. Dos llamadas por quest como mucho;
2. la primera oración es el `[lead]` si es corta en los dos idiomas (110 caracteres visibles en inglés, 125 en español);
3. las enumeraciones se vuelven listas: «intro: a, b, c y d», las cláusulas con punto y coma, las viñetas «•» de la v1, y tres o más oraciones cortas seguidas;
4. una viñeta que nombra un ítem lleva su ícono (`[li:ítem]`) sólo si el ícono del inventario es la textura plana del propio ítem, cuadrada y quieta. Lo lee `mcassets.flat_icon` de los JAR fijados;
5. los números van con `[hl|…]`, y uno solo va con `[big|…]`: el de la tarea, un ×N o el que está en el título, donde `[big]` puede ir;
6. si la primera página pasa de 330 caracteres en algún idioma, sus últimos párrafos pasan a la página 2, sin cortar una lista.

Cada quest se compila con `quest_engine.quest_copy`, el mismo control que usa el libro. Si una falla, se queda con su texto v1. Las que ya están en v2 no se tocan. Imprime lo que no pudo decidir: el lead largo, oraciones que no cortan igual en los dos idiomas, un `[big]` sin gemelo. Sobre los 80 capítulos v1 del 28/9, ningún borrador rompe una regla (`tools/test_kit.py`).

**Vistas previas: `tools/preview/preview_v2.py`.** El renderer del piloto, en el repo:
- lee todo de los JAR fijados al dibujar y escribe fuera del repo (`E:/Elias/Codex/Entrelumen-ssd/previews/<worktree>/`), así que no se sube ningún asset de mod;
- dibuja estados intermedios (`--state steps`: fresco, 25, 50 y 75% en orden de dependencias, completo) para ver el dibujo completarse, la pantalla de 1080p (`--screen`) y hojas de paneles para revisar texto (`--panels all --sheet`).

**Disciplina de RAM.** La PC tiene 16 GB para cinco o seis sesiones:
- una sola vista previa por vez: `mkdir E:/Elias/Codex/Entrelumen-ssd/render.lock` con `owner.json` (proceso, arranque, carpeta, comando), que se libera al salir, también con Ctrl+C o `SIGTERM`;
- si el dueño ya no existe, el candado viejo se limpia: sólo su `owner.json` y la carpeta vacía. Una carpeta con otra cosa adentro, o un enlace, no se toca;
- antes de cada capítulo espera mientras la memoria libre (`MemFree` de `/proc/meminfo`; en Windows, la memoria física disponible, que es lo que Git Bash informa como `MemFree`) esté bajo 1,5 GB, hasta 30 minutos;
- el renderer es Pillow puro y no abre navegador. Los 1,3 GB de Chrome headless que se vieron el 28/9 eran de Playwright, no de las vistas previas. Las PNG se miran con un visor, sin navegador; si alguien abre uno, lo cierra al terminar la tanda.

**Presupuesto de imágenes.** Al 28/9 el libro tiene 21.552 imágenes de capítulo: Mundos profundos 683, Almacenamiento 626, Create · Cinética 553. El capítulo más pesado de ATM10 tiene 132.
- `check_guides.py` compila el libro en memoria, avisa arriba de **700 imágenes por capítulo** e imprime el total del libro y los tres más pesados.
- Una pintura grande antes que muchas fichas donde se lee igual: un fondo es una imagen, no un mosaico de 200.
- Lo que cuestan en FPS y en la sincronización al entrar no está medido. FTB manda todas las imágenes al cliente al entrar y dibuja las de un capítulo abierto en cada cuadro. Queda para la QA del cliente, y hasta entonces los 700 son un techo prudente, no una medición.

### Las familias Create y Mekanism, enteras (28 de septiembre)

Con el kit, las otras seis cadenas de las dos familias del piloto pasaron a la v2. Conservan todas sus claves, tareas, dependencias, recompensas, datos y fuentes; cambian la posición, el dibujo y la forma del texto.

**Texto.** `quest_draft.py` hizo el borrador de las 339 quests. A mano se escribieron 113: sobre todo leads que el borrador dejó sin decidir y listas que no eran paralelas. En el camino, el borrador aprendió cuatro cosas que ahora hace solo:
- una lista de comas pide ítems parejos;
- una oración sobre el pack mismo pasa a `[note]`;
- un secreto conserva su remate entero;
- cada `[big]` que pone aparece en el reporte, con su motivo.

**Escenas.** Cada familia reusa su escena y cada capítulo toma otra parte:
- **Create · Contraptions:** un molino de viento en la caverna. El rodamiento es el eje y las cuatro aspas son las cuatro ramas: la tela de vela de Create sube paño por paño con sus quests. El mástil baja hasta los rieles, que se tienden para los dos lados, y la vagoneta que se hace el camino pone el último tramo. Juguete: un barrilete.
- **Create · Lógica y medición:** la sala de control. Un tablero de tiza en la pared; desde la palanca, dos pistas de redstone se dibujan hasta la consola del final. Cada módulo recibe su panel con su primera quest, y al final aparece un asiento frente a la consola. Juguete: el timbre.
- **Create · Vías y energía:** la misma pintura de la caverna que Cinética, con la vía de Create tendida delante, quest por quest, y el portal del Nether arriba de ella. La cadena de paquetes cierra su vuelta eslabón por eslabón. El cable de cobre de Crafts & Additions prende sus lámparas y el sol de Heliodor de New Age se enciende al cerrar el círculo. Juguete: el silbato.
- **Create · Complementos:** el tablero de nueve complementos del subtítulo, hecho tablero de herramientas, con un compartimento por addon. Adentro corre la experiencia líquida hasta el rayo del final, se curva el hipertubo, se junta el aliento de dragón y humea la olla. Juguete: la tarta de experiencia.
- **Mekanism · Energía y reactores:** la central de la misma planta, en corte, un piso por era. Generadores abajo; vapor y almacenamiento; fisión y el laboratorio de radiación; el CFS y la fusión arriba; en la azotea, la antimateria como un sol chiquito. Cada multibloque está dibujado con sus propios bloques y aparece con su hito. La energía, el vapor y el combustible corren por el edificio, y el cielo se llena de estrellas por era. Juguete: la palanca general.
- **Mekanism · Logística y equipo:** el ala de logística y la armería de la misma planta. Un transportador con lingotes, caños de agua hasta el tanque dinámico, tubos químicos. El piso cuántico tiene el portal y los racks OEC; el sótano, el minero en la roca; en el medio, la bahía del MekaTraje con el sol. Juguete: fuegos artificiales.

**Los pilotos también arrancan como boceto.** En Cinética, la pintura, el río, la rueda, el eje (que ahora crece tramo por tramo), las cintas, la sala de calderas, el pozo, los estratos, la pizarra, el estante y la mesa llegan con sus quests. En Básico, el edificio, cada piso, las escaleras peldaño por peldaño, el chorro de menas, los caños, la torre, la veta, el anexo, las chimeneas y las estrellas.

**Imágenes y boceto** (`check_guides.py`, el 28/9):

| Capítulo | Imágenes | Visibles al empezar (fuertes) |
|---|---|---|
| Create · Cinética | 591 | 3% |
| Create · Contraptions | 477 | 10% |
| Create · Lógica y medición | 300 | 5% |
| Create · Vías y energía | 355 | 4% |
| Create · Complementos | 307 | 5% |
| Mekanism · Básico | 430 | 7% |
| Mekanism · Energía y reactores | 555 | 3% |
| Mekanism · Logística y equipo | 328 | 4% |

Vistas previas en `E:/Elias/Codex/Entrelumen-ssd/previews/wt-presentation/`: el boceto, el avance (50%) y el final de cada capítulo, más las hojas de paneles de Contraptions.

### Guías e historia en la v2 (30 de septiembre)

Pedido de Elias: que todo el libro se vea como la v2, con texto legible y dibujos que se completan jugando. Las 89 cadenas ya tenían la v2; faltaban las 21 guías y los 8 capítulos de historia, que usan otros formatos.

**Motor: `tools/quest_v2.py`.** Una guía o un capítulo de historia con `"presentation": 2` toma las dos mitades de la v2 con el mismo código que las cadenas:
- **Texto.** Cada quest pasa a `{"title", "text": [párrafos]}` con las marcas de `quest_engine` y `quest_text`, y la compila `quest_engine.quest_copy`, con las mismas comprobaciones: el lead primero, 330 caracteres en la página 1, sin frases meta, y los mismos enlaces, ítems y teclas en los dos idiomas.
  - Los códigos `&` ya no van: FTB no los lee dentro de una línea JSON. Los reemplazan `[hl|…]`, `[b|…]` e `[i|…]`.
  - Para la interferencia del Atlas hay dos marcas nuevas en `quest_text`: `[glitch|…]` (letras ofuscadas, hasta 8) y `[strike|…]` (tachado).
- **Lienzo.**
  - Una lista `art` con los tipos de `quest_art`: `reveal`, `through` y `grow` (las posiciones son las del `layout`) y `sketch`.
  - Quests con `"role": "decor"`: forma `none`, sin premio, y no cuentan en ningún total.
  - `motif` elige la paleta; sin motivo, el oro de Heliodor.
- **Guías.**
  - Subtítulo de quest y enlaces a cualquier capítulo.
  - `hide_lines`, `hide_dependent_lines`, `reveal` e `icon_scale`, como en una cadena.
  - El medallón se puede mover o sacar (`medallion`).
  - El motor valida que los nodos no se pisen.
- **Historia.** Sigue con sus reglas, y la v2 suma:
  - qué y después por qué: el último párrafo es el lore, simple (la rama opcional queda exenta);
  - sin subtítulos de quest; dos glitches como mucho; enlaces sólo a quests de la historia;
  - la gramática de nodos, los hitos de campaña, el sentido de lectura y los IDs no cambian, y los digests de `test_generate_quests.py` lo comprueban;
  - el numeral, el emblema y un título a escala 3 se ponen arriba de todo el dibujo;
  - el sol y el medallón del final llegan con el final;
  - los rótulos de rama pasan a escala 2, o se sacan con `"book": {"panels": false}`.
- **Lo que no pide la v2 no cambia.** Con el motor nuevo y el contenido de `origin/main`, las 175 salidas del libro salen idénticas byte a byte (`generate_quests.py --check` y un digest de cada archivo generado). Un capítulo v1 no puede traer `art`, `motif`, adornos ni texto v2.
- **El kit.**
  - `quest_draft.py` también toma guías e historia: convierte el formato y los códigos, marca `"presentation": 2`, hace el borrador y deja entero el lore de cada quest.
  - `format_sector.py` ordena también los capítulos v2 de guías e historia.
  - `check_guides.py` revisa contra los JAR fijados lo que suman su texto y su arte, les aplica el presupuesto de 700 imágenes y el aviso de boceto, y avisa de notas que nadie ve.
  - `preview_v2.py` dibuja cualquier capítulo, también por archivo (`content/act_two.json`).
- **Pruebas:** `GuidesAndStory` en `tools/test_presentation.py` (9, también dentro de `test_sector_book.py`). Sobre los 27 capítulos v1, el borrador conserva claves, tareas y fuentes, y cada quest que no marca «fix by hand» compila. `test_generate_quests.py` y `test_quest_book.py` leen los dos formatos, y los adornos quedan fuera de digests y totales.

**Pilotos.** Conservan todas sus claves, tareas, datos y fuentes; cambian la posición (en la guía), el texto y el dibujo.
- **Despertar entre ruinas: el patio del sol.**
  - Al oeste, el pasto donde despertás, con los libros que no te regalan como fantasmas. Arriba, las nubes del Aether.
  - En el medio, el patio: el piso de toba y calcita, el sol de cobre oxidado con sus rayos y las ocho columnas, cuatro rotas y con su tope caído al lado. Llega con «La ruina inicial».
  - El marco dorado de la protección se prende con su quest. Las reglas están dibujadas: la TNT tachada, la puerta, el cofre y el yunque, el pico frenado en el borde, las vasijas rotas del punto ciego.
  - Al este, el camino al Atlas y más allá, a la aldea y a la cámara de desafío. Solsticio se ve a lo lejos.
  - Juguetes: pulir el sol, que muestra el oro bajo la pátina, y prender los faroles de las columnas.
- **II · Los oficios perdidos: el Taller hundido en corte.**
  - Arriba, el pasto y la tierra; la escalera baja al descanso. Desde ahí, la pasarela crece hasta cada mesa con su primera quest.
  - Terra tiene el arroyo, la rueda, el eje que baja por la cadena de quests y las prensas oxidadas en fila.
  - B., el estante de rezos, las geodas y el cantero de flor mágica.
  - El cristal tiene el generador que humea y el haz rojo hasta los cristales que zumban.
  - J., la olla sobre la fogata y el canal con peces.
  - El viajero, el mapa gastado con su camino punteado.
  - Cada mesa se arma con su hito y lleva «Terra» rayado en runas.
  - Abajo, la bóveda: el plano sale, el brazo vuelve y el nombre aparece en grande.
  - Juguete: la campana del turno prende las lámparas.

| Capítulo | Imágenes | Visibles al empezar (fuertes) |
|---|---|---|
| Despertar entre ruinas | 257 | 2% |
| II · Los oficios perdidos | 295 | 4% |

Vistas previas en `E:/Elias/Codex/Entrelumen-ssd/previews/wt-v2-engine-guides/`: el boceto, el avance (25, 50 y 75%), el final, la pantalla de 1080p y las hojas de paneles, en los dos idiomas.

### Límites de la v2

- **Nada se vio en el cliente:** las dos fuentes, los glifos de ítems, los sprites animados, las imágenes `item:`, los rótulos girados y los adornos están leídos del código y compilados, y las vistas previas los imitan.
  - Si una textura falta, su glifo sale como un cuadrado y el resto del texto no se rompe.
  - Si un sprite no está en el atlas, se dibuja con la textura de «falta».
- **Detalles del cliente:** una imagen `item:` ignora el tinte y el alfa. Los glifos de ícono miden 8 px, el alto de una letra: a GUI 2 muestran la textura de 16 px pixel por pixel.
- **Salidas generadas:** las fuentes van con el resto del libro (`generate_quests.py`) y se regeneran al integrar, como el tema y los idiomas.

## Validación

**Estado al 27/9** (main 156fc68): 118 capítulos (8 de historia, 43 guías, 66 cadenas y el hub) y 5.105 quests, por encima de las 4.790 de ATM10. Pasan `generate_quests.py --check`, `test_sector_book.py`, `test_quest_book.py`, `check_guides.py` (4.933 quests de guías y cadenas, sin errores) y `format_sector.py --check`; la auditoría KubeJS cubre 5.007 ítems. La carga del libro completo en un servidor no se volvió a probar desde el registro de abajo: queda para el próximo arranque de QA del pack completo, junto con las tareas de «cualquiera de estos».

**28/9, recompensas propias.** El libro generado es idéntico byte a byte al de antes: ninguna cadena las usa todavía. Pasan `generate_quests.py --check`, `test_quest_book.py`, `check_guides.py` y `format_sector.py --check`. `test_sector_book.py` sube a 44 pruebas; las nuevas compilan una cadena de prueba con ítems y tablas y comprueban que el resto del libro quede igual byte a byte y que los IDs de las 18 tablas no se muevan. Falta la carga en un servidor (ver Límites).

El registro que sigue es el del motor v3, sobre `origin/main` 81bd9a4 más su rama:

- `python tools/generate_quests.py --check`: 97 capítulos (8 de historia, 83 guías, 5 cadenas y el hub), 1.992 quests y 18 tablas; IDs globales, grafo sin ciclos y paridad EN/ES.
- `tools/test_generate_quests.py` (36 pruebas: los digests semánticos de la historia no se mueven), `tools/test_quest_book.py` (14: libro, hub, recompensas, tema y que la historia no hable de sí misma) y `tools/test_sector_book.py` (27: texto enriquecido, geometría, curvas, gramática, estándar de cadena, cada función del catálogo usada, tablas con IDs enteros exactos, cajas moderadas y sin salidas con puerta, formas y textos del companion).
- `tools/check_guides.py`: 83 guías y 5 cadenas, 1.820 quests, sin errores contra los JAR fijados y Almost Unified.
- `tools/check_runtime_content.py`: la auditoría KubeJS cubre los 1.461 ítems del libro, las tablas incluidas.
- Las 34 comprobaciones de Python de `verify.yml` (con `test_sector_book.py` y `build_quest_placeholders.py --check` agregados) pasan (`questbook-v3/ci/ci-6.log`, fuera del repo).
- El companion compila (`gradlew --offline build`) y lleva el tema, las cinco formas, las texturas y los textos de `ftbquests`.

En un servidor desechable (`server-questbook3`, 272 JAR de servidor y el companion de la rama, 4 GB de heap, un servidor por vez; borrado después). Recibos en `E:/Elias/Codex/Entrelumen-ssd/questbook-v3/qa/`:

- **Cabeza final** (`install-qb3d.json`, `qb3d.json`, `roundtrip-qb3d.json`): FTB registró «Loaded 6 chapter groups, 97 chapters, 1992 quests, 18 reward tables» (los 5 grupos más el de FTB) y tablas de traducción para dos idiomas, **sin ningún aviso de FTB Quests ni de FTB Library**. La auditoría KubeJS pasó con los 1.461 ítems del libro. Cierre limpio (código 0, sin watchdog). FTB volvió a guardar 117 archivos en su formato; leídos de vuelta, **las 49.273 claves generadas coinciden**: capítulos, quests, 2.020 tareas, 1.532 recompensas, 1.728 imágenes, 15 enlaces, las 18 tablas con sus 88 entradas y los 57 `table_id` exactos, presets y `data.snbt`; sólo omite valores por defecto (cantidad 1, peso 1, tamaño 1, tipo ítem en tablas). Los idiomas no pierden claves.
- El arranque anterior (`qb3c-first.json`) encontró el único error de contenido de la fase: la llave arcana de Ars Technica 2.7.6 no es un ítem propio sino una llave de Create con componente. La tarea ahora pide esa llave con `match_components: fuzzy`, y el motor lo soporta.
- Los tres errores del log son anteriores al libro: una etiqueta de Pam's y dos fluidos de experiencia en data maps (Ender IO, Reliquary). Los arranques previos a la ampliación (`qb3-pre-rebase.json`, `qb3b.json`) quedan como historia.

Vistas previas a zoom 16, fuera del repo, en `E:/Elias/Codex/Entrelumen-ssd/questbook-v3/previews/`: el hub, las cinco cadenas y el acto I, cada uno en un mundo nuevo (`-fresh`) y completo (`-done`), la versión en inglés en `en_us/`, y en `panels/` el panel de quests con texto enriquecido en los dos idiomas.

## Límites

- **Recompensas propias (28/9): falta cargarlas en un servidor.** La RAM no alcanzó para un arranque. En el próximo arranque de QA hay que ver cuatro cosas: que FTB cargue las tablas del capítulo sin avisos, que el huevo suelte una abeja del tipo pedido, que el gen muestre su valor y su pureza, y que reclamar la recompensa entregue la pila con sus componentes. El formato está leído del código de FTB Quests, de vanilla y de Productive Bees, no visto en el juego.
- **«Cualquiera de estos» (27/9).** Hasta esta fecha una tarea de ítem pedía un solo ítem: FTB Quests 2101.1.34 trae `ItemMatchingSystem` sin adaptadores. Algunas cadenas lo esquivaron con un logro o pidiendo un ítem concreto.
  - Ahora el lock tiene FTB Filter System 21.1.4 y FTB XMod Compat 21.1.11, que registra el adaptador (`ftbquests/filtering/FFSSetup`).
  - El motor compila `{"any": [ítems y #tags]}` a una tarea de ítem cuyo ítem es un filtro inteligente con la expresión en su componente (formato en [content/sectors/README.md](../../content/sectors/README.md#tarea-de-cualquiera-de-estos)). El resto de las formas de tarea sale igual byte a byte.
  - Sigue sin expresarse:
    - Componentes por alternativa: FTB Filter System tiene `component(...)`, pero el motor no lo expone, así que una variante por componente sigue siendo una tarea `item` con `components`.
    - Tags que sólo agrega un script de KubeJS: `check_guides.py` no los ve.
    - El nombre de la lista: el título lo escribe quien redacta.
  - Todavía no se cargó en un servidor. El chequeo está listo ([mod-pingpong](mod-pingpong.md#ftb-filter-system-y-ftb-xmod-compat-279)).
- Nada de esto se vio en el cliente. Las formas propias, las líneas hechas con rectángulos finos rotados, los rótulos con fuente rúnica, las curvas de Bézier, las imágenes en descripciones y los enlaces y notas dentro del texto están leídos del código y cargados en un servidor, pero no mirados.
- Las vistas previas salen de un renderer propio que imita la geometría de FTB a zoom 16. No son capturas.
- Las otras 83 guías siguen en formato v2 hasta convertirse en cadenas.
