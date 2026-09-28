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
| `reward_tables` | Opcional: tablas de recompensa propias del capítulo, por nombre (ver [Recompensas propias](#recompensas-propias)) |
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
| `[tip]` al principio | Prefijo de consejo: «» Pro tip:» / «» La posta:» |
| `{page}` | Salto de página |
| `{image:ns:textures/….png width:N height:N align:center}` | Imagen en la descripción (una por párrafo) |
