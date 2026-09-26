# Libro de quests: escala, gramática visual, hub y recompensas (25 de septiembre de 2026)

Rediseño del libro de FTB Quests después del [playtest del 24/9](playtest-2026-09-24.md): «todo lo de adentro se lee muy grande en muy poco espacio» (el panel de una quest abierta es angosto y el texto queda apretado, incluso en pantalla completa) y «la questline no es super visual». También cumple las respuestas de Elias en [reference-packs](../research/reference-packs.md): hub, capítulo final que dibuja el Sol de Heliodor, recompensas moderadas, imágenes y nodos grandes. Las 93 guías de `content/guides` entran al libro.

Todo lo genera `tools/generate_quests.py` desde `content/*.json`, `content/guides/*.json` y `content/quest_book.json`. Nada se edita a mano en `pack/config/ftbquests`.

## Referencias inspeccionadas (hook de diseño)

Capturas de sólo lectura en `E:/Elias/Codex/Entrelumen-ssd/research/quests/`, hechas con el lector estructural de la investigación del 24/9:

| Captura | Qué se tomó | Qué no |
|---|---|---|
| `ftbevo/create.png` (FTB Evolution 1.43.1, Create) | Emblema redondo grande a la izquierda de la entrada del capítulo; paneles detrás de grupos de nodos chicos; hitos grandes en el tronco | Sus texturas de madera y sus renders de máquinas |
| `atm10/chapter_2_the_star.png` (ATM10 8.1, la estrella) | El layout dibuja el objetivo: nodos en anillo alrededor de la pieza final; título grande sobre el lienzo | Sus letras, sus imágenes y su densidad (128 quests) |
| `craftoria/endgame.png` | Textura grande de fondo con pocos nodos encima | Sus tarjetas |
| `entrelumen/*.png` (nuestro libro del 24/9) | Nodos de 0,75 a 2,5–6 celdas: la densidad era un cuarto de la de las referencias | — |

El arte propio (sol, numerales, emblemas, medallón, esquinero, separador) es el de [quest-book-art](quest-book-art.md). Se usa a escala entera de texel, como pide `DESIGN.md`.

## Qué controla cada cosa en FTB Quests 2101.1.34

Evidencia: `ftb-quests-neoforge-2101.1.34.jar` y `ftb-library-neoforge-2101.1.35.jar` (los fijados en `catalog/curated.json`), descompilados con Vineflower 1.12 para leerlos. Las rutas son de clases del JAR; los fragmentos son del código descompilado.

