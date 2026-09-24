# Acto VI · Solsticio (diseño, 24 de septiembre de 2026)

Diseño del controlador sobre la [biblia](story-bible.md). **Corto y denso:** 11 misiones de historia, con viajes al Overworld. Lo largo es el posgame: comercio, lote e ítems creativos. Todavía no está implementado; lo implementa un worker cuando entre la renumeración de actos (acto V «El Arca», acto VI Solsticio).

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
