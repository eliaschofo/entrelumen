# Ruinas de Heliodor

Diseño del controlador, 24 de septiembre de 2026. Sigue la [biblia de la historia](story-bible.md): pocas ruinas, memorables, una por acto, indestructibles, y todo simétrico. Es un **boceto**: nadie las vio en el juego y el arte va a cambiar.

## Herramientas

- `art/structures/voxkit.py`: diccionario de bloques con colocación simétrica D4 (`sym`), comprobación `is_symmetric()`, vista isométrica con el color promedio de las texturas vanilla y lectura de NBT para renderizar referencias.
- Cada ruina es una función `design()` en coordenadas centradas. La capa 0 es el piso que reemplaza la superficie del terreno.
- Las vistas previas van a `art/structures/preview/`.

## Estado

| Ruina | Acto | Archivo | Tamaño | Estado |
|---|---|---|---|---|
| Ruina inicial (patio del sol, pedestal de la brújula) | inicio | `ruin_start.py` → `tools/build_heliodor_ruin_start.py` | 15×9×15 | En el juego: reemplaza la plantilla provisional de 7×3×7 |
| Patio del Atlas (claustro, atril con el Atlas) | I | `ruin_atlas.py` | 21×8×21 | Boceto, sin colocación |
| Taller hundido (foso con cuatro escaleras, motor de cobre ahogado) | II | `ruins_acts.py` | 19×7×19, 5 bajo el suelo | Boceto, sin colocación |
| Invernadero-domo (domo de vidrio roto, árbol en flor) | III | `ruins_acts.py` | 21×14×21 | Boceto, sin colocación |
| Observatorio en un risco (torre, telescopio cenital) | IV | `ruins_acts.py` | 17×25×17 | Boceto, sin colocación |
| Templo de la Luz Sagrada (terrazas de calcita, cráter de la fusión, anillo roto) | V | `ruins_acts.py` | 27×14×27 | Boceto, sin colocación |
| Solsticio | VI | `art/solsticio/city6.py` | 250×189×239 | En el juego (ciudad orgánica v6) |
| Fundición bajo la lava (crisol fundido, contrafuertes de basalto, caños de cobre) | Nether | `ruins_dims.py` | 29×16×29 | Boceto, sin colocación |
| Santuario (círculo de menhires, estanque, árbol en flor con luciérnagas) | Twilight Forest | `ruins_dims.py` | 27×19×27 | Boceto, sin colocación |
| Antesala del Sun Spirit (plataforma de cuarzo y oro entre nubes, puertas del sol, escalera de luz) | Aether | `ruins_dims.py` | 31×17×31 | Boceto, sin colocación; los bloques del Aether pueden reemplazar a los vanilla |
| Observatorio sobre el vacío (anillo telescopio de purpur, pilones de obsidiana, piso abierto al vacío) | End | `ruins_dims.py` | 27×23×27 | Boceto, sin colocación |

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
- **Cómo se consigue.** Sale garantizado de la loot table `entrelumen:chests/ruin_act2_workshop`, que queda lista para cuando se coloque el cofre del taller. Mientras las ruinas no estén en el mundo, es la recompensa del proyecto de campaña `lost_workshop`, que cierra el archivo del acto II; la quest `crafts_archive` refleja ese hito. El generador de quests no se tocó: no admite recompensas y el capítulo del acto II está congelado por hash.
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
| 1 | Torre de la Señal | Overworld | I | **gigante** | Brasa de la Señal / Signal Ember | `signal` | Exploración (pisos derrumbados) + luz (cuatro braseros en el orden de los vitrales) |
| 2 | Taller hundido | Overworld | II | **gigante** | Plano de Terra / Terra's Blueprint (+ Brazo de Terra) | `crafts_archive` | Mecanismo (reactivar el motor, vaciar el foso y abrir la bóveda) + combate liviano |
| 3 | Viaducto | Overworld | III | **gigante** | Sello de Ruta / Route Seal | `exchange_archive` | Combate (Guardián del Peaje, jefe con barra) + exploración por los arcos |
| 4 | Invernadero-domo | Overworld | III | mediana | Semilla Madre / Mother Seed | `exchange_nursery` | Ofrenda (plantar cuatro retoños en los canteros para abrir la cripta) |
| 5 | Fundición bajo la lava | Nether | III | mediana | Crisol de Heliodor / Heliodor Crucible | `exchange_power` | Combate (oleada) + cerradura de redstone |
| 6 | Observatorio del Risco | Overworld | IV | **gigante** | Ocular de las Voces / Eyepiece of Voices | `voices_lens` | Luz (orientar espejos hasta el telescopio) + exploración |
| 7 | Santuario | Twilight Forest | IV | mediana | Testimonio del Bosque / Forest Testimony | `voices_spirits` | Exploración oculta (orden de menhires, bodega entre raíces) |
| 8 | Antesala del Sol | Aether | IV | mediana | Llave del Sol / Sun Key | `voices_sun_spirit` | Ofrenda + parkour entre nubes |
| 9 | Templo de la Luz Sagrada | Overworld | V | **gigante** | Llama Sagrada / Sacred Flame | `world_network` | Luz + ofrenda + combate (Custodio de la Luz) |
| 10 | Observatorio sobre el vacío | End | V | mediana | Carta Estelar / Star Chart | `world_settlement` | Exploración y parkour sobre el vacío + combate |
| 11 | Solsticio | su dimensión | VI | **gigante** | — | — | ciudad (sistema propio) |

Los bocetos de `ruins_acts.py`, `ruins_dims.py` y `ruin_atlas.py` quedan como punto de partida: el arte final se redibuja a escala. El Patio del Atlas sale del plantel porque el acto I ya tiene su gigante y es la primera hora.

### Reglas del sistema

- **Colocación en el Overworld.** El companion coloca cada ruina una vez por mundo, cuando el primer equipo abre el acto. Busca sitio en un anillo alrededor del spawn: 250–500 bloques en el acto I y 400–1200 después. Pide terreno apto (pendiente, sin agua ni árboles grandes) y evita Solsticio y las otras ruinas. La colocación espera a que los chunks estén cargados. La ruina queda en `RuinData`, protegida por `StructureProtection`, y es ancla de la brújula.
- **Colocación en dimensiones.** La primera llegada de cualquier jugador elige un sitio a 150–500 bloques del punto de llegada.
- **Pieza clave.** Un pedestal la entrega una vez por equipo, después de que ese equipo resuelve el desafío. Si se pierde antes de entregarla al Atlas, el pedestal la devuelve. No tiene receta.
- **Desafíos por equipo.** Cada equipo resuelve el suyo. La bóveda se abre para los miembros del equipo que la resolvió (bloque con puerta por equipo).
- **Loot.** Cofres de Lootr, por jugador, con tablas por acto. Lore del Atlas que se desbloquea una vez al entrar.
- **Indestructible.** Todo el volumen registrado. Sólo se usan palancas, botones, braseros, espejos y los mecanismos del desafío.
- **Jefes.** Mobs con barra de jefe, nombre y atributos propios. Reaparecen para cada equipo que todavía no resolvió el desafío.
- **Sin mod.** Una ruina de dimensión cuyo mod falta se saltea y su pieza pasa al pedestal de la gigante del mismo acto.

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

## Pendiente

- Arte de las diez ruinas a escala final. Orden: Torre de la Señal, Taller hundido, Viaducto, Observatorio del Risco, Templo, y después las medianas.
- Implementación del sistema (worker): colocación, pedestales, desafíos, jefes, loot, lore y cableado de las piezas en los proyectos de campaña.
