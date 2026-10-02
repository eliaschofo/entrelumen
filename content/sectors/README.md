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
| `groups` | Paneles de rama: `label` por idioma, `axis` (`radial`, `auto`, `x`, `y` o grados), `caption`; el rótulo toma `caption_scale` (cualquier tamaño: 1,5, 2, 3…; 1 por defecto, 2 en un capítulo con `"presentation": 2`), `caption_tint` y `caption_bold` |
| `links` | Enlaces a quests de otros capítulos: `target` y `at` |
| `art` | Imágenes: `texture` (a escala entera), `label`, `line`, `panel`; con `click`, `hover`, `reveal`, `alpha`. Presentación v2: los tipos de [abajo](#arte-presentación-v2) |
| `reward_tables` | Opcional: tablas de recompensa propias del capítulo, por nombre (ver [Recompensas propias](#recompensas-propias)) |
| `presentation` | Opcional: `2` dibuja `[tip]` con el farol del libro en vez de «» » ([quest-book-v3](../../docs/design/quest-book-v3.md#presentación-v2-28-de-septiembre-de-2026)) |
| `quests` | Lista de quests |

El acto de un capítulo es el más temprano en el que un jugador llega de verdad, no el de la historia: las tablas de recompensa siguen esa etiqueta (revisión del 1/10/2026):

- `bumblezone_hive` y `bumblezone_court`: **II**. Una perla de ender contra un nido entra en la Bumblezone desde el acto I, sin ninguna puerta del Atlas.
- `aether_deep`: **III**, como `aether`. Es el mismo portal (glowstone y un balde de agua) y su quest de entrada depende de `aether_enter`, así que nada de Deep Aether se alcanza antes que el Aether.
- `starlight_night`: sigue en **IV**. Las ruinas del portal están en el Overworld, pero entrar pide el Orbe de la Profecía, un componente que el Atlas abre en el acto IV.
- `deep_worlds`: sigue en **IV**. La Ciudad Antigua se puede pisar antes, pero el diseño pone Deeper and Darker en el IV (exploration-structures-family) y no hay otra puerta que la del Warden.

Estilos de `draw`: `ring` (arco o círculo de segmentos), `teeth` (dientes de engranaje), `spokes`, `rails` (dos rieles y durmientes), `polyline`, `glyphs` (texturas de ítems de un mod alrededor de un círculo, por referencia).

## Quest

| Clave | Qué es |
|---|---|
| `key` | Clave semántica global; el ID es `stable_id("quest:" + key)`. Reusar la clave conserva el progreso |
| `role` | `entry`, `step`, `milestone`, `side`, `tip`, `info`, `secret`, `bounty`, `boss`, `capstone`; y `decor`, un juguete del lienzo que no es contenido ([abajo](#adornos-decor)) |
| `deps` | Claves de dependencias (pueden ser de otro capítulo) |
| `task` / `tasks` | `item` (+`count`, `consume`, `components` para un ítem que sólo existe como variante), `any` (cualquiera de varios ítems o tags; ver abajo), `checkmark`, `advancement`, `dimension`, `biome`, `structure`, `kill`, `observation` (`observe`, `target`, `ticks`), `stat`; cada una con `title` e `icon` opcionales |
| `at` | `{"x", "y"}`, `{"figure", "slot", "out", "along"}` o `{"near", "dx", "dy"}` |
| `group` | Panel de rama al que pertenece |
| `icon`, `icon_scale`, `size` | Ícono (ítem, `{"texture"}` o `{"entity"}`), escala del ícono, tamaño si no es el del rol |
| `curve` | `{dependencia: curvatura}`: línea de Bézier |
| `hide_lines`, `hide_dependent_lines`, `reveal` | Líneas ocultas; aparecer recién con las dependencias completas |
| `min_deps`, `any_dep`, `exclusive` | N de M dependencias; una sola alcanza; rama exclusiva (N hijos) |
| `sequential`, `lore_after`, `cooldown` | Tareas en orden; texto oculto hasta completar; espera de un encargo |
| `rewards`, `reward_table`, `reward_choice` | Opcionales, encima de lo que paga el rol: ítems (con componentes), una tabla del capítulo al azar, una tabla del capítulo a elección (ver [Recompensas propias](#recompensas-propias)) |
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

## Recompensas propias

El rol ya paga XP, tablas del acto y cajas ([quest-book-v3](../../docs/design/quest-book-v3.md#recompensas-y-cajas)). Una quest puede sumar ítems propios y una tabla del capítulo:

```json
{"key": "bees_diamond", "role": "milestone", "deps": ["bees_mutation"], "reward_table": "perfect_genes",
 "task": {"item": "productivebees:configurable_honeycomb", "components": {"productivebees:bee_type": "productivebees:diamond"}},
 "rewards": [{"item": "productivebees:spawn_egg_configurable_bee", "count": 1,
              "components": {"minecraft:entity_data": {"id": "productivebees:configurable_bee", "type": "productivebees:diamond"}}},
             {"item": "minecraft:honeycomb", "count": 8}]}
```

```json
"reward_tables": {
  "perfect_genes": {"title": {"en_us": "A perfect gene", "es_es": "Un gen perfecto"}, "rolls": 1, "entries": [
    {"item": "productivebees:gene", "weight": 3, "components": {"productivebees:gene_group": {"attribute": "productivity", "value": "productivity.very_high", "purity": 100}}},
    {"item": "productivebees:gene", "weight": 1, "components": {"productivebees:gene_group": {"attribute": "endurance", "value": "endurance.strong", "purity": 100}}}]},
  "starter_bees": {"title": {"en_us": "Pick a bee", "es_es": "Elegí una abeja"}, "entries": [
    {"item": "productivebees:spawn_egg_configurable_bee", "components": {"minecraft:entity_data": {"id": "productivebees:configurable_bee", "type": "productivebees:iron"}}},
    {"item": "productivebees:spawn_egg_configurable_bee", "components": {"minecraft:entity_data": {"id": "productivebees:configurable_bee", "type": "productivebees:gold"}}}]}
}
```

- **`rewards`**: lista de `{"item", "count", "components"}`. `count` va de 1 a 64 (1 si falta) y `components` es opcional. Se suman a lo que paga el rol, al final de la lista.
- **`reward_table`** sortea en una tabla del capítulo; **`reward_choice`** deja elegir una entrada. Las dos nombran tablas de `reward_tables` del mismo capítulo.
- **Tablas** (`reward_tables`): nombre en minúsculas con `_`, `title` en los dos idiomas (hasta 40 caracteres, sin marcado), `rolls` (sorteos con reposición, de 1 a 8; 1 si falta) y `entries` con `weight` (mayor que 0; 1 si falta). Sin ícono propio, FTB rota los íconos de las entradas.
  - En `reward_choice` el jugador elige una sola entrada: el peso no cuenta y una tabla que sólo se elige tiene `rolls` 1.
  - Cada tabla tiene que usarla alguna quest.
- **Cuándo no**: una quest de sólo checkmark (consejos y notas) no paga, ni siquiera con recompensas propias. En un encargo (`bounty`) se repiten con el encargo.
- **Qué no se da**, como en las tablas del libro: ítems de ENTRELUMEN, salidas de recetas con puerta, ni ítems que Almost Unified reemplaza (`check_guides.py` nombra el que corresponde). Repetir el mismo ítem con los mismos componentes es un error: se suman las cantidades.
- **IDs**: cada ítem sale de la clave de la quest y del ítem (`reward:<clave>:item:<ítem>`, y `:2`, `:3` para otra pila del mismo ítem, como otra abeja); una tabla, de `reward:<clave>:reward_table:<nombre>`. Reordenar la lista no entrega de nuevo lo ya reclamado. La tabla usa `table_id("<capítulo>/<nombre>")` y queda en `reward_tables/<capítulo>__<nombre>.snbt`, después de las 18 del libro, que no cambian.
- `format_sector.py` pone `rewards` en su propia fila del bloque de la quest, y cada tabla en una línea.

### Componentes

`components` es el mapa `{"tipo": valor}` que FTB Quests 2101.1.34 guarda con la pila de la recompensa: `ItemReward` lee el ítem con `itemOrMissingFromNBT`, que se lo pasa entero al `ItemStack.CODEC` de 1.21.1 (`id`, `count`, `components`).

- Un tipo sin registrar, o uno que no se guarda (`minecraft:creative_slot_lock`, `minecraft:map_post_processing`), hace fallar la pila entera, y FTB muestra su ítem faltante en lugar de la recompensa.
- `check_guides.py` busca cada tipo en los JAR fijados:
  - los de `minecraft:` en la clase `DataComponents` del JAR de vanilla;
  - los de un mod, en las clases que registran componentes en sus JAR.
- No se quitan componentes con `!`.
- Los valores se escriben en JSON y FTB los lee como SNBT. Por eso no se aceptan `null`, enteros fuera del rango de `int` ni listas que mezclen tipos: una lista mezclada rompe el archivo entero.

### Una abeja de regalo (Productive Bees)

El ítem es el huevo de la abeja configurable con el componente vanilla `minecraft:entity_data`: `id` es la criatura y `type`, el tipo de abeja (primer ejemplo de arriba).

- Es lo mismo que arma el mod (`BeeCreator.getSpawnEgg`) para la pestaña creativa, JEI, el clic central sobre una abeja y la incubadora, que convierte un gen de tipo en huevo.
- Vanilla exige el `id` en `entity_data` (`CustomData.CODEC_WITH_ID`). Al usar el huevo, `EntityType.updateCustomEntityTag` vuelca esos datos en la abeja nueva, y `ConfigurableBee.readAdditionalSaveData` toma `type`. El nombre y el color del huevo también salen de `type` (`SpawnEgg.getName`, `getColor`).
- Las abejas con criatura propia (las solitarias, por ejemplo) tienen su huevo sin componentes: `productivebees:spawn_egg_<criatura>`, como `productivebees:spawn_egg_blue_banded_bee`.
- `check_guides.py` pide que el tipo esté definido en los datos del mod (`data/<ns>/productivebees/**/<nombre>.json` da `<ns>:<nombre>`) y que cargue en este pack. Las condiciones `mod_loaded` se miran contra el lock y las de tag, contra los JAR. Hoy cargan 173 de los 431 tipos: `productivebees:diamond` sí, `productivebees:allthemodium` no.
- **La jaula no conviene.** `productivebees:bee_cage` guarda la abeja en `minecraft:custom_data` con todo su guardado de entidad (`BeeCage.captureEntity`) y la suelta con `entity.load` de esos datos. Escribirla a mano es imitar ese guardado; el huevo es lo que el propio mod entrega.

### Un gen perfecto

`productivebees:gene` con `productivebees:gene_group`: `{"attribute", "value", "purity"}`.

| Atributo | Valores (el mejor, en negrita) |
|---|---|
| `productivity` | `productivity.normal`, `.medium`, `.high`, **`.very_high`** |
| `endurance` | `endurance.weak`, `.normal`, `.medium`, **`.strong`** |
| `temper` | **`temper.passive`**, `.normal`, `.aggressive`, `.hostile` |
| `behavior` | `behavior.diurnal`, `.nocturnal`, **`.metaturnal`** (día y noche) |
| `weather_tolerance` | `weather_tolerance.none`, `.rain`, **`.any`** |
| `type` | Un tipo de abeja (`productivebees:diamond`) en lugar de un valor |

- `purity` va de 1 a 100.
  - El Honey Treat aplica el gen con esa probabilidad (`nextInt(100) <= purity`): con 100 siempre pega.
  - Combinar genes suma purezas hasta 100 (`CombineGeneRecipe`).
- «Perfecto» es el mejor valor con pureza 100, como en el ejemplo de la tabla `perfect_genes`.
- Valores de Productive Bees 13.13.5 (`GeneAttribute`, `GeneValue`). `check_guides.py` rechaza otro atributo, otro valor o una pureza fuera de rango.

### Semillas y esencias (Mystical Agriculture)

Son ítems simples, sin componentes: `{"item": "mysticalagriculture:inferium_seeds", "count": 2}`, `{"item": "mysticalagriculture:inferium_essence", "count": 16}`.

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
| `[tip]` al principio | Prefijo de consejo: «» Pro tip:» / «» La posta:»; con `"presentation": 2`, el farol del libro y «Pro tip:» / «La posta:» |
| `{page}` | Salto de página |
| `{image:ns:textures/….png width:N height:N align:center}` | Imagen en la descripción (una por párrafo) |

Presentación v2 (`tools/quest_text.py`; las reglas de uso están en [quest-copy](../../docs/design/quest-copy.md#forma-presentación-v2)):

| Marca | Resultado |
|---|---|
| `[lead]` al principio del primer párrafo | Toda la oración en negrita: la línea que se lee si se lee una sola |
| `[li]` al principio | Viñeta con un punto del color del motivo; entre viñetas seguidas no hay línea en blanco |
| `[li:mod:ítem]`, `[li:ns:textures/….png]`, `[li:nombre]` | Viñeta con un ícono: la textura plana del ítem (con su tooltip), una textura o un ícono con nombre |
| `[icon:…]` | El mismo ícono dentro del renglón. Con nombre: `right_click`, `click`, `info`, `alert`, `check`, `tip`, `secret`, `heart` |
| `[big\|×5]` | Hasta 10 caracteres al doble de tamaño, en el color del motivo, sobre la línea base. Nunca en el primer párrafo de una página ni después de una viñeta |
| `[careful]`, `[note]` al principio | Llamadas: «Careful:» / «Ojo:» con la alerta, «Note:» / «Dato:» con el ícono de información |
| `{rule}` | Una línea tenue entre dos párrafos |

Los íconos son glifos de una fuente que `generate_quests.py` escribe en el companion (`entrelumen:quest_icons`, 8 px, la textura referenciada desde su JAR). `check_guides.py` pide que la textura exista y que no sea animada. Los bloques con modelo 3D no tienen textura de ícono: para ellos, `[li]` sin ícono.

## Pasar un capítulo a la v2

La lista de cada redactor (para una guía o un capítulo de historia, [la suya](../guides/README.md#pasar-una-guía-o-un-capítulo-de-historia-a-la-v2)). Las reglas están en [quest-book-v3](../../docs/design/quest-book-v3.md#presentación-v2-28-de-septiembre-de-2026) (texto, lienzo, kit) y en [quest-copy](../../docs/design/quest-copy.md#forma-presentación-v2); esto es el orden de trabajo.

**0. Rama y dueño.** Una rama desde `origin/main` y un dueño por capítulo. Tocás tu `sector_*.json` y, si hace falta, arte nuevo propio en el companion; nada más.

**1. Borrador del texto.**
```
python tools/quest_draft.py content/sectors/sector_x.json
```
Reescribe el texto de todas las quests, inglés y español juntos, e imprime lo que no pudo decidir. Revisalo con `git diff`:
- cada `[lead]` es una oración que se sostiene sola y no repite el título; escribí los que faltan (la lista impresa);
- una lista es de cosas paralelas; si no lo son, volvela párrafo;
- `[careful]`, sólo para lo que rompe algo; `[big|…]`, uno o dos por quest, en el número que la resume;
- el chiste y la voz son tuyos: el borrador sólo cambia la forma.

Claves, tareas, títulos y datos no se tocan. Si un dato cambia, cambia con su `sources`.

**2. Escena: se dibuja de a poco (regla 6).**
- `"presentation": 2` en el capítulo.
- **Reusá la escena de la familia.** La primera cadena de una familia paga el dibujo y las demás toman otra parte de la misma escena: el mismo fondo y la misma paleta, otro lugar. Por ejemplo, las cadenas de Create viven en la caverna del molino y las de Mekanism, en la planta en corte. Se copian las entradas de `art` de la cadena que pagó el dibujo, nunca los archivos de un mod. Las pinturas que ya hay en los JAR fijados están listadas en quest-book-v3 («Cómo pasar los otros capítulos»).
- **Arrancá con un boceto:** el marco de la escena, la entrada, dos a cuatro hitos y el título.
- **Completalo con el progreso:**
  - los caminos (tronco, río, caño, cinta), con `through` y `grow`;
  - las piezas de la escena, la utilería y la luz, con el `reveal` de la quest que las gana;
  - el boceto de lo que falta, con `sketch`.
- **Pocos rótulos y grandes:** escala 2 para secciones, 3 o 4 para el título.
- **De uno a tres `decor`.**
- **Imágenes:** 700 por capítulo como mucho; apuntá a la mitad. Una pintura grande en vez de muchas fichas donde se lee igual.

**3. Validar**, en este orden:
```
python tools/format_sector.py content/sectors/sector_x.json
python tools/generate_quests.py
python tools/check_guides.py sector_x
python tools/test_sector_book.py
python tools/generate_quests.py --check
```
Sin errores. Los avisos de imágenes (más de 700) y de boceto (más del 40% visible al empezar) se arreglan o se explican en el reporte.

Si `check_guides.py` dice que `tools/quest_client_facts.json` no conoce una textura o un ítem nuevo de tu arte, corré `python tools/check_guides.py --write-client-facts`, regenerá con `generate_quests.py` y validá de nuevo. Ese archivo dice cómo dibuja el cliente cada textura (si anima, si es una hoja de baldosas, si el ítem tiene textura plana) y es parte de tu commit.

**4. Vistas previas.**
```
python tools/preview/preview_v2.py sector_x --state steps --screen --panels all --sheet --locale both
```
- Una sola por vez en toda la PC: el renderer toma `E:/Elias/Codex/Entrelumen-ssd/render.lock`, espera su turno y espera también mientras haya menos de 1,5 GB libres. Pedí todo en una sola llamada y dejalo terminar.
- Salen a `E:/Elias/Codex/Entrelumen-ssd/previews/<worktree>/`, nunca al repo.
- Mirá el boceto (`fresh`), cómo se completa (25, 50 y 75%), el final (`done`), la pantalla de 1080p y las hojas de paneles.
- Las PNG se miran con un visor, sin navegador. Si abrís uno, cerralo al terminar la tanda.

**5. Commit.** Sólo tus archivos, con `git add <ruta>` (nunca `git add -A`), sin las salidas generadas: `pack/config/ftbquests`, los textos y el tema del companion y sus fuentes se regeneran al integrar.

**6. Reporte**, corto:
- el capítulo y sus quests: todas las claves, tareas y datos conservados;
- la escena: qué se ve al empezar y qué llega con qué quest;
- las imágenes: total y porcentaje visible al empezar (lo imprime `check_guides.py`);
- lo que el borrador no decidió y cómo lo resolviste;
- las rutas de las vistas previas;
- los avisos que quedan y los riesgos.

## Arte (presentación v2)

`tools/quest_art.py`. Cada entrada de `art` es de un tipo, el de la primera de estas claves que tenga. Coordenadas y medidas en celdas, como las quests. Todas aceptan `order`, `alpha`, `tint` (el color de la imagen), `rotation`, `reveal` (aparece al completar esa quest), `hover` y `click`. Un camino, una pintura, un sprite o un marco aceptan además `sketch` (abajo).

| Tipo | Claves | Qué dibuja |
|---|---|---|
| `lettering` | `lettering` {idioma: texto de igual largo}, `path`, `scale`, `font`, `smooth` | Una letra por posición a lo largo de un camino, girada con él |
| `path` | `path` [[x, y]…] o `through` [clave o [x, y]…] (pasa por las quests), `grow`, `width`, `smooth` (tramos por segmento, Catmull-Rom), `step` (trozos de ese largo), `turn` (la veta de la textura va a lo largo), `closed`; y `color`, `texture`, `sprite`, `dots` (+`size`) o `items` | Un camino: río, eje, cinta, caño, cable, raíz, órbita |
| `mosaic` | `mosaic` [x, y] (esquina), `cell`, `rows` (cadenas), `legend` {carácter: dibujable} | Pixel art: cada carácter es un color (las corridas se juntan), una textura, un sprite o `item:` |
| `scatter` | `scatter` (dibujable o lista), `region` [x0, y0, x1, y1], `count`, `size` [mín, máx], `alphas`, `seed`, `spin`, `avoid` [[x, y, r]…], `near` (camino) + `band` [mín, máx] | Un puñado con semilla: estrellas, chispas, humo, flores en una orilla |
| `frame` | `frame` [x0, y0, x1, y1], `color`, `thickness_px`, `fill`, `fill_alpha`, `corners` | Marco con esquinas y relleno |
| `glow` | `glow` [x, y], `r` | Una luz suave (la partícula `flash` de vanilla), casi siempre con `reveal` |
| `text` | `text` {idioma}, `x`, `y`, `scale`, `font`, `bold`, `align`, `anchor`, `shadow` | Un rótulo de cualquier tamaño y ángulo |
| `item` | `item` (id), `x`, `y`, `size` | El ítem en 3D (`item:<id>`), como en el inventario; ignora `tint` y `alpha` |
| `sprite` | `sprite` (`ns:block/…` o una carpeta del atlas), `x`, `y`, `w`, `h` o `cells` [[x, y]…] + `cell` | Un sprite del atlas de bloques, animado en el juego |
| `picture` | `picture` (`ns:textures/….png`), `x`, `y`, `w`, `h` | Cualquier textura estirada a cualquier tamaño: una pintura del mod, una placa |

`line` y `panel` (los del motor) también respetan `reveal` y `click` desde el 28/9: ya no hace falta dibujarlos con `px.png` para que aparezcan con su quest o abran algo.

**El dibujo se completa jugando** (regla 6 del lienzo, [quest-book-v3](../../docs/design/quest-book-v3.md#reglas-v2-del-lienzo)):
- `through`: el camino pasa por las posiciones de esas quests, con puntos `[x, y]` intermedios donde haga falta.
- `grow: true`: cada tramo aparece con la quest a la que llega, así el camino se dibuja detrás del jugador. `grow` también puede ser una lista con una quest (o `null`, siempre visible) por segmento. No va junto con `reveal`.
- `sketch: true` (o `{"color", "alpha", "width"}`): además del dibujo, una copia tenue que está desde el principio, un orden más abajo. En un camino es una línea fina de tiza; en una pintura, un sprite o un marco, la misma forma teñida y casi transparente (alfa 64 por defecto, 90 como mucho). La tinta llega encima con `reveal` o `grow`. Un `item` no se puede bocetar, porque FTB dibuja los ítems sin alfa.

```json
{"id": "shaft", "through": ["crb_alloy", "crb_shafts", [-18.0, 1.0], "crb_casing"], "texture": "create:textures/block/axis.png",
 "width": 1.8, "step": 0.45, "turn": true, "grow": true, "sketch": true}
```

**Notas al pasar el mouse.** FTB muestra la nota (`hover`) de una imagen sólo si la imagen tiene un clic: sin clic, sólo la ve quien edita el libro (`ChapterImageButton.checkMouseOver`). Por eso:
- una imagen con `hover` y `reveal` pero sin `click` abre, al hacerle clic, la quest que la revela;
- una con `hover` y sin ninguno de los dos queda como aviso en `check_guides.py`: hay que darle un `click`.

Un dibujable es una textura (`ns:textures/….png`), un sprite (`ns:block/…`), `item:<id>` o un color `#rrggbb`. FTB manda cada imagen al cliente como texto, así que no hay recortes ni mosaicos: un patrón repetido son varias imágenes. `check_guides.py` revisa que las texturas y los ítems existan en los JAR fijados y que cada sprite esté en el atlas de bloques.

Lo que el cliente hace distinto de la vista previa, y el motor ya resuelve solo (1/10):
- Una textura animada del atlas se dibuja como sprite, que anima; FTB dibujaría la tira entera aplastada.
- Un ítem con textura plana se dibuja como sprite `ns:item/x`, detrás de los nodos. Un ítem 3D (`item:`) se dibuja por encima de nodos y líneas: no lo pongas debajo de un nodo (`check_guides.py` avisa).
- Una imagen rotada no se recorta fuera de pantalla y se dibuja siempre: rotá sólo lo que lo necesita.
- Un enlace a una quest oculta abre su capítulo, no la quest.

### Adornos (`decor`)

Un juguete del lienzo: un farol que prende la escena, un silbato. Es un checkmark opcional, sin recompensa, aviso, candado ni líneas. Tiene forma `none` y un tamaño de 0,5 a 4 (`size`, con `icon_scale` si hace falta), y suele apagar o encender arte con `reveal`. No cuenta para nada: ni en los totales del libro, ni en el rango de quests de la cadena, ni en la proporción de consejos (`quest_art.is_counted`). Uno a tres por capítulo, con texto EN/ES y `sources` como cualquier quest.
