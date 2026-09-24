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
| Taller hundido | II | — | — | Pendiente |
| Invernadero-domo (domo de vidrio roto, árbol en flor) | III | `ruins_acts.py` | 21×14×21 | Boceto, sin colocación |
| Observatorio en un risco (torre, telescopio cenital) | IV | `ruins_acts.py` | 17×25×17 | Boceto, sin colocación |
| Templo de la Luz Sagrada | V | — | — | Pendiente |
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

## Pendiente

- Diseñar el Taller hundido, el Templo de la Luz Sagrada y las cuatro ruinas de otras dimensiones.
- Colocar las ruinas de los actos. Tienen que ser raras pero accesibles, sin caminatas de miles de bloques. Hay que conectarlas con la lista de objetivos de la brújula y con la protección de `feature/solsticio`.
- Poner en cada ruina la pieza clave del acto, un minidesafío, su altar o artefacto, loot y lore que se desbloquea una sola vez.
