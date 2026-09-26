# Ruinas de Heliodor

Diseño del controlador, 24 de septiembre de 2026; sistema del Plan v2 en el juego desde el 26 de septiembre. Sigue la [biblia de la historia](story-bible.md): pocas ruinas, memorables, indestructibles, y todo simétrico. El arte es una **primera pasada**: Elias la revisa ruina por ruina y va a cambiar; el sistema sólo depende de los marcadores.

## Herramientas

- `art/structures/voxkit.py`: diccionario de bloques con colocación simétrica D4 (`sym`), comprobación `is_symmetric()`, vista isométrica con el color promedio de las texturas vanilla y lectura de NBT para renderizar referencias.
- Cada ruina del Plan v2 es un `build()` que devuelve `(Voxels, marcadores)` en coordenadas centradas; `y` puede ser negativa (fosos, bóvedas). `ruins_medium.RUINS` junta las cinco medianas.
- `tools/build_heliodor_ruins.py` exporta las diez a `companion/src/main/resources/data/entrelumen/structure/ruins/<id>.nbt` con un solo comando; `--check` compara con lo commiteado y `--report` lista tamaños y marcadores. Rechaza IDs de bloque que el 1.21.1 no conoce.
- Las vistas previas van a `art/structures/preview/` o a `$RUIN_OUT`.

## Estado

| Ruina | Acto | Dónde | Arte | Plantilla | Estado |
|---|---|---|---|---|---|
| Ruina inicial (patio del sol, pedestal de la brújula) | inicio | Overworld, spawn | `ruin_start.py` | `heliodor_ruin_start.nbt`, 15×9×15 | En el juego |
| Torre de la Señal | I | Overworld | `ruin_signal_tower.py` | `ruins/signal_tower.nbt`, 51×83×51 | En el juego, arte de primera pasada |
| Taller hundido | II | Overworld | `ruin_sunken_workshop.py` | `ruins/sunken_workshop.nbt`, 77×49×77 (17 bajo el suelo) | En el juego, arte de primera pasada |
| Viaducto | III | Overworld | `ruin_viaduct.py` | `ruins/viaduct.nbt`, 97×53×97 | En el juego, arte de primera pasada |
| Invernadero-domo | III | Overworld | `ruins_medium.py` | `ruins/dome_greenhouse.nbt`, 45×29×45 | En el juego, arte de primera pasada |
| Fundición bajo la lava | III | Nether | `ruins_medium.py` | `ruins/nether_foundry.nbt`, 47×21×47 | En el juego, arte de primera pasada |
| Observatorio del Risco | IV | Overworld | `ruin_cliff_observatory.py` | `ruins/cliff_observatory.nbt`, 57×55×57 | En el juego, arte de primera pasada |
| Santuario | IV | Twilight Forest | `ruins_medium.py` | `ruins/twilight_sanctuary.nbt`, 47×24×47 | En el juego con el mod, arte de primera pasada |
| Antesala del Sol | IV | Aether | `ruins_medium.py` | `ruins/sun_antechamber.nbt`, 49×19×49 | En el juego con el mod, arte de primera pasada |
| Templo de la Luz Sagrada | V | Overworld | `ruin_temple.py` | `ruins/light_temple.nbt`, 85×39×85 | En el juego, arte de primera pasada |
| Observatorio sobre el vacío | V | End | `ruins_medium.py` | `ruins/void_observatory.nbt`, 39×23×39 | En el juego, arte de primera pasada |
| Solsticio | VI | su dimensión | `art/solsticio/city6.py` | `solsticio/city.nbt` | En el juego (ciudad orgánica v6) |

Los bocetos anteriores (`ruin_atlas.py`, `ruins_acts.py`, `ruins_dims.py`) quedan como referencia.

## Decisiones de forma

- **Simetría.** Cada ruina es simétrica bajo rotaciones de 90° y espejos. El deterioro también: grietas, musgo, piezas caídas y huecos salen de funciones de `(a, b) = orden(|x|, |z|)`. La única excepción es la orientación de un atril.
- **Paleta común.** Toba, calcita, cuarzo y cobre oxidado encerado; es la misma familia de materiales que Solsticio, con el cobre ya verde.
- **Terreno.** Los bordes redondos dejan fuera las celdas de piso de las esquinas, así el terreno sigue ahí. Si el piso queda por encima del suelo, la colocación rellena la celda con el suelo que encuentra abajo.

## Referencias inspeccionadas

Renderizadas desde el JAR del servidor 1.21.1 con `voxkit.load_nbt`:

