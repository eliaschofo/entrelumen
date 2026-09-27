# Libro de quests v3: cadenas, motor y dos ejemplares (26 de septiembre de 2026)

El libro v2 ([quest-book](quest-book.md)) resolvió la escala, la historia y el hub, pero las 93 guías seguían siendo listas de nodos con un texto largo cada una. La v3 las reemplaza de a poco por **cadenas** (sectores): capítulos con una entrada, un tronco de hitos, ramas, consejos, secretos, un encargo y una cumbre, dibujados con la forma de su tema. Esta primera fase trae el motor y los dos ejemplares que pidió Elias, Create y Ars Nouveau, ya a la escala de ATM10; el resto del pack sigue el [plan de cobertura](quest-coverage-plan.md).

- Catálogo de todo lo que FTB Quests 2101.1.34 puede expresar, con la decisión de cada elemento: [ftbquests-2101-features](../research/ftbquests-2101-features.md).
- Voz y reglas de texto: [quest-copy](quest-copy.md).
- Formato de un archivo de cadena: [content/sectors/README.md](../../content/sectors/README.md).

## Qué hay

| Pieza | Qué hace |
|---|---|
| `content/sectors/sector_*.json` | Una cadena por archivo: quests con rol, tareas, posición y texto en los dos idiomas; figuras, paneles, arte y enlaces |
| `tools/quest_engine.py` | Compila las cadenas: gramática de nodos, presets, texto enriquecido, imágenes, curvas, recompensas, tablas, tema y validación |
| `tools/generate_quests.py` | Arma el libro entero (historia, guías, cadenas, hub) y escribe `pack/config/ftbquests/quests` y los recursos del companion |
| `content/quest_book.json` | Motivos (colores y formas por tema), colores por rol, ritmo de recompensas, las 18 tablas y los nombres de las formas |
| `tools/build_quest_placeholders.py` | Texturas provisorias del arte pedido; nunca pisa un archivo que ya existe |
| `tools/test_sector_book.py`, `tools/check_guides.py` | Contratos de las cadenas; ítems, logros, estructuras, criaturas, teclas y nombres contra los JAR fijados; Almost Unified |
| `tools/mod_facts.py`, `tools/recipe_of.py` | Para redactar: ítems con nombre EN/ES, logros, recetas, estructuras y documentación de un mod, y las recetas nativas de cualquier ítem, leídos de los JAR fijados |
| `tools/format_sector.py` | Deja cada archivo de cadena en el mismo formato (una línea por figura e imagen, un bloque por quest), para que los cambios de varios redactores se lean bien |

Los IDs salen de claves semánticas: `stable_id("quest:" + clave)`. Reusar la clave de una guía conserva el progreso de quien ya la había completado.

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
- **Tamaño.** De 45 a 90 quests por capítulo (el círculo de Ars llega a 110); un mod más grande se parte por etapas, y sus complementos van a un capítulo propio. La meta por mod es igual o mayor que ATM10 cuando ATM10 lo tiene ([plan de cobertura](quest-coverage-plan.md)). Referencias (`research/quests/chapter-stats.json`): Create tiene 89 quests en ATM10, 102 en FTB Evolution y 109 en Craftoria; Ars Nouveau, 130, 57 y 91.
- **Mínimos que prueba `test_sector_book.py`:** una entrada, una cumbre y un encargo; al menos tres hitos, dos consejos y dos secretos; consejos y notas, no más de un cuarto del capítulo.

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

- **XP por rol**, multiplicada por la XP de guía del acto (5, 8, 10, 12, 15, 15): entrada, paso, rama y secreto ×1; hito ×2; jefe ×3; cumbre ×5; consejos y notas, nada. Las cinco cadenas pagan 2.455 puntos (las diez guías que reemplazan pagaban 1.277). El libro entero da 17.654 puntos, cerca del tope de 18.000 que fija `test_quest_book.py`: con el plan de cobertura hay que decidir si la XP pasa a hitos y cumbres o sube el tope.
- **18 tablas, tres por acto** (`reward_tables/`): la caja del acto (`crate_N`), un premio a elección (`choice_N`) y cosas sueltas (`supplies_N`). Las cajas se llaman de rescate, de taller, de correo, de expedición, del Arca y del Solsticio; dan 2 tiradas en los actos I a IV y 3 en V y VI, cada una con su color, y sólo la del Solsticio brilla. No caen de criaturas.
- **Dónde:** hito → a elección; secreto → al azar; jefe → el contenido de una caja; cumbre y encargo → la caja cerrada, que se abre con clic derecho.

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

