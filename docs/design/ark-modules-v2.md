# Módulos del Arca, segunda versión (25 de septiembre de 2026)

Decisión de Elias: los módulos **sólo funcionan dentro del Arca** y **sólo cuenta una Arca por equipo**. Poner más módulos o más Arcas no suma nada. El Arca armada es un **beacon 2.0 global e infinito**: cada módulo le da un efecto a todo el equipo, en cualquier lugar y dimensión, mientras el Arca esté armada.

Reemplaza a los servicios de la primera versión, descritos en `ark-services-boundary.md`: taller de reparación, biblioteca arcana, restauración, sala de cartas, depósito con kits y hospedaje. El módulo de Naturaleza dejó de restaurar; esa función la absorbe el altar de Renovación (ver `ark-altars.md`).

Los efectos se definen de a uno con Elias. Esta tabla se completa a medida que se decide:

| Módulo | Efecto global | Estado |
|---|---|---|
| Ingeniería | **Carga inalámbrica:** todos los ítems con energía (FE) del equipo se cargan solos, en cualquier dimensión: inventario, armadura y curios. | decidido |
| Arcano | **Maná desbordado:** más maná máximo y más regeneración en todos los sistemas de magia que lo exponen como atributo (Ars Nouveau, Iron's Spells y los que haya). | decidido |
| Naturaleza | **Vitalidad:** Regeneración I permanente y el hambre baja a la mitad de velocidad. | decidido |
| Exploración | **Viajero:** las waystones no cobran experiencia y se habilita `/rtp`. Velocidad y Salto se sacaron (Elias, 25/9). | decidido |
| Logística | **Comercio a distancia:** desde el Atlas se abre el catálogo de las tiendas de Solsticio que el equipo ya conoce y se compra desde cualquier lugar. Lo comprado llega al inventario, con los mismos precios y reposiciones que la tienda física. | decidido |
| Habitabilidad | **Hogar:** habilita `/sethome` y `/home`, con una sola casa por jugador. | decidido |

## Detalles de rutina (decisión del controlador; Elias puede ajustarlos)

- **Condición.** Un módulo funciona si está en su lugar, el Arca obligatoria está completa y **hay un controlador** (Elias: como el controlador es fácil de hacer, es obligatorio). El estado se guarda por equipo, así que no hace falta tener el Arca cargada. Si se rompe un módulo, se apaga su efecto.
- **Beacons en las columnas (Elias).**
  - Las cuatro columnas tienen en la punta un lugar opcional para un beacon.
  - Cada beacon colocado ahí suma **1 nivel** a los efectos de poción y a los buffs de los módulos: la Regeneración de Vitalidad, el Maná desbordado y el Héroe de la Aldea de Logística. Con 4 beacons son 4 niveles más.
  - Los efectos particulares no cambian: carga, comercio, casa, `/rtp` y waystones.
  - Esos beacons funcionan como beacons sin pirámide: se abren y se eligen sus buffs como siempre.
  - No hace falta ningún beacon para que los módulos funcionen.
- **Casa.** Una sola por jugador (Elias): `/sethome` reemplaza la anterior y `/home` va a ella. Funciona entre dimensiones. Enfriamiento de **15 minutos** y una espera de **5 segundos** sin moverse ni recibir daño; si te movés o te pegan, se cancela. Vale igual con trucos activados o no. **No hay `/back`**: si algún mod del pack lo trae, se desactiva. No se puede marcar ni usar una casa en Solsticio, donde se entra y se sale con la Llave.
- **Viaje aleatorio.** `/rtp` elige un punto seguro entre 1.000 y 5.000 bloques del spawn, en la dimensión actual. Funciona en el Overworld y en las dimensiones de exploración, no en Solsticio. Enfriamiento de **1 hora** (Elias).
- **Efectos.** Se aplican como modificadores de atributo o efectos ambientales sin partículas, así no ensucian la pantalla ni se pisan con los beacons.
- **Servicios de la primera versión.** Se quitan: taller de reparación, biblioteca arcana, sala de cartas, depósito con kits y hospedaje. Los depósitos que tenga un equipo se devuelven al inventario o se sueltan al lado del módulo; nada se pierde.

## El Arca a lo largo de la historia (decisión de Elias, 25 de septiembre)

La queja fue: «esas 6 cosas súper OP de late game sólo se desbloquean en late game; estaría bueno que se vayan desbloqueando en los 5 actos, así como los altares: progresar te hace cada vez más poderoso».

- **El Arca es un multibloque estético,** con un lugar definido para cada módulo y para el controlador. La forma la diseña el controlador de la sesión (arte) en `docs/design/ark-multiblock.md`. Se ve en el mundo con una guía fantasma que muestra dónde va cada bloque.
- **Sin lotes.** Se eliminan las entregas por módulo.
- **Un módulo conseguido ya está activo.** Colocarlo en su lugar, con el Arca bien armada y el controlador puesto, prende su efecto global (revisión: el controlador es obligatorio). Poner más módulos o más Arcas no suma nada: cuenta una por equipo.
- **El controlador** es la quilla del Arca. Hoy es una receta suelta de magnetita, Núcleo de señal y diamante; se hace desde el acto I porque el Núcleo sale del primer hito. Marca dónde se construye el Arca y hace falta para la activación final.
- **Qué acto da cada módulo** (el proyecto del Atlas que lo entrega pasa a ese acto, con materiales de ese acto):

| Acto | Módulo | Efecto |
|---|---|---|
| I | Habitabilidad | Casa: `/sethome` y `/home`, una por jugador |
| II | Exploración | Viajero: waystones gratis y `/rtp` |
| III | Naturaleza | Vitalidad: Regeneración I y la mitad de hambre |
| IV | Arcano | Maná desbordado |
| V | Logística | Comercio a distancia con Solsticio y descuento con aldeanos (Héroe de la Aldea permanente; se puede ajustar) |
| V | Ingeniería | Carga inalámbrica de energía |

- **Activación final (acto V → VI):** el Arca armada con sus 6 módulos y el controlador, más el último proyecto del acto V. Forja la Llave de Luz. Los requisitos se muestran como lista de chequeo en el Atlas y en la pantalla del Arca, con qué falta y dónde conseguirlo. Nada de prerrequisitos invisibles: si «Red del mundo» o la visita al End siguen siendo requisito, se ven ahí.
- **Balance aparte:** los jetpacks del pack consumen 2,5 veces más energía por tick (Elias). Se aplica a los de FE (Iron Jetpacks, Oritech y otros) y también al de hidrógeno de Mekanism.

## Implementación (companion, 25 y 26 de septiembre)

Lo que hace el código, con sus números. Los ajustables están en la configuración de servidor `entrelumen-ark-server.toml`.

**Estado del Arca por equipo.** `ArkData` (datos guardados `entrelumen_ark`) guarda por equipo: dimensión, controlador y orientación, si el núcleo está completo, cuántos bloques faltan, si está el controlador, qué módulos están en su lugar y cuántos beacons hay en las columnas.
- Se actualiza cuando un módulo o el controlador se coloca o se quita, por cualquier causa (el bloque lo avisa), cuando un jugador rompe o pone un bloque cerca, con las explosiones y cada 5 s si el Arca está cargada. Un Arca descargada conserva lo último que se leyó: los efectos no dependen de que esté cargada y nunca se carga un chunk para revisarla.
- **Una por equipo.** El controlador que coloca un miembro funda el Arca del equipo, en la orientación que mejor encaja con lo ya construido; un módulo suelto nunca funda una. Después sólo cuentan las piezas dentro de esa Arca; otra Arca del mismo equipo no suma nada y la de otro equipo no se puede tomar. Si se quitan el controlador y todos los módulos, el equipo puede empezar otra.
- **Validación.** `ark_multiblock.json`, con posiciones relativas al controlador, en cualquiera de las cuatro orientaciones. Las escaleras aceptan cualquier `facing` y `shape` con el `half` pedido. El cobre acepta cualquier oxidación y el encerado, y lo de Rechiseled acepta su variante `_connecting`. Los lugares de beacon son opcionales.
- **Amatista pulida.** `rechiseled:amethyst_block_polished` existe en Rechiseled 1.2.5 con sus variantes `_connecting`, `_slab` y `_stairs`. Sale del cincel de Rechiseled (lingote de hierro y palo, `data/rechiseled/recipe/chisel.json`) con bloques de amatista (`chiseling_recipes/amethyst_block.json`); no hay receta de cortapiedras. La guía «Construí el Arca» del Atlas lo dice.
- **Un módulo funciona** si está en su lugar, el núcleo está completo y el controlador está puesto (Elias). El nivel del Arca es 1 más un nivel por beacon en las columnas, hasta 5.

**Los efectos, cada 2 s** (40 ticks), para cada jugador conectado y en cualquier dimensión:

| Módulo | Qué hace |
|---|---|
| Ingeniería | Carga en su lugar cada ítem con energía FE (capability de ítem de NeoForge) del inventario, la armadura, la mano secundaria y los curios: 0,5 % de su capacidad por segundo, con un piso de 100 FE/s y un techo de 10.000 FE/s por ítem, sin límite de ítems y sin fuente. Una batería de 1 M FE se llena en unos 3,5 min; una de 100 M, al techo, en unas 2,8 h. El costo medido está en «Validación». |
| Arcano | +50 % por nivel al maná máximo y a su regeneración, como modificador de atributo `entrelumen:ark_overflowing_mana` sobre el total. IDs leídos en los JAR fijados: Ars Nouveau 5.13.1 `ars_nouveau:ars_nouveau.perk.max_mana` y `…mana_regen`, Iron's Spells 3.16.3 `irons_spellbooks:max_mana` y `mana_regen`, Psi 110 `psi:total_psi` y `psi:regen`. Malum sólo tiene cargas de hechizo, no maná, y queda afuera. |
| Naturaleza | Regeneración infinita, ambiental y sin partículas, del nivel del Arca (I sin beacons). El agotamiento se multiplica por 0,5 en `FoodData.addExhaustion`, el único embudo de toda el hambre: NeoForge 21.1.249 no tiene evento de agotamiento. La saciedad desbordada nunca pisa un efecto infinito, así que con Vitalidad su sorteo elige otros efectos. |
| Exploración | Marca al jugador como viajero con el atributo sincronizado `entrelumen:ark_traveller`. Waystones 21.1.41 no le cobra experiencia: la condición `entrelumen:ark_traveller` se registra con `WaystonesAPI.registerConditionPredicate` y `pack/config/waystones-common.toml` termina los requisitos con `[entrelumen:ark_traveller] multiply_xp_cost(0)`, así la lista del cliente no muestra costo. En el servidor, además, `WaystoneTeleportEvent.Pre` (Balm) pone en cero los requisitos de experiencia; el enfriamiento del botón del inventario sigue. `/rtp`, con las reglas de arriba. |
| Logística | Héroe de la Aldea infinito y sin partículas: I, más un nivel por beacon (`heroAmplifier`; −1 lo apaga). El comercio a distancia, abajo. |
| Habitabilidad | `/sethome` y `/home`, abajo. |

Los efectos de poción que pone el Arca se reconocen por ser infinitos y ambientales, y sólo esos se quitan. Una poción o un beacon ajeno queda como está.

**Casa.** `/sethome` guarda o reemplaza la única casa (dimensión, posición y mirada). `/home` espera 5 s: si el jugador se mueve más de 0,25 bloques o recibe daño, se cancela. Después revisa que el lugar siga libre (pies y cabeza sin colisión, fluido ni peligro) y viaja, también entre dimensiones. Enfriamiento de 15 min; no hay casa en Solsticio. Los dos comandos funcionan sin nivel de permiso.

**Sin `/back`.** El único del pack es `/moonlight back` de Moonlight 3.5.2, sólo para operadores y sin opción de configuración, así que el companion lo cancela antes de que se ejecute.

**Viaje aleatorio.** `/rtp` prueba hasta 8 puntos al azar, uniformes en el anillo de 1.000 a 5.000 bloques del spawn.
- Cada chunk se carga con un ticket y se lee en los ticks siguientes, así el servidor nunca espera la generación.
- En una dimensión con techo busca el hueco más alto bajo el techo.
- Pide piso sólido, dos bloques libres, sin fluido ni peligro, y dentro del borde del mundo.
- El enfriamiento de 1 h cuenta sólo si llega.

**Comercio a distancia.** Una tienda de Solsticio queda conocida por el equipo cuando un miembro habla con su tendero o pasa a 8 bloques de su puesto (4 de altura), en cualquier acto; conocerla no hace nada sin el módulo.
- En el Atlas, la entrada «Tiendas de Solsticio» abre el catálogo, y cada tienda abre el mostrador real a distancia. Mientras dura la pantalla, un ticket mantiene cargado el chunk del tendero.
- Corre la misma preparación que en el mostrador (reposición perezosa) y después el inicio de comercio de vanilla, con reputación, Héroe de la Aldea, precios de Solsticio y compuertas de acto.
- El stock es el del tendero. Se paga del inventario con la pantalla de comercio de vanilla, y lo que no entra cae a los pies al cerrar.
- Un tendero ocupado con otro jugador no abre. La experiencia del trueque queda en la tienda, donde la suelta el aldeano.

**Pantallas.** Un módulo o el controlador abren la pantalla del Arca: el efecto global del módulo y si funciona (con la causa, si no), el nivel y los beacons, los seis módulos con su efecto, y la lista de activación con qué falta y dónde se consigue. El Atlas tiene la entrada «El Arca», con la misma lista y el botón «Mostrar guía».

**Guía.** «Mostrar guía» proyecta el Arca con la vista de multibloques de Patchouli 1.21.1-93, por reflexión y sin depender de Patchouli para compilar; si falta, marca con partículas cada bloque que falta. Se ancla en el controlador del equipo o, si no hay, donde mira el jugador y de frente a él. El mismo botón la oculta.

**Beacons en las columnas.** `BeaconBlockEntityMixin` sube a 4 el nivel que vanilla calcula con la base (`updateBase`, cada 80 ticks) cuando el beacon está en un lugar de beacon de un Arca en pie. Su pantalla y su elección de efectos son las de vanilla. NeoForge 21.1 no tiene evento para el nivel de un beacon.

**Activación.** Agachado y con la mano vacía, en el controlador del Arca del equipo, con el Arca en pie y sus seis módulos, la Red del mundo y el viaje al End. Forja la Llave de Luz y abre el acto VI.

**Módulos por acto.** Cada proyecto pide materiales de su acto y a lo sumo un componente de ENTRELUMEN. El proyecto de cierre de su acto lo espera; los dos del acto V van antes de la activación.

| Acto | Proyecto | Pide | Requiere |
|---|---|---|---|
| I | Habitabilidad | cama blanca, fogata, 2 faroles y 4 panes | Mesa para viajeros |
| II | Exploración | 2 provisiones de viaje, brújula, 4 mapas vacíos y catalejo | Primera señal |
| III | Naturaleza | núcleo de propagación, 8 bloques de musgo y 4 rodajas de sandía reluciente | Archivo del taller |
| IV | Arcano | lente espectral, 16 fragmentos de amatista y 16 lapislázulis | Ruta de intercambio |
| V | Logística | matriz de distribución, 16 esmeraldas y 8 perlas de Ender | Las voces del Atlas |
| V | Ingeniería | bus del Arca, 8 bloques de redstone y 8 bloques de cobre | Las voces del Atlas |

**Lo que se retiró y cómo vuelve.**
- Lo entregado a un lote sin terminar se devuelve completo al primer miembro del equipo que se conecta: al inventario y, si no entra, a sus pies. Los lotes terminados no se devuelven.
- El stock del depósito de Logística cae arriba de su módulo la primera vez que el módulo corre en el servidor, como ítems que no desaparecen.
- Un hospedaje que seguía siendo el punto de reaparición devuelve el anterior al conectarse.
- El taller, la biblioteca arcana, la sala de cartas y los kits ya no existen.