| Qué | Propiedad | Dónde se lee y cómo actúa | Valor en el libro |
|---|---|---|---|
| Ancho del panel de una quest | `default_min_width` del capítulo; `min_width` de la quest (gana la de la quest) | `quest.Chapter.readData`: `defaultMinWidth = nbt.getInt("default_min_width")`. `quest.Quest.readData`: `minWidth = nbt.getInt("min_width")`. `client.gui.quests.ViewQuestPanel.addWidgets`: `w = Math.max(200, titleField.width + 54)`, después `Math.max(quest.getMinWidth(), w)` o, si es 0, `Math.max(chapter.getDefaultMinWidth(), w)`. El texto se corta en `panelText` de ancho `w - 6` | 320 en todos los capítulos (antes 200, el mínimo de FTB) |
| Panel a pantalla completa | Propiedad de tema `full_screen_quest` | `ViewQuestPanel.addWidgets`: si vale 1, `w = questScreen.width - 1`. En un monitor grande deja líneas de más de 200 caracteres | No se usa |
| Tamaño de nodo | `size` de la quest; `default_quest_size` del capítulo (tag double) | `QuestScreen.getQuestButtonSize() = zoom * 3 / 2`; `QuestPanel.alignWidgets` dibuja cada nodo de `round(bs * size)` px. `QuestButton.draw`: el ícono mide `(int)(w * 0.6666667 * iconScale)` | Gramática de abajo |
| Escala del ícono | `icon_scale` (sólo si es double) | `Quest.readData`: `nbt.contains("icon_scale", 6)` | No se usa |
| Espaciado | Coordenadas `x`, `y`; propiedad de tema `quest_spacing` (0 a 8, 1 por defecto) | `QuestScreen.getQuestButtonSpacing() = zoom * quest_spacing / 4`. Una celda de la grilla mide `bs + bp`: 28 px al zoom por defecto, y un nodo de tamaño 1 ocupa 24 px de esos 28 | Coordenadas rehechas; `quest_spacing` queda en 1 |
| Zoom | Ninguna propiedad de capítulo ni de archivo | `QuestScreen`: `int zoom = 16`; `addZoom` lo limita a 4..28 de a 4; sólo teclas, rueda y los datos de pantalla del cliente | No existe un zoom por defecto configurable: todo se diseñó para 16 |
| Forma | `shape` de la quest, `default_quest_shape` del capítulo | Formas = carpetas `ftbquests:textures/shapes/<forma>/` (`theme.ThemeLoader.findShapes`): circle, diamond, gear, heart, hexagon, none, octagon, pentagon, rsquare, square | Gramática de abajo |
| Color de un nodo | Tema: `quest_locked_color`, `quest_not_started_color` por selector | `QuestButton.draw` toma esos colores del tema para la quest. `theme.selector.ThemeSelector.parseSelector`: `[#etiqueta]` es un `TagSelector` que mira los `tags` de la quest. `ThemeLoader.loadTheme` apila todos los `assets/ftbquests/ftb_quests_theme.txt` de los recursos, el de FTB primero | `companion/src/main/resources/assets/ftbquests/ftb_quests_theme.txt`, generado |
| Imágenes del capítulo | Lista `images` del capítulo | `quest.ChapterImage.readData`: `x`, `y`, `width`, `height`, `rotation`, `image`, `color`, `alpha`, `order`, `click_action`, `text_on_image`, `text_shadow`, `text_h_align`, `text_v_align`, `dependency`. Se dibujan de `round(bs * width)` px y por debajo de las líneas de dependencia (`ftblibrary.ui.Panel.draw`: capa BACKGROUND, luego `drawOffsetBackground`, luego nodos) | Numerales, emblemas, esquineros, rótulos, soles, medallones |
| Texto sobre el lienzo | `text_on_image` con el título de la imagen (`image.<ID>.title` del idioma) | `ChapterImageButton.maybeRenderText`: escala `min(ancho / texto, alto / (9 × líneas))`. `ftblibrary.util.TextComponentParser.parse` convierte la secuencia literal `\n` en salto de línea | Rótulos de rama y del hub, a escala exacta 1 |
| Clic en una imagen | `click_action` `open_quest:<ID>` | `ImageClickAction.openQuest` → `QuestScreen.open`: si el ID es un capítulo, lo abre | Emblemas del hub y medallones de grupo |
| Enlaces a quests de otro capítulo | Lista `quest_links` (`linked_quest`, `x`, `y`, `shape`, `size`) | `quest.QuestLink.readData` | Hub |
| Grupos de capítulos | `chapter_groups.snbt` y `group` en el capítulo; título `chapter_group.<ID>.title` | `BaseQuestFile.readChapterGroupsFile` | Cinco grupos de guías |
| IDs | `id` de cada objeto | `BaseQuestFile.readID`: sin ID, FTB inventa uno al azar y marca el archivo para guardarlo | Todo objeto (imágenes, enlaces, recompensas) lleva un ID estable |

## Gramática de nodos de la historia

La impone el generador por rol; el contenido la declara y el generador rechaza lo que no coincide.

| Rol | Forma | Tamaño | Ícono a zoom 16 |
|---|---|---|---|
| Final del acto (el hito que cierra el capítulo) | hexágono | 3 | 48 px (3×) |
| Hito de campaña | hexágono | 2 | 32 px (2×) |
| Viaje o hecho que observa el servidor (llegadas a Aether, Twilight, Bumblezone, End; el Corazón caído) | octógono | 2 | 32 px |
| Tarea de ítem | cuadrado | 1 | 16 px (1×) |
| Opcional con ítem | diamante | 1 | 16 px |
| Informativa (checkmark) | círculo | 0,75 | 12 px |

