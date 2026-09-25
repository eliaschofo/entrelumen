# Acto VI · Solsticio (diseño, 24 de septiembre de 2026)

Diseño del controlador sobre la [biblia](story-bible.md). **Corto y denso:** 11 misiones de historia, con viajes al Overworld. Lo largo es el posgame: comercio, lote e ítems creativos. *Implementado el 24/9 en la rama `feature/act-six`: ver «Implementación» al final.*

## Punto de partida

El equipo activó el Arca y tiene la Llave de Luz. La Llave abre el paso una vez, se rompe y queda como llave personal de ida y vuelta. Solsticio está congelada en un mediodía eterno: sus habitantes viven, pero la ciudad no puede salir del Entrelumen. El portal del Ayuntamiento se arma con **tres reliquias y la llave rota**. Al abrirse, el Entrelumen queda libre.

## Misiones de historia

| # | Misión | Quién | Qué hacer | Recompensa |
|---|---|---|---|---|
| 1 | **Cruzar la luz** | — | Usar la Llave de Luz y llegar a la plaza del Ayuntamiento. | Llave rota (la de regreso) |
| 2 | **La ciudad que no amanece** | Aurelia | Hablar con la alcaldesa en el Ayuntamiento. Explica el portal: tres reliquias y la llave. Pide el Corazón de Heliodor «para custodiarlo». | Marca en la brújula a los tres salones |
| 3 | **Semillas del mundo** | Juan | Juan teme quedarse sin suministros. Traer semillas de 8 especies distintas del Overworld (variedad, no cantidad). | Sus cultivos se reabastecen (flores y comida en los jardines) |
| 4 | **Una cosecha para todos** | Juan | Entregar 4 platos elaborados distintos de Farmer's Delight, 16 de cada uno (se automatizan con Central Kitchen o Slice & Dice). | **Semilla Luminosa** (reliquia I) |
| 5 | **Energía de afuera** | Terra | Terra necesita energía que no sea luz congelada: entregar componentes de Create: New Age (bobina y placa solar avanzadas) y una batería cargada de un mod de energía. | Diagrama del Terraprisma (lore) |
| 6 | **Canalizar la luz** | Terra | Traer una Luminosidad de cada disciplina (se compran a los nativos de la sala de comercio). Terra las funde en su obra maestra. | **Terraprisma** (reliquia II) |
| 7 | **Buenas obras** | Bodhi | Bodhi no bendice por encargo: pide ayudar a la gente de la ciudad. Completar encargos de las posadas hasta sumar **relación 5** con Bodhi (ver abajo). | Bodhi escucha |
| 8 | **La bendición** | Bodhi | Devolverle el Corazón de Heliodor (lo diste al Atlas en el acto IV: el Atlas te lo devuelve para esto). Bodhi lo bendice con «la fuerza del amor». | **Corazón de Heliodor bendito** (reliquia III) |
| 9 | **La mesa de las dos luces** | Aurelia y Bodhi | Aurelia quiere el cristal para controlar la luz; Bodhi teme repetir la fusión. Llevarlos a la misma mesa (hablar con los dos en la capilla y en el Ayuntamiento). Acuerdo: Aurelia deja de ser autócrata y llama a elecciones, y el cristal queda en manos del jugador para abrir el portal. | — |
| 10 | **El portal** | — | Colocar las tres reliquias y mostrar la llave rota en el portal del Ayuntamiento. Se abre para todos y el Entrelumen queda **libre**: bajan los precios de todas las tiendas. | Waystone global / portal gemelo |
| 11 | **Elecciones en Solsticio** | Aurelia | Unos días después (tiempo de juego), votación en la plaza. Aurelia es reelegida por su buen gobierno. | Fin de la historia; se abre el posgame |

- **Ningún final alternativo:** es un solo final (decisión de Elias). Tampoco hay descuentos por camino; el único cambio de precios es el de «liberado».
- **Posgame:** lote propio, tiendas con precios de liberado, nativos que venden Luminosidades en sus biomas, ítems creativos crafteables con Luminosidades.

