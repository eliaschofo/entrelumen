# Packs de referencia: quests, recetas y progresión

Investigación del 24 de septiembre de 2026, pedida por Elias: mirar las quests de ATM10 y FTB Evolution, sacarles foto, revisar las recetas cambiadas de los packs de referencia (Craftoria y los otros dos) y proponer una progresión propia para ENTRELUMEN. Todo es inspiración y análisis propio. **No se copia narrativa, quests, scripts, recetas ni arte de ningún pack** (ver `AGENTS.md`). Este documento describe estructuras con palabras propias y cifras medidas; los nombres de ítems o capítulos aparecen sólo para ubicar el ejemplo.

La propuesta del final no está implementada.

## Fuentes y método

| Pack | Versión | De dónde salió | Qué se leyó |
|---|---|---|---|
| ATM10 | 8.1, instancia local | `G:/curseforge/Instances/All the Mods 10 - ATM10`, sólo lectura: no se escribió nada | `config/ftbquests/quests` (66 capítulos, 63 tablas de recompensa, idioma `en_us`) y `kubejs` (207 scripts, unos 780 KB; `data/`) |
| FTB Evolution | 1.43.1, pack 125, versión 100487 | API oficial `api.feed-the-beast.com/v1/modpacks/modpack/125/100487`; se bajaron sólo `config/ftbquests`, `kubejs` (sin sonidos ni idiomas ajenos) y `datapacks/ftb` | 3.573 archivos, 39,6 MB; cada uno verificado contra el SHA-256 del manifiesto, 0 fallas. Los 40 capítulos coinciden en SHA-1 y SHA-256 con la [evidencia del 23/9](../verification/scale-ftb-evolution-1.43.1.json). El manifiesto en sí cambia de hash entre consultas porque trae contadores vivos (instalaciones, jugadas) |
| Craftoria | 1.36.0, archivo CF 8850018 | ZIP oficial del CDN de CurseForge, SHA-256 `c87a6dd7…084c`, igual al anotado en [scale-reference](../design/scale-reference.md) | `overrides/config/ftbquests` y `overrides/kubejs` (130 scripts, unos 420 KB; `data/`), más las texturas de quests del mismo ZIP |

Todo lo bajado vive fuera del repo, en `E:/Elias/Codex/Entrelumen-ssd/research/` (`craftoria/`, `ftb-evolution/`). C: tenía 31 GB libres; no se usó.

Método:
- Un lector SNBT propio convierte cada capítulo en estructura: posición, forma, tamaño, dependencias, tipos de tarea y de recompensa, banderas (opcional, oculta, invisible) y largo de la descripción. Los conteos coinciden con los de `scale-reference.md`: 4.790, 2.072 y 1.136 quests.
- Las recetas se analizaron leyendo los scripts y contando operaciones (quitar, reemplazar, agregar). Las tablas de abajo describen patrones, no código.
- Licencias: ATM10 declara «All Rights Reserved» en 141 scripts y en una quest invisible por capítulo (54 de sus 62 quests invisibles son ese aviso). Craftoria y FTB Evolution no publican licencia en sus scripts, así que valen los mismos límites: nada se reutiliza.

## Fotos de las quests

Carpeta: `E:/Elias/Codex/Entrelumen-ssd/research/quests/`. Hay una imagen por capítulo de cada pack y una vista general por pack. Cada nodo usa la forma, el tamaño y la posición del `.snbt`, con el ícono dibujado desde las texturas de los JAR de la instancia ATM10, el JAR de Minecraft 1.21.1 y los assets de cada pack.

Cómo leer las fotos:
- El borde del nodo indica la tarea: azul entregar ítem, gris checkmark, rojo matar, violeta logro, turquesa viaje, naranja observar, amarillo fluido, energía o custom.
- El punto de la esquina indica la recompensa: dorado tabla de loot, blanco ítem fijo, rosa comando, verde sólo XP.
- Contorno cortado: quest opcional. Línea amarilla cortada: dependencia «una de varias». Punteado gris: línea oculta en el juego.
- A la derecha de cada foto hay un resumen de tareas, recompensas y formas.

| Pack | Vista general | Inicio | Mitad | Endgame |
|---|---|---|---|---|
| ATM10 | `atm10/00-overview.png` | `atm10/mainquestline_part_1.png` | `atm10/allthemodium.png`, `atm10/mekanism.png` | `atm10/chapter_2_the_star.png` (la estrella), `atm10/achapter_2r_6the_atm_star.png` (creativos) |
| FTB Evolution | `ftbevo/00-overview.png` | `ftbevo/the_beginning.png` | `ftbevo/modern_industrialization.png`, `ftbevo/the_travelers_quest.png` | `ftbevo/ftb_pyramid.png` |
| Craftoria | `craftoria/00-overview.png` | `craftoria/welcome.png` | `craftoria/mi_electric_age.png`, `craftoria/applied_energistics.png` | `craftoria/endgame.png`, `craftoria/completionist.png` |
| ENTRELUMEN, para comparar | `entrelumen/00-overview.png` | `entrelumen/a_light_among_ruins.png` | `entrelumen/voices_of_the_atlas.png` | `entrelumen/last_horizon.png` |

Se renderizaron los 66, 40 y 28 capítulos; los de la tabla son los representativos. También quedan:
- los datos estructurales en `quests/data/*.json`;
- las cifras por capítulo en `quests/chapter-stats.json`;
- las herramientas en `research/tools/` (`extract.py`, `render.py`, `stats.py`, `patterns.py`), que sirven también para fotografiar nuestro libro después de cada cambio.

## Números comparados

