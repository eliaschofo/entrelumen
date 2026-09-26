# Módulos del Arca, segunda versión (25 de septiembre de 2026)

Decisión de Elias: los módulos **sólo funcionan dentro del Arca** y **sólo cuenta una Arca por equipo**. Poner más módulos o más Arcas no suma nada. El Arca armada es un **beacon 2.0 global e infinito**: cada módulo le da un efecto a todo el equipo, en cualquier lugar y dimensión, mientras el Arca esté armada.

Reemplaza a los servicios de la primera versión, descritos en `ark-services-boundary.md`: taller de reparación, biblioteca arcana, restauración, sala de cartas, depósito con kits y hospedaje. El módulo de Naturaleza dejó de restaurar; esa función la absorbe el altar de Renovación (ver `ark-altars.md`).

Los efectos se definen de a uno con Elias. Esta tabla se completa a medida que se decide:

| Módulo | Efecto global | Estado |
|---|---|---|
| Ingeniería | **Carga inalámbrica:** todos los ítems con energía (FE) del equipo se cargan solos, en cualquier dimensión: inventario, armadura y curios. | decidido |
| Arcano | **Maná desbordado:** más maná máximo y más regeneración en todos los sistemas de magia que lo exponen como atributo (Ars Nouveau, Iron's Spells y los que haya). | decidido |
| Naturaleza | **Vitalidad:** Regeneración I permanente y el hambre baja a la mitad de velocidad. | decidido |
| Exploración | **Viajero:** Velocidad I y Salto I permanentes, las waystones no cobran experiencia y se habilita `/rtp`. | decidido |
| Logística | **Comercio a distancia:** desde el Atlas se abre el catálogo de las tiendas de Solsticio que el equipo ya conoce y se compra desde cualquier lugar. Lo comprado llega al inventario, con los mismos precios y reposiciones que la tienda física. | decidido |
| Habitabilidad | **Hogar:** habilita `/sethome` y `/home`, con una sola casa por jugador. | decidido |

## Detalles de rutina (decisión del controlador; Elias puede ajustarlos)

- **Condición.** Todo funciona mientras el Arca del equipo esté completa: el controlador y los 6 módulos en su volumen. El estado se guarda por equipo, así que no hace falta tener el Arca cargada. Si se rompe un módulo, se apaga su efecto.
- **Casa.** Una sola por jugador (Elias): `/sethome` reemplaza la anterior y `/home` va a ella. Funciona entre dimensiones. Enfriamiento de 30 s. No se puede marcar ni usar una casa en Solsticio, donde se entra y se sale con la Llave.
- **Viaje aleatorio.** `/rtp` elige un punto seguro entre 1.000 y 5.000 bloques del spawn, en la dimensión actual. Funciona en el Overworld y en las dimensiones de exploración, no en Solsticio. Enfriamiento de 5 min.
- **Efectos.** Se aplican como modificadores de atributo o efectos ambientales sin partículas, así no ensucian la pantalla ni se pisan con los beacons.
- **Servicios de la primera versión.** Se quitan: taller de reparación, biblioteca arcana, sala de cartas, depósito con kits y hospedaje. Los depósitos que tenga un equipo se devuelven al inventario o se sueltan al lado del módulo; nada se pierde.

## El Arca a lo largo de la historia (decisión de Elias, 25 de septiembre)

La queja fue: «esas 6 cosas súper OP de late game sólo se desbloquean en late game; estaría bueno que se vayan desbloqueando en los 5 actos, así como los altares: progresar te hace cada vez más poderoso».

- **El Arca es un multibloque estético,** con un lugar definido para cada módulo y para el controlador. La forma la diseña el controlador de la sesión (arte) en `docs/design/ark-multiblock.md`. Se ve en el mundo con una guía fantasma que muestra dónde va cada bloque.
- **Sin lotes.** Se eliminan las entregas por módulo.
- **Un módulo conseguido ya está activo.** Colocarlo en su lugar, con el Arca bien armada, prende su efecto global, haya o no controlador. Poner más módulos o más Arcas no suma nada: cuenta una por equipo.
- **El controlador** es la quilla del Arca. Hoy es una receta suelta de magnetita, Núcleo de señal y diamante; se hace desde el acto I porque el Núcleo sale del primer hito. Marca dónde se construye el Arca y hace falta para la activación final.
- **Qué acto da cada módulo** (el proyecto del Atlas que lo entrega pasa a ese acto, con materiales de ese acto):

| Acto | Módulo | Efecto |
|---|---|---|
| I | Habitabilidad | Casa: `/sethome` y `/home`, una por jugador |
| II | Exploración | Viajero: Velocidad I, Salto I, waystones gratis, `/rtp` |
| III | Naturaleza | Vitalidad: Regeneración I y la mitad de hambre |
| IV | Arcano | Maná desbordado |
| V | Logística | Comercio a distancia con Solsticio y descuento con aldeanos (Héroe de la Aldea permanente; se puede ajustar) |
| V | Ingeniería | Carga inalámbrica de energía |

- **Activación final (acto V → VI):** el Arca armada con sus 6 módulos y el controlador, más el último proyecto del acto V. Forja la Llave de Luz. Los requisitos se muestran como lista de chequeo en el Atlas y en la pantalla del Arca, con qué falta y dónde conseguirlo. Nada de prerrequisitos invisibles: si «Red del mundo» o la visita al End siguen siendo requisito, se ven ahí.
- **Balance aparte:** los jetpacks del pack consumen 2,5 veces más energía por tick (Elias). Se aplica a los de FE (Iron Jetpacks, Oritech y otros) y también al de hidrógeno de Mekanism.