## Relación con Bodhi y side quests de las posadas

- Cada posada (4) tiene un posadero con **2 encargos** de vecinos: son 8 en total. Cada encargo completado suma 1 de relación con Bodhi para el equipo. Hacen falta 5.
- Encargos, cortos y variados, que obligan a recorrer la ciudad y el Overworld:
  - devolver un libro a la librería;
  - llevar flores al jardín secreto;
  - reparar un farol (cobre encerado);
  - encontrar un gato perdido en los tejados;
  - traer miel del colmenar para la panadería;
  - llevar un mapa del cartógrafo a un vecino anciano;
  - recuperar una herramienta caída al río;
  - encender las velas de la capilla.
- **Easter eggs:**
  - la taberna escondida bajo la plaza;
  - el jardín secreto en la colina más alta;
  - el reloj de sol.
  Encontrar cada uno da una página de lore (una sola vez) y un recuerdo decorativo. Los tres juntos revelan una pista del **rumor de corrupción** de Aurelia (lore oculto del posgame, sin castigo).

## Aldeanos

- Todos los aldeanos que genera la ciudad llevan la **ropa de Heliodor** (tipo de aldeano `entrelumen:heliodor`, textura ya dibujada).
- Los personajes con nombre llevan algo distintivo:
  - Aurelia: sello dorado y banda de cobre;
  - Terra: guante mecánico;
  - Juan: delantal de jardinero;
  - Bodhi: estola turquesa.
- Diálogos cortos EN/ES en la voz de [quest-lore.md](quest-lore.md). Los vecinos repiten líneas que cambian después de la liberación y de las elecciones, con rumores sueltos sobre Aurelia.

## Qué necesita el código (resumen para el worker)

1. Tipo de aldeano `entrelumen:heliodor` aplicado a todos los aldeanos de la ciudad, más un distintivo para los cuatro personajes.
2. Diálogo por clic derecho de los personajes y posaderos, con estado por equipo (qué misión va).
3. Tareas de quest que dependan de hablar con un NPC y de entregar ítems a un NPC. Integración con FTB Quests por comando, recompensa o tarea personalizada, lo más robusto.
4. Relación con Bodhi por equipo (`SolsticioData`), sumada por los encargos de las posadas (gancho `SolsticioCommerce.registerSideQuest`).
5. Bendición: el Corazón de Heliodor se entrega a Bodhi y devuelve `heliodor_relic_3`. Si el Atlas se quedó el Corazón en el acto IV, se lo devuelve al equipo en la misión 8.
6. La misión 10 usa el portal que ya existe y activa `SolsticioCommerce.setLiberated`.
7. Elecciones: un evento con fecha de juego después de la liberación, que cambia diálogos y lore.
8. Tests: GameTests por misión y por gate.

## Implementación (24 de septiembre de 2026)

Rama `feature/act-six`. Código: `SolsticioStoryRules` (reglas puras, con JUnit) y `SolsticioStory` (runtime), más ajustes en `SolsticioCommerce`, `CommerceSites`, `CommerceRules`, `SolsticioPortalBlock`, `SolsticioData` y `SolsticioCity`. Quests: `content/act_six.json` (capítulo `solsticio`, 11 quests). Datos: `tags/item/solsticio/{seeds,meals}.json`, cinco objetivos nuevos en `content/compass_targets.json` y unas 170 líneas EN/ES.

### Cómo se completa una misión: hitos de campaña, no checkmarks

Cada misión es un **hito de la campaña del equipo** (`campaign.completed`, el mismo conjunto que usa el Atlas): `solsticio_mayor`, `solsticio_seeds`, `solsticio_harvest`, `solsticio_power`, `solsticio_terraprism`, `solsticio_good_works`, `solsticio_blessing`, `solsticio_accord`, `solsticio_portal` y `solsticio_elections`. La misión 1 es el `solsticio_arrival` que ya existía.