## Validación

Estáticas, sobre `origin/main` b7ed842 más esta rama:

- `python tools/generate_quests.py --check`: 96 capítulos (8 de historia, 83 guías, 4 cadenas y el hub), 1.906 quests y 18 tablas; IDs globales, grafo sin ciclos y paridad EN/ES.
- `tools/test_generate_quests.py` (36 pruebas: los digests semánticos de la historia no se mueven), `tools/test_quest_book.py` (14: libro, hub, recompensas, tema y que la historia no hable de sí misma) y `tools/test_sector_book.py` (26: texto enriquecido, geometría, curvas, gramática, estándar de cadena, cada función del catálogo usada, tablas con IDs enteros exactos, cajas moderadas y sin salidas con puerta, formas y textos del companion).
- `tools/check_guides.py`: 83 guías y 4 cadenas, 1.734 quests, sin errores contra los JAR fijados y Almost Unified.
- `tools/check_runtime_content.py`: la auditoría KubeJS cubre los 1.364 ítems del libro, las tablas incluidas.
- Las 34 comprobaciones de Python de `verify.yml` (con `test_sector_book.py` y `build_quest_placeholders.py --check` agregados) pasan sobre el estado final (`questbook-v3/ci/ci-5.log`, fuera del repo).
- El companion compila (`gradlew --offline build`) y lleva el tema, las cinco formas, las texturas y los textos de `ftbquests`.

En un servidor desechable (`server-questbook3`, 272 JAR de servidor y el companion de la rama, 4 GB de heap, uno por vez; borrado después). Recibos en `E:/Elias/Codex/Entrelumen-ssd/questbook-v3/qa/`:

- **Antes del último rebase** (d2ec472 sobre 56c3dd8, `qb3-pre-rebase.json`): FTB registró «Loaded 6 chapter groups, 96 chapters, 1906 quests, 18 reward tables» (los 5 grupos más el de FTB) y tablas de traducción para dos idiomas, **sin ningún aviso de FTB Quests ni de FTB Library**. La auditoría KubeJS pasó con los 1.364 ítems del libro, `ftbquests:lootcrate` incluido. Cierre limpio (código 0). FTB volvió a guardar 116 archivos en su formato; leídos de vuelta (`roundtrip-qb3-pre-rebase.json`), **las 41.663 claves generadas coinciden**: capítulos, quests, tareas, recompensas, 1.195 imágenes, enlaces, las 18 tablas con sus 88 entradas, los 45 `table_id` exactos, presets y `data.snbt`; sólo omite valores por defecto (cantidad 1, peso 1, tamaño 1, tipo ítem en tablas). Los idiomas no pierden claves.
- **Cabeza final** (sobre b7ed842, `qb3b.json`): mismo registro de FTB, sin avisos, y la auditoría KubeJS pasa (1.364 ítems, 31 pares receta/salida). El cierre no fue limpio: con otros dos servidores arrancando a la vez y la memoria agotada, el primer tick (ComputerCraft reconstruye las pestañas creativas, `qb3b-watchdog-crash.txt`) tardó más de 60 s y el watchdog cortó el servidor. Es del entorno, no del libro, pero sin ese cierre no hay segunda lectura de ida y vuelta.
- Los tres errores del log son anteriores al libro: una etiqueta de Pam's y dos fluidos de experiencia en data maps (Ender IO, Reliquary).

Vistas previas a zoom 16, fuera del repo, en `E:/Elias/Codex/Entrelumen-ssd/questbook-v3/previews/`: el hub, las cuatro cadenas y el acto I, cada uno en un mundo nuevo (`-fresh`) y completo (`-done`), y en `panels/` el panel de quests con texto enriquecido en los dos idiomas.

## Límites

- Nada de esto se vio en el cliente. Las formas propias, las líneas hechas con rectángulos finos rotados, los rótulos con fuente rúnica, las curvas de Bézier, las imágenes en descripciones y los enlaces y notas dentro del texto están leídos del código y cargados en un servidor, pero no mirados.
- Las vistas previas salen de un renderer propio que imita la geometría de FTB a zoom 16. No son capturas.
- Las otras 83 guías siguen en formato v2 hasta convertirse en cadenas.