| | ATM10 | FTB Evolution | Craftoria | ENTRELUMEN hoy |
|---|---|---|---|---|
| Capítulos / quests | 66 / 4.790 | 40 / 2.072 | 28 / 1.136 | 7 / 171 |
| Quests por capítulo (mediana, máximo) | 56, 252 | 46, 145 | 26,5, 120 | 26, 29 |
| Profundidad de la cadena más larga (mediana, máximo) | 8,5, 26 | 9, 50 | 5,5, 21 | 6, 10 |
| Opcionales | 12% | 33% | 4% | 27% |
| Sólo checkmark | 3% | 4% | 7% | 23% |
| Ocultas hasta tener las dependencias | 179 | 5 | 0 | 0 |
| Nodos grandes (tamaño ≥ 1,5) | 11% | 14% | 10% | 0 |
| Imágenes en capítulos / en descripciones | 1.201 / 479 | 856 / 40 | 642 / 63 | 0 / 0 |
| Tareas de ítem que aceptan alternativas (filtro o tag) | 430 | 240 | 67 | 0 |
| Sin recompensa | 5% | 5,5% | 20% | 100% |
| Tablas de recompensa (cajas de loot) | 63 (1) | 36 (26) | 15 (0) | 0 |
| Densidad (quests por celda de grilla, mediana de los capítulos) | 0,21 | 0,21 | 0,24 | 0,06 |

ATM10 y FTB ordenan el libro en grupos de capítulos: Main Questline, un grupo por disciplina (Tech, Magic, Storage, Logistics, Resources, Exploration) y algunos capítulos sueltos arriba (bienvenida, consejos, tablero de recompensas). Craftoria también agrupa por disciplina, pero no tiene línea principal: su libro es un menú de mods más un capítulo final.

## Patrones de diseño de quests

### Estructura de capítulos y densidad

- **Un capítulo por mod, uno o dos por familia grande.** ATM10 parte Mekanism en dos (máquinas y reactores) y Modern Industrialization en cuatro edades. FTB junta Tech Mods y Magic Mods en grupos grandes. Craftoria parte Mekanism en industria, energía y logística. El capítulo típico tiene de 30 a 90 quests; los de 150–250 son listas de colección: abejas, árboles, semillas de Mystical Agriculture, Just Dire Things.
- **Tronco y ramas.** El capítulo de mod casi siempre tiene 1 o 2 raíces (mediana 2, 1 y 2) y un tronco de 8 a 15 pasos. Lo opcional cuelga hacia un costado: mejoras, herramientas, variantes. En `ftbevo/modern_industrialization.png` el tronco recorre las edades de MI hasta 50 pasos.
- **Densidad.** Los nodos de tamaño 1 van a 1–1,5 celdas entre sí, en una grilla de media celda (ATM10 y FTB). Craftoria usa una grilla de 0,05, casi libre, para componer paneles.
- **Capítulos de colección.** Son de raíces sueltas, sin cadena: armaduras básicas de ATM10 (128 raíces, 108 opcionales), Artefactos, Reliquias de Craftoria (71 raíces) y los bestiarios de FTB (La Cacería Salvaje, 61 raíces) y ATM10 (el tablero de recompensas). Funcionan como lista de pendientes opcional.

### Cómo guían al jugador

- **Bienvenida sin juego.** ATM10 tiene un nodo central enorme (pentágono de tamaño 3) que se completa solo al entrar al mundo, rodeado de 4 checkmarks de ajustes, reclamos, equipos y hogar que dan XP simbólica. Dos imágenes: logo y un botón de Discord que abre un enlace. FTB repite la idea con 4 checkmarks. Craftoria arma una bienvenida radial de 14 checkmarks que explican los sistemas del pack: eventos lunares, estaciones, habilidades, World Tiers, Ultimine, reclamos, emotes. Ninguna pide craftear.
- **Tronco y centro.** FTB hace un tronco corto (árbol, pico, edad de piedra) que desemboca en un nodo centro con unos diez rayos. Cada rayo presenta un sistema o abre un capítulo: magia, comida, energía, mochilas, aldeanos, habilidades, exploración. Después el tronco sigue al Nether, las blazes, el End y el dragón. ATM10 hace lo mismo con un tronco de madera, piedra, metal, diamante, Nether y Wither: debajo cuelgan las ramas opcionales (hornos, generadores) y a la derecha los jefes. Ver `ftbevo/the_beginning.png` y `atm10/mainquestline_part_1.png`.
- **Bifurcaciones.** ATM10 tiene 740 nodos con dos o más hijos y 701 uniones. FTB, 389 y 215. Las dependencias «una de varias» son pocas (65, 36, 5) y se usan para rutas alternativas. En la estrella de ATM10 la misma pieza se acepta de Create o de MI (un brazo mecánico o un motor avanzado; un quemador de blaze o una barra de combustible), y la receta acepta lo mismo.
- **Opcionales.** FTB marca un tercio de las quests como opcionales: casi todas las ramas laterales. ATM10 sólo el 12%, porque usa capítulos enteros como opcionales. Craftoria casi no marca ninguna.
- **Ocultar para no abrumar.** ATM10 esconde 179 quests hasta que se vean sus dependencias, sobre todo en almacenamiento, PneumaticCraft, Twilight Forest y Forbidden & Arcanus. Revela el árbol a medida que se avanza.
- **Tareas flexibles.** Muchas tareas de ítem usan filtros: «cualquier hacha de diamante», «cualquier tipo de cable de energía», «cualquier horno mejorado». Son 430 en ATM10 y 240 en FTB. Evitan obligar a usar un mod concreto.
- **Imágenes.**
  - Carteles de título por capítulo con tipografía gruesa.
  - Renders de jefes y criaturas al lado de su quest.
  - Capturas de multibloques armados (ATM10 las pone en 479 descripciones).
  - Craftoria compone tarjetas: una textura grande de fondo con 4 o 5 quests sin forma encima (ver `craftoria/endgame.png`).