- **Quién lo registra:** el servidor, cuando pasa lo que la misión pide: un clic derecho a un personaje con lo que corresponde encima, el portal o la votación. Nada del libro puede completar una misión.
- **Qué ve el libro:** cada quest usa la tarea propia `entrelumen:campaign`. Es una proyección de solo lectura: mira el hito cada 20 ticks y al entrar, sigue el DAG de FTB y resetea la quest si el hito desaparece.
- **Por qué así:** es lo más robusto con FTB Quests 2101.1.34.
  - Un `checkmark` se puede tildar desde el cliente.
  - Una tarea de ítem de FTB no expresa «ocho especies distintas» ni «una batería cargada», y consumiría ítems de cualquiera del equipo.
  - Un comando o una recompensa de FTB dependen del orden de reclamo.
  - Los hitos se guardan con la campaña: un grupo los hereda del fundador y un grupo archivado los conserva.
- **Recompensas:** las reliquias, el diagrama y la canasta los da el personaje en el mundo, una vez por campaña, al registrar el hito. Las quests no tienen recompensas de FTB (`rewards: []`, como el resto de los capítulos). Sus textos nombran lo que se recibe.
- **El grafo:** `SolsticioStoryRules.REQUIRES` es el mismo grafo que las dependencias del capítulo, y lo comparan `SolsticioStoryRulesTest` y `tools/test_generate_quests.py`. Juan (3–4), Terra (5–6) y Bodhi (7–8) son tres ramas después de Aurelia, para que un equipo pueda repartírselas. La 9 pide las tres reliquias.
- **Visitantes:** nada de esto corre para un equipo que no cruzó con su propia Llave de Luz. Un visitante escucha una línea de visitante.

### Las once misiones en el juego

