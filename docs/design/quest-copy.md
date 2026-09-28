# Texto de las quests: guía de estilo

Reglas para todo texto del libro: historia, cadenas (`content/sectors`), guías, hub. Salen del [playtest del 24/9](playtest-2026-09-24.md) («demasiado relleno, un cartel que dice "soy un cartel"; explicaciones breves, con onda») y del pedido del 25/9: directo, sin hablar de sí mismo, con algún juego de palabras, un chiste chico o un dato de oficio. Lo que una máquina puede revisar lo revisa `tools/quest_engine.py` (`check_copy`) en cada generación; el resto es criterio.

## La voz

**Inglés.** Directo y cálido. Segunda persona o imperativo, frases cortas, verbos concretos. Sin «simply», «just», «easily», «powerful», «amazing» ni signos de exclamación de vendedor.

**Español rioplatense.** Voseo siempre: «hacé», «poné», «tenés», «podés», «fijate». Palabras de acá cuando salen solas: «laburo», «la posta», «a pulmón», «de una», «un recreo». Sin forzar lunfardo en cada línea: una por quest, como mucho, y nunca en un dato mecánico. Los nombres de ítems son los del juego en español (el tooltip del ítem aparece al pasar el mouse, así que el nombre tiene que coincidir); si el juego no tiene traducción, se escribe en castellano natural y el tooltip muestra el original.

**La historia** tiene otro registro (ver la [biblia](story-bible.md)): misterio melancólico con esperanza, sin chistes fuera de lugar. Las guías y las cadenas pueden jugar; la historia, apenas.

## Reglas

1. **Al grano.** De una a tres oraciones cortas en la primera página. El motor rechaza una primera página de más de 330 caracteres visibles; lo que sobra va a una segunda página (`{page}`) o a una quest aparte.
2. **Nada meta.** Ni «esta quest…», «en este capítulo vas a aprender…», «completá esto para…», «bienvenido a…», «confirmá cuando…», ni describir el libro («los hexágonos son hitos»). El nodo ya es un cartel: el texto dice lo que el cartel no puede.
3. **No repetir el título.** Si el título es «Aleación de andesita», la primera oración no empieza «La aleación de andesita es…». Arranca por el cómo o el para qué.
4. **Sin adjetivos de relleno.** «Poderoso», «increíble», «esencial», «muy útil»: afuera. Un número dice más: «seis ejes en vez de cuatro».
5. **Un chiste, a veces.** Un juego de palabras, una broma chica o una imagen, en una de cada tres o cuatro quests, y mejor en el **subtítulo**: se ve al pasar el mouse sin abrir la quest y no estorba la instrucción.
6. **Consejos con prefijo.** Un dato de oficio lleva el prefijo dorado `» Pro tip:` / `» La posta:` (marca `[tip]`). Los nodos de consejo empiezan con él; en una quest común, va en su propio párrafo.
7. **Datos verdaderos para las versiones fijadas.** Recetas, cantidades, rangos y teclas salen de los JAR de `catalog/curated.json` o de la config del pack, con la fuente en `sources`. Si algo cambia por el pack (una puerta, un componente de acto), se dice.
8. **Nunca una tecla escrita.** Las teclas se muestran con `[key:key.id]`: el jugador ve la que tiene asignada. El motor rechaza «F8», «Alt+E», «Ctrl+T».
9. **Ítems con su nombre y su tooltip.** `[item:mod:id|nombre]` pinta el nombre y muestra el tooltip real del ítem.
10. **Mismo contenido en los dos idiomas.** Mismos ítems enlazados, mismas páginas, mismos consejos. La traducción es libre; el dato, no.

## Colores del texto

| Qué | Color | Marca |
|---|---|---|
| Ítems y bloques (con tooltip) | turquesa `#8FD6C8` | `[item:…\|…]` |
| Teclas | amarillo `#F2D060` | `[key:…]` |
| Enlaces a quests o capítulos | celeste `#9DC3FF`, subrayado | `[quest:…\|…]`, `[chapter:…\|…]` |
| Consejo | dorado `#E8B04A` | `[tip]` |
| Advertencia | salmón `#FF8C7A` | `[warn\|…]` |
| Acento del motivo | cobre (Create) o lila (Ars) | `[hl\|…]` |
| Nota al pasar el mouse | lavanda, subrayado | `[hover\|…\|…]` |
| Runas | violeta, fuente de la mesa de encantamientos | `[rune\|…]` |
| Positivo | verde `#9BE08A` | `[good\|…]` |
| Voz del Atlas (historia) | tachado `&m` y glitch `&k` de hasta 8 letras, dos por texto | actos I a IV |
| Primera oración | blanco, negrita | `[lead]` |
| Número estrella | acento del motivo, al doble de tamaño | `[big\|…]` |
| Ojo | salmón `#FF8C7A`, con la alerta | `[careful]` |
| Dato | celeste `#9DC3FF`, con el ícono de información | `[note]` |
| Íconos | los colores de la textura (el glifo va en blanco para no teñirse) | `[icon:…]`, `[li:…]` |

