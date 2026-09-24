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
| Solsticio | VI | `art/solsticio/city.py` | 105×78×105 | En la rama `feature/solsticio` |
| Nether, Twilight Forest, Aether y End | — | — | — | Pendiente |

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

Pedido de Elias del 24 de septiembre: un brazo ciborg hecho por Terra, curio, que da +5 de alcance de bloques y nada más.

- **Ítem.** `entrelumen:terra_arm` («Terra's Arm» / «Brazo de Terra»): stack de 1, rareza épica, resistente al fuego, sin durabilidad y sin receta. El tooltip es una sola línea de lore: «Terra se lo hizo para llegar adonde la máquina no la dejaba».
- **Slot.** `hands` de Curios 9.5.1, el de la versión fijada en el catálogo. Una prótesis de brazo va en la mano y no en `bracelet`, y en el pack ya se lo asignan al jugador Cataclysm, Artifacts, Occultism e Industrial Foregoing, con dos espacios. El tag `curios:hands` hace que Curios lo acepte. `data/entrelumen/curios/entities/terra_arm.json` le asigna `hands` al jugador aunque esos mods salgan del pack. Curios ya trae el nombre del slot en inglés y en español.
- **Efecto.** Mientras está puesto, suma un modificador aditivo y transitorio de +5, con ID `entrelumen:terra_arm_reach`, a `minecraft:player.block_interaction_range`. El alcance de entidades no cambia. Dos brazos dan el mismo +5.
- **Integración con Curios.** El acompañante no compila contra Curios. `CuriosCompat` resuelve por reflexión, una sola vez, `CuriosApi.getCuriosInventory` e `ICuriosItemHandler.isEquipped`, igual que hace `SilentGearCompat`. Cada 10 ticks el servidor le pregunta a Curios si el jugador tiene el brazo en un slot activo y pone o saca el modificador para que coincida. No se usa el componente `curios:attribute_modifiers`: en Curios 9.5.1 ese camino le cambia el ID al modificador por el del slot (`curios:hands0`), y con dos slots de manos, sacarse uno de dos brazos borraría el bono del otro. Sin Curios, el ítem existe, se puede llevar o guardar y no hace nada.
- **Cómo se consigue.** Sale garantizado de la loot table `entrelumen:chests/ruin_act2_workshop`, que queda lista para cuando se coloque el cofre del taller. Mientras las ruinas no estén en el mundo, es la recompensa del proyecto de campaña `lost_workshop`, que cierra el archivo del acto II; la quest `crafts_archive` refleja ese hito. El generador de quests no se tocó: no admite recompensas y el capítulo del acto II está congelado por hash.
- **Pruebas.** `TerraArmDataTest` (JUnit) valida los datos. `RuntimeGameTestsTerraArm` corre en el servidor de GameTest sin Curios y prueba el ítem, el cofre del taller, que llevarlo o ponerlo en slots vanilla no hace nada, y la lógica del +5 llamada directo. El test del acto II espera un brazo al cerrar `lost_workshop`. `TerraArmFullpackGameTests` (poner uno o dos brazos en `hands` y ver el +5) queda para la QA de pack completo, que todavía no lo corrió. Una copia temporal, que no se commiteó, corrió el 24/9 en el servidor de GameTest con sólo Curios 9.5.1 agregado y dos slots de manos, y pasó. El alcance quedó en 9,5 con uno o dos brazos, siguió en 9,5 al sacar uno y volvió a 4,5 sin ninguno; el de entidades quedó en 3,0.

## Pendiente

- Ícono y modelo del Brazo de Terra (`art/build_art.py`, en `main`).
- Diseñar las cuatro ruinas de otras dimensiones.
- Colocar las ruinas de los actos. Tienen que ser raras pero accesibles, sin caminatas de miles de bloques. Hay que conectarlas con la lista de objetivos de la brújula y con la protección de `feature/solsticio`.
- Poner en cada ruina la pieza clave del acto, un minidesafío, su altar o artefacto, loot y lore que se desbloquea una sola vez.