| # | Hito | Dónde | Qué comprueba el servidor | Qué da |
|---|---|---|---|---|
| 1 | `solsticio_arrival` | — | El cruce con la llave propia (ya existía) | La llave rota |
| 2 | `solsticio_mayor` | Aurelia | Hablar con ella | La brújula marca el salón de Aurelia, los jardines, el taller, la capilla y el portal (`solsticio_*` en `compass_targets.json`, tipo de objetivo nuevo `city_marker`: lee los marcadores de la ciudad colocada, así que sirve para cualquier plantilla) |
| 3 | `solsticio_seeds` | Juan | 8 especies distintas del tag `entrelumen:solsticio/seeds` (vanilla, `#c:seeds`, `#minecraft:villager_plantable_seeds`). Se lleva una de cada una | Una canasta por día y por equipo: 6 de una comida y 2 flores |
| 4 | `solsticio_harvest` | Juan | 4 platos distintos del tag `entrelumen:solsticio/meals` (las 27 comidas de Farmer's Delight, sin los guisos vanilla), 16 de cada uno | `heliodor_relic_1` |
| 5 | `solsticio_power` | Terra | 4 bobinas del generador y 2 placas solares avanzadas de Create: New Age, más una batería de cualquier mod de energía de ≥100.000 FE cargada al 90 % (capacidad `EnergyStorage.ITEM`). Si falta un mod, su pieza se omite | Libro «Diagrama del Terraprisma» (3 páginas) |
| 6 | `solsticio_terraprism` | Terra | Una Luminosidad de cada disciplina | `heliodor_relic_2` |
| 7 | `solsticio_good_works` | Bodhi | Relación ≥5, se registra sola con el quinto encargo | «Bodhi escucha» |
| 8 | `solsticio_blessing` | Bodhi | 1.º clic: si el Corazón está en el Atlas, `HeliodorHeart.release` lo devuelve. 2.º clic con el Corazón encima: lo consume | `heliodor_relic_3` |
| 9 | `solsticio_accord` | Aurelia y Bodhi | Hablar con los dos, en cualquier orden. El segundo cierra la mesa con una escena de tres líneas | — |
| 10 | `solsticio_portal` | Portal | Tras el acuerdo, las tres reliquias del equipo (una vez cada una) y su propia llave rota | El primer equipo lo abre para todos: waystone global o gemelo y `SolsticioCommerce.setLiberated`. Los equipos siguientes ponen sus reliquias en el portal abierto y completan la suya |
| 11 | `solsticio_elections` | Aurelia | La votación ya ocurrió: hablar con ella | Fin de la historia |

**Casos límite de la misión 8:**

- Si el Espíritu del Sol todavía tiene el Corazón, Bodhi lo dice.
- Un Corazón recuperado y nunca entregado al Atlas se bendice directo desde la mano.
- Si el Corazón se liberó y se perdió, Bodhi lo espera; `/entrelumen admin heart give` lo recupera.

**Cambio en el portal:**

- Las reliquias ahora se cuentan por equipo, en su campaña.
- Poner una reliquia exige el acuerdo, y el portal ya no la rechaza si está abierto.
- El dormido muestra cuántas puso el propio equipo.
- La llave se sigue mostrando, no se consume.

**Elecciones:**

- Son un evento global en `SolsticioData`: `liberatedAt` (tiempo de juego del Overworld al liberar), `electionsHeld` y `electionsAt`.
- Se votan **3 días de juego** (72.000 ticks) después de la liberación. Lo revisa el servidor cada 100 ticks: difunde el resultado, con partículas y campana en la plaza si está cargada.
- Un mundo liberado antes de esta rama empieza a contar la primera vez que se revisa.
- A partir de ahí cambian:
  - Aurelia: «Llamé a elecciones… en %s días» → «elegida».
  - Terra, Juan y Bodhi: líneas `after_elections`.
  - Los vecinos: el pool `elected`.

### Personajes y diálogo

- **Aparición.** Aurelia, Terra, Juan y Bodhi aparecen en sus marcadores (`mayor`, `inventor`, `gardener`, `priest`) como sitios `CHARACTER` del mismo registro de la población.
  - Son estatuas: sin IA, invulnerables y fijas. Si mueren o se los llevan, vuelven.
  - No comercian y están nombrados.
  - Una ciudad colocada antes de esta rama los recibe al arrancar (`CommerceSites.ensureCharacters`).
- **Diálogo.** El clic derecho elige la línea según la campaña del equipo y el estado del mundo (`SolsticioStoryRules.stage`).
  - Todas las líneas son cortas y están en EN y ES, con la voz de [quest-lore.md](quest-lore.md). Aurelia es aristócrata y justa; Terra, literal y precisa; Juan, cálido; Bodhi, sereno.
  - Las claves son `entrelumen.solsticio.character.<personaje>.<etapa>`.
- **Posaderos.** Dorotea, Tobías, Amparo y Ciro usan el gancho `registerSideQuest`. El gancho se registra para cada `sidequest:` de la ciudad al arrancar, al colocar la ciudad y al aparecer el posadero. A un visitante le dicen su saludo de siempre.
- **Vecinos.** Hay tres pools de 8 líneas: el de siempre, `liberated` (desde la liberación) y `elected` (desde la votación). Hay rumores sobre los ventanales, las barandas y el segundo libro de cuentas de Aurelia.

### Relación con Bodhi y los ocho encargos

La relación es la cantidad de encargos cumplidos por el equipo: se guardan en su campaña como `solsticio.errand.<id>`, y `solsticio.taken.<id>` mientras están en curso. Cada posadero ofrece sus dos encargos de a uno. Si el lugar de un encargo falta en la ciudad (otra plantilla), lo recibe el posadero.

| Posadero | Encargo | Tarea verificable |
|---|---|---|
| Dorotea | Miel para la panadería | 4 botellas de miel a Miga (`shop:bakery`) |
| Dorotea | El gato perdido en los tejados | Canela aparece en el techo de la casa más cercana a la posada y se coloca cuando se carga su chunk. Pararse a ≤6 bloques en horizontal y ≤16 en vertical la hace bajar; se oye maullar desde ≤24. Dorotea da la dirección |
| Tobías | Devolver un libro a la librería | Tobías da el «Cancionero de Tobías», que se entrega a Lucerna (`shop:bookstore`). Si se pierde, da otro |
| Tobías | Encender las velas de la capilla | En la capilla (≤14 bloques del marcador `priest`), clic derecho con yesquero o carga ígnea y 4 velas encima. Consume las velas y 1 de durabilidad |
| Amparo | El mapa del cartógrafo para un vecino anciano | Rumbo (`shop:maps`) da el «Mapa para Anselmo», que se entrega a Anselmo: el residente más cercano al cartógrafo, elegido una vez y nombrado |
| Amparo | La herramienta caída al río | Pescar en el agua de Solsticio con el encargo activo trae las «Tijeras de podar de Amparo». Se devuelven a Amparo |
| Ciro | Reparar un farol (cobre encerado) | 1 farol y 4 de cobre cortado encerado, a Ciro |
| Ciro | Flores al jardín secreto | 4 flores pequeñas distintas, entrando al jardín secreto (≤7 bloques del marcador). Se deja una de cada una |

- Los ítems propios de un encargo son vanilla con nombre, lore y `custom_data`. No se usan, así que el mapa no se dibuja.
- Canela es un gato por equipo: invulnerable, sin IA y ajeno a clics. Un gato cuyo encargo terminó mientras su chunk estaba descargado se borra al cargar.

### Easter eggs

- **Descubrimiento.** Los tres marcadores `easter:tavern`, `easter:secret_garden` y `easter:sundial` se descubren entrando a ≤5 bloques en horizontal y ≤3 en vertical. Lo revisa el servidor cada 20 ticks, solo con jugadores en Solsticio.
- **Qué da.** Cada equipo recibe, una vez:
  - una página de lore (libro escrito de 2 páginas, traducido en cada cliente);
  - un recuerdo con nombre: el jarro de la taberna (maceta decorada), la azalea del jardín secreto y el gnomon del reloj de sol (pararrayos).
- **Los tres juntos** dan «Una cuenta que no cierra» (3 páginas): la pista de la corrupción de Aurelia, sin castigo.

### Ropa de Heliodor y distintivos

- **Tipo de aldeano.** Se registró `entrelumen:heliodor`, con la textura ya dibujada. Lo llevan todos los aldeanos que genera la ciudad: tenderos, nativos, posaderos, residentes y personajes.
  - Los aldeanos ya colocados se cambian al cargarse.
  - Los que se mudan desde afuera conservan su ropa.
  - Los nativos dejaron de usar el `villager_type` de su tabla; su profesión sigue igual.
- **Distintivos (gancho).** Cada personaje tiene su profesión `entrelumen:mayor`, `inventor`, `gardener` y `priest`, sin puesto de trabajo, con textura en `textures/entity/villager/profession/<personaje>.png`.
  - **Pendiente de arte del controlador:** hoy son PNG transparentes de 64×64. Hay que dibujar el sello dorado y la banda de cobre de Aurelia, el guante mecánico de Terra, el delantal de jardinero de Juan y la estola turquesa de Bodhi, sobre la capa de profesión del aldeano vanilla.
  - Vanilla también dibuja la insignia de nivel (diamante, nivel 5) en el cinturón.

### Comandos de operador

- `/entrelumen admin solsticio story` muestra el estado del equipo del operador.
- `… story elections` vota ya, si el Entrelumen está liberado.
- `… story reset` borra la historia del acto VI de la campaña del operador.

### Pendiente

- Arte: los cuatro distintivos (arriba).
- Ver en un cliente:
  - la ropa y los personajes;
  - el gato en su techo;
  - los libros de lore;
  - las líneas en los dos idiomas;
  - la aguja de la brújula dentro de Solsticio.
- Jugar el acto de punta a punta en un mundo real: los tiempos de viaje, la economía de los platos y las baterías.
- Si una plantilla futura no trae los marcadores `easter:`, los easter eggs no se pueden descubrir y el encargo de las flores lo recibe Ciro. Las GameTests ponen huevos de prueba alrededor de la llegada sólo para los que falten.
