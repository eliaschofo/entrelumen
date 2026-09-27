# Cadenas del libro (quest book v3)

Un archivo `sector_<nombre>.json` por capítulo, en el formato de `tools/format_sector.py`. Para redactar, `tools/mod_facts.py` y `tools/recipe_of.py` leen de los JAR fijados los ítems con sus nombres EN/ES, los logros, las recetas y la documentación de cada mod. Lo compila `tools/quest_engine.py` desde `tools/generate_quests.py`; lo valida contra los JAR fijados `tools/check_guides.py`, y sus contratos están en `tools/test_sector_book.py`. El estándar de una cadena, la gramática, las recompensas y el arte pedido están en [quest-book-v3](../../docs/design/quest-book-v3.md); las reglas de texto, en [quest-copy](../../docs/design/quest-copy.md).

## Capítulo

| Clave | Qué es |
|---|---|
| `format` | `"sector"` |
| `chapter` | `sector_<nombre>`: nombre del archivo y del ID estable |
| `group`, `act` | Grupo del libro y acto (`any`, `I`…`VI`): paga la XP y elige las tablas del acto |
| `motif` | Paleta de `quest_book.json` → `motifs` (colores de línea, panel, forma de hito) |
| `icon`, `emblem` | Ícono del capítulo y textura del medallón del hub |
| `title`, `subtitle` | Por idioma; el subtítulo es una lista de líneas |
| `entry` | Clave de la quest de entrada (sin dependencias locales) |
| `center` | Opcional: centro del dibujo; los paneles de rama se orientan radiales a él |
| `figures` | Figuras con nombre: `ring`, `arc` o `line`. Dan posiciones (`slot`) y se dibujan con `draw` |
| `groups` | Paneles de rama: `label` por idioma, `axis` (`radial`, `auto`, `x`, `y` o grados), `caption` |
| `links` | Enlaces a quests de otros capítulos: `target` y `at` |
| `art` | Imágenes: `texture` (a escala entera), `label`, `line`, `panel`; con `click`, `hover`, `reveal`, `alpha` |
| `quests` | Lista de quests |

Estilos de `draw`: `ring` (arco o círculo de segmentos), `teeth` (dientes de engranaje), `spokes`, `rails` (dos rieles y durmientes), `polyline`, `glyphs` (texturas de ítems de un mod alrededor de un círculo, por referencia).

## Quest

| Clave | Qué es |
|---|---|
| `key` | Clave semántica global; el ID es `stable_id("quest:" + key)`. Reusar la clave conserva el progreso |
| `role` | `entry`, `step`, `milestone`, `side`, `tip`, `info`, `secret`, `bounty`, `boss`, `capstone` |
| `deps` | Claves de dependencias (pueden ser de otro capítulo) |
| `task` / `tasks` | `item` (+`count`, `consume`, `components` para un ítem que sólo existe como variante), `any` (cualquiera de varios ítems o tags; ver abajo), `checkmark`, `advancement`, `dimension`, `biome`, `structure`, `kill`, `observation` (`observe`, `target`, `ticks`), `stat`; cada una con `title` e `icon` opcionales |
| `at` | `{"x", "y"}`, `{"figure", "slot", "out", "along"}` o `{"near", "dx", "dy"}` |
| `group` | Panel de rama al que pertenece |
| `icon`, `icon_scale`, `size` | Ícono (ítem, `{"texture"}` o `{"entity"}`), escala del ícono, tamaño si no es el del rol |
| `curve` | `{dependencia: curvatura}`: línea de Bézier |
| `hide_lines`, `hide_dependent_lines`, `reveal` | Líneas ocultas; aparecer recién con las dependencias completas |
| `min_deps`, `any_dep`, `exclusive` | N de M dependencias; una sola alcanza; rama exclusiva (N hijos) |
| `sequential`, `lore_after`, `cooldown` | Tareas en orden; texto oculto hasta completar; espera de un encargo |
| `en_us`, `es_es` | `title`, `subtitle` opcional, `text` (párrafos con marcado) |
| `sources` | Evidencia de cada dato mecánico |

## Tarea de «cualquiera de estos»

```json
{"any": ["minecraft:oak_log", "#minecraft:logs"], "count": 4,
 "title": {"en_us": "Any four logs", "es_es": "Cuatro troncos cualesquiera"}}
```

- `any` lista ítems y tags (con `#`), y cualquiera sirve. Un tag solo vale; un ítem solo es una tarea `item` común.
- `count` suma entre alternativas: dos troncos de roble y dos de abedul cuentan cuatro. `consume` funciona igual que en `item` (encargos).
- `title` es obligatorio en los dos idiomas, porque sin él FTB muestra «Smart Filter».
- El ícono es el primer ítem de la lista, o `icon` si lo das. Si la lista tiene sólo tags, `icon` es obligatorio. La quest usa ese ícono si no tiene uno propio.
- En el juego, un clic en la tarea abre la lista de ítems válidos.
- El motor la compila a una tarea `item` de FTB cuyo ítem es un filtro inteligente de FTB Filter System (`ftbfiltersystem:smart_filter`). La expresión va en su componente `ftbfiltersystem:filter`, con la sintaxis del propio mod: `or(item(minecraft:oak_log)item_tag(minecraft:logs))`. FTB Quests la resuelve con el adaptador de FTB XMod Compat. Si alguno de los dos mods falta en el lock, el motor y `check_guides.py` rechazan la tarea.
- `check_guides.py` pide que cada ítem exista en los JAR fijados, porque uno que falte anula el filtro entero. Cada tag tiene que estar definido por un JAR fijado, vanilla, NeoForge (sus tags `c:`), el companion o `pack/kubejs/data`, y tener al menos un ítem. A los ítems de la lista les aplica la misma regla de Almost Unified que a cualquier tarea.

## Marcado del texto

| Marca | Resultado |
|---|---|
| `[item:mod:id\|texto]` | Nombre del ítem en color, con el tooltip real del ítem |
| `[key:key.id]` | La tecla que el jugador tiene asignada |
| `[quest:clave\|texto]`, `[quest:clave/2\|texto]`, `[chapter:nombre\|texto]` | Enlace que abre la quest (en una página), o el capítulo |
| `[name:item.mod.id]` | Nombre traducido por el juego |
| `[hl\|…]`, `[b\|…]`, `[i\|…]`, `[warn\|…]`, `[good\|…]` | Acento del motivo, negrita, cursiva, advertencia, positivo |
| `[hover\|texto\|nota]` | Texto con nota al pasar el mouse |
| `[rune\|…]` | Fuente de la mesa de encantamientos |
| `[tip]` al principio | Prefijo de consejo: «» Pro tip:» / «» La posta:» |
| `{page}` | Salto de página |
| `{image:ns:textures/….png width:N height:N align:center}` | Imagen en la descripción (una por párrafo) |