- **Consejos aparte.** ATM10 y FTB tienen un capítulo de consejos o ítems útiles con raíces sueltas: ascensores, bolsas de dormir, imanes, papelera. No bloquean nada. Craftoria los mete en la bienvenida.
- **Recuperación.** En el endgame de FTB, cuatro checkmarks devuelven las capas de la pirámide si se perdieron. Craftoria regala el libro de quests al entrar. Nosotros no, por diseño.

### Tipos de recompensa

| Pack | Mezcla | Detalle |
|---|---|---|
| ATM10 | ítem 2.955, XP 1.960, tabla aleatoria 1.046, niveles 910, caja 262, a elección 95 | Tablas temáticas por mod (bolsas de semillas por tier en Mystical Agriculture, piezas de Draconic por tier). Muchos ítems básicos: hierro, carbón, libros encantados, manzanas doradas |
| FTB Evolution | XP 1.601, caja de loot 768, ítem 699, a elección 356, comando 57 | 26 de sus 36 tablas son cajas que se abren como ítem: gemas, módulos de Draconic, paquetes temáticos. Los comandos dan puntos de un árbol de habilidades (Pufferfish's Skills) y resetean ramas |
| Craftoria | tabla aleatoria 547, ítem 450, niveles 77 | Tablas escalonadas por mod (Mekanism T1–T3, JDT 1–4). Trofeos (Trofers) como premio de colección. El 20% no da nada |

- Lo que funciona: premios temáticos del capítulo, que ayudan a dar el siguiente paso (el cable del próximo tier, una semilla), y premios de elección, donde el jugador elige entre tres.
- Lo que no:
  - Premios que saltan tiers: en el capítulo 1 de ATM10, guardar la primera energía regala cables del tier máximo de Mekanism.
  - Cajas de loot a granel: tapan el inventario y abaratan el hallazgo.

### Formas, tamaños y grillas

- **Una gramática por pack.**
  - Los tres conservan los presets de FTB Quests: meta = hexágono de tamaño 2, info = engranaje de tamaño 1, normal = cuadrado de tamaño 1.
  - ATM10 usa el círculo por defecto, el engranaje de tamaño 2 para jefes y logros grandes, octógonos para metales y ruedas de piezas, el pentágono para la estrella y el corazón para cosas simpáticas (el sniffer).
  - FTB usa hexágonos grandes para hitos, diamantes para combate y colección, y engranajes para logros e información.
  - Craftoria usa círculo en casi todo y nodos sin forma sobre imágenes.
- **Tamaño = jerarquía.** El 10–14% de los nodos es grande (≥ 1,5). Son los hitos del tronco, las entradas de capítulo y los jefes. El resto va en tamaño 1; pocos nodos chicos.
- **El layout dibuja el objetivo.**
  - En el capítulo de la estrella de ATM10, los nodos forman una estrella: un pentágono de tamaño 5 en el centro, un anillo de diez octógonos de tamaño 2,5 (los componentes) y afuera un anillo de entradas por mod, rectángulos redondeados de tamaño 2.
  - En FTB la pirámide final es una pirámide de cuadrados, con las capas como filas.
  - Es el recurso visual más fuerte que se vio.
- **Grilla.** Media celda en ATM10 y FTB, lo que permite alinear en dos niveles. La grilla casi libre de Craftoria es para composiciones sobre imágenes.

### Cómo marcan hitos y endgame

- **Jefes como mojones.** Wither, Warden y dragón aparecen en el tronco del primer capítulo con forma y tamaño distintos (ATM10), o cierran el primer capítulo (FTB: el dragón, hexágono grande).
- **Capítulo final propio.**
  - ATM10: el capítulo de la estrella, y otro de creativos que se abre con ella.
  - FTB: la pirámide, que pide construir una estructura física.
  - Craftoria: un capítulo de «eterno» con tres tarjetas (jefes, materia cósmica, celdas infinitas) y otro de «completista», con un trofeo por capítulo terminado y una copa final.
- **Colección como meta secundaria.** El completista de Craftoria depende del final de cada capítulo (18 dependencias entre capítulos) y paga con trofeos, no con poder.

### Qué hacen bien y qué evitar

Bien:
- El layout como metáfora del objetivo final (estrella, pirámide).
- Bienvenida informativa aparte del tronco: el primer paso real es «pegale a un árbol».
- Un centro con rayos hacia capítulos, en vez de encadenar todo.
- Tareas con filtro que aceptan equivalentes de varios mods.
- Revelar ramas a medida que se abren, para que el libro no asuste.
- Imágenes de multibloques en la descripción.
- Premios temáticos y premios a elección.
- Checkmarks de recuperación en el endgame.

Evitar:
- Capítulos-lista de 150–250 quests que inflan el contador: una quest por abeja, una por semilla.
- Capítulos placeholder de 1 quest (Craftoria: cocina y cultivo) y capítulos de prueba publicados (FTB: un capítulo de prueba de recompensas).
- Tareas o premios con ítems inexistentes (ATM10 y FTB tienen `missing_item`).
- Descripciones de varios miles de caracteres: la del altar de la estrella supera los 3.600.
- Cadenas de 50 pasos sin respiro.
- Premios que saltan tiers.
- Quests invisibles con avisos legales o notas internas.

## Recetas cambiadas

### ATM10

| Patrón | Qué hacen | Lectura para ENTRELUMEN |
|---|---|---|
| Metales propios por dimensión como puerta | Allthemodium, Vibranium y Unobtainium salen de dimensiones propias (minera, The Other, The Beyond) y marcan la mitad y el final | Equivale a nuestra lente en bruto y a los materiales de Aether y Twilight: la puerta es un lugar, no un número |
| Gatear el salto de tier, no el mod | Las piezas del MekaSuit piden la armadura de Unobtainium; los módulos Meka (fortuna, seda, excavación) piden herramientas de Allthemodium o Vibranium y circuitos altos; la armadura cuántica de Advanced AE pide Unobtainium; la carta de alcance infinito (addon de AE2) pide engranajes de aleación | Es lo que ya hacemos con un componente de acto en un casillero |
| Rebalance numérico documentado | MekaSuit con más capacidad y más consumo; Meka-Tool con mucho más daño; solares de Mekanism ×3–4 para igualar Powah. Está escrito en un `.md` junto al script | Nuestras familias ya documentan; falta anotar balance numérico cuando se toque |
| Quitar atajos | Replicador de MI quitado; canteras de MI rehechas con salidas propias; unas 340 llamadas de baja de recetas, las más frecuentes por ID en Railcraft, Deeper and Darker, Mekanism e Industrial Foregoing | Coincide con lo nuestro: replicador de MI y estrellas del Nether por atajo quitados |
| Unificación por scripts | Scripts para minerales, lingotes, engranajes, cultivos, aserrado, herramientas, coque y creosota | Nosotros usamos Almost Unified con prioridades; es más barato de mantener |
| Multibloques propios | Con la API de MI arman un encantador rúnico, una forja automática, un crisol y el altar de la estrella. También multibloques de Mekanism en Modular Machinery | Nuestro Arca cumple ese rol, con un mod propio |
| Ítem final | La estrella ATM: diez componentes vitrina, la mayoría fusiona de 6 a 9 ítems de endgame de más de 30 mods (jefes, reactores, redes, magia), más decenas de bloques de aleación y de estrellas del Nether comprimidas. Se arma en el altar | Vitrina del pack completo. Es impresionante y es un muro: obliga a automatizar casi todo |
| Creativos | Cada ítem creativo pide la estrella más el tier máximo del mod (celdas, jarra de source, cubo de energía) | Igual que nuestros creativos con Luminosidades |
| QoL por receta | Marcos invisibles, concreto con agua, recetas que limpian datos de un ítem, lista de bloqueos para servidores | Útil, sin impacto en progresión |

### FTB Evolution

| Patrón | Qué hacen | Lectura para ENTRELUMEN |
|---|---|---|
| Unificación con mod de materiales propio | Casi todo metal pasa a su mod de materiales: cientos de reemplazos y un datapack con unas 2.000 recetas reescritas (MI 736, IE 486, Oritech 224, Mekanism 179) | Caro de mantener; Almost Unified nos alcanza |
| Pocas puertas por receta | Casi no hay gates en el medio: la progresión la dan las quests y la estructura final | Menos control; el orden depende del libro |
| Integración cruzada | Rutas que atan mods: silicio por el horno de aleación de Ender IO, un gas propio en Mekanism, cristales en máquinas de otro mod | Es el espíritu de nuestros componentes |
| Componentes propios de endgame | Unos 30 ítems propios de rareza épica. Cada componente principal fusiona 3–6 mods en una máquina concreta (colisión de partículas de Oritech, cámara de presión de PneumaticCraft, ensamblador de MI) | Se parece a nuestros componentes de acto, pero concentrado al final |
| Ítem final como estructura física | Cuatro capas de bloques propios (9 + 25 + 49 + 81) bajo un faro de nivel 4. Se activa con una estrella del Nether y da la Ascensión Creativa, con fuegos artificiales la primera vez | Muy cercano a nuestra Arca: la meta se construye en el mundo |
| Creativos | Tier básico del mod + Ascensión Creativa | Mismo esquema que el nuestro |
| Sistemas alrededor | Árbol de habilidades con puntos por quest y sigilos de reset; rangos de Ultimine escalonados (16 → 128 bloques); mobs más fuertes en ciertos biomas de cuevas y dimensiones | Lo del Ultimine por rangos es una idea para nosotros (ver la propuesta) |

### Craftoria

| Patrón | Qué hacen | Lectura para ENTRELUMEN |
|---|---|---|
| MI como columna | Reescribe el ensamblador de MI para que dé 2 o 3 unidades por operación en circuitos, motores y componentes: premia automatizar sin castigar lo manual | Buena idea: el camino automático rinde más, el manual sigue vivo |
| Equivalencias por tag | Un tag de «casco avanzado» acepta el marco avanzado de Industrial Foregoing, el casco básico de MI o el instalador ultimate de Mekanism. Just Dire Things tier 2 lo pide | La mejor idea anti-tedio que se vio: la puerta existe, pero el jugador elige con qué mod pasarla |
| Cadenas de armadura | La armadura cuántica de AE pide el MekaSuit; el MekaSuit pide circuitos de Mekanism Extras; la armadura cuántica de MI se funde con moldes y un fluido propio | Encadena los mejores equipos entre sí |
| Escalones cruzados | Reactores y paneles de Powah piden circuitos de Mekanism y plástico | Dependencia tecnológica suave |
| Jefes con loot extra | Cuatro jefes de Bosses of Mass Destruction, configurados desde Apotheosis, sueltan como loot extra cuatro esencias elementales que se usan en el endgame | Materiales de jefe para el final, como pide nuestra biblia |
| Endgame | Materia cósmica → inyector → replicadores de MI rehechos. Celdas infinitas de AE2 con esencias y decenas de máquinas. Boss Rush con Gateways | Varios finales paralelos, sin un ítem único |
| Bajas globales con motivo | Cargadores de chunks (AE2, Mekanism, PneumaticCraft), la bomba de Industrial Foregoing, AIOTs, carbón chico | La baja de cargadores de chunks es por rendimiento de servidor; para tener en cuenta |
| Multibloques propios | Incubadora de geodas, sintetizador de modelos, laboratorio de ooze, acelerador de partículas, turbina de plasma grande (API de MI) | — |
| Trofeos | Trofeos de jefes y trofeos por capítulo terminado | Idea para la vitrina del lote en Solsticio |

### Lo común

- **Qué gatean:**
  - Saltos de tier: armaduras finales, módulos, canteras, teletransporte, vuelo, granjas de mobs, reactores, replicadores.
  - Lo básico de cada mod queda libre.
- **Materiales propios:**
  - ATM10: metales por dimensión y diez componentes de estrella.
  - FTB: unos 30 componentes épicos.
  - Craftoria: esencias de jefe, materia cósmica y un fluido cuántico.
- **Cadenas típicas:**
  - Mekanism: circuitos, aleaciones, fisión, fusión, SPS y MekaSuit.
  - AE2: prensas, procesadores, controlador, celdas grandes y cuántica.
  - Create: latón, mecanismo de precisión, ensamblaje secuenciado.
  - Magia: altar, espíritus, rituales.
  - Los packs atan el tope de cada cadena a otra: el MekaSuit a los metales propios o a Mekanism Extras, la armadura cuántica de AE al MekaSuit.
- **Unificación:** los tres unifican. El modo va de scripts a un mod propio de materiales; Almost Unified es el menos costoso.
- **Recetas baratas quitadas:** replicadores, estrellas del Nether por atajo, alquimia que duplica, canteras gratis y cargadores de chunks.
- **Ítem final:**
  - ATM10: un objeto vitrina (la estrella).
  - FTB: una estructura (la pirámide).
  - Craftoria: varios hitos paralelos.
  - En los tres, los creativos salen del ítem final más el tier máximo de cada mod.

## ENTRELUMEN hoy

- **Componentes de acto:** 16 más 6 módulos del Arca, definidos en `content/integration-design.json`. Cada uno se craftea sin forma con materiales de 2–3 mods y un componente anterior. El resultado es un grafo sin ciclos: la lente en bruto lleva al Marco de Calibración, y de ahí a la Matriz Viva, la Matriz de Enrutamiento y la Lente Espectral. Es una estrella ATM repartida a lo largo de los actos en vez de concentrada al final. Es la fortaleza del diseño.
- **Escalonado:**
  - Unas 80 recetas nativas llevan un componente en un solo casillero, reemplazando un material repetido. Hay una familia por lote de mods: industrial, arcana, Apotheosis, QoL, RFTools, pingpong y pingpong4. El script comprueba al cargar que el componente siga ahí.
  - Además, 1.421 variantes de recursos (Botany Pots Tiers, JAMD, Modular Bees) cambian catalizadores arbitrarios (estrella del Nether, manzana encantada) por componentes.
  - Ver [resource-balance](../design/resource-balance.md) y [mod-pingpong](../design/mod-pingpong.md).
- **Las recetas de componentes son públicas.** No miran el acto ni el equipo: la puerta es de material, no un permiso narrativo. Lo decidió `resource-balance.md` y la propuesta lo respeta. No hay gamestages como en la pirámide de FTB.
- **Familias funcionales ya coherentes:**
  - Jetpacks básicos → Regulador de Energía.
  - Teletransporte e inalámbrico → Matriz de Enrutamiento.
  - Vuelo → Carta de Horizonte.
  - Granjas de mobs → Cápsula de Ecosistema.
  - Bombas y reactores → Sello de Contención.
  - Tiempo y réplica → Motor de Renovación.
  - Endgame → Bus del Arca.
- **Final y posgame:** el Arca forja la Llave de Luz. En Solsticio se consiguen las reliquias y las Luminosidades, que craftean los creativos. El Lingote Luminoso hace el mejor equipo.
- **El libro:**
  - 171 quests en 7 capítulos, sin grupos, sin recompensas FTB (los premios salen de la entrega autoritativa del servidor) y sin imágenes.
  - Nodos de tamaño 0,75 muy separados: la densidad es un cuarto de la de las referencias.
  - Formas: cuadrado para ítem, hexágono para entrega de campaña, círculo para checkmark.

Brechas encontradas al cruzarlo con las referencias:
1. **La Matriz de Enrutamiento gatea el controlador de Refined Storage, pero no el de AE2.** La paridad «misma entrada que AE2» de `mod-pingpong.md` sólo es cierta a medias: AE2 igual pide procesadores del inscriptor, pero su controlador está libre.
2. **Las canteras no tienen una sola puerta.**
   - Con Lente Espectral (IV): la cantera eléctrica de MI, el láser de Industrial Foregoing y el taladro profundo de Oritech.
   - Con Regulador de Energía (III): la tarjeta de cantera de RFTools.
   - Libres: el Digital Miner de Mekanism y los mineros dimensionales de Occultism.
3. **El Sensor de Inventario no gatea ninguna receta**, y el Núcleo de Propagación sólo gatea macetas. Son componentes sin peso fuera de su entrega.
4. **Mekanism y AE2, las dos columnas técnicas, casi no tienen escalones propios.** Los packs de referencia los gatean en el tope: MekaSuit, módulos, fisión y fusión, SPS, armadura cuántica.
5. **El libro no tiene hitos grandes, imágenes ni recompensas**, y los checkmarks son el 23% de las quests contra el 3–7% de las referencias.

## Propuesta de progresión para ENTRELUMEN

Numeración de actos de la [biblia](../design/story-bible.md) del 24/9: V = «El Arca», VI = Solsticio. El código todavía usa la numeración vieja: los módulos del Arca figuran como acto 6. La renumeración está en curso en `feature/acts-renumber`, así que cada fila que se implemente toma el acto nuevo recién cuando esa rama entre.

### Principios

1. **Gatear saltos de tier y funciones, no mods.** Una familia funcional (canteras, vuelo, teletransporte, reactores, granjas de mobs, réplica) usa el mismo componente en todos los mods. Si un mod suma una cantera, hereda la puerta de las canteras.
2. **Un componente en un casillero**, reemplazando un material repetido de la receta nativa, como hoy. Nada de 4× componentes ni de multiplicar la receta. En multibloques se gatea la pieza que va una sola vez o pocas (controlador, puerto), nunca la carcasa que se usa por decenas.
3. **Materiales de Heliodor = componentes de acto, más la línea de la lente.** No inventar un metal nuevo. La lente es el hilo material de la historia: lente en bruto (I) → Marco de Calibración (II) → Lente Espectral (IV) → Corazón de Heliodor (jefe del IV) → Lingote Luminoso y Luminosidades (VI). Si en el futuro hace falta un material de ruina, que sea una pieza de la ruina del acto, no un mineral a farmear.
4. **Alternativas por tag, a lo Craftoria**, donde la puerta sea genérica: por ejemplo, un tag de «casco de máquina avanzado» con equivalentes de IE, MI y Mekanism para recetas de terceros. Los componentes siguen siendo únicos.
5. **Nada que gatee lo que necesita el propio componente.** Ver la lista de «no gatear» de abajo.
6. **El mundo escala solo.** La historia ya sube el World Tier de Apotheosis. Se puede sumar otra palanca de comodidad, no de poder, como el Ultimine por rangos de FTB (ver las decisiones del final).

### Por acto

| Acto | Ruina y jefe | Componentes | Ya escalonado | Proponer además | Libre a propósito |
|---|---|---|---|---|---|
| I · Una luz entre ruinas | Patio del Atlas | Lente en bruto (hallada) | Nada | Nada. Sólo la brújula recrafteable barata | Todo lo vanilla, Create básico, cocina, almacenamiento, construcción |
| II · Los oficios perdidos | Taller hundido | Marco de Calibración, Acoplador de Energía, Matriz Viva, Raciones | Placa solar y bobina de New Age, CAD de Psi, granja de hierro, macetas con tolva, portal minero de JAMD | **Infusor metalúrgico de Mekanism ← Marco de Calibración:** la entrada a Mekanism pasa por el taller. **Automatización de Ars Nouveau** (amuletos de starbuncle, wixie y drygmy) **← Matriz Viva:** se gatea automatizar la magia, no aprenderla | La primera energía (generador de carbón de Actually Additions, Powah inicial, dínamo de IE): el Acoplador necesita FE para cristalizar la restonia |
| III · Rutas de intercambio | Invernadero-domo | Matriz de Enrutamiento, Núcleo de Propagación, Regulador de Energía, Sensor de Inventario, Núcleo de Manipulación | Jetpacks básicos, mining gadgets, teletransporte (Ender IO, JDT, dislocador), Flux, Ender Storage, controlador y autocrafter de RS, XNet, núcleos de Psi | **Controlador ME de AE2 ← Matriz de Enrutamiento** (paridad real con RS). **Teletransportador de Mekanism ← Matriz** (familia teletransporte). **Jetpack de Mekanism ← Regulador** (familia jetpack). **QIO de Mekanism** (matriz de discos) **← Sensor de Inventario:** almacenamiento inteligente, le da uso al Sensor. **Drones de PneumaticCraft ← Núcleo de Manipulación:** «manos». **Tarjeta de cantera de RFTools: pasar a Lente Espectral** (IV), por coherencia de familia | AE2 sin controlador (red chica), LaserIO, Integrated Dynamics y Modular Routers básicos: son insumos de los componentes. Waystones y almacenamiento QoL |
| IV · Las voces del Atlas | Observatorio del risco y Templo de la Luz Sagrada; **Sun Spirit** (Corazón de Heliodor) | Lente Espectral, Carta de Horizonte, Cápsula de Ecosistema, Sello de Contención | Canteras de MI, Industrial Foregoing y Oritech; vuelo (gravipechera, exo-jetpack, mejora de vuelo, Rending Gale); spawners y granjas (Hostile Neural Networks, duplicador, spawner con energía); bombas; barra de reactor de New Age; estanterías de Apotheosis | **Digital Miner de Mekanism y mineros dimensionales de Occultism ← Lente Espectral** (cierra la familia canteras). **Puerto del reactor de fisión de Mekanism ← Sello de Contención** (familia reactores; eco del Templo). **Unidades de vuelo del MekaSuit** (jetpack y modulador gravitatorio) **← Carta de Horizonte**, sólo si el MekaSuit no pasa al V (decisión 2) | Las dimensiones en sí: se entra con los portales nativos. El Corazón no gatea ninguna receta: es la reliquia |
| V · El Arca | Sin ruina nueva; el Arca en la base | Bus del Arca, Motor de Renovación, Carta de Habitabilidad; los seis módulos | Reactor nuclear y circuito cuántico de MI, núcleos wyvern y awakened, acelerador de Oritech, banco de la NASA de Ad Astra, capacitores top de Powah e Iron Jetpacks, máquina paradoja y varita del tiempo | **Controlador del reactor de fusión y puerto del SPS de Mekanism ← Bus del Arca** (o el Motor de Renovación para la antimateria, a elegir). **Armaduras tope** (MekaSuit, cuántica de MI, cuántica de Advanced AE) **← Bus del Arca**, para que el equipo luminoso del VI quede arriba. **Altar de despertar de Mystical Agriculture ← Motor de Renovación**. **Drops de jefes en los módulos**, que se construyen una vez por partida, como pide la biblia: estrella del Nether (Wither) en el módulo arcano, aliento de dragón (se embotella en la pelea) en el de exploración, esponja húmeda (Elder Guardian) en el de naturaleza | Cualquier cosa que se use para construir los componentes del V (aleación atómica, PCB, lingote celeste, imperium, procesador de cálculo) |
| VI · Solsticio | Solsticio | Reliquias (Semilla, Terraprisma, Corazón bendecido), Llave de Luz, Luminosidades, Lingote Luminoso | Creativos con Luminosidades; equipo luminoso; 13 creativos no crafteables | **Vitrina opcional en el lote:** trofeos por capítulo terminado, sin poder (idea del completista de Craftoria). Precios que bajan al liberar el Entrelumen, ya decidido | Nada nuevo en recetas: el VI es corto y denso. Lo largo es el posgame |

Balance a decidir con Elias: ¿el MekaSuit y las armaduras cuánticas en el V o en el VI? El Lingote Luminoso tiene que seguir siendo el mejor equipo.

### No gatear: insumos de los componentes

Gatear cualquiera de estos con su componente, o con uno posterior, crearía un ciclo o un muro:

| Mod o sistema | Qué queda libre | Qué componente lo necesita |
|---|---|---|
| Create | Prensa, lámina de hierro, aleación de andesita, latón, mecanismo de precisión | Marco, Acoplador, Núcleo de Manipulación, Lente Espectral |
| Immersive Engineering | Pinza de cables, cable de cobre, placa de acero | Marco, Regulador |
| Actually Additions | Reconstructor atómico y carcasa de hierro, más una fuente de FE libre | Acoplador, Matriz de Enrutamiento |
| Ars Nouveau | Aparato de encantamiento, cámara de imbuición, magebloom, gemas de source | Matriz Viva |
| AE2 | Inscriptor, prensas del meteorito, procesadores | Matriz de Enrutamiento, Carta de Habitabilidad |
| PneumaticCraft | Cámara de presión, caja UV, tanque de grabado (PCB) | Matriz de Enrutamiento, Bus del Arca |
| Mystical Agriculture / Productive Bees | Cristal de infusión, esencias hasta imperium, centrífuga básica | Núcleo de Propagación, Cápsula, Motor |
| Mekanism | Infusor metalúrgico (a lo sumo con un componente del II), circuitos básicos, cadena de aleaciones | Regulador, Bus del Arca |
| Integrated Dynamics / LaserIO / Modular Routers | Exprimidor y cuenca de secado, chips lógicos, módulo en blanco | Sensor, Núcleo de Manipulación |
| Occultism / Malum / EvilCraft | Fuego espiritual, cristal sintonizado, guadaña inicial, infusión espiritual, gema oscura | Lente Espectral, Sello |
| Aether / Twilight / Bumblezone | Ambrosium, zanita, ironwood, steeleaf, puf de polen | Carta, Cápsula, módulos |
| Nature's Aura / Farmer's Delight | Mesa de ofrendas y altar, olla de cocción | Motor de Renovación, Raciones, Carta de Habitabilidad |

### Evitar el tedio

- **Nunca más de un componente por receta ajena.** Cantidades de 1 a 2 en tareas y recetas. Las referencias piden 16 o más unidades en unas 30 a 120 tareas por pack; nosotros, en ninguna, y conviene mantenerlo.
- **No gatear QoL, almacenamiento básico, construcción, decoración, cocina, Waystones ni mochilas.** Tampoco la primera energía de cada estilo: rueda hidráulica y vapor de Create, generadores iniciales de Actually Additions, Powah o Mekanism.
- **Cada componente nuevo en una receta ajena sube la demanda de ese componente.** Sus recetas tienen que seguir baratas y automatizables con autocrafteo de AE2 o RS. Si un componente empieza a pedirse en más de 15–20 recetas, conviene ofrecer una receta alternativa por la otra disciplina, técnica o mágica. Por ejemplo, una Matriz de Enrutamiento por Integrated Dynamics en lugar de PneumaticCraft.
- **No construir un muro tipo estrella ATM.** El Arca pide seis módulos y, a través de sus componentes, unos 20 mods, y alcanza. La vitrina del VI es opcional.
- **Quitar atajos, pero sin catalizadores arbitrarios.** Resource balance ya cambió la estrella del Nether y la manzana encantada por componentes; mantener ese criterio.
- **Cargadores de chunks.** Craftoria los quita por rendimiento. Nosotros tenemos FTB Chunks con carga forzada: medir antes de decidir; no es tema de recetas.

### Encaje con el sistema de componentes

- Cada fila propuesta es una entrada más en `tools/generate_family_balance.py`: salida, componente, acto y receta nativa con el casillero reemplazado. El chequeo de carga (`loaded-ingredient-check`) y las GameTests por familia, como `heliodorsolarandpsipiecesneedactcomponents`, cubren la regresión.
- Conviene agrupar las filas nuevas en una familia «funciones» con una tabla de familia → componente. Así un mod nuevo hereda la puerta de su familia y no se decide caso por caso. Las inconsistencias de hoy (tarjeta de cantera de RFTools, controlador de AE2) se corrigen ahí.
- El generador debería rechazar, además, que un componente aparezca en una receta que esté en la clausura de insumos de ese mismo componente. Hoy lo cuida el diseño a mano.
- Los proyectos de campaña no cambian: siguen consumiendo el prototipo por entrega autoritativa y los regalos siguen sirviendo.

### Ideas para el libro de quests

Son para quien escriba quests; `wt-acts` tiene hoy el libro y el mod.

- **Grupos:** «Historia» con los seis actos, «Disciplinas» con los capítulos por mod de campaign-outline y «Oficios y consejos».
- **Gramática fija en `data.snbt` con presets:**
  - hito de acto: hexágono de tamaño 2;
  - entrega de campaña: hexágono de 1,25;
  - crafteo: cuadrado de 1;
  - viaje y dimensión: diamante;
  - jefe: engranaje de 2;
  - nota o lore: círculo de 0,75.
  Nodos de tamaño 1 a 1–1,5 celdas, no 0,75 sobre grillas enormes.
- **Layout con metáfora:**
  - el capítulo V con forma de Arca (seis módulos alrededor del controlador);
  - el IV como el Observatorio (anillo de dimensiones);
  - el VI como el plano de Solsticio.
  Es lo más memorable que tienen ATM10 y FTB.
- **Imágenes:** carteles de acto en el estilo del Atlas (cobre, pergamino, luz) y bocetos de cada ruina y multibloque en la descripción. Todo original, siguiendo la regla de diseño de `AGENTS.md`.
- **Recompensas livianas:**
  - XP y una elección entre tres consumibles útiles del acto (raciones, antorchas, un repuesto);
  - nada que salte un tier;
  - nada que complete la historia: los premios fuertes siguen en la entrega del Atlas.
- **Menos checkmarks en el tronco.** Pasar los informativos a «Oficios y consejos» y dejar en el tronco tareas de ítem o de campaña.
- **Filtros y tags en las tareas de ítem** para aceptar equivalentes de varios mods, como hacen 430 tareas de ATM10.
- **Ramas que aparecen.** Ocultar las ramas opcionales hasta que se vean sus dependencias, para que cada acto no se vea entero desde el primer día.
- **Bestiario por dimensión** (Aether, Twilight, Undergarden, Eternal Starlight) como capítulo opcional de matar con premios de elección, al estilo del tablero de ATM10 y la cacería de FTB. Opcional y sin contar como avance.

### Decisiones para Elias

1. ¿Gatear el infusor metalúrgico con el Marco de Calibración (II), o dejar libre la entrada a Mekanism?
2. ¿MekaSuit y armaduras cuánticas en el V (Bus del Arca) o en el VI?
3. ¿Drops de Wither, dragón y Elder Guardian en los módulos del Arca, como propone la tabla?
4. ¿Ultimine por rangos atado al acto (por ejemplo 32 → 64 → 128), como la otra palanca de «el mundo escala solo»?
5. ¿Vitrina de trofeos en el lote de Solsticio?

### Respuestas de Elias (24 de septiembre)

1. **Marco de Calibración y Mekanism:** el primer Marco sale de la historia (recompensa del acto, sin receta de mesa). El **infusor metalúrgico pide un Marco**, y con el infusor se **fabrican más Marcos por infusión**. Esos alimentan el proyecto del acto II y las demás recetas gateadas (New Age, Psi, etc.). «Buen gate mechanic.»
2. **MekaSuit y armaduras Quantum:** en el **acto VI**.
3. **Drops de Wither, Dragón y Elder Guardian:** **sí**, como materiales de los módulos del Arca (acto V).
4. **Libro de quests:** llevarlo a la altura de ATM y FTB, con un centro del que salen los capítulos, un capítulo final que dibuja el **Sol de Heliodor**, recompensas moderadas, imágenes de las ruinas y nodos grandes en los hitos.
5. **Guías:** muchas líneas de quests informativas por mod y por tema (QoL, logística, construcción, granjas, tips), bien documentadas con las guías de los JARs y las wikis de la versión fijada. Se escriben en `content/guides/` (validador `tools/check_guides.py`).
6. **Ultimine como curio propio** con 6 tiers:
   - Sin el curio no hay Ultimine.
   - El tier 1 permite 16 bloques y cada tier suma 16, hasta **96** (confirmado por Elias).
   - La receta de cada tier incluye el curio anterior más materiales cada vez más difíciles, mezclando del pack (Heliodor) y de mods. El tier 1 es accesible («que el de 16 no sea TAN difícil»); después escala.
   - El tooltip muestra el alcance.
   - Hay que ver cómo limita FTB Ultimine por jugador en la versión fijada: config, permisos o API.
7. Sigue sin responder: la vitrina de trofeos.

## Límites

- Se leyó lo exportado, no se lanzó ningún pack. En ningún pack se inspeccionó el contenido que aportan los propios mods (por ejemplo, recetas nativas cambiadas por configs). Los datapacks de FTB se clasificaron por tipo; no se revisaron uno por uno.
- Los íconos que no se resolvieron (mods ausentes de la instancia ATM10 o modelos especiales) aparecen como sigla de tarea.
- Las propuestas no se probaron: faltan ciclos de receta cargados, EMI, supervivencia y el balance numérico.