## Forma (presentación v2)

Desde el 28/9, pedido de Elias: el texto «está medio crudo y muy chico». FTB no deja agrandar la letra de la descripción ([quest-book-v3](quest-book-v3.md#qué-deja-hacer-ftb-quests-2101134)), así que se gana legibilidad con jerarquía y aire. Rige para los capítulos con `"presentation": 2`; los demás se pasan de a uno ([plan](quest-book-v3.md#presentación-v2-28-de-septiembre-de-2026)).

1. **La primera oración, en negrita.** `[lead]` abre la quest con una oración corta: lo que te llevás si leés un solo renglón. No repite el título ni dice qué es la quest; dice para qué sirve o qué la hace distinta («Energía gratis, pareja y silenciosa.», «Cuatro de cada cinco salen bien; el resto, chatarra.»). Un consejo o una nota abre con su prefijo en lugar del lead.
2. **Una idea por párrafo**, dos renglones como mucho. Lo que se puede decir en una lista (modos, usos, pasos, recetas) va en viñetas `[li]` de pocas palabras, sin punto final si son fragmentos.
3. **Íconos en las viñetas** cuando el ítem tiene textura plana (`[li:create:iron_sheet]`). Con modelo 3D, la viñeta es un punto. Las acciones del mouse van con su ícono: `[icon:right_click] Clic derecho…`.
4. **Los números en el acento.** `[hl|…]` para las cantidades que el jugador va a buscar con la vista. `[big|…]` sólo para el número estrella, uno o dos por quest: nunca en el primer párrafo de una página ni después de una viñeta.
5. **Una llamada por tipo:** `[tip]` para el dato de oficio, `[careful]` para lo que rompe algo o te hace perder, `[note]` para el contexto que no es instrucción. Cada una en su párrafo.
6. **Lo que es profundidad va a la página 2:** diagramas, tablas, cifras de rendimiento, casos raros. El tope de 330 caracteres visibles de la primera página no cambia.
7. **Mismo contenido en los dos idiomas:** los mismos ítems enlazados, los mismos íconos, las mismas páginas y las mismas llamadas.

Para pasar un capítulo, `tools/quest_draft.py` arma un borrador con esta forma: el lead, las listas, los íconos de textura plana, los números, las advertencias en `[careful]` y la página 2 ([kit](quest-book-v3.md#kit-para-pasar-capítulos-28-de-septiembre)). La voz no la toca: el chiste, el orden de las ideas y los leads que faltan los pone quien redacta.

Ejemplo, «Consejo: Catalizadores» de Create · Cinética. Antes, un párrafo de unos 200 caracteres con cuatro procesos seguidos. Después: «La posta: Lo que haya en la corriente decide el trabajo:», cuatro viñetas con el balde de lava, la fogata, el balde de agua y la fogata de almas («Lava o un Quemador de Blaze prendido: funde», «Fuego: ahúma», «Agua: lava», «Fuego de almas: embruja»), un «Ojo: La comida en lava se quema: ahumala.» y una línea final sobre la velocidad.

## Diez reescrituras

Tomadas del libro del 25/9. Las seis primeras ya están aplicadas (cadenas e historia); las otras cuatro son el modelo para las guías que falta pasar a cadenas.

**1. La bienvenida que explica el capítulo** (`crb_welcome`, guía de Create)
- Antes: un checkmark titulado «Rotation does the work» con «A source makes rotation, shafts and cogs carry it, machines spend it to press, mix, grind and move. No electricity needed.». Leer y tildar.
- Después: no hay bienvenida. La entrada es la primera tarea real, «Andesite Alloy» / «Aleación de andesita», con el subtítulo «Create's duct tape.» / «El Poxipol de Create.»

**2. La tecla escrita** (`crb_ponder`)
- Antes: «Hover any Create block and hold W (Ponder)…» Si el jugador movió la tecla, el texto miente.
- Después: «» La posta: Pasá el mouse por cualquier bloque de Create y mantené [W]…», con la tecla que el jugador tiene asignada (`[key:key.ponder.ponder]`).

**3. El libro que se presenta a sí mismo** (`arrival`, acto I)
- Antes: «…Quests open with F8 or the book icon in your inventory; confirm once you have read this.»
- Después: se borra la oración. Quien la lee ya abrió el libro.

**4. La leyenda del mapa** (`crafts_welcome`, acto II)
- Antes: «Pick any branch below. Hexagonal tasks open the Atlas, and only a delivery there moves your team's story; the rest are lessons. Confirm when ready.»
- Después: «Four benches, four crafts: work them in any order. Only a delivery in the Atlas moves your team's story; everything else is practice.» / «Cuatro bancos, cuatro oficios: trabajalos en el orden que quieras…»

**5. El cartel que dice «soy un cartel»** (`horizon_welcome`, acto V)
- Antes: «…activating the Ark forges the Light Key and opens act VI. This optional page only introduces the chapter: confirm when ready.»
- Después: queda la primera oración. El mismo pase tocó 52 descripciones de la historia (EN y ES): se fueron los «Confirm…» / «Confirmá…» del final, las teclas escritas y las explicaciones del libro. `test_quest_book.py` impide que vuelvan.

**6. El cierre que sólo resume** (`arssrc_mastery`, guía de Ars)
- Antes: título «Jars that stay full» y «Several Sourcelinks, a bank of jars, relays feeding the apparatus… When jars stay full while you work, move on to rituals and creatures.»
- Después: la rama de la Fuente cierra con una tarea, «The Living Matrix» / «La Matriz viva», el componente que el Atlas pide; la línea que sale de ahí ya muestra a dónde seguir.

**7. Teclas en una guía** (`de_tips`, Draconic Evolution)
- Antes: «Keys in this pack: Place Item is Alt+P… Tool Config is Alt+Y and Tool Modules Shift+U…»
- Después: «» Pro tip: [Place Item] sets a held item down as a display block.», y las otras dos teclas igual, cada una con `[key:…]`: el jugador ve la que tiene asignada, sea la del pack o la suya.

**8. El jefe que se describe como opcional** (`cata_mastery`, Cataclysm)
- Antes: el logro de matar a todos los jefes, con un segundo párrafo: «The hardest optional challenge in this family, and pure bragging rights: the story never asks for it.»
- Después: una cumbre con el mismo logro y el subtítulo «Eight for eight.» / «Ocho de ocho.»; el texto dice sólo lo que el jugador no sabe (el logro cuenta por jugador, así que uno tiene que dar cada golpe final). Que es opcional lo dicen la forma del nodo y el color. (La versión anterior prometía un disco que Cataclysm 3.33 ya no suelta.)

**9. El título que ya es un prefijo** (`entrelumen_altars_tip_farm`)
- Antes: título «Tip: Stack the Effects».
- Después: título «Stack the altars» / «Apilá los altares» en un nodo de consejo; el prefijo dorado lo pone el motor.

**10. El cierre épico sin tarea** (`deepworlds_mastery`, mundos profundos)
- Antes: un checkmark con «You stole the Warden's heart, crossed below the bedrock and opened portals to worlds made for digging. The deep is no longer a place to fear. Only a place to mine.»
- Después: una cumbre con una tarea real (el ítem o el logro que cierra la rama) y la frase final como texto que aparece al completarla (`lore_after`).

## Cómo se revisa

- En las cadenas, `tools/quest_engine.py` rechaza al generar: frases meta de la lista `BANNED`, teclas escritas, títulos de más de 40 caracteres, subtítulos de más de 64, primeras páginas de más de 330 caracteres, una primera oración igual al título, marcado roto, y idiomas con distinto número de páginas o distintos ítems, teclas o enlaces. Con la forma v2 (`tools/quest_text.py`) también rechaza un `[lead]` que no abre la quest, un `[big|…]` que abre página o sigue a una viñeta o tiene más de 10 caracteres, y un `{rule}` en un borde.
- En la historia, `tools/test_quest_book.py` rechaza frases meta, teclas escritas y pedidos de confirmar; `generate_quests.py` limita los códigos de formato y los glitches.
- Las guías viejas no pasan por estas reglas hasta que se conviertan en cadenas.
- `tools/check_guides.py` comprueba contra los JAR que existan los ítems enlazados, las teclas, los logros, las criaturas, las estructuras y las texturas, y que Almost Unified no cambie el ítem que se pide.
- Lo demás (el chiste, la voz, el dato de oficio) lo revisa una persona con esta guía al lado.
