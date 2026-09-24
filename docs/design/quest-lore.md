# Texto de las quests con el lore nuevo

24 de septiembre de 2026. Aplica la [biblia de la historia](story-bible.md) al texto de las quests. Se cambiaron sólo títulos, subtítulos y descripciones: tareas, recompensas, dependencias, íconos y layout siguen igual.

## Qué cambió

- **Actos I a V:** se reescribieron las 128 quests (I 26, II 22, III 27, IV 29, V 24), en inglés y en español, junto con las etiquetas de ruta que nombraban a los personajes viejos (Mara, Ivo, Sera).
- **Acto VI:** cambiaron el título («VI · Solsticio»), el subtítulo, la etiqueta `threshold` y la quest de presentación `horizon_welcome`. Las otras 26 quests del acto VI y el capítulo lateral *El inventario que recuerda* no se tocaron: quedan para quien haga el acto VI y las side quests.
- **Títulos de acto:** se mantienen I–IV. El V pasa a llamarse «El Arca» / «The Ark» y el VI, «Solsticio».
- **Subtítulo de capítulo:** los seis actos tienen una presentación de una línea. `generate_quests.py` acepta un `subtitle` opcional por capítulo y lo escribe como `chapter.<id>.chapter_subtitle`, la clave de FTB Quests.
- **Formato:** cada descripción tiene dos párrafos. El primero dice qué hacer y el segundo por qué importa para la historia. Tienen como máximo 500 caracteres; antes llegaban a 1.000. Las entregas nombran el proyecto como lo muestra el Atlas («Entregá … en Banco de precisión»), no por su ID interno.
- **Recompensas nombradas:** cada hito que da algo lo dice con el nombre que se ve en el juego: los seis altares, el Brazo de Terra, el Atlas Vivo, la lente en bruto, las notas de relevamiento y el núcleo de señal.

## Actos, ruinas y personajes

| Acto | Ruina | Quién aparece | El Atlas |
|---|---|---|---|
| I · Una luz entre ruinas | Ruina inicial (pedestal de la brújula) y Patio del Atlas | Sólo iniciales: notas de T., J. y B. y un sello con una A dorada | Fragmentos con mucha interferencia: «…hacé lugar para… quienes todavía… no llegaron» |
| II · Los oficios perdidos | Taller hundido | Terra con nombre: el taller es suyo y el Atlas devuelve su brazo. J. alimenta a los turnos largos y B. cuida la magia | Al cierre, la voz dice su nombre a medias: «…Terra» |
| III · Rutas de intercambio | Invernadero-domo | Juan con nombre: el invernadero y el Altar de Crecimiento son suyos. Tinta dorada y sellos dorados rumbo a Solsticio | Terra se presenta. Solsticio no figura en ningún mapa: «La ciudad no cayó. Sigue acá» |
| IV · Las voces del Atlas | Observatorio del risco y Templo de la Luz Sagrada | Aurelia, en una sesión del Consejo. Bodhi es B., el sacerdote que dijo que no | El Templo revela la fusión. El Espíritu del Sol los congeló y se llevó el Corazón de Heliodor, el cristal de Bodhi |
| V · El Arca | (ninguna nueva) | Terra diseña el Arca, Juan pone la reserva, Aurelia firma la carta de habitabilidad y Bodhi pone la condición | Con el Corazón habla claro. Recién acá se nombra **el Entrelumen**. El Arca va a forjar la Llave de Luz |
| VI · Solsticio | Solsticio | Los cuatro, dentro del Entrelumen | Presentación nueva; el resto del acto no cambió |

Otras dimensiones: el santuario de Juan en Twilight Forest, el tratado con las abejas de Bumblezone y la luz del Aether, que vista desde el Observatorio es «de donde vino la luz».

## Interferencia del Atlas

FTB Library interpreta códigos `&` en las descripciones. El Atlas los usa para las palabras tachadas (`&m`) y para glitches cortos (`&k`). El generador rechaza:

- códigos inválidos o un `&` suelto;
- estilos que no se cierran con `&r` antes de que termine el párrafo;
- glitches de más de 8 caracteres o más de dos por descripción, para que el texto siempre se pueda reconstruir;
- códigos en títulos, subtítulos o etiquetas.

Desde el acto V el Atlas no tiene interferencia (hay un test que lo verifica).

## Mods nombrados

Las menciones van en la línea de qué hacer, no en la voz narrativa:

- **Create: New Age** como tecnología solar de Heliodor. En el acto II, las placas solares llevan el marco de calibración y cada bobina del generador un acoplador. En el III, el regulador habilita la placa solar avanzada, el energizador avanzado y el motor fuerte. En el IV, la lente espectral habilita el energizador y el motor reforzados, y el sello de contención la varilla de reactor. **Depende del merge de `feature/mods-r4`**, que todavía no está en `main`.
- Modern Industrialization y Ad Astra piden el bus del Arca y el motor de renovación (acto V).

## Tests

- Siguen congelados, con los mismos digests: los 7 `.snbt` de capítulo (IDs, tareas, dependencias, íconos y layout), `content/campaign_task_ids.json` y todas las frases de adquisición que ya se exigían («sin Toque de Seda», «ocho esmeraldas», «tres papeles y un lingote de cobre», etc.).
- Los tres digests del **texto** de idioma (primeros 3, 4 y 5 capítulos) se reemplazaron a propósito: congelaban el texto viejo. Ahora se comprueba que las claves de idioma son exactamente las que implican esos IDs y que sumar capítulos no cambia el texto de los anteriores.
- Contratos nuevos:
  - títulos y presentación de cada acto;
  - que no queden Mara, Ivo ni Sera en los actos I–V;
  - que el Entrelumen no aparezca antes del acto V;
  - personajes, ruinas, Espíritu del Sol, Corazón y Llave por acto;
  - qué hacer y por qué, en dos párrafos, en cada quest;
  - que la interferencia desaparezca desde el acto V;
  - que se rechace el formato inseguro;
  - que las recompensas de `projects.json` (altares y Brazo de Terra) aparezcan nombradas con el texto del idioma del acompañante.

## Pendiente

- **El capítulo del acto VI contiene la construcción del Arca.** Los seis módulos, los lotes y la activación que forja la Llave están en `last_horizon`, y en `projects.json` los módulos son de acto 6. La biblia pone «construir el Arca» en el acto V. Por ahora el VI se llama «Solsticio» y su presentación dice que primero se termina el Arca. Hay que elegir entre mover los módulos al acto V, lo que toca el acompañante y la progresión, o dejar que el VI empiece terminando el Arca. Lo tiene que saber quien escriba el acto VI.
- **Espíritu del Sol:** la biblia lo pone al final del acto IV. Los objetivos de la brújula ponen las mazmorras de plata y de oro en el acto 5, y no existe ninguna quest ni ningún ítem del Corazón de Heliodor. El texto lo presenta como la meta que sigue y no exige nada. Falta el drop en el acompañante y alinear la brújula.
- `heliodor-ruins.md` todavía pone el Templo en el acto V; este texto sigue a la biblia, que lo pone en el IV.
- Las ruinas de los actos no están colocadas: el texto las nombra como lugares de la historia, pero la brújula todavía no apunta a ellas.
- Los textos del acompañante no se tocaron: nombres de proyectos del Atlas, lore de la brújula y tooltips. Las quests citan los nombres de proyecto tal como se ven hoy.
- `content/story_bible.md` y `content/glossary.json` («Arca de los Horizontes») son de la narrativa anterior.
- No se vio en el cliente cómo se ven `&k`/`&m` ni los subtítulos de capítulo.