Los tamaños 1, 2 y 3 dejan los íconos de 16×16 en texeles enteros. Los normales pasan de 0,75–0,85 a 1 y los hitos de 1,1 a 2 y 3: más contraste, menos distancia. Las guías conservan su `layout` escrito (tamaños de 0,85 a 2,25 y sus formas).

Distancia mínima entre nodos: `(a + b) / 2 × 6/7 + 0,4` celdas. Las cadenas van a 1,5 celdas; las ramas a 4,5; la dirección de lectura sigue de arriba abajo.

## Colores

Cada quest lleva una sola etiqueta de color y el tema del companion la pinta. Completo y en curso quedan con los colores de FTB (verde y turquesa).

| Etiqueta | Bloqueada | Disponible | Paleta |
|---|---|---|---|
| `entrelumen_story` (historia) | `#FF8E4631` | `#FFECC866` | cobre, latón |
| `entrelumen_guide` (guía) | `#FF2B5E50` | `#FF72C49E` | verdín |
| `entrelumen_optional` (opcional, historia o guía) | `#FF7D6843` | `#FFE8DCB5` | pergamino |

El tema vive en el companion, así que el cliente necesita el JAR del companion de esta rama para ver los colores. El servidor no lo usa.

## Imágenes alrededor de los nodos

Las ubica el generador desde el propio layout:

- **Cabecera de acto:** numeral 2× arriba a la izquierda y el emblema del acto 2× al lado. El emblema lleva al hub con un clic.
- **Paneles de rama:** cuatro esquineros 1× (el mismo, rotado) alrededor de los nodos chicos de cada grupo del layout, y el rótulo del grupo abajo, o arriba si una línea de dependencia lo cruzaría. Un grupo que se mezcla con otros no lleva panel. Los paneles de una misma fila terminan a la misma altura.
- **Final de acto:** el Sol de Heliodor 1× detrás del hexágono de tamaño 3.
- **VI · Solsticio:** el sol 3× (384 px) en el centro y las misiones en hexágono alrededor del disco: el Ayuntamiento arriba, Juan a la izquierda, Bodhi a la derecha, Terra en el eje y el Terraprisma en el corazón del sol. Debajo del disco, el acuerdo, el portal y las elecciones, con el medallón 2× detrás del final.
- **Guías:** medallón 2× a la izquierda de la entrada, con la textura del ítem clave 4× encima (`emblem` de cada guía, validado por `tools/check_guides.py` en el JAR fijado: cuadrada, 16 o 32 px, sin animación, porque FTB dibuja el PNG entero).

## Hub: «El Sol de Heliodor»

Primer capítulo (sin grupo, orden 0). El sol 2× en el centro con un enlace al Corazón de Heliodor. Alrededor, los seis actos en sentido horario desde arriba: numeral, emblema (un clic abre el capítulo del acto) y el nombre del acto; entre el emblema y el sol, un enlace a la quest que cierra el acto, que muestra si ya se completó. Abajo, un separador y un medallón por grupo de guías, que abre la primera guía del grupo. Texto propio: «Seis actos bajo un mismo sol.»

## Guías en el libro

- Grupos, en este orden: Legado de Heliodor, Calidad de vida, Tecnología, Magia, Exploración. Dentro de cada grupo van primero los capítulos `lead` de `quest_book.json`, después «cualquier acto» y los actos I a VI, y por último el título en inglés.
- Tareas: `item`, `checkmark`, `advancement` (criterio vacío: el logro entero) y `dimension`. No hay mod de filtros de ítem instalado, así que las cinco tareas con `tag` nombran un ítem concreto: el de Almost Unified cuando es un metal unificado (acero y plata de Immersive Engineering).
- Siete tareas pedían ítems que Almost Unified reemplaza en las recetas y esconde (acero de Ad Astra, polvo de hierro y de azufre de Mekanism, bronce y polvo de hierro de Modern Industrialization, níquel de Oritech, vara de hierro de Silent Gear): ahora piden el ítem unificado, que es el que el jugador obtiene. Se revisaron todas las tareas de ítem contra `pack/config/almostunified` (metales y cultivos) y las etiquetas de los JAR fijados.
- `optional` y el `layout` escrito se respetan tal cual.

## Recompensas