- `data/minecraft/structure/trial_chambers/chamber/pedestal/quadrant_2.nbt`: toba y cobre oxidado como una sola paleta.
- `data/minecraft/structure/trail_ruins/tower/tower_1.nbt`: escala de una ruina chica y enterrada.
- `data/minecraft/structure/ancient_city/city_center/city_center_1.nbt`: un marco simétrico alrededor de una pieza central.

## Artefacto del Taller hundido: el Brazo de Terra

Pedido de Elias del 24 de septiembre: un brazo ciborg hecho por Terra, curio, que da +3 de alcance de bloques y nada más. Al principio daba +5; Elias lo bajó a +3 el mismo día y pidió que el tooltip mostrara el efecto.

- **Ítem.** `entrelumen:terra_arm` («Terra's Arm» / «Brazo de Terra»): stack de 1, rareza épica, resistente al fuego, sin durabilidad y sin receta. El tooltip tiene una línea de lore, «Terra se lo hizo para llegar adonde la máquina no la dejaba», y una de efecto en azul, como las de atributos de vanilla: «+3 de alcance de bloques» / «+3 block reach» (`entrelumen.terra_arm.reach`, con el valor de `TerraArm.REACH_BONUS`).
- **Slot.** `hands` de Curios 9.5.1, el de la versión fijada en el catálogo. Una prótesis de brazo va en la mano y no en `bracelet`, y en el pack ya se lo asignan al jugador Cataclysm, Artifacts, Occultism e Industrial Foregoing, con dos espacios. El tag `curios:hands` hace que Curios lo acepte. `data/entrelumen/curios/entities/terra_arm.json` le asigna `hands` al jugador aunque esos mods salgan del pack. Curios ya trae el nombre del slot en inglés y en español.
- **Efecto.** Mientras está puesto, suma un modificador aditivo y transitorio de +3, con ID `entrelumen:terra_arm_reach`, a `minecraft:player.block_interaction_range`. El alcance de entidades no cambia. Dos brazos dan el mismo +3.
- **Integración con Curios.** El acompañante no compila contra Curios. `CuriosCompat` resuelve por reflexión, una sola vez, `CuriosApi.getCuriosInventory` e `ICuriosItemHandler.isEquipped`, igual que hace `SilentGearCompat`. Cada 10 ticks el servidor le pregunta a Curios si el jugador tiene el brazo en un slot activo y pone o saca el modificador para que coincida. No se usa el componente `curios:attribute_modifiers`: en Curios 9.5.1 ese camino le cambia el ID al modificador por el del slot (`curios:hands0`), y con dos slots de manos, sacarse uno de dos brazos borraría el bono del otro. Sin Curios, el ítem existe, se puede llevar o guardar y no hace nada.
- **Cómo se consigue.** Sale garantizado de la loot table `entrelumen:chests/ruin_act2_workshop`: desde el 26 de septiembre es el barril del arma (`arm_chest` del arte, el primero de los cuatro) en la bóveda del Taller hundido, un barril de Lootr, así que cada jugador saca el suyo. Sigue siendo, además, la recompensa del proyecto de campaña `lost_workshop`, que cierra el archivo del acto II; la quest `crafts_archive` refleja ese hito. El generador de quests no se tocó: no admite recompensas y el capítulo del acto II está congelado por hash.
- **Pruebas.** `TerraArmDataTest` (JUnit) valida los datos. `RuntimeGameTestsTerraArm` corre en el servidor de GameTest sin Curios y prueba el ítem, el cofre del taller, que llevarlo o ponerlo en slots vanilla no hace nada, y la lógica del +3 llamada directo, además de la línea de efecto del tooltip. El test del acto II espera un brazo al cerrar `lost_workshop`. `TerraArmFullpackGameTests` pone uno o dos brazos en `hands` y comprueba el +3 en la QA de pack completo. Con +5, una copia temporal que no se commiteó corrió el 24/9 en el servidor de GameTest con sólo Curios 9.5.1 agregado y dos slots de manos, y pasó: el alcance quedó en 9,5 con uno o dos brazos, siguió en 9,5 al sacar uno y volvió a 4,5 sin ninguno; el de entidades quedó en 3,0. Con +3 el alcance esperado es 7,5.

## Plan v2 (26 de septiembre de 2026)

Decisiones de Elias del 25/9:
- cada ruina guarda una **pieza clave obligatoria** de su acto;
- los desafíos son **mixtos** (uno o varios por ruina);
- la escala también es mixta: cada acto tiene **exactamente una ruina gigante** (landmark, 80+ bloques y visible de lejos) y puede sumar ruinas medianas (40–60 bloques, con interior);
- todas son **indestructibles**.

### Plantel

| # | Ruina | Dónde | Acto | Escala | Pieza clave (ES / EN) | La pide | Desafío |
|---|---|---|---|---|---|---|---|
| 0 | Ruina inicial | Overworld, spawn | I | chica | — (el Atlas) | — | — |
| 1 | Torre de la Señal | Overworld | I | **gigante** | Brasa de la Señal / Signal Ember, y el **Atlas** en la linterna (26/9) | `signal` | Relevo de luz (26/9): vitrales que tiñen, colores que se suman, un espejo y un colector hasta el blanco; más la trepada del piso roto |
| 2 | Taller hundido | Overworld | II | **gigante** | Plano de Terra / Terra's Blueprint (+ Brazo de Terra) | `crafts_archive` | Mecanismo (reactivar el motor, vaciar el foso y abrir la bóveda) + combate liviano |
| 3 | Viaducto | Overworld | III | **gigante** | Sello de Ruta / Route Seal | `exchange_archive` | Combate (Guardián del Peaje, jefe con barra) + exploración por los arcos |
| 4 | Invernadero-domo | Overworld | III | mediana | Semilla Madre / Mother Seed | `exchange_nursery` | Ofrenda (plantar cuatro retoños en los canteros para abrir la cripta) |
| 5 | Fundición bajo la lava | Nether | III | mediana | Crisol de Heliodor / Heliodor Crucible | `exchange_power` | Combate (oleada) + cerradura de redstone |
| 6 | Observatorio del Risco | Overworld | IV | mediana (26/9) | Ocular de las Voces / Eyepiece of Voices | `voices_lens` | Luz (orientar espejos hasta el telescopio) + exploración |
| 7 | Santuario | Twilight Forest | IV | mediana | Testimonio del Bosque / Forest Testimony | `voices_spirits` | Exploración oculta (orden de menhires, bodega entre raíces) |
| 8 | Antesala del Sol | Aether | IV | mediana | Llave del Sol / Sun Key | `voices_sun_spirit` | Ofrenda + parkour entre nubes |
| 9 | Templo de la Luz Sagrada | Overworld | IV (26/9) | **gigante** | Llama Sagrada / Sacred Flame | `voices_archive`: el Templo revela la fusión | Luz + ofrenda + combate (Custodio de la Luz) |
| 10 | Observatorio sobre el vacío | End | V | **gigante** (26/9) | Carta Estelar / Star Chart | `world_network` | Exploración y parkour sobre el vacío + combate |
| 11 | Solsticio | su dimensión | VI | **gigante** | — | — | ciudad (sistema propio) |

Los bocetos de `ruins_acts.py`, `ruins_dims.py` y `ruin_atlas.py` quedan como punto de partida: el arte final se redibuja a escala. El Patio del Atlas sale del plantel porque el acto I ya tiene su gigante y es la primera hora.

**Cambio del 26/9 (Elias):**
- el Templo pasa al acto IV y es ahí donde se revela la fusión, como dice la biblia;
- para que siga habiendo una sola ruina gigante por acto, el Observatorio del Risco baja a mediana en el IV y el Observatorio sobre el vacío del End sube a gigante del V;
- el arte de las dos se reescala.

### Reglas del sistema

- **Colocación en el Overworld.** El companion coloca cada ruina una vez por mundo, cuando el primer equipo abre el acto. Busca sitio en un anillo alrededor del spawn: 250–500 bloques en el acto I y 400–1200 después. Pide terreno apto (pendiente, sin agua ni árboles grandes) y evita Solsticio y las otras ruinas. La colocación espera a que los chunks estén cargados. La ruina queda en `RuinData`, protegida por `StructureProtection`, y es ancla de la brújula.
- **Colocación en dimensiones.** La primera llegada de cualquier jugador elige un sitio a 150–500 bloques del punto de llegada.
- **Pieza clave.** Un pedestal la entrega una vez por equipo, después de que ese equipo resuelve el desafío. Si se pierde antes de entregarla al Atlas, el pedestal la devuelve. No tiene receta.
- **Desafíos por equipo.** Cada equipo resuelve el suyo. La bóveda se abre para los miembros del equipo que la resolvió (bloque con puerta por equipo).
- **Loot.** Cofres de Lootr, por jugador, con tablas por acto. Lore del Atlas que se desbloquea una vez al entrar.
- **Indestructible.** Todo el volumen registrado. Sólo se usan palancas, botones, braseros, espejos y los mecanismos del desafío.
- **Jefes.** Mobs con barra de jefe, nombre y atributos propios. Reaparecen para cada equipo que todavía no resolvió el desafío.
- **Sin mod.** Una ruina de dimensión cuyo mod falta se saltea y su pieza pasa al pedestal de la gigante del mismo acto.

## La Torre de la Señal: el relevo de luz (Elias, 26/9)

**Look:** más solarpunk.
- La base es de toba gastada, con enredaderas.
- El cuerpo medio es de calcita, con nervios de cobre y vitrales altos, de ámbar a celeste, en las ocho caras.
- El cuerpo alto es una linterna de vidrio.
- Arriba, la cúpula de cobre con pátinas mezcladas, macetas en la galería y lianas colgando.

**El relevo:**
- Cada piso tiene un brasero, vitrales giratorios, algún espejo y un receptor en el techo.
- Los vitrales tiñen la luz y los colores se suman.
- Cuando el receptor recibe exactamente su color, se enciende el brasero del piso de arriba.

| Piso | Pide | Idea | Solución |
|---|---|---|---|
| 1 | rojo | aprender a girar un vitral | rojo: subir |
| 2 | rojo + verde | atravesar dos vitrales en fila | rojo: pasar; verde: subir |
| 3, el roto | verde + azul | rebotar en un espejo | verde: pasar; espejo: este; azul: subir |
| 4 | rojo + verde + azul | un vitral ámbar de trampa; un colector central junta las luces y las manda a la lente | rojo: pasar; verde: sur; azul: este; espejo: norte; colector: arriba |

- El blanco enciende la lente, y el Atlas y la Brasa de la Señal quedan en el pedestal, al lado.
- En cada piso hay vitrales de más, que despistan. Las posiciones están en `markers['relay']`, dentro de `art/structures/ruin_signal_tower.py`.
- Es la única asimetría de la ruina, igual que el atril.
- **Referencia:** el haz del faro (beacon) vanilla toma el color de los vidrios teñidos que atraviesa y mezcla varios (`textures/entity/beacon_beam.png`). Acá la luz corre plana, piso por piso.

## El Motor de Terra: el puzzle de Create del Taller hundido (Elias, 26/9)

«El taller de Terra tiene que tener un puzzle con Create, a pleno Create, terrible complejidad, pero debe ser así.»

Terra dejó el taller ahogado a propósito: el foso sólo se vacía si alguien entiende su motor. El puzzle no tiene una solución única. Son restricciones reales de Create 6.0.10 que se cumplen construyendo.

**Referencias inspeccionadas (hook de diseño):** las escenas ponder del JAR fijado `create-1.21.1-6.0.10.jar`, leídas con `voxkit.load_nbt`:

| Escena | Qué se tomó |
|---|---|
| `assets/create/ponder/large_water_wheel.nbt` | La rueda grande con su estructura de relleno, el eje y el velocímetro |
| `mechanical_pump/speed.nbt` | Bomba con engranajes y tanque |
| `cog/speedup.nbt` | Multiplicar vueltas con engranaje grande contra chico |
| `sequenced_gearshift.nbt` | Rodamiento con chasis y caja secuencial |
| `gearbox.nbt` | Caja de engranajes |

### Las etapas

1. **Las cuatro ruedas.** Cada casa tiene una rueda hidráulica grande de Create en su canal.
   - Una compuerta de cobre retiene el agua en un estanque aguas arriba. La palanca la levanta, el agua corre, pasa por la rueda y cae al foso por un corte en el muro.
   - A cada casa le falta una pieza de su transmisión: hay que encontrarla y reponerla.
   - Por la simetría del conjunto, las ruedas de casas enfrentadas giran en sentidos opuestos.
   - Cada rueda tiene un velocímetro fijo y una nota de Terra.
2. **Las líneas.** Un eje de Create por arriba de cada puente lleva la rotación de la casa a la sala de máquinas.
3. **La sala de máquinas (el banco de Terra).**
   - Es un salón redondo adentro de la caldera, sobre el agua, y el único lugar de la ruina donde se puede construir.
   - Adentro de un disco de 9 de diámetro se ponen y se sacan piezas de transmisión de Create: ejes, engranajes, cajas, embrague, cambio de sentido, cadenas, correas, medidores, caja secuencial y redstone.
   - No se permiten fuentes de energía ni controlador de velocidad.
   - Entran cuatro ejes, uno por lado. Salen cinco puertos en el piso: cuatro bombas en las diagonales y el sello en el centro.
4. **Las bombas del foso.** Cuatro bombas mecánicas fijas, dentro de la caldera, con caños al agua.
   - El foso se vacía, capa por capa, mientras las cuatro giran al mismo tiempo a R RPM o más y en el sentido de sacar agua.
   - Si una afloja o se invierte, se pausa.
   - R y el estrés se eligen con los valores reales de la config de Create del pack:
     - ninguna rueda sola llega;
     - hay que unir las cuatro, y para eso corregir los sentidos espejados;
     - hay que multiplicar la velocidad con engranajes;
     - con el total a R entra justo en la capacidad; a 2R se sobrecarga.
   - Dos bombas miran al revés que las otras dos, así que el reparto también pide invertir.
5. **El sello de la bóveda.**
   - Ya seco el foso, un rodamiento mecánico mueve un anillo de cobre que tapa las cuatro bajadas.
   - Se abre sólo si el anillo queda quieto, girado exactamente un octavo de vuelta (45°, más múltiplos de 90). Hace falta la caja de cambios secuencial programada, o una sincronización fina.
   - Abre para el equipo de quien lo giró.

### Pistas (justas, en la ruina)

Las notas de Terra van en atriles. Los medidores dan los números.

| Nota | ES | EN |
|---|---|---|
| Casa 1 | Las ruedas no discuten: giran para donde las empuja el agua. Las de enfrente, al revés que ésta. | Wheels don't argue: they turn the way the water pushes. The ones across turn the other way. |
| Casa 2 | Una bomba mía no traga con menos de R vueltas. Cuatro juntas pesan lo que pesan: mirá el estresómetro antes de apurarlas. | My pumps won't drink below R RPM. Four of them weigh what they weigh; check the stressometer before you rush them. |
| Casa 3 | Engranaje grande contra chico: el doble de vueltas, el doble de peso. No hay magia, hay cuentas. | Big cog against small: twice the turns, twice the load. No magic, just sums. |
| Casa 4 | Si juntás dos ejes que no giran igual, se rompen los dos. Una caja de engranajes da vuelta cualquier discusión. | Join two shafts that don't agree and both break. A gearbox turns any argument around. |
| Motor | El sello gira un octavo y se queda quieto. Ni un grado más. Si no sabés medir un octavo, todavía no te toca el plano. | The seal turns an eighth and stays put. Not a degree more. If you can't measure an eighth, the blueprint isn't yours yet. |

### Reglas

- **Estado por equipo.** Cuando un equipo reclama el Plano, el taller se rearma para el siguiente diez minutos después de quedar vacío:
  - el foso se vuelve a inundar;
  - el anillo vuelve a su lugar;
  - las piezas puestas en el banco vuelven a quien las puso o a un barril en la puerta.
- **Piezas.** Las pone el jugador: son las del acto II (aleación de andesita, ejes, engranajes). Un barril por casa trae un poco de material oxidado para no moler de más.
- **Sin Create:** vuelve el acertijo de las cuatro palancas.

## Sistema (26 de septiembre de 2026)

Rama `feature/ruins-v2`. Código en `companion/src/main/java/dev/entrelumen/Ruin*.java` y `KeyPieces.java`; reglas puras en `RuinRules`, `RuinMarkers` y `RuinDefinitions`.

### Definiciones

Una ruina es un JSON en `data/entrelumen/heliodor_ruin/<id>.json` (datapack, recargable): acto, dimensión, mods que necesita, escala, plantilla, colocación (`surface`, `cavern` o `sky`; anillo `min`–`max`), pieza, proyecto que la pide, loot table por defecto, desafíos, compuertas y lo que pide el pedestal. Un desafío puede exigir otros antes (`requires`). Un archivo inválido se descarta con un error en el log y el resto carga igual.

| Ruina | Colocación | Desafíos | Compuerta | Pieza | Proyecto |
|---|---|---|---|---|---|
| `signal_tower` | superficie, 250–500 del spawn | `braziers`: cuatro pisos de braseros, de abajo hacia arriba; la linterna se enciende | — | Brasa de la Señal | `first_signal` |
| `sunken_workshop` | superficie, 400–1200 | `drowned`: tres ahogados; `sluices`: las ocho palancas de las compuertas, que vacían el foso | `vault` (`sluices`) | Plano de Terra | `lost_workshop` |
| `viaduct` | superficie, 400–1200 | `toll_guardian`: Guardián del Peaje (vindicador, 120 de vida, escala 1,4) | — | Sello de Ruta | `exchange_route` |
| `dome_greenhouse` | superficie, 400–1200 | `saplings`: cuatro retoños (`#minecraft:saplings`) en cualquiera de los ocho canteros | `crypt` (`saplings`) | Semilla Madre | `nursery_protocol` |
| `nether_foundry` | caverna del Nether, 150–500 de la llegada | `guards`: cuatro esqueletos wither; `furnaces`: las ocho palancas, después de la guardia | `vault` (`furnaces`) | Crisol de Heliodor | `distributed_power` |
| `cliff_observatory` | superficie, 400–1200 | `mirrors`: cuatro espejos hacia el telescopio | — | Ocular de las Voces | `spectral_archive` |
| `twilight_sanctuary` | superficie del Twilight Forest, 150–500 | `stones`: las ocho piedras en orden de brújula (desde el norte, en sentido horario) | `cellar` (`stones`): piso falso de musgo | Testimonio del Bosque | `spectral_archive` |
| `sun_antechamber` | cielo del Aether, 150–500 | `offerings`: un lingote de oro en cada islote | — | Llave del Sol | `heliodor_heart` |
| `light_temple` | superficie, 400–1200 | `offerings`: piedra luminosa al final de cada escalera; `lamps`: las cuatro lámparas de los obeliscos; `keeper`: el Custodio de la Luz (evocador, 200 de vida), que se levanta después de las otras dos | — | Llama Sagrada | `world_network` |
| `void_observatory` | cielo del End, 150–500 | `watcher`: Vigía del vacío (enderman, 120 de vida) | — | Carta Estelar | `settlement_supply` |

Los ítems de las ofrendas, los jefes y sus atributos son datos: se cambian en el JSON.

### Colocación

- **Cuándo.** Las del Overworld, una vez por mundo, cuando algún equipo abre su acto (se revisa cada 5 s). Las de dimensión, cuando alguien llega por primera vez, alrededor de ese punto. Un sitio elegido queda reservado en `RuinData`: si el servidor se corta, la colocación sigue ahí al arrancar. Una sola ruina a la vez.
- **Sitio, sin tocar chunks.** 48 candidatos en espiral áurea dentro del anillo, deterministas por semilla y ruina. Fuera del hilo del servidor se puntúan con el ruido del generador: pendiente y agua (`WORLD_SURFACE_WG` contra `OCEAN_FLOOR_WG`), bioma (sin océanos, ríos ni playas); en el Nether, un piso de caverna (o lago de lava) con altura libre; en el cielo, cuántos bloques ocupa el volumen. Se descartan los que tocan otra ruina, una reserva o una región protegida (48 bloques de margen) y los que no entran en el mundo.
- **Nunca sobre algo de un jugador.** Antes de cargar nada, se lee el `InhabitedTime` de los chunks guardados: si jugadores pasaron más de 5 minutos en alguno, el candidato se saltea.
- **Terreno real.** Los chunks del sitio se cargan en segundo plano con un ticket propio. Con el terreno cargado se vuelve a medir: pendiente, agua, troncos y estructuras (aldeas y demás) que crucen el volumen; hasta 6 intentos, y si ninguno pasa, el mejor que entre en el mundo.
- **Por partes.** La plantilla se lee y se corta en cubos de 16 fuera del hilo (el mismo corte que Solsticio). Cada tick, hasta 8 ms: se vacían las columnas de la ruina sobre la capa del suelo, se colocan los cubos, se rellena el cimiento bajo las columnas que apoyan en el suelo (hasta 12) y los marcadores se vuelven bloques. Después se registra en `RuinData`, queda protegida y es ancla de la brújula.
- **Indestructible.** Todo el volumen registrado, con la protección de siempre (romper, poner, explosiones, fluidos, pistones, fuego, mobs). Palancas y botones siguen usables; los contenedores se abren desde el acto de la ruina.

### Marcadores

Bloques de estructura en modo DATA; el texto es `<tipo> clave=valor ...` (el prefijo `entrelumen:` es opcional). Cualquier marcador acepta `block=<estado>`: el bloque que queda en su celda (aire si falta). Así un brasero puede ser una fogata o una lámpara de cobre, una palanca sigue siendo palanca y un atril sigue ahí. `RuinMarkers.parse` dice qué está mal en uno inválido.

| Tipo | Claves | Qué hace la colocación |
|---|---|---|
| `pedestal` | — | Pone el pedestal de la pieza. |
| `chest` | `loot`, `block` | Un cofre o barril (según `block`) de Lootr con esa tabla (o la de la ruina); sin Lootr, el vanilla. |
| `brazier` | `challenge`, `order` (1), `block` | Nodo que se enciende o se toca en orden. Los del mismo `order` van en cualquier orden entre sí; uno fuera de turno apaga todos. |
| `lamp` | `challenge`, `block` | Se enciende (`lit`) cuando el equipo resuelve el desafío. |
| `mirror` | `challenge`, `facing` (`n`, `ne`, `e`, `se`, `s`, `sw`, `w`, `nw`) | Espejo; cada clic lo gira un octavo. |
| `receptor` | `challenge`, `block` | Adónde deben apuntar los espejos. |
| `lever` | `challenge`, `on` (`true`), `block` | Palanca de una cerradura: se resuelve cuando todas quedan como dice `on`. |
| `lock` | `challenge` | Núcleo de cerradura de redstone: con señal, lo resuelve el equipo que usó la última palanca o botón de la ruina (o el jugador más cercano adentro). |
| `socket` | `challenge`, `item` (ID o `#tag`), `count`, `look` (`pot`, `altar`) | Receptáculo de ofrendas; consume el ítem. |
| `hidden` | `challenge`, `look` | Piedra floja con la apariencia de `look`; tocarla lo resuelve. |
| `gate` | `id`, `look` (`seal` y los de piedra), `climb` | Celda de compuerta por equipo. |
| `boss` | `challenge`, `block` | Donde se levantan los mobs del jefe. |
| `drain` | `challenge`, `size=x,y,z` | Caja de agua que se vacía al resolverlo (desde esta celda). |
| `lore` | `radius` (6), `height` (5), `block` | Volumen que registra la visita; sin `lore`, toda la ruina. |
| `arrival` | `block` | Punto de llegada (`spawn` también vale, para la ruina inicial). |
| `ground` | `block` | La capa de la plantilla que queda al ras del terreno; sin él, la capa 0. |

El exportador traduce los marcadores del arte: `braziers` y `lantern` (Torre), `levers`, `drain_volume`, `vault_doors`, `arm_chest` y `drowned` (Taller), `mirrors` y `beam_receptor` (Risco), `offering_sockets`, `order_stones` (orden de brújula), `boss`, `lore`, `arrival` y `pedestal`. Todo barril del arte es un cofre de Lootr y toda palanca entra en la cerradura de su ruina. Las compuertas salen de `vault_doors` o, en las medianas con bóveda, de los pozos de escalera que bajan desde la capa del suelo (`GATE_STYLE` dice cuál y cómo se ve).

### Desafíos, por equipo

- El progreso vive en `RuinProgress` (`entrelumen_ruin_progress`), por campaña; un grupo nuevo hereda el del fundador, como la campaña y la brújula.
- El mundo muestra el estado del último equipo que tocó el desafío: braseros encendidos, receptáculos llenos, lámparas. Otro equipo ve el suyo en cuanto toca.
- Los espejos y las palancas vuelven a su posición inicial poco después de resolverse (10 y 5 s), para el equipo siguiente. El foso vaciado queda vacío: es del mundo.
- Un desafío con `requires` pendiente responde «Antes tiene que responder otra cosa de este lugar».

### Compuertas

Sólidas para todos; los miembros de un equipo que resolvió lo que pide la compuerta las atraviesan (y, con `climb=true`, las trepan). El servidor decide con el progreso del equipo; al cliente le manda las celdas abiertas cerca (`entrelumen:ruin_gates`), cada medio segundo y al resolver, para que prediga su movimiento. Mobs e ítems nunca pasan. No asfixian ni tapan la vista.

### Piezas clave y pedestal

- Diez ítems (`signal_ember`, `terra_blueprint`, `route_seal`, `mother_seed`, `heliodor_crucible`, `voices_eyepiece`, `forest_testimony`, `sun_key`, `sacred_flame`, `star_chart`): stack de 1, épicos los de las gigantes y raros los de las medianas, resistentes al fuego, sin receta, con una línea de lore.
- El pedestal da la pieza cuando el equipo resolvió todo lo que pide. Cada copia queda ligada a la campaña y a una generación. Si el equipo ya lleva la actual (inventario, ender chest o cursor de algún miembro conectado), no da otra. Si no la lleva, da una nueva generación, y la vieja se desvanece en cuanto alguien la lleve encima. Con el proyecto entregado, no da nada.
- La entrega en el Atlas sólo cuenta la copia vigente del propio equipo (o la heredada del fundador); una pieza sin ligar (comandos, creativo) cuenta.
- Sin el mod de una ruina de dimensión, su pieza pasa al pedestal de la gigante del mismo acto (hoy: Santuario y Antesala al Observatorio del Risco).
- **En la campaña.** Cada pieza es un requisito más del proyecto de su ruina en `campaign/projects.json`, y la quest que lo entrega la nombra («Llevá también…»). La tabla del plantel nombra quests; donde la quest no es un proyecto se usó el proyecto de su rama: `voices_lens` y `voices_spirits` llevan a `spectral_archive` (Ocular y Testimonio), y `voices_sun_spirit`, que es la observación del Sun Spirit, a `heliodor_heart` (Llave del Sol).

### Jefes

Cuando un miembro de un equipo que no lo venció entra en el radio, se levantan sus mobs para ese equipo: con nombre, los atributos del JSON y una barra de jefe que ven los jugadores a 48 bloques. Matarlos a todos lo resuelve para ese equipo. Si el equipo se aleja 30 s, desaparecen y vuelven cuando regrese; los que quedaron guardados en un chunk no vuelven. En pacífico, el guardián deja pasar.

### Visita, lore y brújula

Entrar al volumen de `lore` registra la visita del equipo y le muestra el lore de la ruina. Cada ruina es un objetivo de la brújula (`content/compass_targets.json`: ancla `entrelumen:<id>` y condición `ruin`), antes del objetivo que espera su proyecto; avanza con la visita, la pieza o el proyecto entregado, y deja ese fragmento en el Atlas. El resto de los objetivos quedó marcado `draft`.

### Loot

Una tabla por ruina, por acto (`chests/ruin_act<N>_<ruina>`), sólo con ítems vanilla. El Taller conserva `chests/ruin_act2_workshop` con el Brazo de Terra en un barril y usa `ruin_act2_workshop_stores` en el resto.

### Comandos

`/entrelumen admin ruins list`, `place <id> [here]`, `tp <id>` y `reset <id>` (borra el progreso de todos los equipos en esa ruina; los bloques quedan).

### Arte provisorio

Modelos que usan texturas vanilla, sin copiarlas: pedestal de toba cincelada con tapa de cobre; espejo de hierro sobre un poste de cobre (recto y diagonal); maceta o altar de cuarzo para las ofrendas; sello de vidrio celeste; piedra floja y compuertas con la textura de su `look`; núcleo de lámpara de redstone. Las piezas usan íconos vanilla (carga ígnea, mapa, llave de desafío, vaina de jarra, caldero, catalejo, libro escrito, llave siniestra, polvo de blaze, mapa lleno).

Hace falta, en 16×16 y con referencias inspeccionadas según `DESIGN.md`:

1. Diez íconos de pieza: Brasa de la Señal, Plano de Terra, Sello de Ruta, Semilla Madre, Crisol de Heliodor, Ocular de las Voces, Testimonio del Bosque, Llave del Sol, Llama Sagrada, Carta Estelar.
2. Pedestal de la pieza (bloque con forma propia, tapa encendida).
3. Espejo en dos modelos, recto y a 45°, con la cara plateada distinguible desde lejos.
4. Receptáculo lleno y vacío, en maceta y en altar.
5. Sello de luz (translúcido) y piedra floja.
6. Núcleo de cerradura apagado y encendido.

### Pruebas

- JUnit: `RuinMarkersTest`, `RuinRulesTest`, `RuinDefinitionsTest`, `RuinTemplatesTest` (cada plantilla tiene lo que su definición pide) y `CompassTargetsTest`.
- GameTests (`RuntimeGameTestsRuins`): plantillas con bloques conocidos; una gigante en el Overworld por búsqueda de anillo (registro, protección, cimiento, ancla); el Templo por partes; la Fundición en una caverna del Nether y el Observatorio flotando en el End; cada tipo de desafío resuelto y reiniciado por equipo; la compuerta; el pedestal que da y repone; el jefe con su barra; la adopción de piezas sin mod; la visita. Además, cada una de las diez ruinas se juega entera desde sus propios marcadores (`*PlaysFromItsMarkers`): sin coordenadas escritas en las pruebas.

## Pendiente

- Arte de las diez ruinas a escala final (Elias revisa una por una). El exportador y las pruebas sólo dependen del contrato de marcadores.
- Los íconos y modelos de la lista de arte provisorio.
