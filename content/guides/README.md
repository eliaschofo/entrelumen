# Guías del pack (capítulos informativos)

Capítulos de quests que documentan el pack para quien recién empieza: cada mod importante, QoL, logística, construcción, granjas, tips y los sistemas propios de ENTRELUMEN. Son opcionales y no mueven la historia. El formato está en `tools/check_guides.py`; se valida con `python tools/check_guides.py`. `tools/generate_quests.py` los compila al libro en cinco grupos de FTB (ver [quest-book](../../docs/design/quest-book.md)), con el `layout` y `optional` tal como están escritos.

- Presentación v2: con `"presentation": 2`, una guía (o un capítulo de historia) escribe su texto como las cadenas y dibuja su escena con `art` ([abajo](#pasar-una-guía-o-un-capítulo-de-historia-a-la-v2)). Sin eso, se ve igual que antes.
- `emblem`: textura del ítem clave, dibujada 4× sobre el medallón del capítulo. Tiene que existir en un JAR fijado, ser cuadrada (16 o 32 px) y quieta.
- Una tarea con `tag` también nombra el `item` concreto que FTB va a pedir: no hay mod de filtros. Para metales unificados, el que elige Almost Unified.
- Una tarea de ítem pide el ítem que el jugador realmente recibe: si Almost Unified lo reemplaza, va el unificado.
- Una descripción enlaza quests o capítulos como las cadenas: `[quest:clave|texto]` y `[chapter:nombre|texto]` (ver [el formato de las cadenas](../sectors/README.md)). Ese párrafo va sin códigos `&`, que FTB no lee dentro de un texto con enlace, y los dos idiomas enlazan lo mismo. `tools/generate_quests.py` rechaza un destino que no existe.

## Pasar una guía o un capítulo de historia a la v2

Las guías y los capítulos de historia (`content/first_hour.json`, `content/act_*.json`, `content/inventory_that_remembers.json`) toman el mismo texto y el mismo lienzo que las cadenas; los compila `tools/quest_v2.py`. Esta es la lista de la cadena ([Pasar un capítulo a la v2](../sectors/README.md#pasar-un-capítulo-a-la-v2)) con lo que cambia. Pilotos para copiar: `guide_entrelumen_start.json` (el patio del sol) y `content/act_two.json` (el Taller hundido en corte).

**0. Rama y dueño.** Una rama desde `origin/main` y un archivo por dueño. Tocás tu JSON y nada más.

**1. Borrador del texto.**
```
python tools/quest_draft.py content/guides/guide_x.json      (o content/act_three.json)
```
- Pasa cada quest de `["título", "descripción"]` a `{"title", "text": [párrafos]}` y cambia los códigos `&` por marcas: `&e` y los otros colores por `[hl|…]`, `&l` por `[b|…]`, `&o` por `[i|…]`, `&k` por `[glitch|…]` y `&m` por `[strike|…]`. Pone `"presentation": 2` y escribe el archivo en el formato de `tools/format_sector.py`.
- Después hace el mismo borrador que en una cadena. Revisalo con `git diff` con los mismos criterios: el `[lead]`, listas sólo de cosas parejas, `[careful]` sólo para lo que rompe algo (el borrador a veces confunde un chiste con una advertencia), `[big]` sólo para el número que resume la quest.
- Lo marcado «fix by hand» rompe una regla aun sin marcas: un título de más de 40 caracteres, una primera página de más de 330 o una frase meta. Se arregla a mano.
- Claves, tareas, `type`, `item`, `count`, `optional`, íconos, forma y tamaño del `layout` y `sources` no se tocan. Las posiciones sí se pueden mover.
- Todo ítem nombrado va con `[item:…]`, igual que en las cadenas: `check_guides.py` lo busca en los JAR fijados.

**Guías, además:**
- Los enlaces `[quest:clave|…]` y `[chapter:nombre|…]` van a cualquier capítulo, y una quest puede tener `subtitle`.
- El medallón queda a la izquierda de la entrada. `"medallion": {"x", "y"}` lo mueve y `"medallion": false` lo saca, si la escena lo reemplaza.
- `hide_lines`, `hide_dependent_lines`, `reveal` e `icon_scale`, como en una cadena. Los nodos no se pueden pisar.

**Historia, además** (las revisan el motor y `tools/test_generate_quests.py`):
- Qué y después por qué: el último párrafo es el lore, simple (ni viñeta, ni llamada, ni `{page}`), y antes va al menos un párrafo de qué hacer. Hasta 500 caracteres visibles. La rama opcional (`"book": {"branch": true}`) queda exenta.
- Las quests no llevan subtítulo, y los enlaces sólo van a quests de la historia.
- La interferencia del Atlas es `[glitch|…]` (hasta 8 caracteres, dos por quest) y `[strike|…]`, sólo en los actos I a IV. Desde el V, nada.
- No cambian:
  - los hitos de campaña;
  - las dependencias y el sentido de lectura (la dependencia nunca queda debajo);
  - la forma y el tamaño de cada nodo por su rol;
  - los IDs: los digests de `test_generate_quests.py` los congelan;
  - lo que el texto tiene que nombrar (ruinas, personajes, recompensas de los proyectos): los tests lo buscan.
- El numeral, el emblema del acto y ahora el título a escala 3 se ponen solos arriba de todo el dibujo. Los paneles de rama pasan a escala 2; `"book": {"panels": false}` los saca cuando la escena pone sus rótulos, y un `frame` con esquinas los reemplaza.

**2. Escena**, como en la cadena: boceto primero, el dibujo que llega con cada quest, pocos rótulos y grandes, de uno a tres `decor` y hasta 700 imágenes (apuntá a la mitad).
- `"motif"` elige la paleta de `quest_book.json` → `motifs`; sin motivo, el oro de Heliodor.
- `"art"` usa los tipos de [Arte](../sectors/README.md#arte-presentación-v2). `through` pasa por las posiciones del `layout`.
- Un adorno es una quest con `"role": "decor"`, `"type": "checkmark"`, `"optional": true` y un `layout` con `"shape": "none"` y tamaño de 0,5 a 4; en la historia lleva también su `group`. Tiene texto EN/ES y, en una guía, `sources`.

**3. Validar**, en este orden:
```
python tools/format_sector.py --check
python tools/generate_quests.py
python tools/check_guides.py guide_x               (una historia: por su chapter, the_lost_crafts)
python tools/test_generate_quests.py
python tools/test_quest_book.py
python tools/test_presentation.py
python tools/generate_quests.py --check
```
Sin errores. Los avisos de imágenes y de boceto se arreglan o se explican en el reporte.

**4. Vistas previas.**
```
python tools/preview/preview_v2.py guide_x --state steps --screen --panels all --sheet --locale both
python tools/preview/preview_v2.py content/act_three.json --state steps --screen --panels all --sheet --locale both
```
Con las mismas reglas de RAM y de candado que las cadenas.

**5. Commit y reporte** como en la cadena: sólo tu JSON, sin las salidas generadas.