Antes no había ninguna recompensa de FTB: todo salía de la entrega autoritativa del Atlas. Eso sigue igual; las de FTB son un extra chico, por equipo (`default_reward_team`) y se reclaman a mano (`default_autoclaim_rewards: disabled`).

| | I | II | III | IV | V | VI |
|---|---|---|---|---|---|---|
| Tarea de ítem de la historia | 5 XP | 10 | 15 | 20 | 25 | 30 |
| Hito | 25 XP + 8 antorchas | 50 + 8 aleación de andesita | 75 + 2 cofres | 100 + 8 zanahorias doradas | 125 + 4 piedras luminosas | 150 + 8 cohetes |
| Final del acto | 50 XP + 2 faroles | 100 + 4 sopas de verduras | 150 + 2 tolvas | 200 + 4 perlas de ender | 250 + 2 manzanas doradas | 300 + 1 torta |
| Tarea de guía (ítem, logro o dimensión) | 5 XP | 8 | 10 | 12 | 15 | 15 |
| Checkmark (historia o guía) | nada | nada | nada | nada | nada | nada |

«Cualquier acto» paga 5 XP. Criterios:

- Un checkmark se tilda con un clic y no paga: nada de XP gratis.
- Ningún premio es un componente de ENTRELUMEN ni sale de una receta con puerta (`pack/kubejs`), y ninguno completa una tarea de campaña.
- Los ítems acompañan el acto: luz entre ruinas, taller y despensa, rutas, viaje, luz para el Arca, fiesta en Solsticio.
- Si alguien juntara todo sin gastar: 7.755 puntos de la historia y 9.871 de las guías, unos 17.600 (cerca del nivel 75) en más de 150 horas. Cada guía paga lo que unas pocas criaturas.

## Validación

- `python tools/generate_quests.py --check` y `python tools/test_generate_quests.py`: los IDs, tareas, dependencias, íconos y banderas de las 171 quests de la historia son los mismos que en `origin/main` antes del rediseño (digests semánticos en el test). Los textos no cambiaron; sólo se agregó una línea en blanco entre párrafos de la descripción.
- `python tools/test_quest_book.py`: hub, grupos, guías, gramática, ancho, imágenes a escala entera, recompensas, etiquetas y tema.
- `python tools/check_guides.py`: además, emblemas en los JAR fijados y tareas con `tag` con su ítem.
- `python tools/check_runtime_content.py`: la auditoría KubeJS ahora cubre los ítems de las guías y de las recompensas.
- Servidor de QA desechable con el pack completo (272 JAR de servidor y el companion de main), recibos en `E:/Elias/Codex/Entrelumen-ssd/questbook-20260925/qa`:
  - FTB registró «Loaded 6 chapter groups, 102 chapters, 1932 quests, 0 reward tables» y tablas de traducción para dos idiomas, sin avisos de FTB Quests.
  - La auditoría KubeJS (`check_runtime_content.py --log`) pasó con los 1.353 ítems del libro. La primera corrida encontró dos tareas de Mahou Tsukai que nombraban bloques sin ítem y dos íconos inexistentes (el Astrodux de Ad Astra y la Forja de Hefesto); se corrigieron en las guías.
  - FTB vuelve a guardar los archivos en su propio formato al cerrar, como ya pasaba en `server-slice`. Leídos de vuelta, los 102 capítulos, 1.932 quests, 385 imágenes, 7 enlaces y 1.371 recompensas coinciden con lo generado (sólo omite valores por defecto). Los idiomas no se reescriben.
  - Los errores del log son previos al libro (una etiqueta de Pam's y dos fluidos de XP en data maps).
- Las vistas previas de `E:/Elias/Codex/Entrelumen-ssd/questbook-20260925/` salen de un renderer que imita la geometría de FTB a zoom 16. No son capturas del juego.

## Límites

- Nada de esto se vio todavía dentro del cliente: la escala final depende de la escala de GUI de cada jugador. 320 px caben en una GUI de 426 px de ancho (1280×720 a escala 3).
- Los rótulos usan la fuente de Minecraft a escala 1; con otro paquete de fuentes pueden medir distinto.
- Las guías conservan sus coordenadas: su espaciado (2 a 2,5 celdas) no se tocó.
