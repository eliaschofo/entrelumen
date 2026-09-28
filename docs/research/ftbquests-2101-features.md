# FTB Quests 2101.1.34: catálogo completo de elementos

Inventario de todo lo que el libro de FTB Quests puede expresar en las versiones fijadas del pack, con la decisión de ENTRELUMEN para cada elemento. Base del libro v3 ([quest-book-v3](../design/quest-book-v3.md)) y de su motor (`tools/quest_engine.py`).

## Fuentes y método

- **JAR fijados** (`catalog/curated.json`): `ftb-quests-neoforge-2101.1.34.jar` y `ftb-library-neoforge-2101.1.35.jar`, los de `G:/curseforge/Instances/ENTRELUMEN/mods/`. Descompilados con Vineflower 1.10.1 (caché de Gradle) en `E:/Elias/Codex/Entrelumen-ssd/questbook-v3/decomp/src-ftbq` y `src-ftbl`, fuera del repo. Cada fila cita la clase y el método que leen la clave; las rutas son relativas a `dev/ftb/mods/ftbquests/` (FTB Quests) o `dev/ftb/mods/ftblibrary/` (FTB Library).
- **Documentación de FTB**, consultada el 25/9/2026:
  - [FTB Quests: estilo del libro](https://docs.feed-the-beast.com/mod-docs/mods/suite/Quests/Developer/Styling/) (archivo de tema, selectores, formas propias);
  - [Ajustes de quest](https://docs.feed-the-beast.com/mod-docs/mods/suite/Quests/Developer/Quests/Settings);
  - [Tablas de recompensa y cajas de loot](https://docs.feed-the-beast.com/mod-docs/mods/suite/Quests/Developer/Quests/Reward_Tables);
  - [Capítulos](https://docs.feed-the-beast.com/mod-docs/mods/suite/Quests/Developer/Chapters/).
- **[CHANGELOG de FTB Quests](https://github.com/FTBTeam/FTB-Quests/blob/main/CHANGELOG.md)**, para fechar las funciones nuevas: forma `none` (2101.1.2), `hide_lock_icon`, color arcoíris `&z` y escapes Unicode (2101.1.3), `change_page` con subpágina (2101.1.6), ramas exclusivas (2101.1.7), enfriamiento de repetibles (2101.1.18), curvas de Bézier, presets visuales e imágenes con texto (2101.1.23).
- **Referencias de técnica, sólo lectura:** `research/quests/chapter-stats.json` y la instancia ATM10 (`config/ftbquests`, sin copiar nada). ATM10 usa imágenes `{image:…}` con texturas propias en 5 descripciones de su capítulo de la estrella y ninguna imagen de color, de ítem ni fuente alternativa: esas técnicas son nuestras y quedan para verificar en el cliente (ver «Límites»).

Leyenda de la columna «Decisión»:
- **Sí**: el libro lo usa hoy (dónde y cómo).
- **Motor**: el motor lo expresa y lo valida, pero los dos ejemplares todavía no lo necesitan; queda para las demás cadenas.
- **No**: no lo usamos (por qué).

Recuento al final del documento.

## 1. Archivos del libro

| Elemento | Clave / ruta | Evidencia | Efecto | Decisión |
|---|---|---|---|---|
| Carpeta del libro | `config/ftbquests/quests/` | `quest/BaseQuestFile.readDataFull` | Lo que el servidor carga y sincroniza | **Sí**: generada por `tools/generate_quests.py`; nada se edita a mano |
| Archivo raíz | `data.snbt` | `BaseQuestFile.readDataFull` → `readData` | Ajustes globales (sección 2) | **Sí** |
| Grupos | `chapter_groups.snbt`, lista `chapter_groups` | `BaseQuestFile.readChapterGroupsFile` | Carpetas de la barra lateral | **Sí**: cinco grupos |
| Capítulos | `chapters/<filename>.snbt` | `BaseQuestFile.readDataFull` (listas `quests`, `quest_links`, `images`) | Un lienzo por archivo | **Sí** |
| Tablas de recompensa | `reward_tables/<filename>.snbt` | `BaseQuestFile.loadRewardTableFile` | Tablas y cajas de loot | **Sí**: 18 tablas, tres por acto (sección 11) |
| Idiomas | `lang/<locale>.snbt` | `quest/translation/TranslationManager.loadFromNBT`; claves `<tipo>.<ID>.<campo>` (`makeKey`) | Textos por idioma, con respaldo | **Sí**: `en_us` y `es_es` |
| Tipos de objeto en claves | `file`, `chapter`, `quest`, `task`, `reward`, `reward_table`, `chapter_group`, `quest_link`, `image` | `quest/QuestObjectType` | Prefijo de cada clave de idioma | **Sí**: todos menos `quest_link` tienen texto nuestro (`task`: títulos de observación; `reward`: avisos) |
| Campos traducibles | `title`, `quest_subtitle`, `quest_desc` (lista), `chapter_subtitle` (lista) | `quest/translation/TranslationKey` | Título, subtítulo, descripción | **Sí**, los cuatro |
| IDs | `id` hexadecimal de 16 cifras en cada objeto | `BaseQuestFile.readID` | Sin ID, FTB inventa uno y reescribe el archivo | **Sí**: `stable_id(clave semántica)` para todo objeto, recompensas de tabla incluidas. Las tablas llevan un ID de 31 bits: `RandomReward.readData` lee `table_id` con `getLong`, y en SNBT un número sin sufijo fuera del rango de `int` se lee como double (`SNBTUtils.getNumberType`) y pierde los últimos dígitos |

## 2. `data.snbt` (archivo raíz)

Evidencia: `quest/BaseQuestFile.readData` (líneas 462–502 del descompilado).

| Clave | Valores | Efecto | Decisión |
|---|---|---|---|
| `version` | entero | Versión de formato; distinto de la actual marca el archivo para guardar | **Sí**: 13 |
| `default_reward_team` | bool | Recompensas por equipo por defecto | **Sí**: `true` |
| `default_consume_items` | bool | Tareas de ítem que consumen por defecto | **Sí**: `false`; sólo las tareas de los encargos consumen, con su propia clave |
| `default_autoclaim_rewards` | `disabled`, `enabled`, `no_toast`, `invisible` | Reclamo automático | **Sí**: `disabled`; los avisos y fanfarrias usan `auto` por recompensa |
| `default_quest_shape` | nombre de forma | Forma cuando capítulo y quest no dicen nada | **No**: cada capítulo la fija |
| `default_quest_disable_jei` | bool | Oculta quests del visor de recetas | **No**. Corrección del 27/9: FTB XMod Compat ya está en el lock y JEI lista las quests que el jugador puede empezar; ocultarlas queda por decidir |
| `emergency_items`, `emergency_items_cooldown` | lista de ítems, segundos | Botón de ítems de emergencia | **No**: la biblia no regala el libro ni kits de rescate |
| `drop_loot_crates`, `loot_crate_no_drop` | bool, pesos por tipo de mob | Cajas que caen de mobs | **Sí**: `drop_loot_crates: false` explícito; las cajas salen sólo de quests y los pesos de cada caja van en 0 |
| `disable_gui` | bool | Bloquea el libro | **No** |
| `lock_message` | texto | Aviso cuando el libro está bloqueado (`ClientQuestFile`) | **No**: no bloqueamos el libro |
| `grid_scale` | double, 0,5 | Imán del editor | **No**: el generador coloca todo |
| `pause_game` | bool | Pausa en un jugador | **Sí**: `true` |
| `progression_mode` | `linear`, `flexible` | Modo por defecto del libro | **No**: se fija por capítulo |
| `detection_delay` | ticks | Cada cuánto se revisan inventarios | **No**: el valor por defecto alcanza |
| `show_lock_icons` | bool, `true` | Candado sobre quests bloqueadas | **Sí**: se deja activo; los nodos de adorno lo apagan con `hide_lock_icon` |
| `drop_book_on_death` | bool | Soltar el libro al morir | **No**: el libro no es un ítem de inventario obligatorio |
| `hide_excluded_quests` | bool | Esconde las quests excluidas por una rama exclusiva | **No**: preferimos que la opción descartada se vea, con el motivo «excluida» en su tooltip |
| `fallback_locale` | locale | Idioma de respaldo | **Sí**: `en_us` |
| `verify_on_load` | bool | Revisa dependencias al cargar | **No**: el generador ya lo valida |
| `suppress_all_autoclaiming` | bool | Anula todo reclamo automático | **No** |
| `presets` | mapa `nombre → {shape, size}` | Presets visuales (`quest/preset/VisualPresets`); los de fábrica son `normal`, `info`, `goal` | **Sí**: la gramática de nodos de las cadenas ([quest-book-v3](../design/quest-book-v3.md)) viaja como presets, más los tres de fábrica |
| `preset` | nombre | Preset por defecto del libro | **No** |
| `icon`, título `file.0000000000000001.title` | ítem, texto | Ícono y nombre del libro (`BaseQuestFile.getAltIcon` usa `modpack_icon` del tema) | **Sí**: el Atlas y «ENTRELUMEN» |

## 3. Capítulo

Evidencia: `quest/Chapter.readData`; el archivo lee `id`, `group`, `order_index`, `quests`, `quest_links` e `images` en `BaseQuestFile.readDataFull`.

| Clave | Valores | Efecto visual | Decisión |
|---|---|---|---|
| `id`, `filename`, `group`, `order_index` | ID, nombre, ID de grupo, entero | Identidad y orden en la barra lateral | **Sí** |
| `icon` | ítem (`id`, `components`) | Ícono en la barra lateral | **Sí**; las cadenas pueden usar una textura propia con `ftbquests:custom_icon` |
| título, `chapter_subtitle` | idioma | Nombre y bajada del capítulo | **Sí** |
| `tags` | lista | Selectores del tema (sección 13) | **Sí**: `entrelumen_motif_<motivo>` da a cada cadena sus colores de línea y de texto de panel |
| `default_quest_shape` | forma | Forma por defecto | **Sí** |
| `default_quest_size` | double (tag 6) | Tamaño por defecto | **Motor**: la gramática da el tamaño a cada nodo |
| `default_min_width` | entero | Ancho del panel de lectura (`client/gui/quests/ViewQuestPanel.addWidgets`) | **Sí**: 320 |
| `autofocus_id` | ID de quest | Dónde se centra al abrir | **Sí**: la entrada |
| `always_invisible` | bool | Capítulo fuera de la lista (`Chapter.isVisible`) | **No**: un capítulo sin quests visibles ya se esconde solo, sin trucos |
| `progression_mode` | `default`, `linear`, `flexible` | Flexible deja avanzar tareas antes de tener las dependencias; la quest espera y muestra un tilde gris (`QuestButton.draw`) | **Sí**: `flexible` en las cadenas, para que lo que ya tenés en el inventario cuente |
| `consume_items` | tristate | Consumo por defecto en el capítulo | **No** |
| `hide_quest_details_until_startable` | bool | Por defecto del capítulo | **No**: se decide por quest |
| `hide_quest_until_deps_visible`, `hide_quest_until_deps_complete` | bool | Por defecto del capítulo | **No**: se decide por rol |
| `hide_text_until_complete` | bool | Por defecto del capítulo | **No** |
| `default_repeatable_quest` | bool | Todas repetibles | **No** |
| `require_sequential_tasks` | bool | Tareas en orden por defecto | **No** |
| `default_hide_dependency_lines` | bool | Sin líneas por defecto | **No** |
| `preset` | nombre | Preset por defecto del capítulo | **No** |
| `disable_toast` | bool | Sin aviso al completar el capítulo (`QuestObject.readData`) | **No**: el aviso de capítulo completo es un premio en sí |
| `images` | lista | Sección 5 | **Sí** |
| `quest_links` | lista | Sección 6 | **Sí** |

## 4. Grupo de capítulos

| Clave | Evidencia | Decisión |
|---|---|---|
| `id`, `icon`, título por idioma | `BaseQuestFile.readChapterGroupsFile`; `ChapterGroup` extiende `QuestObject` | **Sí**: cinco grupos |
| Visibilidad | `ChapterGroup.isVisible`: se ve si algún capítulo se ve | **Sí**, implícita |
| `tags` | `QuestObjectBase.readData` | **No**: los motivos van por capítulo |

## 5. Imágenes de capítulo

Evidencia: `quest/ChapterImage.readData`, dibujo en `client/gui/quests/ChapterImageButton.draw` y `maybeRenderText`; posición en `QuestPanel.alignWidgets`: el centro queda en `28·x` px y el lado mide `24·ancho` px al zoom 16. Se dibujan en la capa de fondo, debajo de las líneas y de los nodos, ordenadas por `order`.

| Clave | Valores | Efecto visual | Decisión |
|---|---|---|---|
| `x`, `y`, `width`, `height` | double, en celdas | Centro y tamaño | **Sí** |
| `rotation` | grados | Gira alrededor del centro, o de la esquina con `corner` | **Sí**: las líneas del motor (aros, rieles, rayos, rayos de engranaje) son rectángulos finos rotados |
| `image` | cadena de ícono (sección 8) | Qué se dibuja | **Sí**: texturas de ENTRELUMEN, texturas de los JAR fijados (ítems, bloques, pinturas de GUI) y el píxel blanco `entrelumen:textures/gui/quests/px.png` para paneles y líneas. Presentación v2 (28/9): también sprites del atlas de bloques (animados) e `item:<id>` (el ítem en 3D); ver [quest-book-v3](../design/quest-book-v3.md#presentación-v2-28-de-septiembre-de-2026) |
| `color` | entero RGB | Tiñe la imagen | **Sí**: paneles y líneas toman el color de la paleta del motivo |
| `alpha` | 0–255 | Transparencia | **Sí**: 34 en los paneles de rama, de 45 a 150 en figuras y líneas; el arte va opaco |
| `order` | entero | Orden de dibujo entre imágenes | **Sí**: paneles −3, figuras −2, arte −1, medallón de regreso 0 y su emblema 1, rótulos 5 |
| título `image.<ID>.title` | idioma | Tooltip al pasar el mouse, o texto dibujado con `text_on_image` | **Sí**: rótulos y chistes al pasar el mouse sobre adornos. Corrección del 28/9: el tooltip sólo aparece en una imagen con `click_action` (`ChapterImageButton.checkMouseOver`; sin clic, sólo lo ve quien edita). La presentación v2 le da a una nota el clic de la quest que la revela, y `check_guides.py` avisa de las que quedan sin clic |
| `hover` | lista (formato viejo) | Se convierte en título (`readData`) | **No**: usamos el título por idioma |
| `click_action` | `open_quest:<ID>[/<página>]`, `open_uri:`, `run_command:`, `custom_event:`, `show_recipe:`, `show_docs:` | Clic en la imagen (`quest/ImageClickAction`) | **Sí**: `open_quest` para puertas a capítulos (emblemas del hub, medallones de regreso). **No** el resto: sin enlaces externos en un pack público, sin comandos por clic, `custom_event` necesita KubeJS de FTB, `show_recipe` y `show_docs` no tenían proveedor sin FTB XMod Compat (`RecipeModHelper.NoOp`, `DocsModRegistry` vacío); desde el 27/9 XMod registra JEI, GuideME y Patchouli, pero el libro todavía no los usa |
| `click` | legado: `#id`, `http…`, `command:`, `custom:` | `ImageClickAction.fromLegacy` | **No**: usamos `click_action` |
| `text_on_image` | bool | Dibuja el título escalado a la caja (`min(ancho/texto, alto/(9·líneas))`) | **Sí**: encabezados de rama y títulos de la cadena |
| `text_shadow` | bool | Sombra del texto | **Sí** |
| `text_h_align`, `text_v_align` | `start`, `middle`, `end` | Alineación dentro de la caja | **Sí**: `start` en un encabezado de Create I, pegado a la izquierda de su panel |
| `text_inset` | porcentaje | Margen interno del texto | **Motor** |
| `dependency` | ID de quest | La imagen aparece recién con esa quest completa (`ChapterImage.shouldShowImage`; para quien no edita, ni se crea el widget: `QuestPanel.addWidgets`) | **Sí**: el sol de Heliodor aparece al completar la cumbre (Create I, Ars) o el último hito (Create II) |
| `corner` | bool | Pivote de rotación en la esquina | **No**: todas nuestras figuras giran sobre su centro |
| `dev` | bool | Sólo visible en modo edición | **No** |
| `position_locked` | bool | El editor no la puede arrastrar | **Sí**: todo adorno generado va bloqueado |
| Literal `\n` en el título | `TextComponentParser.parse` lo convierte en salto de línea | Rótulos de varias líneas | **No**: rótulos de una línea; dos líneas son dos rótulos |
| Título JSON (con `font`) | `TextUtils.parseRawText` | Rótulos con otra fuente | **Sí**: el rótulo rúnico de Ars usa `minecraft:alt` (el alfabeto de la mesa de encantamientos) |

## 6. Enlaces de quest

Evidencia: `quest/QuestLink.readData`, dibujo en `client/gui/quests/QuestLinkButton`.

| Clave | Efecto | Decisión |
|---|---|---|
| `linked_quest` | Nodo que muestra el estado de una quest de otro capítulo y la abre | **Sí**: cada cadena enlaza dos quests de la historia o de otra cadena que usan lo que enseña (por ejemplo, «A Measure of Iron» del acto II en Create I y «Precision work» de Create I en Ars) |
| `x`, `y`, `shape`, `size` | Posición, forma y tamaño propios | **Sí**: octógono 1 para todo enlace de cadena |
| `icon`, `tags` | Heredados de `QuestObjectBase` | **No**: el enlace muestra el ícono de la quest destino |

## 7. Quest

Evidencia: `quest/Quest.readData` (líneas 403–464), `Quest.isVisible`, `Quest.checkDependencies`, `Quest.isQuestObjectExcluded`, `TeamData.canStartTasks`.

| Clave | Valores | Efecto | Decisión |
|---|---|---|---|
| `x`, `y` | double | Centro, en celdas | **Sí** |
| `shape` | forma o `default` | Forma del nodo | **Sí**: gramática por rol |
| `size` | double 0–8 | Lado del nodo (`24·size` px) | **Sí**: 0,75, 1, 2 y 3 (íconos a texel entero) |
| `preset` | nombre de preset | Si existe, gana sobre `shape` y `size` (`Quest.getVisualPreset`) | **Sí**: cada nodo de cadena nombra su preset y además escribe forma y tamaño iguales, por si el preset falta |
| `icon_scale` | 0,1–2,0 (sólo tag double) | Escala el ícono dentro del nodo (`QuestButton.draw`) | **Sí**: los nodos de consejo 0,75, las cumbres 1,0 sobre forma grande |
| `icon` | ítem con componentes | Ícono del nodo | **Sí**: ítems, texturas propias (`ftbquests:custom_icon` + `ftbquests:icon`) y caras de criaturas (`ftbquests:entity_face`, sección 8) |
| `tags` | lista | Selectores del tema | **Sí**: una etiqueta de color por rol |
| `custom_id` | texto | Se suma como etiqueta | **No** |
| `dependencies` | lista de IDs | Dependencias | **Sí** |
| `dependency_requirement` | `all_completed`, `one_completed`, `all_started`, `one_started` | Qué pide de las dependencias | **Sí**: `one_completed` en Ars: la marca elemental se abre con cualquiera de los cuatro focos |
| `min_required_dependencies` | entero | Pide N de las dependencias (gana sobre el modo) | **Sí**: la cumbre de Ars pide 3 de sus 4 escuelas |
| `max_completable_dependents` | entero | Rama exclusiva: completar N hijos excluye al resto (2101.1.7) | **Sí**: en Ars, la escuela elemental (se elige una) |
| `hide_dependency_lines` | tristate | Oculta las líneas hacia sus dependencias salvo al pasar el mouse | **Sí**: cumbres, encargos y las quests de Ars cuyas dependencias están lejos |
| `hide_dependent_lines` | bool | Oculta las líneas hacia sus dependientes | **Motor** (`hide_dependent_lines` en la quest) |
| `dep_control_pts` | mapa `ID de dependencia → [x0, y0, x1, y1]` | Curva de Bézier cúbica entre nodos (2101.1.23; `QuestButton.getConnectionPoints`) | **Sí**: el aro del engranaje de Create I y una curva en Create II y III. El motor compensa el corrimiento de `QuestButton.positionControlPoints` (−12·tamaño px) |
| `hide_until_deps_visible` (`hide` legado) | tristate | Oculta hasta que las dependencias se vean | **No**: con raíces visibles, todo se ve igual |
| `hide_until_deps_complete` | tristate | Aparece recién con las dependencias completas | **Sí**: 18 nodos de las ramas laterales largas (Create I y III, Ars), que se revelan a medida que avanzás, como en ATM10 |
| `invisible` | bool | Invisible hasta completarla (`Quest.isVisible`) | **Sí**: secretos |
| `invisible_until_tasks` | entero | Se ve cuando se completan N tareas | **Motor** |
| `hide_details_until_startable` | tristate | No abre el panel hasta poder empezarla (`QuestButton.onClicked`) | **Sí**: cumbres, para no arruinar el final |
| `hide_text_until_complete` | tristate | Oculta la descripción hasta completarla | **Sí**: la nota de Terra que sigue a la cumbre de Create I |
| `optional` | bool | No cuenta para el progreso | **Sí** |
| `can_repeat` | tristate | Repetible | **Sí**: encargos |
| `repeat_cooldown` | segundos (`TeamData`, `·1000L`) | Espera entre repeticiones | **Sí**: 1200 s (un día de juego) |
| `min_width` | entero | Ancho propio del panel | **No**: el del capítulo |
| `progression_mode` | `default`, `linear`, `flexible` | Modo propio | **Sí**: `linear` en las cumbres. En `flexible`, `TeamData.canStartTasks` siempre da verdadero y `hide_details_until_startable` no tendría efecto |
| `require_sequential_tasks` | tristate | Tareas en orden | **Sí**: el montaje secuencial de Create (lámina → mecanismo incompleto → mecanismo) y el amuleto de starbuncle de Ars (ficha → amuleto → verlo trabajar) |
| `disable_recipe_mod` | tristate | Oculta en JEI/EMI | **No**. Corrección del 27/9: con FTB XMod Compat en el lock saca una quest de la categoría de JEI; todavía no hace falta |
| `guide_page` | texto | Botón «Abrir en la guía» (`ViewQuestPanel.OpenInGuideButton`) | **No**: no hay manejador de guía en el pack |
| `ignore_reward_blocking` | bool | Ignora el bloqueo de recompensas | **No**: no bloqueamos recompensas |
| `hide_lock_icon` | bool | Sin candado (2101.1.3) | **Sí**: consejos y notas |
| `disable_toast` | bool | Sin aviso al completarla (`QuestObject.readData`) | **Sí**: consejos y notas, para no llenar la pantalla de avisos por un clic |
| título, `quest_subtitle`, `quest_desc` | idioma | El subtítulo sale gris en el tooltip del nodo y en cursiva en el panel (`QuestButton.addMouseOverText`, `ViewQuestPanel`) | **Sí**: el subtítulo es el lugar del chiste corto, visible sin abrir la quest |
| Quest sin tareas | — | Se completa sola al cumplir dependencias (`Quest.getRelativeProgressFromChildren`) | **No**: toda quest tiene una tarea; los nodos de sólo lectura son checkmarks |
| Tooltip de rama exclusiva | — | «Exclusiva» en dorado (`QuestButton.addMouseOverText`) | **Sí**, automático en la escuela de Ars |

## 8. Íconos (gramática de FTB Library)

Evidencia: `icon/Icon.getIcon` y `getIcon0` de FTB Library; `item/CustomIconItem.getIcon` y `registry/ModDataComponents` de FTB Quests; `icon/EntityIconLoader`.

| Forma de la cadena | Ejemplo | Efecto | Decisión |
|---|---|---|---|
| Textura `ns:textures/….png` | `entrelumen:textures/gui/quests/sun_heliodor.png` | Imagen fija (`ImageIcon`) | **Sí** |
| Sprite del atlas `ns:block/…` | `minecraft:block/water_still` | Sprite del atlas de bloques (`AtlasSpriteIcon`), con su animación | **Sí** (28/9): agua, lava y líquidos de Mekanism que se mueven en el lienzo. `check_guides.py` pide que el atlas lo liste (carpetas `block/` e `item/` de vanilla o una fuente del JAR en `atlases/blocks.json`); si no, sale la textura de «falta» |
| `item:<id>` | `item:create:cogwheel` | Renderiza el ítem, modelo 3D incluido | **Sí** (28/9): utilería en 3D en el lienzo (el tanque y el motor de vapor de Create). Ignora tinte y alfa (`ItemIcon`); no se vio en el cliente |
| Color `#RRGGBB` / `#AARRGGBB`, `color:` | `#40C8814A` | Rectángulo lleno | **No**: `ChapterImageButton.draw` reemplaza el color con `withColor(color, alpha)`; el píxel blanco teñido da lo mismo sin sorpresas |
| `hollow_rectangle:<color>` | — | Marco de 1 px | **No**: en una imagen se escala con la caja y deja de ser un marco |
| `bullet:<color>` | — | Viñeta | **No**: mismo problema de escala (`BulletIcon.draw` resta 2 px) |
| `part:` | — | Recorte de textura | **No** |
| `http:`, `https:`, `file:` | — | Imagen remota (`URLImageIcon`) | **No**: un pack público no descarga imágenes |
| Combinación `a + b` | — | Superpone íconos (`CombinedIcon`) | **No** |
| Propiedades `; padding=`, `border=`, `border_round_edges=`, `color=`, `tint=`, `u0=`…`v1=`, `tile_size=` | — | Modificadores; recorte y mosaico de una textura (`ImageIcon.setProperties`) | **No**: la imagen de capítulo viaja al cliente como `Icon.toString()` (`ChapterImage.writeNetData`) e `ImageIcon.toString()` devuelve sólo la ruta, así que el cliente nunca las ve. Sin recorte ni mosaico: un patrón repetido son varias imágenes |
| `ftbquests:custom_icon` con `ftbquests:icon` | textura | Cualquier textura como ícono de quest o capítulo | **Motor** (`{"texture": …}`): los consejos toman el ícono del tema y los secretos, el de su logro |
| `ftbquests:custom_icon` con `ftbquests:entity_face` | `ars_nouveau:starbuncle` | Cara de criatura (caras incluidas en FTB Library para vanilla, Ars Nouveau, Mekanism, PneumaticCraft, Undergarden, Iron's Spells y otros) | **Sí**: las quests de criaturas de Ars (Starbuncle, Whirlisprig) |
| Tema `icon` por selector | `[#entrelumen_tip] icon: …` | Ícono por defecto de las quests sin ícono (`QuestObjectBase.getIcon`) | **Sí**: los consejos toman el ícono de consejo del tema |

## 9. Tareas

Evidencia: registro en `quest/task/TaskTypes` (y `neoforge/FTBQuestsNeoForge` para `forge_energy`); claves en cada `readData`; comunes en `Task.readData` y `QuestObject.readData`.

| Tipo | Claves | Qué pide | Decisión |
|---|---|---|---|
| Comunes | `id`, `title` (idioma `task.<ID>.title`), `icon`, `optional_task`, `disable_toast` | Título e ícono propios de la tarea | **Sí**: `title` en observaciones y bajas («Mirá a tu starbuncle trabajando»). **No** `optional_task` |
| `item` | `item` (ítem con componentes), `count` (long), `consume_items` (tristate), `only_from_crafting`, `match_components` (`none`, `fuzzy`, `strict`), `task_screen_only` | Tener o entregar ítems | **Sí**: casi todo; `consume_items: true` sólo en encargos; `match_components: fuzzy` para un ítem que sólo existe como variante con componente (la llave arcana de Ars Technica es una llave de Create con `ars_technica:runic_wrench`; el valor por defecto `none` aceptaría cualquier llave). **Motor** (27/9): filtros de FTB Filter System, que FTB Quests usa por el adaptador de FTB XMod Compat, para las tareas `any` («cualquiera de estos», [content/sectors/README.md](../../content/sectors/README.md#tarea-de-cualquiera-de-estos)). **No**: `only_from_crafting` y `task_screen_only` |
| `checkmark` | — | Un clic | **Sí**: consejos y notas, sin recompensa |
| `advancement` | `advancement`, `criterion` (vacío = el logro entero) | Obtener un logro | **Sí**: logros de Create y Ars Nouveau que ya prueban un armado real |
| `dimension` | `dimension` | Entrar a una dimensión | **Sí** en guías (v2); **Motor** en cadenas |
| `biome` | `biome` (`#tag` admitido: `BiomeTask.setBiome`) | Estar en un bioma | **Motor**. Corrección del 27/9: el Bosque de Archwood sí se genera. TerraBlender 4.1.0.3 viene dentro de Deep Aether, y Ars reemplaza algunos `minecraft:forest` porque `archwoodForest` queda en 2 (`common/world/Terrablender` del JAR de Ars) |
| `structure` | `structure` (`#tag` admitido) | Estar dentro de una estructura | **Sí**: secreto de Ars, una guarida de Wilden (`#ars_nouveau:wilden_den`, en bosques) |
| `kill` | `entity`, `entityTypeTag`, `value`, `custom_name`, `nbt_filter` | Matar N criaturas | **Sí**: la Quimera Wilden (`ars_nouveau:wilden_boss`) |
| `location` | `dimension`, `ignore_dimension`, `position`, `size` | Estar en una caja de coordenadas | **No**: el mundo no es fijo |
| `observation` | `observation_type` (`block`, `block_tag`, `block_state`, `block_entity`, `block_entity_type`, `entity_type`, `entity_type_tag`), `to_observe`, `timer` (ticks de cliente, máx. 1200) | Mirar algo N ticks; barra de progreso en pantalla (`FTBQuestsClientEventHandler`) | **Sí**: mirar tu tren entrando a la estación (`create:carriage_contraption`), un buzón junto a una estación (`#create:postboxes`) y un Starbuncle trabajando |
| `stat` | `stat`, `value` | Estadística de Minecraft | **Motor**: pensado para Exploración (distancia volada o nadada) |
| `xp` | `value`, `points` | Entrega experiencia: la resta (`XPTask.submitTask`) | **No**: nada en el libro cobra experiencia |
| `fluid` | `fluid`, `amount` | Fluido | **No**: se entrega en una Task Screen de FTB (`canInsertItem`), un bloque ajeno al pack; un balde se pide como ítem |
| `forge_energy` | `value`, `max_input` | FE | **No**: misma razón |
| `gamestage` | `stage`, `team_stage` | Etapa | **No**: sin sistema de etapas; el de respaldo usa etiquetas de entidad (`EntityTagStageProvider`) y nadie las pone |
| `custom` | — | Tarea por script | **No**: el libro no usa tareas por script (desde el 27/9 FTB XMod Compat y KubeJS podrían resolverlas) |
| `entrelumen:campaign` | `milestone` | Hito de campaña del companion | **Sí** (historia, v2) |

## 10. Recompensas

Evidencia: `quest/reward/RewardTypes`, cada `readData`; comunes en `Reward.readData`.

| Tipo | Claves | Efecto | Decisión |
|---|---|---|---|
| Comunes | `team_reward` (tristate), `auto` (`default`, `disabled`, `enabled`, `no_toast`, `invisible`), `exclude_from_claim_all`, `ignore_reward_blocking`, `disable_reward_screen_blur`, `title`, `icon` | Reparto y reclamo | **Sí**: `auto: enabled` en avisos y fanfarrias, `title` en recompensas de tabla. **No** el resto |
| `item` | `item`, `count`, `random_bonus`, `only_one` | Ítems | **Sí**; `random_bonus` en encargos. **No** `only_one` |
| `xp` | `xp` | Puntos | **Sí** |
| `xp_levels` | `xp_levels` | Niveles | **No**: los niveles valen distinto según el nivel del jugador; los puntos son parejos |
| `choice` | `table_id` o `table_data` | Elegís uno de la tabla (`SelectChoiceRewardScreen`) | **Sí**: hitos de las cadenas |
| `random` | tabla | Uno al azar, sin vacío | **Sí**: secretos |
| `loot` | tabla | Uno al azar con peso vacío y `loot_size` | **Sí**: el jefe de Ars. Las cumbres y los encargos dan la caja misma (`ftbquests:lootcrate` con su componente), que se abre con clic derecho |
| `all_table` | tabla | Toda la tabla | **No**: un kit entero es demasiado |
| `command` | `command`, `permission_level` (o `elevate_perms`), `silent`, `feedback_message`; variables `{p}`, `{x}`, `{y}`, `{z}`, `{chapter}`, `{quest}`, `{team}` | Corre un comando (`CommandReward.claim`) | **Sí**: la fanfarria de cada cumbre (`playsound`, nivel 2, silenciosa, automática). Nada que dé ítems ni poder |
| `toast` | `description` (clave de idioma: `Component.translatable`) | Aviso en pantalla (`FTBQuestsNetClient.displayCustomToast`) | **Sí**: «Secreto encontrado» en cada secreto; las claves viven en el companion (`assets/ftbquests/lang`) |
| `advancement` | `advancement`, `criterion` | Otorga un logro | **No**: regalaría logros de otros mods |
| `gamestage` | `stage`, `remove` | Etapas | **No** |
| `currency` | `amount` | Moneda | **No**: sin proveedor (`register(…, false)`) |
| `custom` | — | Script | **No** |

## 11. Tablas de recompensa y cajas de loot

Evidencia: `quest/loot/RewardTable.readData`, `LootCrate`, `item/LootCrateItem.use`, `BaseQuestFile.loadRewardTableFile`.

| Clave | Valores | Efecto | Decisión |
|---|---|---|---|
| `id`, `order_index`, `icon`, título `reward_table.<ID>.title` | — | Identidad | **Sí** |
| `rewards` | lista de recompensas con `weight` (float, 1 por defecto) e `id` | Contenido ponderado | **Sí**, con IDs estables |
| `empty_weight` | float | Peso de «nada» (sólo `loot` y las cajas) | **No**: peso 0, toda caja da algo |
| `loot_size` | entero | Tiradas por apertura | **Sí**: 2 en las cajas de los actos I a IV, 3 en V y VI; 1 en las demás tablas |
| `hide_tooltip` | bool | Oculta el contenido en el tooltip | **No**: se ve qué puede salir |
| `use_title` | bool | La recompensa muestra el título de la tabla | **Sí** |
| `loot_crate.string_id` | `[a-z0-9_]` | ID de la caja (componente `ftbquests:loot_crate`) | **Sí** |
| `loot_crate.item_name` | clave de idioma | Nombre del ítem caja (`LootCrateItem.getName`) | **Sí**: claves en el companion |
| `loot_crate.color` | RGB | Tinte del modelo de la caja | **Sí**: un color por acto |
| `loot_crate.glow` | bool | Brillo de encantamiento | **Sí**: sólo la caja del acto VI |
| `loot_crate.drops` (`passive`, `monster`, `boss`) | pesos | Caída desde mobs | **Sí**, en 0: las cajas no caen de mobs |
| `loot_table_id` | recurso | Se lee y se escribe, pero ningún código lo usa en 2101.1.34 | **No** |
| Bloque abridor de cajas | `ftbquests:loot_crate_opener` | Abre cajas por automatización | **No**: no hace falta |

Contenido: sección «Recompensas y cajas» de [quest-book-v3](../design/quest-book-v3.md).

## 12. Texto enriquecido

Cada línea de `quest_desc` es un párrafo. `util/TextUtils.parseRawText`: si la línea empieza con `[` o `{` y termina con `]` o `}`, se lee como componente JSON de Minecraft (con escapes Unicode); si no, FTB Library la interpreta (`util/TextComponentParser`, `util/client/ClientTextComponentUtils`).

| Elemento | Sintaxis | Evidencia | Efecto | Decisión |
|---|---|---|---|---|
| Colores y formatos | `&0`–`&f`, `&k` `&l` `&m` `&n` `&o` `&r` | `TextComponentParser.CODE_TO_FORMATTING` | Color, negrita, tachado, subrayado, cursiva, ofuscado | **Sí**: la interferencia del Atlas (historia) y los párrafos simples |
| Color exacto | `&#RRGGBB` | `TextComponentParser.parse` | Color hexadecimal | **Sí**: rótulos y acentos de cada motivo |
| Arcoíris | `&z` | `SPECIAL_COLOR_CODES` (2101.1.3) | Color que cicla | **No**: un color que cicla distrae, y los secretos ya tienen su aviso |
| Escape | `\&` | `TextComponentParser.parse` | `&` literal | **No**: con `&`, el compilador escribe el párrafo como JSON, donde `&` es literal |
| Sustituciones | `{clave.de.idioma}` | `defaultStringToComponent` → `I18n.get` | Texto traducido del cliente | **No**: usamos componentes `translate`, que además llevan hover |
| Imagen en la descripción | `{image:<ícono> width:N height:N align:left\|center\|right fit:true click_action:… text:…}` | `ClientTextComponentUtils.defaultStringToComponent`, `ImageComponent`, `ViewQuestPanel.makeImageComponentWidget` | Una imagen por línea; `fit` la estira al ancho del panel | **Sí**: un diagrama en la segunda página de tres cumbres (texturas provisorias) |
| Enlace web | `{open_url:<url> text:<texto>}` | `defaultStringToComponent` | Enlace | **No**: sin enlaces externos |
| Salto de página | línea `{@pagebreak}` exacta | `Quest.PAGEBREAK_CODE`, `Quest.buildDescriptionIndex`; botones de página en `ViewQuestPanel.addButtonBar` | Páginas con botones | **Sí**: las cumbres (la tarea en la página 1; el diagrama y el remate en la 2) |
| Componente JSON | `["", {"text": …}, …]` | `TextUtils.parseRawText` → `Component.Serializer.fromJson` | Estilos por tramo | **Sí**: lo emite el compilador de texto del motor |
| `hoverEvent` `show_item` | `{"action":"show_item","contents":{"id":…}}` | `ViewQuestPanel.QuestDescriptionField.addMouseOverText` | Tooltip real del ítem | **Sí**: todo ítem nombrado con `[item:…]` |
| `hoverEvent` `show_text` | texto | ídem | Tooltip propio | **Sí**: aclaraciones cortas sin alargar el párrafo (qué es la capacidad de estrés, qué es la Fuente) |
| `hoverEvent` `show_entity` | entidad | ídem (sólo con tooltips avanzados) | — | **No** |
| `clickEvent` `change_page` | ID hex de quest o capítulo, con `/<página>` | `handleCustomClickEvent` → `ImageClickAction.openQuest` (2101.1.6) | Salta a otra quest, capítulo o página | **Sí**: `[quest:clave\|texto]` y `[chapter:…]` (de la prensa al mecanismo de precisión, del latón a los complementos) |
| `clickEvent` `open_url` con `docs:` | — | `SHOW_DOCS` | Abre un libro de docs | **No**: `DocsModRegistry` vacío |
| `clickEvent` `open_url`, `run_command`, `suggest_command`, `copy_to_clipboard` | — | Vanilla (`Screen.handleComponentClicked`) | — | **No** |
| `keybind` | `{"keybind":"key.ponder.ponder"}` | Vanilla | Muestra la tecla que el jugador tiene asignada | **Sí**: nunca escribimos una tecla fija (Ponder en Create, el libro de Ars) |
| `translate` | `{"translate":"block.create.shaft"}` | Vanilla | Nombre del ítem en el idioma del jugador | **Sí**: `[name:…]` (el quemador de blaze en Create) |
| `font` | `minecraft:alt`, `minecraft:illageralt`, `minecraft:uniform`, y fuentes del companion | Vanilla; `FontManager` carga cualquier `assets/<ns>/font/*.json` | Otra fuente | **Sí**: `[rune\|…]` en la cumbre de Ars (alfabeto de la mesa de encantamientos). Presentación v2 (28/9): `entrelumen:quest_big`, las hojas de vanilla al doble (`[big\|…]`), y `entrelumen:quest_icons`, un glifo del área privada por textura de ítem (`[icon:…]`, `[li:…]`). El renglón sigue midiendo 9 px |
| Escala del texto de la descripción | — | `ViewQuestPanel.addDescriptionText` crea cada `TextField` a escala 1 (`TextField.scale` existe pero no se usa); `FTBQuestsClientConfig` sólo escala el rastreador y el changelog | No existe | **No**: la letra la decide la escala de GUI del jugador; la v2 gana legibilidad con jerarquía y aire. La descripción, además, va sin sombra (banderas 0 en `Theme.drawString`) |
| `bold`, `italic`, `underlined`, `strikethrough`, `obfuscated`, `color` | — | Vanilla | — | **Sí**, desde el marcado |
| Escapes Unicode | `\u2022` | `TextUtils` (`UnicodeUnescaper`, 2101.1.3) | Caracteres | **No**: escribimos el carácter |
| Subtítulo de quest y de capítulo | idioma | sección 7 | — | **Sí** |
| Títulos de tarea y recompensa | idioma | `TranslationManager` | — | **Sí** |

## 13. Tema

Evidencia: `quest/theme/ThemeLoader` (apila todos los `assets/ftbquests/ftb_quests_theme.txt`, el de FTB primero), `QuestTheme.get` (busca en la quest, sube al capítulo y al archivo, y recién después usa `[*]`), `selector/ThemeSelector.parseSelector`, `property/ThemeProperties`.

| Elemento | Sintaxis | Efecto | Decisión |
|---|---|---|---|
| Archivo | `assets/ftbquests/ftb_quests_theme.txt` en cualquier recurso | Se apila sobre el de FTB | **Sí**: en el companion, generado |
| Selectores | `*`, `#etiqueta`, tipo (`quest`, `chapter`…), ID hex, `!selector`, `a & b`, `a \| b` | Filtros | **Sí**: `#etiqueta`. **No** el resto: con etiquetas alcanza y el orden entre selectores del mismo tipo no está garantizado |
| Variables | `{{propiedad}}` | Reusar valores | **No** |
| Herencia | quest → capítulo → archivo → `[*]` | Una etiqueta de capítulo tiñe todo el capítulo | **Sí**: colores de línea por motivo |
| `quest_locked_color`, `quest_not_started_color` | ARGB | Contorno del nodo | **Sí**: por rol |
| `quest_started_color`, `quest_completed_color` | ARGB | Contorno en curso y completo | **Sí**: secretos y cumbres completos quedan dorados; el resto, con los de FTB |
| `dependency_line_completed_color`, `_uncompleted_color`, `_unavailable_color`, `_requires_color`, `_required_for_color` | ARGB | Colores de línea | **Sí**: completas, pendientes y no disponibles por motivo (cobre en Create, violeta en Ars); las de requisito, las de FTB |
| `dependency_line_texture` | ícono | Textura de la línea | **No** hasta tener arte (pedido a Elias) |
| `dependency_line_thickness` | double, 0,17 | Grosor | **Sí**: 0,2 en las cadenas |
| `dependency_line_unselected_speed`, `_selected_speed` | double | Animación de la textura | **Sí**: Ars fluye despacio sin selección (0,15) |
| `quest_spacing` | 0–8 | Espaciado de la grilla | **No**: cambia toda la geometría calculada a 28 px |
| `pinned_quest_size` | double | Tamaño en el rastreador | **No** |
| `icon` | ícono | Ícono por defecto | **Sí**: consejos |
| `tasks_text_color`, `rewards_text_color` | color | Encabezados del panel | **Sí**: por motivo |
| `quest_view_title`, `quest_view_border`, `quest_view_background` | — | Panel de lectura | **No**: el panel de FTB se lee bien |
| `background`, `chapter_panel_background`, `widget_*`, `button`, `text_color`… | — | Fondo y widgets globales (`BACKGROUND()` sin objeto: no se puede por capítulo) | **No**: un fondo propio es un pedido de arte, no un color |
| `full_screen_quest` | 0/1 | Panel a pantalla completa | **No** (v2) |
| Íconos de interfaz (`check_icon`, `lock_icon`, `alert_icon`, `pin_icon_*`…) | — | — | **No** |
| `wiki_url`, `wiki_icon` | — | Botón de wiki | **No**: sin enlaces externos |
| Formas propias | `assets/ftbquests/textures/shapes/<nombre>/{background,outline,shape}.png`, 128×128 blancas con `blur` | `ThemeLoader.findShapes` lista cualquier espacio de nombres, pero `QuestShape` carga siempre `ftbquests:` | **Sí**: cinco formas `el_*` en el companion, con texturas provisorias hasta el arte de Elias; nombres en `ftbquests.quest.shape.<nombre>` |

## 14. Otros elementos del JAR

| Elemento | Evidencia | Decisión |
|---|---|---|
| Pantallas de tarea (`screen_1`…`screen_7`), barreras (`barrier`, `stage_barrier`), detector | `registry/ModBlocks`, `block/*` | **No**: bloques de mundo; el pack no los coloca |
| Ítems `book`, `custom_icon`, `lootcrate`, `missing_item`, `task_screen_configurator` | `registry/ModItems` | **Sí** `custom_icon` (íconos) y `lootcrate` (cajas). El resto no |
| Quests fijadas y rastreador | `ViewQuestPanel.PinViewQuestButton`, `pinned_quest_size` | **No**: es una función del jugador, que no hace falta explicar en el libro |
| Teclas de FTB Quests | `key.ftbquests.quests` y las del libro (buscar, recentrar, zoom) | **No** en texto: quien lee el libro ya lo abrió |
| Comandos `/ftbquests` | `FTBQuestsCommands` | **No** en el libro; el QA sólo mira el log de carga |
| Eventos para scripts (`CustomTaskEvent`, `CustomRewardEvent`, `CustomClickEvent`, `ThemePropertyEvent`) | `events/*` | **No**: sin KubeJS de FTB |
| Integración de visor de recetas y filtros | `integration/RecipeModHelper.NoOp`, `ItemMatchingSystem` | **Motor** (27/9): FTB Filter System y FTB XMod Compat están en el lock; el motor compila `any` a un filtro inteligente, y el visor de recetas es JEI ([mod-pingpong](../design/mod-pingpong.md#ftb-filter-system-y-ftb-xmod-compat-279)) |

## Recuento

| | Sí | Motor | No |
|---|---|---|---|
| Archivos (1) | 9 | 0 | 0 |
| `data.snbt` (2) | 10 | 0 | 13 |
| Capítulo (3) | 10 | 1 | 10 |
| Grupo (4) | 2 | 0 | 1 |
| Imágenes (5) | 14 | 1 | 5 |
| Enlaces (6) | 2 | 0 | 1 |
| Quest (7) | 26 | 2 | 7 |
| Íconos (8) | 5 | 1 | 7 |
| Tareas (9) | 9 | 2 | 6 |
| Recompensas (10) | 8 | 0 | 6 |
| Tablas y cajas (11) | 9 | 0 | 4 |
| Texto (12) | 14 | 0 | 9 |
| Tema (13) | 11 | 0 | 9 |
| Otros (14) | 1 | 1 | 5 |
| **Total** | **130** | **8** | **83** |

Cada «No» tiene su motivo en la fila: un bloque ajeno al pack, un enlace externo o algo que el libro ya resuelve de otra forma. Varios pedían FTB XMod Compat o FTB Filter System, que entraron al lock el 27/9.

## Límites

- Todo se leyó del código descompilado y se cargó en un servidor; el dibujo en el cliente no se vio. Tres cosas nuestras no tienen antecedente en ATM10 ni en FTB Evolution y conviene mirarlas en el primer vistazo: los rectángulos finos rotados que dibujan figuras, los rótulos con `font` y las curvas de Bézier generadas.
- `item:` como imagen se usa desde el 28/9 en los pilotos de la presentación v2 sin haberlo visto en el cliente: el código lo permite (`ItemIcon`), pero ningún pack de referencia lo usa en capítulos. Lo mismo vale para los sprites animados, los rótulos girados y las fuentes del companion.
